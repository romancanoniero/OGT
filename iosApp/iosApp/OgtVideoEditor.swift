import UIKit
import AVFoundation
import CoreMedia
import AudioToolbox
import ComposeApp

/// UIKit's own trimming timeline. Only the verified, compressed copy reaches Kotlin/upload.
final class OgtIosVideoEditorHost: NSObject, IosVideoEditorHost, UIVideoEditorControllerDelegate, UINavigationControllerDelegate {
    static let shared = OgtIosVideoEditorHost()
    private enum Phase { case idle, editing, copying, preparing, recovering }
    private var phase = Phase.idle
    private var completion: ((String?, String?, String?) -> Void)?
    private var editor: UIVideoEditorController?
    private var preparing: OgtVideoPreparationController?
    private var watcher: Timer?
    private var watchdog: VideoPreparationWatchdog?
    private var fallback: UIViewController?
    private var work: Task<Void, Never>?
    private var source = ""
    private var maximumMs: Int64 = 0
    private var maximumBytes: Int64 = 0
    private var generation = UUID()

    func edit(path: String, maxDurationMs: Int64, maxBytes: Int64, onResult: @escaping (String?, String?, String?) -> Void) {
        guard phase == .idle, completion == nil else { onResult(nil, nil, "Terminá de editar el video anterior."); return }
        completion = onResult; source = path; maximumMs = maxDurationMs; maximumBytes = maxBytes
        generation = UUID()
        presentEditor()
    }

    private func presentEditor() {
        guard completion != nil, phase == .idle || phase == .recovering else { return }
        phase = .editing
        guard FileManager.default.fileExists(atPath: source) else {
            finish(nil, nil, "El video original ya no está en el dispositivo. Elegilo otra vez."); return
        }
        guard let presenter = Self.topController() else {
            finish(nil, nil, "No se pudo abrir el editor. Volvé a intentarlo."); return
        }
        if !UIVideoEditorController.canEditVideo(atPath: source) {
            let controller = OgtFallbackTrimmer(path: source, maximumMs: maximumMs)
            controller.onCancel = { [weak self] in self?.cancel() }
            controller.onSave = { [weak self, weak controller] start, duration in
                guard let self, self.phase == .editing else { return }
                self.phase = .copying
                let ticket = self.generation, original = self.source
                controller?.dismiss(animated: true) {
                    guard ticket == self.generation, self.phase == .copying else { return }
                    self.fallback = nil
                    self.prepare(path: original, start: start, duration: duration)
                }
            }
            controller.modalPresentationStyle = .fullScreen
            fallback = controller
            presenter.present(controller, animated: true)
            return
        }
        let controller = UIVideoEditorController()
        controller.videoPath = source
        controller.videoMaximumDuration = maximumMs > 0 ? Double(maximumMs) / 1000 : 0
        controller.videoQuality = .typeHigh
        controller.delegate = self
        controller.modalPresentationStyle = .fullScreen
        editor = controller
        presenter.present(controller, animated: true)
    }

    func videoEditorControllerDidCancel(_ editor: UIVideoEditorController) {
        guard self.editor === editor, phase == .editing || phase == .copying else { return }
        cancel()
    }
    func videoEditorController(_ editor: UIVideoEditorController, didFailWithError error: Error) {
        guard self.editor === editor, phase == .editing else { return }
        afterDismissing(editor) { self.finish(nil, nil, "No se pudo recortar el video. El original sigue intacto.") }
    }
    func videoEditorController(_ editor: UIVideoEditorController, didSaveEditedVideoToPath editedVideoPath: String) {
        guard phase == .editing, self.editor === editor else {
            NSLog("OGTVideo ignored duplicate native save")
            return
        }
        phase = .copying
        let ticket = generation
        let ownsNativeOutput = editedVideoPath != source
        // Own the file before releasing UIKit's editor and its temporary output.
        work = Task { @MainActor in
            do {
                let stable = try await Task.detached(priority: .userInitiated) {
                    let directory = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("ogt-video-input", isDirectory: true)
                    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
                    let copy = directory.appendingPathComponent(UUID().uuidString).appendingPathExtension("mov")
                    try FileManager.default.copyItem(atPath: editedVideoPath, toPath: copy.path)
                    return copy.path
                }.value
                guard ticket == self.generation else {
                    Self.remove(stable)
                    if ownsNativeOutput { Self.remove(editedVideoPath) }
                    return
                }
                self.afterDismissing(editor) {
                    if ownsNativeOutput { Self.remove(editedVideoPath) }
                    guard ticket == self.generation else { Self.remove(stable); return }
                    self.editor = nil
                    // Finish UIKit's dismissal before presenting the next controller.
                    DispatchQueue.main.async {
                        guard ticket == self.generation else { Self.remove(stable); return }
                        self.prepare(path: stable)
                    }
                }
            } catch {
                guard ticket == self.generation else { return }
                self.afterDismissing(editor) {
                    if ownsNativeOutput { Self.remove(editedVideoPath) }
                    self.finish(nil, nil, "No pudimos conservar el recorte. El original sigue intacto.")
                }
            }
        }
    }

