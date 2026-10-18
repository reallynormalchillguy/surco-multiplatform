package dev.surco.ios

import androidx.compose.ui.window.ComposeUIViewController
import dev.surco.ui.SurcoRoot
import dev.surco.ui.theme.SurcoTheme
import platform.UIKit.UIViewController
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

/** Punto de entrada para Swift: `MainViewControllerKt.MainViewController()`. */
fun MainViewController(): UIViewController {
    IosCrashReporter.install()
    IosImages.install()
    IosLimits.apply()
    IosDemo.seedIfRequested(IosApp.container)
    if (IosDemo.openPlayer) IosApp.openPlayerSignal = 1
    val route = IosDemo.route
    // Sin el gesto «atrás» de iOS: saltaba también desde el borde derecho y al llegar al final de
    // las listas horizontales; Surco hace el suyo, sólo desde el borde izquierdo (ver Root).
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    val vc = ComposeUIViewController(configure = { enableBackGesture = false }) {
        SurcoTheme { SurcoRoot(container = IosApp.container, openPlayerSignal = IosApp.openPlayerSignal, initialRoute = route) }
    }
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 1_500_000_000L), dispatch_get_main_queue()) {
        IosCrashReporter.showPending(vc)
    }
    // Demo de audio real (capturas): suena una señal de prueba con el ecualizador y el espectrograma.
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 1_000_000_000L), dispatch_get_main_queue()) {
        IosDemo.playIfRequested(IosApp.container)
    }
    return vc
}
