# Surco (multiplataforma)

Surco para **Android e iPhone** desde un único código Kotlin (Kotlin Multiplatform + Compose Multiplatform):
la misma interfaz, la misma biblioteca, los mismos scrobbles y el mismo cliente de Soulseek.

## Instalar en iPhone (SideStore)

1. En el iPhone, abre la release **«ios-latest»** de este repositorio y descarga `Surco.ipa`.
2. En SideStore: **My Apps → +** y elige `Surco.ipa` (o «Abrir con… SideStore» desde Archivos).
3. Primera vez: en Surco, elige tu carpeta de música (por ejemplo, una carpeta de «En mi iPhone»).

Con un Apple ID gratuito, SideStore renueva la firma cada 7 días (lo hace solo si está configurado).

## Estructura

```
shared/       Casi todo: interfaz (Compose), biblioteca (Room), red (Ktor), letras, Last.fm,
              Soulseek, lector/escritor de etiquetas…
  commonMain/   Kotlin común
  androidMain/  Selectores, tipografía y colores de portada en Android
  iosMain/      App de iOS: AVFoundation, escáner, etiquetas, Soulseek, selectores nativos
androidApp/   App de Android: Media3, SAF, jaudiotagger, servicio de descargas
iosApp/       Proyecto de Xcode (XcodeGen) que envuelve el framework `Shared`
```

## Novedades de la versión 1.2 (Android e iPhone)

Borrar álbumes (con doble confirmación), escaneo automático, completar datos de álbum desde MusicBrainz y Deezer,
paletas de color aleatorias y editables, cambiar la foto de un artista, poner un álbum entero en la cola y
letras que ya no necesitan recarga forzada. En iPhone, el escaneo automático revisa la carpeta al volver a la app
y cada 45 s mientras está abierta (iOS no avisa a las apps de cambios en Archivos).

## Diferencias que impone iOS

- Edición de etiquetas: FLAC y MP3 (M4A/ALAC se leen, pero no se editan aún).
- iOS no reproduce OGG/Opus con AVFoundation.
- Las descargas de Soulseek siguen unos segundos en segundo plano; con la app cerrada se pausan.

## CI

Cada push a `main`:
- APK de Android (artefacto `Surco-apk`), pruebas unitarias y capturas → rama `ci`;
- emulador Android → rama `ci-device`;
- pruebas del código común en el simulador de iOS → rama `ci-ios`;
- **.ipa sin firmar** → release `ios-latest`, y capturas reales en el simulador de iPhone → rama `ci-ios-app`.

La firma de Android usa una clave fija (`signing/surco.jks`) para que las actualizaciones se instalen encima.

---

# Surco

Reproductor de música local para Android, pensado para quien cuida su colección: escanea una carpeta,
edita etiquetas y portadas dentro de los propios archivos, busca portadas en internet y hace scrobbling a Last.fm.

Kotlin · Jetpack Compose · Media3/ExoPlayer · Room · jaudiotagger · minSdk 26 (Android 8.0)

## Compilar e instalar

### Opción A: Android Studio
1. Abre la carpeta `Surco` con Android Studio (Ladybug o más reciente) y espera a que sincronice Gradle.
2. Conecta el móvil con la depuración USB activada y pulsa ▶️ *Run*.
   Para un APK suelto: *Build › Build App Bundle(s)/APK(s) › Build APK(s)*.

### Opción B: GitHub Actions (sin instalar nada)
1. Sube la carpeta a un repositorio de GitHub.
2. En la pestaña *Actions* se ejecuta el flujo **Compilar APK**; al terminar, descarga el artefacto `Surco-apk`.
3. (Opcional) Añade los secretos `LASTFM_API_KEY` y `LASTFM_SECRET` en *Settings › Secrets › Actions*
   para que el APK ya lleve tus claves de Last.fm.

El APK de *release* va firmado con la clave de depuración para poder instalarlo directamente.

## Last.fm

Hay tres modos (Ajustes › Last.fm):

