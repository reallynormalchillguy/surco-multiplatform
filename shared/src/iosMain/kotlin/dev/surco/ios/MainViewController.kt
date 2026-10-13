package dev.surco.ios

import androidx.compose.ui.window.ComposeUIViewController
import dev.surco.ui.SurcoRoot
import dev.surco.ui.theme.SurcoTheme
import platform.UIKit.UIViewController

/** Punto de entrada para Swift: `MainViewControllerKt.MainViewController()`. */
fun MainViewController(): UIViewController {
    IosDemo.seedIfRequested(IosApp.container)
    if (IosDemo.openPlayer) IosApp.openPlayerSignal = 1
    val route = IosDemo.route
    return ComposeUIViewController {
        SurcoTheme { SurcoRoot(container = IosApp.container, openPlayerSignal = IosApp.openPlayerSignal, initialRoute = route) }
    }
}
