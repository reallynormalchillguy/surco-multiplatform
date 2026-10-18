package dev.surco.ios

import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState
import platform.UIKit.alternateIconName
import platform.UIKit.setAlternateIconName
import platform.UIKit.supportsAlternateIcons

/** Icono alternativo de iOS a juego con el color de acento. */
object IosAppIcon {
    /**
     * Lo pone la parte Swift de la app al arrancar: cambia el icono sin el aviso «Has cambiado el
     * icono de Surco» que iOS enseña con la llamada normal. Si no está, se usa la normal.
     */
    var silentSetter: IconSetter? = null

    fun apply(name: String) {
        val app = UIApplication.sharedApplication
        if (!app.supportsAlternateIcons) return
        val want = if (name == "Green") null else "AppIcon-$name"
        if (app.alternateIconName == want) return
        // iOS sólo deja cambiarlo con la app en primer plano.
        if (app.applicationState != UIApplicationState.UIApplicationStateActive) return
        val silent = silentSetter
        if (silent != null) silent.change(want) else app.setAlternateIconName(want) { _: platform.Foundation.NSError? -> }
    }
}

/** Lo implementa Swift (SurcoApp.swift): cambia el icono alternativo ([name] null = el normal). */
interface IconSetter {
    fun change(name: String?)
}
