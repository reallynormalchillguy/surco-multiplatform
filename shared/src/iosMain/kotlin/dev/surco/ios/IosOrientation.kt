package dev.surco.ios

import platform.UIKit.UIApplication
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS

/**
 * Orientaciones permitidas en iOS. Swift las consulta en
 * `application(_:supportedInterfaceOrientationsFor:)` (ver SurcoApp.swift): por defecto sólo
 * vertical; con [landscapeAllowed] (reproductor con espectrograma) también horizontal.
 */
object IosOrientation {
    var landscapeAllowed: Boolean = false
        private set

    /** Swift avisa aquí a UIKit de que han cambiado las orientaciones permitidas. */
    var onChange: (() -> Unit)? = null

    fun setLandscapeAllowed(value: Boolean) {
        if (landscapeAllowed == value) return
        landscapeAllowed = value
        onChange?.invoke()
        // Si el móvil se ha quedado en horizontal, se le pide que vuelva a vertical.
        if (!value) {
            val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull { it is UIWindowScene } as? UIWindowScene
            scene?.requestGeometryUpdateWithPreferences(UIWindowSceneGeometryPreferencesIOS(UIInterfaceOrientationMaskPortrait), errorHandler = null)
        }
    }
}