    private func prepare(path: String, start: Double = 0, duration: Double? = nil) {
        guard completion != nil else { if path != source { Self.remove(path) }; return }
        guard phase == .copying else { NSLog("OGTVideo ignored duplicate preparation"); return }
        phase = .preparing
        let ticket = generation
        NSLog("OGTVideo prepare session=%@", ticket.uuidString)
        let ownsInput = path != source
        showPreparing()
        watchdog = VideoPreparationWatchdog(startedAtMs: Self.clockMs, stallAfterMs: 90_000, totalAfterMs: 600_000)
        // The clock and recovery must not depend on the executor doing media work.
        let timer = Timer(timeInterval: 1, target: self, selector: #selector(checkPreparationProgress(_:)), userInfo: ticket.uuidString, repeats: true)
        watcher = timer
        RunLoop.main.add(timer, forMode: .common)
        let limit = maximumMs, budget = maximumBytes
        work = Task { @MainActor in
            defer { if ownsInput { Self.remove(path) } }
            do {
                let result = try await OgtVideoCompression.prepare(path: path, maximumMs: limit, maximumBytes: budget, start: start, selectedDuration: duration) { [weak self] stage, fraction in
                    guard let self, ticket == self.generation else { return }
                    self.watchdog?.observe(stage: stage, progressPermille: fraction.map { Int32(($0 * 1000).rounded(.down)) } ?? -1, nowMs: Self.clockMs)
                    self.preparing?.update(stage: stage, fraction: fraction)
                }
                guard ticket == self.generation else { Self.remove(result.0); Self.remove(result.1); return }
                self.stopWatching()
                self.dismissPreparing {
                    guard ticket == self.generation else { Self.remove(result.0); Self.remove(result.1); return }
                    self.finish(result.0, result.1, nil)
                }
            } catch {
                guard ticket == self.generation else { return }
                self.stopWatching()
                if error is CancellationError { self.dismissPreparing { self.finish(nil, nil, nil) }; return }
                NSLog("OGTVideo preparation failed domain=%@ code=%ld", (error as NSError).domain, (error as NSError).code)
                self.recover(message: "No pudimos leer o preparar este video. Volvé a recortarlo o elegí otro archivo; el original se conserva.")
            }
        }
    }

    private func recover(message: String) {
        phase = .recovering
        let ticket = generation
        dismissPreparing {
            guard ticket == self.generation, self.completion != nil else { return }
            let alert = UIAlertController(title: "No se pudo preparar el video", message: message, preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "Volver a recortar", style: .default) { _ in self.presentEditor() })
            alert.addAction(UIAlertAction(title: "Cancelar", style: .cancel) { _ in self.finish(nil, nil, nil) })
            if let presenter = Self.topController() { presenter.present(alert, animated: true) }
            else { self.finish(nil, nil, message) }
        }
    }
    private func dismissPreparing(_ completion: @escaping () -> Void) {
        let controller = preparing
        preparing = nil
        if let controller, controller.presentingViewController != nil { controller.dismiss(animated: true, completion: completion) }
        else { completion() }
    }
    private func afterDismissing(_ controller: UIViewController, completion: @escaping () -> Void) {
        if controller.isBeingDismissed, let transition = controller.transitionCoordinator {
            if !transition.animate(alongsideTransition: nil, completion: { _ in completion() }) {
                DispatchQueue.main.async(execute: completion)
            }
        } else if controller.presentingViewController != nil {
            controller.dismiss(animated: true, completion: completion)
        } else {
            completion()
        }
    }
    @objc private func checkPreparationProgress(_ timer: Timer) {
        guard timer.userInfo as? String == generation.uuidString, let monitor = watchdog else { return }
        monitor.setPaused(paused: UIApplication.shared.applicationState != .active, nowMs: Self.clockMs)
        preparing?.updateElapsed(monitor.elapsedMs(nowMs: Self.clockMs))
        if let message = monitor.timeoutMessage(nowMs: Self.clockMs) {
            generation = UUID()
            work?.cancel()
            stopWatching()
            recover(message: message)
        }
    }
    private func stopWatching() { watcher?.invalidate(); watcher = nil; watchdog = nil }
    private static var clockMs: Int64 { Int64(ProcessInfo.processInfo.systemUptime * 1000) }

