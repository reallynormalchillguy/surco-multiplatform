package dev.surco.ios

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header

/**
 * Cargador de imágenes del iPhone. Pide expresamente JPEG/PNG/WebP/GIF: algunas CDN (la de las
 * fotos de perfil de Last.fm, por ejemplo) eligen el formato según la cabecera Accept y pueden
 * mandar AVIF, que el decodificador de imágenes de Compose en iOS no sabe abrir (en Android sí).
 */
object IosImages {
    fun install() {
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(
                        KtorNetworkFetcherFactory(httpClient = {
                            HttpClient(Darwin) {
                                defaultRequest { header("Accept", "image/jpeg,image/png,image/webp,image/gif;q=0.9") }
                            }
                        }),
                    )
                }
                .build()
        }
    }
}
