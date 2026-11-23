import SwiftUI
import UIKit
import Shared
import WidgetKit

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            Bootstrap().ignoresSafeArea()
        }
    }
}

/// Links from the home screen widget (medic://quick, medic://review). SwiftUI's onOpenURL stops firing
/// once the root controller is swapped, so links are caught by a scene delegate SwiftUI forwards to.
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, configurationForConnecting connectingSceneSession: UISceneSession,
                     options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let config = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        config.delegateClass = LinkSceneDelegate.self
        return config
    }
}

final class LinkSceneDelegate: NSObject, UIWindowSceneDelegate {
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        connectionOptions.urlContexts.first.map { DeepLink.shared.open(url: $0.url.absoluteString) }
    }
    func scene(_ scene: UIScene, openURLContexts URLContexts: Set<UIOpenURLContext>) {
        URLContexts.first.map { DeepLink.shared.open(url: $0.url.absoluteString) }
    }
}

/// Hosts the shared Kotlin (Compose Multiplatform) UI and sets the status bar: every screen starts with
/// the orange brand header, so the clock and battery are always white.
final class ComposeHostController: UIViewController {
    private let compose = MainViewControllerKt.MainViewController()

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(compose)
        compose.view.frame = view.bounds
        compose.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(compose.view)
        compose.didMove(toParent: self)
        // The widget shows the streak and today's count: refresh it whenever the user leaves the app.
        NotificationCenter.default.addObserver(forName: UIApplication.willResignActiveNotification, object: nil, queue: .main) { _ in
            WidgetCenter.shared.reloadAllTimelines()
        }
    }

    override var preferredStatusBarStyle: UIStatusBarStyle { .lightContent }
}

/// SwiftUI can't set the status bar style, so once the window exists its root controller is swapped for
/// [ComposeHostController]. The app keeps SwiftUI's scene setup, so saved sessions from older versions still open.
private struct Bootstrap: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController { SwapIn() }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}

    final class SwapIn: UIViewController {
        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            guard let window = view.window, !(window.rootViewController is ComposeHostController) else { return }
            window.rootViewController = ComposeHostController()
        }
    }
}