    func cancel() {
        generation = UUID(); stopWatching(); work?.cancel(); work = nil
        let active = editor ?? fallback ?? preparing
        if let active { active.dismiss(animated: true) { self.finish(nil, nil, nil) } }
        else { finish(nil, nil, nil) }
    }
    private func finish(_ path: String?, _ poster: String?, _ error: String?) {
        stopWatching()
        let callback = completion
        phase = .idle
        completion = nil; editor = nil; fallback = nil; preparing = nil; work = nil
        callback?(path, poster, error)
    }
    private func showPreparing() {
        let controller = OgtVideoPreparationController()
        controller.onCancel = { [weak self] in self?.cancel() }
        controller.modalPresentationStyle = .fullScreen
        preparing = controller
        Self.topController()?.present(controller, animated: false)
    }
    private static func topController() -> UIViewController? {
        let windows = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap { $0.windows }
        var top = windows.first { $0.isKeyWindow }?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
    private static func remove(_ path: String) { try? FileManager.default.removeItem(atPath: path) }
}

/// Metadata, encoding and poster extraction run off the UI actor. Every export is checked
/// for both size and full selected duration: fileLengthLimit must never silently truncate it.
enum OgtVideoCompression {
    /// Copy the already prepared video samples without an audio track or another encode.
    static func removingAudio(path: String) async throws -> String {
        let task = Task.detached(priority: .userInitiated) { () throws -> String in
            let asset = AVURLAsset(url: URL(fileURLWithPath: path))
            let composition = AVMutableComposition()
            let originalDuration = try await readMetadata(asset: asset) {
                let sources = try await asset.loadTracks(withMediaType: .video)
                guard !sources.isEmpty else { throw PreparationError.exportFailed }
                let duration = try await asset.load(.duration).seconds
                for source in sources {
                    guard let video = composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid) else { throw PreparationError.exportFailed }
                    let range = try await source.load(.timeRange)
                    try video.insertTimeRange(range, of: source, at: range.start)
                    video.preferredTransform = try await source.load(.preferredTransform)
                }
                return duration
            }
            try Task.checkCancellation()
            let directory = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("ogt-video", isDirectory: true)
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            let output = directory.appendingPathComponent(UUID().uuidString).appendingPathExtension("mp4")
            var succeeded = false
            defer { if !succeeded { try? FileManager.default.removeItem(at: output) } }
            guard let exporter = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetPassthrough) else { throw PreparationError.exportFailed }
            exporter.outputURL = output; exporter.outputFileType = .mp4; exporter.shouldOptimizeForNetworkUse = true
            exporter.metadata = try await readMetadata(asset: asset) { try await asset.load(.metadata) }
            let handle = ExportHandle(exporter), gate = ExportCompletion()
            try await withTaskCancellationHandler(operation: {
                try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                    guard gate.install(continuation) else { return }
                    handle.session.exportAsynchronously {
                        if handle.session.status == .completed { gate.finish(.success(())) }
                        else { gate.finish(.failure(handle.session.error ?? PreparationError.exportFailed)) }
                    }
                }
            }, onCancel: {
                gate.finish(.failure(CancellationError()))
                DispatchQueue.global(qos: .userInitiated).async { handle.session.cancelExport() }
            })
            try Task.checkCancellation()
            let result = AVURLAsset(url: output)
            let valid = try await readMetadata(asset: result) {
                let duration = try await result.load(.duration).seconds
                let audio = try await result.loadTracks(withMediaType: .audio)
                let video = try await result.loadTracks(withMediaType: .video)
                return audio.isEmpty && !video.isEmpty && abs(duration - originalDuration) <= 0.1
            }
            let bytes = try output.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
            guard valid, bytes > 0, bytes <= 8 * 1024 * 1024 else { throw PreparationError.exportFailed }
            try Task.checkCancellation()
            succeeded = true
            return output.path
        }
        return try await withTaskCancellationHandler(operation: { try await task.value }, onCancel: { task.cancel() })
    }
    static func prepare(path: String, maximumMs: Int64, maximumBytes: Int64, start: Double = 0, selectedDuration: Double? = nil, onProgress: @escaping @MainActor @Sendable (String, Float?) -> Void = { _, _ in }) async throws -> (String, String) {
        let task = Task.detached(priority: .userInitiated) { () throws -> (String, String) in
            await onProgress("Leyendo información del video…", nil)
            let asset = AVURLAsset(url: URL(fileURLWithPath: path))
            let metadata = try await readMetadata(asset: asset) {
                NSLog("OGTVideo metadata duration start")
                let seconds = try await asset.load(.duration).seconds
                await onProgress("Leyendo las pistas del video…", nil)
                let video = try await asset.loadTracks(withMediaType: .video).first
                guard let video else { throw PreparationError.exportFailed }
                let size = try await video.load(.naturalSize)
                let rate = try await video.load(.estimatedDataRate)
                let formats = try await video.load(.formatDescriptions)
                await onProgress("Comprobando el audio original…", nil)
                let audio = try await asset.loadTracks(withMediaType: .audio)
                var compatibleAudio = true
                for track in audio {
                    let descriptions = try await track.load(.formatDescriptions)
                    if descriptions.contains(where: { CMFormatDescriptionGetMediaSubType($0) != kAudioFormatMPEG4AAC }) { compatibleAudio = false }
                }
                let optimized = rate <= 2_000_000 && max(abs(size.width), abs(size.height)) <= 1280 && formats.first.map { CMFormatDescriptionGetMediaSubType($0) == kCMVideoCodecType_H264 } == true && compatibleAudio
                NSLog("OGTVideo metadata ready")
                return SourceMetadata(duration: seconds, optimized: optimized, hasAudio: !audio.isEmpty)
            }
            try Task.checkCancellation()
            let sourceDuration = metadata.duration
            let duration = selectedDuration ?? sourceDuration
            guard start >= 0, start + duration <= sourceDuration + 0.001, duration.isFinite, duration > 0, maximumMs == 0 || duration * 1000 <= Double(maximumMs) + 0.5 else { throw PreparationError.invalidDuration }
            let root = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("ogt-video", isDirectory: true)
            try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
            let output = root.appendingPathComponent(UUID().uuidString).appendingPathExtension("mp4")
            let poster = root.appendingPathComponent(UUID().uuidString).appendingPathExtension("jpg")
            var succeeded = false
            defer { if !succeeded { try? FileManager.default.removeItem(at: output); try? FileManager.default.removeItem(at: poster) } }
            let presets = (metadata.optimized ? [AVAssetExportPresetPassthrough] : []) + [AVAssetExportPreset1280x720, AVAssetExportPresetMediumQuality, AVAssetExportPreset640x480, AVAssetExportPresetLowQuality]
            for (index, preset) in presets.enumerated() {
                try Task.checkCancellation()
                try? FileManager.default.removeItem(at: output)
                guard let exporter = AVAssetExportSession(asset: asset, presetName: preset) else { continue }
                exporter.outputURL = output; exporter.outputFileType = .mp4
                exporter.shouldOptimizeForNetworkUse = true
                // AVFoundation may exceed this target with audio/container overhead.
                // Keep headroom and validate the complete exported file below.
                exporter.fileLengthLimit = maximumBytes * 85 / 100
                exporter.timeRange = CMTimeRange(start: CMTime(seconds: start, preferredTimescale: 600), duration: CMTime(seconds: duration, preferredTimescale: 600))
                let handle = ExportHandle(exporter)
                let gate = ExportCompletion()
                let stage = index == 0 ? "Comprimiendo video" : "Optimizando tamaño · intento \(index + 1) de \(presets.count)"
                await onProgress(stage, 0)
                let monitoring = Task {
                    while !Task.isCancelled {
                        let value = handle.session.progress
                        await onProgress(stage, value.isFinite ? min(1, max(0, value)) : nil)
                        do { try await Task.sleep(nanoseconds: 250_000_000) } catch { return }
                    }
                }
                defer { monitoring.cancel() }
                do {
                    try await withTaskCancellationHandler(operation: {
                        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                            guard gate.install(continuation) else { return }
                            handle.session.exportAsynchronously {
                                if handle.session.status == .completed { gate.finish(.success(())) }
                                else { gate.finish(.failure(handle.session.error ?? PreparationError.exportFailed)) }
                            }
                        }
                    }, onCancel: {
                        // Cancellation must return even if AVFoundation never invokes its callback.
                        gate.finish(.failure(CancellationError()))
                        DispatchQueue.global(qos: .userInitiated).async { handle.session.cancelExport() }
                    })
                    monitoring.cancel()
                    await onProgress("Comprobando audio, duración y tamaño…", nil)
                    try Task.checkCancellation()
                    let result = AVURLAsset(url: output)
                    let validation = try await readMetadata(asset: result) {
                        let duration = try await result.load(.duration).seconds
                        let tracks = try await result.loadTracks(withMediaType: .video)
                        let audio = try await result.loadTracks(withMediaType: .audio)
                        return (duration, !tracks.isEmpty, !audio.isEmpty)
                    }
                    let resultDuration = validation.0
                    try Task.checkCancellation()
                    let size = try output.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
                    guard validation.1, !metadata.hasAudio || validation.2,
                          size > 0, Int64(size) <= maximumBytes,
                          abs(resultDuration - duration) <= 0.1,
                          maximumMs == 0 || resultDuration * 1000 <= Double(maximumMs) + 0.5 else { continue }
                    await onProgress("Creando la vista previa…", nil)
                    let generator = AVAssetImageGenerator(asset: result)
                    generator.appliesPreferredTrackTransform = true
                    generator.maximumSize = CGSize(width: 720, height: 720)
                    let cg = try generator.copyCGImage(at: .zero, actualTime: nil)
                    guard let jpeg = UIImage(cgImage: cg).jpegData(compressionQuality: 0.82) else { throw PreparationError.exportFailed }
                    try jpeg.write(to: poster, options: .atomic)
                    succeeded = true
                    return (output.path, poster.path)
                } catch is CancellationError { throw CancellationError() }
                catch { monitoring.cancel(); try Task.checkCancellation(); continue }
            }
            throw PreparationError.exportFailed
        }
        return try await withTaskCancellationHandler(operation: { try await task.value }, onCancel: { task.cancel() })
    }
    // Configuration is finished before export starts. Only AVFoundation's cancellation
    // method crosses executors; completion reads status after AVFoundation calls it.
    private final class ExportHandle: @unchecked Sendable {
        let session: AVAssetExportSession
        init(_ session: AVAssetExportSession) { self.session = session }
    }
    private struct SourceMetadata: Sendable { let duration: Double; let optimized: Bool; let hasAudio: Bool }
    /// AVAsset async loads do not inherit Swift task cancellation. Cancel the asset
    /// and release our caller even when a framework callback never returns.
    private static func readMetadata<Value>(asset: AVAsset, operation: @escaping @Sendable () async throws -> Value) async throws -> Value {
        let gate = CompletionGate<Value>()
        let reader = Task.detached(priority: .userInitiated) {
            do {
                try Task.checkCancellation()
                let value = try await operation()
                try Task.checkCancellation()
                gate.finish(.success(value))
            }
            catch { gate.finish(.failure(error)) }
        }
        defer { reader.cancel() }
        return try await withTaskCancellationHandler(operation: {
            try await withCheckedThrowingContinuation { continuation in _ = gate.install(continuation) }
        }, onCancel: {
            NSLog("OGTVideo metadata cancelled")
            gate.finish(.failure(CancellationError()))
            DispatchQueue.global(qos: .userInitiated).async {
                reader.cancel()
                asset.cancelLoading()
            }
        })
    }
    private typealias ExportCompletion = CompletionGate<Void>
    /// Exactly one result wins when framework completion and cancellation race.
    private final class CompletionGate<Value>: @unchecked Sendable {
        private let lock = NSLock()
        private var continuation: CheckedContinuation<Value, Error>?
        private var result: Result<Value, Error>?
        func install(_ continuation: CheckedContinuation<Value, Error>) -> Bool {
            lock.lock()
            if let result { lock.unlock(); continuation.resume(with: result); return false }
            self.continuation = continuation
            lock.unlock()
            return true
        }
        func finish(_ result: Result<Value, Error>) {
            lock.lock()
            guard self.result == nil else { lock.unlock(); return }
            self.result = result
            let continuation = self.continuation
            self.continuation = nil
            lock.unlock()
            continuation?.resume(with: result)
        }
    }
    private enum PreparationError: Error { case invalidDuration, exportFailed }
}

