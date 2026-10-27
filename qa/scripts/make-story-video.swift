import Foundation
import AVFoundation
import CoreVideo
let destination = CommandLine.arguments[1]
try? FileManager.default.removeItem(atPath: destination)
let writer = try AVAssetWriter(outputURL: URL(fileURLWithPath: destination), fileType: .mp4)
let input = AVAssetWriterInput(mediaType: .video, outputSettings: [AVVideoCodecKey: AVVideoCodecType.h264, AVVideoWidthKey: 320, AVVideoHeightKey: 480])
let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32ARGB, kCVPixelBufferWidthKey as String: 320, kCVPixelBufferHeightKey as String: 480])
writer.add(input); writer.startWriting(); writer.startSession(atSourceTime: .zero)
for index in 0..<30 {
    let deadline = Date().addingTimeInterval(5)
    while !input.isReadyForMoreMediaData && Date() < deadline { Thread.sleep(forTimeInterval: 0.005) }
    guard input.isReadyForMoreMediaData else { fatalError("Video encoder timed out") }
    var pixel: CVPixelBuffer?
    CVPixelBufferCreate(kCFAllocatorDefault, 320, 480, kCVPixelFormatType_32ARGB, nil, &pixel)
    guard let pixel else { fatalError("No pixel buffer") }
    CVPixelBufferLockBaseAddress(pixel, [])
    let data = CVPixelBufferGetBaseAddress(pixel)!.assumingMemoryBound(to: UInt8.self)
    let stride = CVPixelBufferGetBytesPerRow(pixel)
    for y in 0..<480 { for x in 0..<320 {
        let at = y * stride + x * 4
        data[at] = 255; data[at+1] = UInt8(20 + index*3); data[at+2] = UInt8(70+y/4); data[at+3] = 130
    } }
    CVPixelBufferUnlockBaseAddress(pixel, [])
    guard adaptor.append(pixel, withPresentationTime: CMTime(value: Int64(index), timescale: 15)) else { fatalError("Append failed") }
}
input.markAsFinished()
let done = DispatchSemaphore(value: 0)
writer.finishWriting { done.signal() }
guard done.wait(timeout: .now()+30) == .success && writer.status == .completed else { fatalError("Export failed: \(String(describing: writer.error))") }
print("QA video created")
