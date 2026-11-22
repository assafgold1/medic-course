import SwiftUI
import UIKit
import Shared

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            Bootstrap().ignoresSafeArea()
        }
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
