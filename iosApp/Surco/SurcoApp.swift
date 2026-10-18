import SwiftUI
import Shared

@main
struct SurcoApp: App {
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
                .preferredColorScheme(.dark)
                .background(Color(red: 0.063, green: 0.063, blue: 0.063))
                .onOpenURL { url in IosApp.shared.handleUrl(url: url.absoluteString) }
        }
        .onChange(of: phase) { newPhase in
            if newPhase == .active { IosApp.shared.onForeground() }
            if newPhase == .background { IosApp.shared.onBackground() }
        }
    }
}

/** La interfaz es Compose Multiplatform (la misma que en Android). */
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
