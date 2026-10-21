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

    /** Swift avisa aquí a UIKit de que han cambiado las orientaciones permitidas. */
    var onChange: (() -> Unit)? = null

    fun setPortraitOnly(value: Boolean) {
        if (portraitOnly == value) return
        portraitOnly = value
        onChange?.invoke()
        // Si el móvil ya está en horizontal, se le pide que vuelva a vertical.
        if (value) {
            val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull { it is UIWindowScene } as? UIWindowScene
            scene?.requestGeometryUpdateWithPreferences(UIWindowSceneGeometryPreferencesIOS(UIInterfaceOrientationMaskPortrait), errorHandler = null)
        }
    }
}
