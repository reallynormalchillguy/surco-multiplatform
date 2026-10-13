package dev.surco.platform

import android.content.Context
import dev.surco.data.AppFiles
import java.io.File

class AndroidFiles(context: Context) : AppFiles {
    private val dir = context.filesDir
    override fun readText(name: String): String? = File(dir, name).takeIf { it.exists() }?.readText()
    override fun writeText(name: String, text: String) = File(dir, name).writeText(text)
}
