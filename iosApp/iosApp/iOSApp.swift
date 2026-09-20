import SwiftUI
import UIKit
import FirebaseCore
import FirebaseAuth
import FirebaseMessaging
import GoogleSignIn
import AuthenticationServices
import LocalAuthentication
import CryptoKit
import UserNotifications
import Contacts
import MessageUI
import ComposeApp

class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            let ok = settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional
            DispatchQueue.main.async {
                OgtPushRuntime.shared.permissionGranted = ok
                if ok { application.registerForRemoteNotifications() }
            }
        }
        if let payload = launchOptions?[.remoteNotification] as? [AnyHashable: Any] {
            offerPush(payload)
        }
        return true
    }

    func application(_ app: UIApplication, open url: URL, options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
        if GIDSignIn.sharedInstance.handle(url) { return true }
        if Auth.auth().canHandle(url) { return true }
        OgtIncomingLinks.shared.offer(uri: url.absoluteString)
        return true
    }

    func application(
        _ application: UIApplication,
        continue userActivity: NSUserActivity,
        restorationHandler: @escaping ([UIUserActivityRestoring]?) -> Void
    ) -> Bool {
        if userActivity.activityType == NSUserActivityTypeBrowsingWeb, let url = userActivity.webpageURL {
            OgtIncomingLinks.shared.offer(uri: url.absoluteString)
            return true
        }
        return false
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        // Auth ya recibe el token por el swizzle (FirebaseAppDelegateProxyEnabled).
        // setAPNSToken(.unknown) en Firebase 12 hace fatalError en globalWorkQueue.
        Messaging.messaging().apnsToken = deviceToken
    }

    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        if Auth.auth().canHandleNotification(userInfo) {
            completionHandler(.noData)
            return
        }
        offerPush(userInfo)
        completionHandler(.newData)
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        DispatchQueue.main.async {
            OgtPushRuntime.shared.token = fcmToken
            if fcmToken != nil { OgtPushRuntime.shared.permissionGranted = true }
        }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .badge])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        offerPush(response.notification.request.content.userInfo)
        completionHandler()
    }

}

final class OgtIosPushHost: IosPushHost {
    func requestPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { ok, _ in
            DispatchQueue.main.async {
                OgtPushRuntime.shared.permissionGranted = ok
                if ok {
                    UIApplication.shared.registerForRemoteNotifications()
                }
            }
        }
    }
}

final class OgtIosHonorInviteHost: NSObject, IosHonorInviteHost, MFMessageComposeViewControllerDelegate {
    func requestContacts(onResult: @escaping (KotlinBoolean) -> Void) {
        CNContactStore().requestAccess(for: .contacts) { ok, _ in
            DispatchQueue.main.async { onResult(KotlinBoolean(value: ok)) }
        }
    }

    func contactsBlob() -> String {
        let store = CNContactStore()
        // El formatter lee middleName y más: hay que pedir esas keys o Contacts tira NSException.
        let keys: [CNKeyDescriptor] = [
            CNContactFormatter.descriptorForRequiredKeys(for: .fullName),
            CNContactPhoneNumbersKey as CNKeyDescriptor,
            CNContactThumbnailImageDataKey as CNKeyDescriptor,
        ]
        let request = CNContactFetchRequest(keysToFetch: keys)
        var lines: [String] = []
        do {
            try store.enumerateContacts(with: request) { contact, _ in
                let fallback = [contact.givenName, contact.familyName].filter { !$0.isEmpty }.joined(separator: " ")
                let name = CNContactFormatter.string(from: contact, style: .fullName) ?? fallback
                let label = name.isEmpty ? "Contacto" : name
                for labeled in contact.phoneNumbers {
                    let phone = labeled.value.stringValue
                    if phone.filter(\.isNumber).count >= 8 {
                        if let photo = contact.thumbnailImageData?.base64EncodedString(), !photo.isEmpty {
                            lines.append("\(label)\u{1f}\(phone)\u{1f}\(photo)")
                        } else {
                            lines.append("\(label)\u{1f}\(phone)")
                        }
                    }
                }
            }
        } catch {
            return ""
        }
        return lines.sorted {
            $0.localizedCaseInsensitiveCompare($1) == .orderedAscending
        }.joined(separator: "\n")
    }

