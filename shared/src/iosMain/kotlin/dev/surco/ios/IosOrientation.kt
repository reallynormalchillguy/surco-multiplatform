package dev.surco.ios

import platform.UIKit.UIApplication
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS

/**
 * Orientaciones permitidas en iOS. Swift las consulta en
 * `application(_:supportedInterfaceOrientationsFor:)` (ver SurcoApp.swift): con [portraitOnly]
 * sólo vertical; si no, vertical y horizontal.
 */
object IosOrientation {
    var portraitOnly: Boolean = false
        private set

    fun setPortraitOnly(value: Boolean) {
        if (portraitOnly == value) return
        portraitOnly = value
        val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull { it is UIWindowScene } as? UIWindowScene
        scene?.windows?.forEach { w ->
            (w as? platform.UIKit.UIWindow)?.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
        }
        // Si el móvil ya está en horizontal, se le pide que vuelva a vertical.
        if (value) scene?.requestGeometryUpdateWithPreferences(UIWindowSceneGeometryPreferencesIOS(UIInterfaceOrientationMaskPortrait), errorHandler = null)
    }
}
