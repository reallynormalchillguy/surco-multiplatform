package dev.surco.slsk

import dev.surco.i18n.S
import kotlinx.coroutines.flow.StateFlow


data class SlskFile(
    val user: String,
    val path: String,
    val size: Long,
    val bitrate: Int = 0,
    val durationSec: Int = 0,
    val sampleRate: Int = 0,
    val bitDepth: Int = 0,
    val vbr: Boolean = false,
) {
    val name get() = path.substringAfterLast('\\')
    val folder get() = path.substringBeforeLast('\\', "")
    val ext get() = name.substringAfterLast('.', "").lowercase()
    val isAudio get() = ext in AUDIO_EXT
    val isImage get() = ext in IMAGE_EXT
    val lossless get() = ext in LOSSLESS_EXT

    companion object {
        val AUDIO_EXT = setOf("mp3", "flac", "m4a", "aac", "alac", "ogg", "oga", "opus", "wav", "aif", "aiff", "ape", "wv", "wma", "dsf")
        val LOSSLESS_EXT = setOf("flac", "alac", "wav", "aif", "aiff", "ape", "wv", "dsf")
        val IMAGE_EXT = setOf("jpg", "jpeg", "png")
    }
}

data class UserResult(
    val user: String,
    val files: List<SlskFile>,
    val slotFree: Boolean,
    val avgSpeed: Long,
    val queueLength: Long,
)

enum class TransferState(val active: Boolean) {
    CONNECTING(true), QUEUED(true), STARTING(true), TRANSFERRING(true), SAVING(true),
    DONE(false), FAILED(false), CANCELLED(false);

    val label: String
        get() = when (this) {
            CONNECTING -> S.stConnecting
            QUEUED -> S.stQueued
            STARTING -> S.stStarting
            TRANSFERRING -> S.stTransferring
            SAVING -> S.stSaving
            DONE -> S.stDone
            FAILED -> S.error
            CANCELLED -> S.stCancelled
        }
}

data class Transfer(
    val id: Long,
    val user: String,
    val path: String,
    val size: Long,
    val state: TransferState = TransferState.CONNECTING,
    val bytes: Long = 0,
    val speed: Long = 0,
    val place: Long? = null,
    val error: String? = null,
    val savedAs: String? = null,
    val token: Long? = null,
) {
    val name get() = path.substringAfterLast('\\')
    val folder get() = path.substringBeforeLast('\\', "").substringAfterLast('\\')
    val progress get() = if (size > 0) (bytes.toFloat() / size).coerceIn(0f, 1f) else 0f
}

sealed interface SlskState {
    data object Offline : SlskState
    data object Connecting : SlskState
    data class Online(val user: String, val port: Int) : SlskState
    /** [retrying]: se volverá a intentar solo (corte de red); si no, hace falta volver a iniciar sesión. */
    data class Failed(val message: String, val retrying: Boolean = false) : SlskState
}

/** Cliente de la red Soulseek: sesión, búsquedas y descargas. */
interface SoulseekEngine {
    val state: StateFlow<SlskState>
    val results: StateFlow<List<UserResult>>
    val searching: StateFlow<Boolean>
    val transfers: StateFlow<List<Transfer>>
    fun search(query: String)
    fun clearResults()
    fun cancel(id: Long)
    /** Cancela las transferencias que cumplan [which]; devuelve cuántas. */
    fun cancelAll(which: (Transfer) -> Boolean = { true }): Int
    /** Cancela las que aún esperan turno. */
    fun cancelQueued(): Int
    /** Cancela las que se están descargando ahora. */
    fun cancelDownloading(): Int
    fun clearFinished()
    /** Sólo para las capturas de pantalla automáticas. */
    fun debugSet(state: SlskState, results: List<UserResult>, transfers: List<Transfer>)
}

/** Une el cliente de Soulseek con la app: cuenta, carpeta de destino, descargas en segundo plano. */
interface SoulseekService {
    val client: SoulseekEngine
    val hasAccount: Boolean
    /** Carpeta donde se guardan las descargas (la elegida o la de música). */
    val targetTree: String?
    /** Conecta con la cuenta guardada si aún no hay sesión. */
    fun ensureConnected()
    fun login(user: String, pass: String)
    fun logout()
    fun download(files: List<SlskFile>)
    fun retry(id: Long)
    fun retryFailed(): Int
}