/// Sound tools are independent of the editor's presentation/session state.
final class OgtIosVideoToolsHost: NSObject, IosVideoToolsHost {
    static let shared = OgtIosVideoToolsHost()
    private var work: Task<Void, Never>?
    private var generation = UUID()
    func removeAudio(path: String, onResult: @escaping (String?, String?) -> Void) {
        guard work == nil else { onResult(nil, "Esperá a que termine el cambio de sonido."); return }
        let ticket = UUID(); generation = ticket
        work = Task { @MainActor in
            defer { if ticket == self.generation { self.work = nil } }
            do {
                let output = try await OgtVideoCompression.removingAudio(path: path)
                guard ticket == self.generation else { try? FileManager.default.removeItem(atPath: output); return }
                onResult(output, nil)
            } catch {
                guard ticket == self.generation else { return }
                onResult(nil, "No se pudo quitar el sonido. El video original se conserva.")
            }
        }
    }
    func cancelAudio() { generation = UUID(); work?.cancel(); work = nil }
}

private final class OgtVideoPreparationController: UIViewController {
    var onCancel: (() -> Void)?
    private let spinner = UIActivityIndicatorView(style: .medium)
    private let stageLabel = UILabel()
    private let percent = UILabel()
    private let progress = UIProgressView(progressViewStyle: .default)
    private let elapsed = UILabel()
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        let title = UILabel(); title.text = "Preparando video"; title.font = .preferredFont(forTextStyle: .title2); title.accessibilityTraits = .header
        stageLabel.font = .preferredFont(forTextStyle: .body); stageLabel.textAlignment = .center; stageLabel.numberOfLines = 0
        percent.font = .monospacedDigitSystemFont(ofSize: 26, weight: .semibold)
        progress.progressTintColor = UIColor(red: 0.06, green: 0.24, blue: 0.30, alpha: 1)
        progress.trackTintColor = .systemGray5
        progress.accessibilityLabel = "Progreso de preparación"
        elapsed.font = .monospacedDigitSystemFont(ofSize: 14, weight: .regular); elapsed.textColor = .secondaryLabel
        let note = UILabel(); note.text = "El video original se conserva."; note.numberOfLines = 0; note.textAlignment = .center; note.font = .preferredFont(forTextStyle: .footnote); note.textColor = .secondaryLabel
        let cancel = UIButton(type: .system); cancel.setTitle("Cancelar", for: .normal)
        cancel.addAction(UIAction { [weak self] _ in self?.onCancel?() }, for: .touchUpInside)
        let stack = UIStackView(arrangedSubviews: [title, stageLabel, spinner, percent, progress, elapsed, note, cancel])
        stack.axis = .vertical; stack.alignment = .center; stack.spacing = 20; stack.translatesAutoresizingMaskIntoConstraints = false
        let scroll = UIScrollView(); scroll.translatesAutoresizingMaskIntoConstraints = false
        let content = UIView(); content.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scroll); scroll.addSubview(content); content.addSubview(stack)
        let guide = view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            scroll.leadingAnchor.constraint(equalTo: guide.leadingAnchor), scroll.trailingAnchor.constraint(equalTo: guide.trailingAnchor),
            scroll.topAnchor.constraint(equalTo: guide.topAnchor), scroll.bottomAnchor.constraint(equalTo: guide.bottomAnchor),
            content.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor), content.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor),
            content.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor), content.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
            content.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor), content.heightAnchor.constraint(greaterThanOrEqualTo: scroll.frameLayoutGuide.heightAnchor),
            stack.centerXAnchor.constraint(equalTo: content.centerXAnchor), stack.centerYAnchor.constraint(equalTo: content.centerYAnchor),
            stack.topAnchor.constraint(greaterThanOrEqualTo: content.topAnchor, constant: 24), stack.bottomAnchor.constraint(lessThanOrEqualTo: content.bottomAnchor, constant: -24),
            stack.leadingAnchor.constraint(greaterThanOrEqualTo: content.leadingAnchor, constant: 24), stack.trailingAnchor.constraint(lessThanOrEqualTo: content.trailingAnchor, constant: -24),
            stack.widthAnchor.constraint(lessThanOrEqualToConstant: 440), stageLabel.widthAnchor.constraint(equalTo: stack.widthAnchor), note.widthAnchor.constraint(equalTo: stack.widthAnchor),
            progress.widthAnchor.constraint(equalTo: stack.widthAnchor), progress.heightAnchor.constraint(equalToConstant: 6), cancel.heightAnchor.constraint(greaterThanOrEqualToConstant: 44),
        ])
        update(stage: "Leyendo información del video…", fraction: nil); updateElapsed(0)
    }
    func update(stage: String, fraction: Float?) {
        loadViewIfNeeded()
        stageLabel.text = stage
        if let fraction {
            spinner.stopAnimating(); spinner.isHidden = true; progress.isHidden = false; percent.isHidden = false
            progress.setProgress(fraction, animated: true)
            percent.text = "\(Int((fraction * 100).rounded(.down))) %"
            progress.accessibilityValue = percent.text
        } else {
            progress.isHidden = true; percent.isHidden = true; spinner.isHidden = false; spinner.startAnimating()
        }
    }
    func updateElapsed(_ milliseconds: Int64) { elapsed.text = String(format: "Tiempo transcurrido: %lld:%02lld", milliseconds / 60_000, (milliseconds / 1000) % 60) }
}

