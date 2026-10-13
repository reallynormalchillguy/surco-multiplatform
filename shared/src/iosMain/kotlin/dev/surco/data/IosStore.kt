package dev.surco.data

import platform.Foundation.NSUserDefaults

/** Ajustes guardados en NSUserDefaults. */
class IosStore(private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults) : KeyValueStore {
    override fun string(key: String): String? = defaults.stringForKey(key)
    override fun bool(key: String, default: Boolean): Boolean =
        if (defaults.objectForKey(key) == null) default else defaults.boolForKey(key)
    override fun save(strings: Map<String, String?>, bools: Map<String, Boolean>) {
        strings.forEach { (k, v) -> if (v == null) defaults.removeObjectForKey(k) else defaults.setObject(v, k) }
        bools.forEach { (k, v) -> defaults.setBool(v, k) }
    }
}