    func shareWhatsApp(phone: String, text: String) {
        let digits = String(phone.filter(\.isNumber))
        var allowed = CharacterSet.urlQueryAllowed
        allowed.remove(charactersIn: "&+")
        let encoded = text.addingPercentEncoding(withAllowedCharacters: allowed) ?? text
        if digits.count >= 8,
           let url = URL(string: "whatsapp://send?phone=\(digits)&text=\(encoded)"),
           UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url)
            return
        }
        // Selector de chats de WhatsApp (como compartir un documento).
        if let url = URL(string: "whatsapp://send?text=\(encoded)"),
           UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url)
            return
        }
        present(UIActivityViewController(activityItems: [text], applicationActivities: nil))
    }

    func sendSms(phone: String, text: String) {
        if MFMessageComposeViewController.canSendText() {
            let composer = MFMessageComposeViewController()
            composer.recipients = [phone]
            composer.body = text
            composer.messageComposeDelegate = self
            present(composer)
            return
        }
        var allowed = CharacterSet.urlQueryAllowed
        allowed.remove(charactersIn: "&+")
        let encoded = text.addingPercentEncoding(withAllowedCharacters: allowed) ?? text
        if let url = URL(string: "sms:\(phone)&body=\(encoded)") {
            UIApplication.shared.open(url)
        }
    }

    func messageComposeViewController(_ controller: MFMessageComposeViewController, didFinishWith result: MessageComposeResult) {
        controller.dismiss(animated: true)
    }

    private func present(_ controller: UIViewController) {
        DispatchQueue.main.async {
            Self.topController()?.present(controller, animated: true)
        }
    }

    private static func topController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let window = scenes.flatMap { $0.windows }.first { $0.isKeyWindow } ?? scenes.first?.windows.first
        var top = window?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}

private extension AppDelegate {
    func offerPush(_ userInfo: [AnyHashable: Any]) {
        if let link = userInfo["deepLink"] as? String, !link.isEmpty {
            OgtIncomingLinks.shared.offer(uri: link)
            return
        }
        if let postId = userInfo["postId"] as? String, !postId.isEmpty {
            OgtIncomingLinks.shared.offer(uri: "ogt://p/\(postId)")
        }
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    init() {
        FirebaseApp.configure()
        Auth.auth().settings?.isAppVerificationDisabledForTesting = true
        IosAuthRuntime.shared.host = OgtFirebaseHost()
        OgtPushRuntime.shared.host = OgtIosPushHost()
        IosHonorInviteRuntime.shared.host = OgtIosHonorInviteHost()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    if GIDSignIn.sharedInstance.handle(url) { return }
                    if Auth.auth().canHandle(url) { return }
                    OgtIncomingLinks.shared.offer(uri: url.absoluteString)
                }
        }
    }
}

final class OgtFirebaseHost: NSObject, IosAuthHost {
    private var appleDelegate: AppleSignInDelegate?
    private var currentNonce: String?

    func currentUser() -> AuthUser? {
        guard let user = Auth.auth().currentUser else { return nil }
        return Self.map(user)
    }

    func idToken(forceRefresh: Bool, onResult: @escaping (String?, String?) -> Void) {
        guard let user = Auth.auth().currentUser else {
            onResult(nil, nil)
            return
        }
        user.getIDTokenForcingRefresh(forceRefresh) { token, error in
            onResult(token, error.map(Self.mensaje))
        }
    }

    func signInEmail(email: String, password: String, onResult: @escaping (AuthUser?, String?) -> Void) {
        Auth.auth().signIn(withEmail: email, password: password) { result, error in
            Self.finish(result?.user, error, onResult)
        }
    }

    func signUpEmail(email: String, password: String, displayName: String, onResult: @escaping (AuthUser?, String?) -> Void) {
        Auth.auth().createUser(withEmail: email, password: password) { result, error in
            guard let user = result?.user else {
                onResult(nil, error.map(Self.mensaje) ?? "No se pudo crear la cuenta")
                return
            }
            let change = user.createProfileChangeRequest()
            change.displayName = displayName
            change.commitChanges { _ in
                user.reload { _ in
                    Self.finish(Auth.auth().currentUser, nil, onResult)
                }
            }
        }
    }

    func sendPasswordReset(email: String, onResult: @escaping (String?) -> Void) {
        Auth.auth().sendPasswordReset(withEmail: email) { error in
            onResult(error.map(Self.mensaje))
        }
    }

    func startPhoneAuth(phoneE164: String, onResult: @escaping (String?, String?) -> Void) {
        PhoneAuthProvider.provider().verifyPhoneNumber(phoneE164, uiDelegate: nil) { verificationId, error in
            onResult(verificationId, error.map(Self.mensaje))
        }
    }

    func confirmPhone(verificationId: String, code: String, onResult: @escaping (AuthUser?, String?) -> Void) {
        let credential = PhoneAuthProvider.provider().credential(withVerificationID: verificationId, verificationCode: code)
        Auth.auth().signIn(with: credential) { result, error in
            Self.finish(result?.user, error, onResult)
        }
    }