/// AVPlayer fallback for devices/formats where UIKit's legacy editor is unavailable.
/// Handles share the same constrained range model as Android, including accessibility.
private final class OgtFallbackTrimmer: UIViewController {
    var onSave: ((Double, Double) -> Void)?
    var onCancel: (() -> Void)?
    private let path: String
    private let maximumMs: Int64
    private var range = VideoTrimRange(startMs: 0, endMs: 0)
    private var sourceMs: Int64 = 0
    private var task: Task<Void, Never>?
    private var player: AVPlayer?
    private var observer: Any?
    private let preview = OgtPlayerView()
    private let timeline = OgtTrimTimeline()
    private let timeLabel = UILabel()
    private let status = UILabel()
    private let save = UIButton(type: .system)

    init(path: String, maximumMs: Int64) { self.path = path; self.maximumMs = maximumMs; super.init(nibName: nil, bundle: nil) }
    required init?(coder: NSCoder) { fatalError() }
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        let title = UILabel(); title.text = "Recortar video"; title.font = .preferredFont(forTextStyle: .title2)
        title.accessibilityTraits = .header
        let cancel = UIButton(type: .system); cancel.setTitle("Cancelar", for: .normal)
        cancel.addAction(UIAction { [weak self] _ in self?.onCancel?() }, for: .touchUpInside)
        save.setTitle("Usar video", for: .normal); save.isEnabled = false
        save.addAction(UIAction { [weak self] _ in
            guard let self else { return }
            guard self.save.isEnabled else { return }
            self.save.isEnabled = false
            self.player?.pause()
            self.onSave?(Double(self.range.startMs) / 1000, Double(self.range.durationMs) / 1000)
        }, for: .touchUpInside)
        let header = UIStackView(arrangedSubviews: [cancel, title, save]); header.distribution = .equalSpacing
        let play = UIButton(type: .system); play.setTitle("Reproducir recorte", for: .normal)
        play.addAction(UIAction { [weak self] _ in
            guard let self else { return }
            self.player?.seek(to: CMTime(seconds: Double(self.range.startMs)/1000, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
            self.player?.play()
        }, for: .touchUpInside)
        timeLabel.font = .monospacedDigitSystemFont(ofSize: 17, weight: .medium); timeLabel.textAlignment = .center
        status.font = .preferredFont(forTextStyle: .footnote); status.textColor = .secondaryLabel; status.numberOfLines = 0
        status.text = maximumMs > 0 ? "Mové los extremos para elegir hasta \(maximumMs / 1000) segundos." : "Mové los extremos para elegir el segmento."
        let stack = UIStackView(arrangedSubviews: [header, preview, play, timeline, timeLabel, status])
        stack.axis = .vertical; stack.spacing = 16; stack.translatesAutoresizingMaskIntoConstraints = false
        let scroll = UIScrollView(); scroll.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scroll); scroll.addSubview(stack)
        let guide = view.safeAreaLayoutGuide
        NSLayoutConstraint.activate([
            scroll.topAnchor.constraint(equalTo: guide.topAnchor), scroll.bottomAnchor.constraint(equalTo: guide.bottomAnchor),
            scroll.leadingAnchor.constraint(equalTo: guide.leadingAnchor), scroll.trailingAnchor.constraint(equalTo: guide.trailingAnchor),
            stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor, constant: 16),
            stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -16),
            stack.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor, constant: -16),
            stack.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor, constant: -32),
            preview.heightAnchor.constraint(equalTo: guide.heightAnchor, multiplier: 0.45), timeline.heightAnchor.constraint(equalToConstant: 72),
            header.heightAnchor.constraint(greaterThanOrEqualToConstant: 44),
        ])
        timeline.changed = { [weak self] start, end in self?.setRange(start, end) }
        task = Task { @MainActor in
            do {
                let asset = AVURLAsset(url: URL(fileURLWithPath: path))
                let seconds = try await asset.load(.duration).seconds
                guard seconds.isFinite, seconds > 0, try await asset.load(.isPlayable) else { throw NSError(domain: "video", code: 1) }
                try Task.checkCancellation()
                sourceMs = Int64((seconds * 1000).rounded(.down))
                range = VideoTrimRange(startMs: 0, endMs: maximumMs > 0 ? min(sourceMs, maximumMs) : sourceMs)
                timeline.duration = sourceMs
                updateRange()
                player = AVPlayer(playerItem: AVPlayerItem(asset: asset)); preview.playerLayer.player = player
                observer = player?.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.05, preferredTimescale: 600), queue: .main) { [weak self] time in
                    guard let self else { return }
                    if time.seconds >= Double(self.range.endMs)/1000 { self.player?.pause(); self.player?.seek(to: CMTime(seconds: Double(self.range.startMs)/1000, preferredTimescale: 600)) }
                }
                save.isEnabled = true
                let generator = AVAssetImageGenerator(asset: asset); generator.appliesPreferredTrackTransform = true; generator.maximumSize = CGSize(width: 180, height: 120)
                var frames: [UIImage] = []
                for index in 0..<8 {
                    try Task.checkCancellation()
                    let result = try await generator.image(at: CMTime(seconds: seconds * Double(index) / 8, preferredTimescale: 600))
                    frames.append(UIImage(cgImage: result.image))
                }
                timeline.frames = frames
            } catch is CancellationError {} catch { status.text = "No se pudo abrir este video. Elegí otro archivo." }
        }
    }
    private func setRange(_ start: Int64, _ end: Int64) {
        guard sourceMs > 0 else { return }
        range = OgtVideoEditorKt.constrainVideoTrim(old: range, startMs: start, endMs: end, sourceMs: sourceMs, maxMs: maximumMs > 0 ? KotlinLong(value: maximumMs) : nil)
        player?.pause()
        player?.seek(to: CMTime(seconds: Double(range.startMs)/1000, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
        updateRange()
    }
    private func updateRange() {
        timeline.start = range.startMs; timeline.end = range.endMs; timeline.setNeedsLayout()
        timeLabel.text = String(format: "%.1f s – %.1f s · %.1f s", Double(range.startMs)/1000, Double(range.endMs)/1000, Double(range.durationMs)/1000)
    }
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        task?.cancel(); player?.pause()
        if let observer { player?.removeTimeObserver(observer); self.observer = nil }
        preview.playerLayer.player = nil
    }
}