- **Integrado**: Surco habla directamente con la API de Last.fm (la misma que usan Pano Scrobbler y Web Scrobbler).
  Aplica las reglas oficiales (canción de más de 30 s, escuchada la mitad o 4 minutos), envía "Now playing"
  y guarda los scrobbles en una cola si no hay conexión.
  Necesitas una API key y un *shared secret* gratuitos: https://www.last.fm/api/account/create
  Puedes pegarlos en Ajustes o ponerlos en `local.properties`:
  ```
  lastfm.apiKey=TU_API_KEY
  lastfm.secret=TU_SECRET
  ```
- **Pano Scrobbler u otra app**: Surco publica una `MediaSession` estándar; activa Surco en la lista de apps de
  Pano Scrobbler y él hace el resto. (Así evitas scrobbles duplicados.)
- **Desactivado**.

Con la API key, la pantalla de inicio también usa `artist.getSimilar` para la sección «Más como…».

## Qué hace

- **Escaneo de carpeta** con el Storage Access Framework (incremental: sólo relee lo que cambia).
  Agrupa álbumes por *artista del álbum*; si falta, por carpeta, para que los recopilatorios no se rompan.
- **Formatos**: FLAC, MP3, M4A/AAC/ALAC, OGG, Opus, WAV, AIFF… (lo que soporte ExoPlayer/el dispositivo).
  Muestra la calidad real (p. ej. `FLAC · 24 bit / 96 kHz`, en Android 12+).
- **Editor de etiquetas** por canción (título, artista, álbum, artista del álbum, año, pista, disco, género)
  y por álbum completo (incluida la portada). Los cambios se escriben en el archivo.
- **Portadas**: de la propia etiqueta, de `cover.jpg`/`folder.jpg`, de la galería o buscadas en
  Deezer, iTunes y MusicBrainz/Cover Art Archive. Búsqueda manual con resultados para elegir, o automática
  para todos los álbumes sin portada. Opcionalmente se incrustan en los archivos.
- **Fotos de artistas** desde Deezer.
- **Interfaz**: Inicio (accesos rápidos, «Sumérgete de nuevo», Recientes, «Recomendada para hoy», «Más como…»,
  artistas favoritos), Buscar (recientes y géneros), Tu biblioteca (listas, álbumes y artistas, lista/cuadrícula),
  mini reproductor con el color de la portada y reproductor a pantalla completa con cola, aleatorio y repetición.
- **Listas de reproducción** y «Canciones que te gustan».
- La cola y la posición se recuerdan al cerrar la app.

## Novedades de la versión 1.1

- **Idioma**: inglés por defecto; español en *Settings › Language*. Todos los textos están en
  `app/src/main/java/dev/surco/i18n/Strings.kt` (cada texto, una vez, en los dos idiomas).
- **Letras sincronizadas** debajo del reproductor y a pantalla completa (toca una línea para saltar).
  Orden de búsqueda: archivo `.lrc` junto a la canción → etiquetas del archivo → [LRCLIB](https://lrclib.net).
  Se pueden escribir/editar y guardar dentro del archivo.
- **Soulseek** (la red de Nicotine+): buscar, ver resultados por carpeta con calidad y velocidad,
  y descargar directamente a tu carpeta de música (una subcarpeta por álbum). Al terminar se re-escanea solo.
  Surco sólo descarga, no comparte. Se identifica como cliente experimental (versión 177), como pide
  la [documentación del protocolo](https://nicotine-plus.org/doc/SLSKPROTOCOL.html).
- **Recomendaciones** en Inicio: artistas parecidos a lo que más escuchas (Deezer) y su álbum más
  popular que aún no tienes. Al abrirlo, «Buscar en Soulseek».

## CI y capturas

Cada push a `main` compila el APK (artefacto `Surco-apk`), ejecuta los tests y genera capturas de las
pantallas principales con Robolectric + Roborazzi. El log y las capturas se publican en la rama `ci`.

## Estructura

```
app/src/main/java/dev/surco/
  data/       Room (canciones, historial, listas…), escáner, repositorio, preferencias
  tags/       escritura de etiquetas con jaudiotagger
  i18n/       textos en inglés y español
  net/        portadas, Last.fm, LRCLIB y Deezer
  slsk/       cliente de Soulseek y descargas
  playback/   servicio Media3, contador de scrobbles, conexión con la UI
  ui/         pantallas Compose
```
