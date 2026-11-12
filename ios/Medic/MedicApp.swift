import SwiftUI
import WebKit

@main
struct MedicApp: App {
    var body: some Scene {
        WindowGroup {
            WebScreen()
                .ignoresSafeArea(edges: .bottom)
                .background(Color("LaunchBlue").ignoresSafeArea())
        }
    }
}

/// Shows the course site from GitHub Pages; falls back to the bundled copy when offline.
struct WebScreen: UIViewRepresentable {
    static let siteURL = URL(string: "https://assafgold1.github.io/medic-course/")!

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        config.allowsInlineMediaPlayback = true
        let web = WKWebView(frame: .zero, configuration: config)
        web.navigationDelegate = context.coordinator
        web.allowsBackForwardNavigationGestures = true
        web.isOpaque = false
        web.backgroundColor = UIColor(named: "LaunchBlue")
        web.scrollView.contentInsetAdjustmentBehavior = .never
        web.load(URLRequest(url: Self.siteURL, cachePolicy: .reloadRevalidatingCacheData, timeoutInterval: 12))
        return web
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    final class Coordinator: NSObject, WKNavigationDelegate {
        private var usingFallback = false

        func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction,
                     decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard let url = action.request.url else { return decisionHandler(.allow) }
            let ours = url.isFileURL || url.host == WebScreen.siteURL.host || url.scheme == "about"
            if !ours, action.navigationType == .linkActivated {
                UIApplication.shared.open(url)
                return decisionHandler(.cancel)
            }
            decisionHandler(.allow)
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            loadFallback(webView)
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            loadFallback(webView)
        }

        private func loadFallback(_ webView: WKWebView) {
            guard !usingFallback, let file = Bundle.main.url(forResource: "index", withExtension: "html") else { return }
            usingFallback = true
            webView.loadFileURL(file, allowingReadAccessTo: file.deletingLastPathComponent())
        }
    }
}
