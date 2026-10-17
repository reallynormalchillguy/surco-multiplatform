package dev.surco.ios

import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState
import platform.UIKit.alternateIconName
import platform.UIKit.setAlternateIconName
import platform.UIKit.supportsAlternateIcons

/** Icono alternativo de iOS a juego con el color de acento (iOS avisa con un mensaje al cambiarlo). */
object IosAppIcon {
    fun apply(name: String) {
        val app = UIApplication.sharedApplication
        if (!app.supportsAlternateIcons) return
        val want = if (name == "Green") null else "AppIcon-$name"
        if (app.alternateIconName == want) return
        // iOS sólo deja cambiarlo con la app en primer plano.
        if (app.applicationState != UIApplicationState.UIApplicationStateActive) return
        app.setAlternateIconName(want) { _: platform.Foundation.NSError? -> }
    }
}
