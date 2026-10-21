import SwiftUI
import Shared

@main
struct SurcoApp: App {
    @Environment(\.scenePhase) private var phase
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    init() {
        // Cambio de icono sin el aviso del sistema.
        IosAppIcon.shared.silentSetter = SilentIcon()
        // El reproductor bloquea o libera el giro: UIKit vuelve a preguntar las orientaciones permitidas.
        IosOrientation.shared.onChange = {
            for scene in UIApplication.shared.connectedScenes {
                guard let ws = scene as? UIWindowScene else { continue }
                for w in ws.windows { w.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations() }
            }
        }
    }

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

/** El reproductor no gira a horizontal (salvo con el espectrograma): lo decide `IosOrientation`. */
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, supportedInterfaceOrientationsFor window: UIWindow?) -> UIInterfaceOrientationMask {
        IosOrientation.shared.portraitOnly ? .portrait : .allButUpsideDown
    }
}

/** La interfaz es Compose Multiplatform (la misma que en Android). */
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/**
 * Cambia el icono alternativo sin la alerta «Has cambiado el icono de Surco».
 * Usa el método interno `_setAlternateIconName:completionHandler:` de UIApplication (el mismo que
 * la llamada pública, pero sin el aviso); si en alguna versión de iOS no existe, usa la pública.
 */
final class SilentIcon: NSObject, IconSetter {
    func change(name: String?) {
        let app = UIApplication.shared
        let selector = NSSelectorFromString("_setAlternateIconName:completionHandler:")
        guard app.responds(to: selector) else {
            app.setAlternateIconName(name, completionHandler: nil)
            return
        }
        typealias Setter = @convention(c) (NSObject, Selector, NSString?, @escaping @convention(block) (NSError?) -> Void) -> Void
        let setter = unsafeBitCast(app.method(for: selector), to: Setter.self)
        setter(app, selector, name as NSString?, { _ in })
    }
}
