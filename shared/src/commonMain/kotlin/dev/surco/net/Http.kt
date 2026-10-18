package dev.surco.net

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Cliente HTTP de la plataforma (OkHttp en Android, NSURLSession en iOS). */
expect fun createHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient

/** Cliente HTTP común de Surco para las APIs públicas (Deezer, iTunes, LRCLIB, Last.fm…). */
fun surcoHttpClient(userAgent: String = USER_AGENT): HttpClient = createHttpClient {
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 45_000
        socketTimeoutMillis = 30_000
    }
    // En iOS, NSURLSession da «The network connection was lost» (-1005) cuando reutiliza una conexión
    // que el servidor o el propio sistema ya cerró (p. ej. al volver de segundo plano). Un reintento
    // inmediato abre una conexión nueva y funciona; OkHttp en Android ya lo hace por su cuenta.
    install(HttpRequestRetry) {
        retryOnException(maxRetries = 2, retryOnTimeout = false)
        delayMillis { retry -> 300L * retry }
    }
    defaultRequest { header("User-Agent", userAgent) }
}

const val USER_AGENT = "Surco/1.0 (https://github.com/reallynormalchillguy/surco)"

internal fun enc(s: String): String = s.encodeURLParameter()

internal val JsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun parseJson(text: String): JsonElement? = runCatching { JsonParser.parseToJsonElement(text) }.getOrNull()

// Accesos tolerantes al estilo de org.json (opt*): nunca lanzan, devuelven un valor por defecto.

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject
internal fun JsonElement?.arr(): JsonArray? = this as? JsonArray
internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
internal fun JsonObject.isNull(key: String): Boolean = this[key] == null || this[key] is JsonNull

internal fun JsonObject.str(key: String, default: String = ""): String {
    val p = this[key] as? JsonPrimitive ?: return default
    if (p is JsonNull) return default
    return p.content
}

internal fun JsonObject.long(key: String, default: Long = 0): Long {
    val p = this[key] as? JsonPrimitive ?: return default
    return p.longOrNull ?: p.doubleOrNull?.toLong() ?: default
}

internal fun JsonObject.int(key: String, default: Int = 0): Int = long(key, default.toLong()).toInt()

internal fun JsonObject.double(key: String, default: Double = 0.0): Double =
    (this[key] as? JsonPrimitive)?.doubleOrNull ?: default

internal fun JsonObject.bool(key: String, default: Boolean = false): Boolean =
    (this[key] as? JsonPrimitive)?.booleanOrNull ?: default

internal fun JsonArray.objects(): List<JsonObject> = mapNotNull { it as? JsonObject }