private final class OgtPlayerView: UIView {
    override class var layerClass: AnyClass { AVPlayerLayer.self }
    var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
    override init(frame: CGRect) { super.init(frame: frame); backgroundColor = .black; playerLayer.videoGravity = .resizeAspect; layer.cornerRadius = 16; clipsToBounds = true }
    required init?(coder: NSCoder) { fatalError() }
}

/// Native sliders provide reliable touch tracking and VoiceOver adjustment.
private final class OgtTrimSlider: UISlider {
    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        let thumb = thumbRect(forBounds: bounds, trackRect: trackRect(forBounds: bounds), value: value)
        return thumb.insetBy(dx: -12, dy: -8).contains(point)
    }
}

private final class OgtTrimTimeline: UIView {
    var duration: Int64 = 0
    var start: Int64 = 0
    var end: Int64 = 0
    var changed: ((Int64, Int64) -> Void)?
    var frames: [UIImage] = [] { didSet { images.arrangedSubviews.forEach { $0.removeFromSuperview() }; frames.forEach { let image = UIImageView(image: $0); image.contentMode = .scaleAspectFill; image.clipsToBounds = true; images.addArrangedSubview(image) } } }
    private let images = UIStackView()
    private let selection = UIView()
    private let left = OgtTrimSlider(frame: .zero)
    private let right = OgtTrimSlider(frame: .zero)
    override init(frame: CGRect) {
        super.init(frame: frame)
        images.distribution = .fillEqually; images.clipsToBounds = true; images.layer.cornerRadius = 8
        addSubview(images); selection.layer.borderColor = UIColor.systemYellow.cgColor; selection.layer.borderWidth = 3; selection.isUserInteractionEnabled = false; addSubview(selection)
        let thumb = UIGraphicsImageRenderer(size: CGSize(width: 20, height: 64)).image { _ in
            UIColor.systemYellow.setFill(); UIBezierPath(roundedRect: CGRect(x: 0, y: 0, width: 20, height: 64), cornerRadius: 5).fill()
            UIColor.black.setFill(); UIBezierPath(roundedRect: CGRect(x: 8.5, y: 22, width: 3, height: 20), cornerRadius: 1.5).fill()
        }
        for slider in [left, right] {
            slider.minimumTrackTintColor = .clear; slider.maximumTrackTintColor = .clear
            slider.setThumbImage(thumb, for: .normal); slider.setThumbImage(thumb, for: .highlighted)
            slider.addTarget(self, action: #selector(slid(_:)), for: .valueChanged)
            addSubview(slider)
        }
        left.accessibilityLabel = "Inicio del recorte"; right.accessibilityLabel = "Fin del recorte"
    }
    required init?(coder: NSCoder) { fatalError() }
    override func layoutSubviews() {
        super.layoutSubviews()
        images.frame = bounds.insetBy(dx: 10, dy: 6)
        guard duration > 0 else { return }
        for slider in [left, right] { slider.frame = bounds; slider.minimumValue = 0; slider.maximumValue = Float(duration) }
        left.value = Float(start); right.value = Float(end)
        let x1 = left.thumbRect(forBounds: left.bounds, trackRect: left.trackRect(forBounds: left.bounds), value: left.value).midX
        let x2 = right.thumbRect(forBounds: right.bounds, trackRect: right.trackRect(forBounds: right.bounds), value: right.value).midX
        selection.frame = CGRect(x: x1, y: 4, width: x2-x1, height: bounds.height-8)
        left.accessibilityValue = String(format: "%.1f segundos", Double(start)/1000)
        right.accessibilityValue = String(format: "%.1f segundos", Double(end)/1000)
    }
    @objc private func slid(_ slider: UISlider) {
        if slider === left { changed?(Int64(slider.value), end) } else { changed?(start, Int64(slider.value)) }
    }
}
