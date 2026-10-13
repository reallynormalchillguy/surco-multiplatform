package dev.surco.platform

import android.content.Context
import dev.surco.data.KeyValueStore

/** Ajustes guardados en SharedPreferences (mismo archivo «surco» de siempre). */
class AndroidStore(context: Context) : KeyValueStore {
    private val sp = context.getSharedPreferences("surco", Context.MODE_PRIVATE)
    override fun string(key: String): String? = sp.getString(key, null)
    override fun bool(key: String, default: Boolean) = sp.getBoolean(key, default)
    override fun save(strings: Map<String, String?>, bools: Map<String, Boolean>) {
        sp.edit().apply {
            strings.forEach { (k, v) -> putString(k, v) }
            bools.forEach { (k, v) -> putBoolean(k, v) }
        }.apply()
    }
}
