package dev.surco.ios

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIPasteboard
import platform.UIKit.UIViewController
import kotlin.experimental.ExperimentalNativeApi

/**
 * Si la app se cierra por un error de Kotlin, guarda el motivo y lo enseña al abrirla de nuevo
 * (con un botón para copiarlo), igual que la versión de Android.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, ExperimentalNativeApi::class)
object IosCrashReporter {
    private val path get() = "${IosDirs.support}/last-crash.txt"

    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        var previous: ((Throwable) -> Unit)? = null
        previous = setUnhandledExceptionHook { e ->
            runCatching {
                val text = "Surco ${IosApp.versionName}\n${e.stackTraceToString()}"
                NSString.create(string = text).writeToFile(path, true, NSUTF8StringEncoding, null)
            }
            previous?.invoke(e)
        }
    }

    /** Muestra el último cierre (si lo hubo) y lo borra. */
    fun showPending(host: UIViewController) {
        val text = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) ?: return
        NSFileManager.defaultManager.removeItemAtPath(path, null)
        val alert = UIAlertController.alertControllerWithTitle(
            "Surco se cerró la última vez",
            text.take(1500),
            UIAlertControllerStyleAlert,
        )
        alert.addAction(UIAlertAction.actionWithTitle("Copiar detalles", UIAlertActionStyleDefault) { _ ->
            UIPasteboard.generalPasteboard.string = text
        })
        alert.addAction(UIAlertAction.actionWithTitle("Cerrar", UIAlertActionStyleCancel, null))
        host.presentViewController(alert, animated = true, completion = null)
    }
}
