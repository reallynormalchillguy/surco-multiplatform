package dev.surco.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.surco.i18n.S
import dev.surco.resources.Res
import dev.surco.ui.theme.SurcoColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Cuenta toques seguidos: si entre dos toques pasan más de [maxGapMs], vuelve a empezar.
 * [tap] devuelve true al llegar a [needed] toques y se reinicia.
 */
class RapidTapCounter(private val needed: Int = 10, private val maxGapMs: Long = 450) {
    private var count = 0
    private var last = Long.MIN_VALUE

    fun tap(nowMs: Long): Boolean {
        count = if (last != Long.MIN_VALUE && nowMs - last <= maxGapMs) count + 1 else 1
        last = nowMs
        if (count >= needed) {
            count = 0
            last = Long.MIN_VALUE
            return true
        }
        return false
    }
}

private const val EASTER_FRAMES = 51
private const val EASTER_FRAME_MS = 83L // 12 fps, como el vídeo original convertido

/** Diálogo secreto de créditos con el gatito bailando en bucle. */
@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun EasterEggDialog(onDismiss: () -> Unit) {
    // Se leen los bytes una vez (≈570 KB) y cada fotograma se decodifica al mostrarse,
    // así no se guardan 51 mapas de bits en memoria a la vez.
    val frames by produceState<List<ByteArray>?>(null) {
        value = runCatching {
            (1..EASTER_FRAMES).map { Res.readBytes("files/easter/f${it.toString().padStart(2, '0')}.jpg") }
        }.getOrNull()
    }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(frames) {
        val list = frames ?: return@LaunchedEffect
        var i = 0
        while (true) {
            bitmap = withContext(Dispatchers.Default) { runCatching { list[i].decodeToImageBitmap() }.getOrNull() } ?: bitmap
            delay(EASTER_FRAME_MS)
            i = (i + 1) % list.size
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .testTag("easterEgg")
                .clip(RoundedCornerShape(16.dp))
                .background(SurcoColors.Surface)
                .clickable(onClick = onDismiss)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(width = 216.dp, height = 374.dp).clip(RoundedCornerShape(12.dp)).background(SurcoColors.Background),
                contentAlignment = Alignment.Center,
            ) {
                bitmap?.let {
                    Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 216.dp, height = 374.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                S.easterEggCredits,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
