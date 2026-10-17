package dev.surco

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dev.surco.ui.SurcoRoot
import dev.surco.ui.theme.SurcoTheme

class MainActivity : ComponentActivity() {

    /** Se incrementa cada vez que hay que abrir el reproductor (p. ej. al pulsar la notificación). */
    private val openPlayerSignal = mutableIntStateOf(0)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as SurcoApp).container
        handleIntent(intent)

        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Al abrir la app, reescaneo rápido (incremental) para detectar música nueva.
        if (savedInstanceState == null && container.prefs.current.treeUri != null) {
            container.repository.rescan()
        }

        setContent {
            // Con una paleta clara, los iconos de la barra de estado pasan a oscuros (y al revés).
            val light = dev.surco.ui.theme.SurcoColors.palette.isLight
            androidx.compose.runtime.LaunchedEffect(light) {
                val style = if (light) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT) else SystemBarStyle.dark(Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            SurcoTheme {
                SurcoRoot(container = container, openPlayerSignal = openPlayerSignal.intValue)
                CrashDialog()
            }
        }
    }

    /** «Atrás» cierra primero la letra y el reproductor; después, la navegación normal. */
    @Deprecated("Se sigue llamando con el gesto de atrás clásico (no usamos el predictivo).")
    override fun onBackPressed() {
        if (dev.surco.ui.Overlays.back()) return
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) {
            openPlayerSignal.intValue += 1
            intent.removeExtra(EXTRA_OPEN_PLAYER)
        }
        // Enlace de Spotify compartido con Surco («Compartir → Surco») o abierto con «Abrir con…».
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        text?.let { dev.surco.net.SpotifyPage.findLink(it) }?.let { link ->
            dev.surco.ui.Overlays.pendingLink = link
            intent?.action = null
        }
    }

    override fun onStart() {
        super.onStart()
        (application as SurcoApp).container.player.connect()
        // Si quedaron scrobbles pendientes (p. ej. de escuchar sin conexión), se intentan enviar ya.
        (application as SurcoApp).container.repository.flushScrobblesAsync()
        // Música copiada mientras la app estaba en segundo plano.
        (application as SurcoApp).container.autoScan.onForeground()
    }

    override fun onStop() {
        val c = (application as SurcoApp).container
        // El icono se cambia al salir de la app (no mientras la usas) y sin cortar la música que suene.
        if (!c.player.state.value.isPlaying) {
            AppIcon.apply(this, dev.surco.ui.theme.SurcoColors.Accent, c.prefs.current.iconFollowsAccent)
        }
        c.player.release()
        super.onStop()
    }

    /** Muestra el informe del último cierre inesperado para poder copiarlo y enviarlo. */
    @androidx.compose.runtime.Composable
    private fun CrashDialog() {
        var report by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(CrashReporter.pending(this)) }
        val text = report ?: return
        val dismiss = { CrashReporter.clear(this); report = null }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = dismiss,
            title = { androidx.compose.material3.Text(dev.surco.i18n.S.crashTitle) },
            text = {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.Text(dev.surco.i18n.S.crashText)
                    androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(androidx.compose.ui.unit.Dp(10f)))
                    androidx.compose.material3.Text(
                        text.take(1500),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        modifier = androidx.compose.ui.Modifier
                            .height(androidx.compose.ui.unit.Dp(260f))
                            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val cm = getSystemService(android.content.ClipboardManager::class.java)
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Surco crash", text))
                    dismiss()
                }) { androidx.compose.material3.Text(dev.surco.i18n.S.copyReport) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = dismiss) { androidx.compose.material3.Text(dev.surco.i18n.S.close) }
            },
        )
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"
    }
}