    func signInGoogle(onResult: @escaping (AuthUser?, String?) -> Void) {
        guard let clientId = FirebaseApp.app()?.options.clientID else {
            onResult(nil, "Falta CLIENT_ID de Firebase")
            return
        }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(
            clientID: clientId,
            serverClientID: "521063054927-2i8k3qkmh48p5h3qp3nqaunt5t7j4sj0.apps.googleusercontent.com"
        )
        guard let presenter = Self.topController() else {
            onResult(nil, "No hay una ventana para mostrar Google Sign-In")
            return
        }
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
            if let error {
                onResult(nil, Self.mensaje(error))
                return
            }
            guard let idToken = result?.user.idToken?.tokenString else {
                onResult(nil, "Google no devolvió un token")
                return
            }
            let accessToken = result?.user.accessToken.tokenString ?? ""
            let credential = GoogleAuthProvider.credential(withIDToken: idToken, accessToken: accessToken)
            Auth.auth().signIn(with: credential) { authResult, authError in
                Self.finish(authResult?.user, authError, onResult)
            }
        }
    }

    func signInApple(onResult: @escaping (AuthUser?, String?) -> Void) {
        let nonce = Self.randomNonce()
        currentNonce = nonce
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = Self.sha256(nonce)
        let delegate = AppleSignInDelegate { [weak self] authorization, error in
            self?.appleDelegate = nil
            if let error {
                onResult(nil, Self.mensaje(error))
                return
            }
            guard
                let credential = authorization?.credential as? ASAuthorizationAppleIDCredential,
                let tokenData = credential.identityToken,
                let idToken = String(data: tokenData, encoding: .utf8),
                let nonce = self?.currentNonce
            else {
                onResult(nil, "Apple no devolvió un token válido")
                return
            }
            let firebaseCred = OAuthProvider.appleCredential(
                withIDToken: idToken,
                rawNonce: nonce,
                fullName: credential.fullName
            )
            Auth.auth().signIn(with: firebaseCred) { result, authError in
                Self.finish(result?.user, authError, onResult)
            }
        }
        appleDelegate = delegate
        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = delegate
        controller.presentationContextProvider = delegate
        controller.performRequests()
    }

    func signInFacebook(onResult: @escaping (AuthUser?, String?) -> Void) {
        guard let presenter = Self.topController() else {
            onResult(nil, "No hay una ventana para Facebook")
            return
        }
        let provider = OAuthProvider(providerID: "facebook.com")
        provider.scopes = ["email", "public_profile"]
        provider.getCredentialWith(nil) { credential, error in
            if let error {
                onResult(nil, Self.mensaje(error))
                return
            }
            guard let credential else {
                onResult(nil, "Facebook no devolvió credencial")
                return
            }
            Auth.auth().signIn(with: credential) { result, authError in
                Self.finish(result?.user, authError, onResult)
            }
        }
        _ = presenter
    }

    func unlockBiometric(onResult: @escaping (KotlinBoolean) -> Void) {
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
            onResult(KotlinBoolean(value: false))
            return
        }
        context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: "Ingresar a tu barrio") { ok, _ in
            DispatchQueue.main.async { onResult(KotlinBoolean(value: ok)) }
        }
    }

    func canUseBiometric() -> Bool {
        var error: NSError?
        return LAContext().canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
    }

    func signOut() {
        try? Auth.auth().signOut()
        GIDSignIn.sharedInstance.signOut()
    }

    private static func finish(_ user: User?, _ error: Error?, _ onResult: (AuthUser?, String?) -> Void) {
        if let error {
            onResult(nil, mensaje(error))
        } else if let user {
            onResult(map(user), nil)
        } else {
            onResult(nil, "No se pudo autenticar")
        }
    }

    private static func map(_ user: User) -> AuthUser {
        IosAuthRuntimeKt.iosAuthUser(
            uid: user.uid,
            email: user.email,
            phone: user.phoneNumber,
            displayName: user.displayName,
            photoUrl: user.photoURL?.absoluteString
        )
    }

    private static func mensaje(_ error: Error) -> String {
        let ns = error as NSError
        switch AuthErrorCode(rawValue: ns.code) {
        case .invalidEmail: return "El correo no es válido"
        case .wrongPassword, .invalidCredential: return "Correo o contraseña incorrectos"
        case .userNotFound: return "No hay una cuenta con esos datos"
        case .emailAlreadyInUse: return "Ese correo ya está registrado"
        case .weakPassword: return "La contraseña es demasiado débil"
        case .tooManyRequests: return "Demasiados intentos. Probá más tarde"
        case .invalidVerificationCode: return "El código SMS no es válido"
        case .sessionExpired: return "El código SMS venció. Pedí uno nuevo"
        case .quotaExceeded: return "Se agotó la cuota de SMS de Firebase"
        case .operationNotAllowed: return "Este método de ingreso no está habilitado en Firebase"
        default: return ns.localizedDescription
        }
    }

    private static func topController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let window = scenes.flatMap { $0.windows }.first { $0.isKeyWindow } ?? scenes.first?.windows.first
        var top = window?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }

    private static func randomNonce(length: Int = 32) -> String {
        let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        var remaining = length
        while remaining > 0 {
            let randoms: [UInt8] = (0..<16).map { _ in
                var random: UInt8 = 0
                _ = SecRandomCopyBytes(kSecRandomDefault, 1, &random)
                return random
            }
            randoms.forEach { random in
                if remaining == 0 { return }
                if random < charset.count {
                    result.append(charset[Int(random)])
                    remaining -= 1
                }
            }
        }
        return result
    }

    private static func sha256(_ input: String) -> String {
        let data = Data(input.utf8)
        return SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }
}

private final class AppleSignInDelegate: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private let completion: (ASAuthorization?, Error?) -> Void

    init(completion: @escaping (ASAuthorization?, Error?) -> Void) {
        self.completion = completion
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        completion(authorization, nil)
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        completion(nil, error)
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        return scenes.flatMap { $0.windows }.first { $0.isKeyWindow } ?? UIWindow()
    }
}
