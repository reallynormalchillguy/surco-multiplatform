package dev.surco.ios

import dev.surco.i18n.S
import dev.surco.tags.TagEditor
import dev.surco.tags.TagEdits
import dev.surco.tags.TagFileWriter
import dev.surco.tags.TagReader
import dev.surco.tags.TagWriteException
import dev.surco.util.IoDispatcher
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.closeFile
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.seekToEndOfFile
import platform.Foundation.writeData

/**
 * Edita etiquetas en iOS con el escritor común (FLAC y MP3). Escribe la versión nueva en un temporal
 * junto al original (cabecera nueva + audio intacto, en bloques de 1 MB) y luego lo sustituye.
 */
@OptIn(ExperimentalForeignApi::class)
class IosTagEditor : TagEditor {
    override suspend fun write(uri: String, fileName: String, edits: TagEdits) = withContext(IoDispatcher) {
        val url = NSURL.URLWithString(uri) ?: throw TagWriteException(S.cannotOpenFile)
        val path = url.path ?: throw TagWriteException(S.cannotOpenFile)
        val src = IosFileSource.open(url) ?: throw TagWriteException(S.cannotOpenFile)
        val tmp = "$path.surco-tmp"
        try {
            val plan = TagFileWriter.rewrite(src, fileName, edits)
            val fm = NSFileManager.defaultManager
            fm.removeItemAtPath(tmp, null)
            if (!fm.createFileAtPath(tmp, plan.header.toNSData(), null)) throw TagWriteException(S.noWritePermission)
            val out = NSFileHandle.fileHandleForWritingAtPath(tmp) ?: throw TagWriteException(S.noWritePermission)
            try {
                out.seekToEndOfFile()
                var pos = plan.audioOffset
                while (pos < src.size) {
                    val chunk = src.read(pos, 1 shl 20)
                    if (chunk.isEmpty()) break
                    out.writeData(chunk.toNSData())
                    pos += chunk.size
                }
            } finally {
                out.closeFile()
            }
        } catch (e: TagWriteException) {
            NSFileManager.defaultManager.removeItemAtPath(tmp, null)
            throw e
        } catch (e: Throwable) {
            NSFileManager.defaultManager.removeItemAtPath(tmp, null)
            throw TagWriteException(S.couldNotSaveTags, e)
        } finally {
            src.close()
        }
        val fm = NSFileManager.defaultManager
        if (!fm.removeItemAtPath(path, null) || !fm.moveItemAtPath(tmp, path, null)) {
            fm.removeItemAtPath(tmp, null)
            throw TagWriteException(S.noWritePermission)
        }
    }

    override suspend fun readLyrics(uri: String, fileName: String): String? = withContext(IoDispatcher) {
        val url = NSURL.URLWithString(uri) ?: return@withContext null
        url.withSource { TagReader.read(it, fileName).lyrics }?.takeIf { it.isNotBlank() }
    }
}
