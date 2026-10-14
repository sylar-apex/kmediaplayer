package org.app.kmediaplayer.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
    fun keys(): Set<String>
}

class MemoryStore(private val map: MutableMap<String, String> = mutableMapOf()) : KeyValueStore {
    override fun getString(key: String) = map[key]
    override fun putString(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    override fun keys() = map.keys.toSet()
}

sealed class Setting<T>(val key: String, val title: String, val summary: String, val default: T) {
    abstract fun parse(raw: String): T?
    open fun encode(value: T): String = value.toString()
}

class BoolSetting(key: String, title: String, default: Boolean, summary: String = "") :
    Setting<Boolean>(key, title, summary, default) {
    override fun parse(raw: String) = raw.toBooleanStrictOrNull()
}

class IntSetting(key: String, title: String, default: Int, val range: IntRange, summary: String = "", val unit: String = "") :
    Setting<Int>(key, title, summary, default) {
    override fun parse(raw: String) = raw.toIntOrNull()?.coerceIn(range)
}

class FloatSetting(key: String, title: String, default: Float, val range: ClosedFloatingPointRange<Float>, summary: String = "", val unit: String = "") :
    Setting<Float>(key, title, summary, default) {
    override fun parse(raw: String) = raw.toFloatOrNull()?.coerceIn(range)
}

class TextSetting(key: String, title: String, default: String, summary: String = "") :
    Setting<String>(key, title, summary, default) {
    override fun parse(raw: String) = raw
}

class ChoiceSetting(key: String, title: String, default: String, val choices: List<Pair<String, String>>, summary: String = "") :
    Setting<String>(key, title, summary, default) {
    override fun parse(raw: String) = raw.takeIf { v -> choices.any { it.first == v } }
    fun labelOf(value: String) = choices.firstOrNull { it.first == value }?.second ?: value
}

/** 0xAARRGGBB colour. */
class ColorSetting(key: String, title: String, default: Long, summary: String = "") :
    Setting<Long>(key, title, summary, default) {
    override fun parse(raw: String) = raw.toLongOrNull()
}

/** A group of settings declared as properties of an `object`; each `r(...)` call registers one. */
abstract class SettingRegistry {
    private val registry = mutableListOf<Setting<*>>()
    protected fun <S : Setting<*>> r(s: S): S = s.also { registry += it }
    val all: List<Setting<*>> get() = registry
}

/** Typed access to every setting of [registries], persisted in [store]. */
class Settings(val store: KeyValueStore, private val registries: List<SettingRegistry>) {
    private val all: List<Setting<*>> get() = registries.flatMap { it.all }

    operator fun <T> get(s: Setting<T>): T = store.getString(s.key)?.let { s.parse(it) } ?: s.default
    operator fun <T> set(s: Setting<T>, value: T) = store.putString(s.key, s.encode(value))

    fun reset() = all.forEach { store.putString(it.key, null) }

    /** Line-based `key=value` export of every changed setting plus any extra [userData] lines. */
    fun export(userData: Map<String, String> = emptyMap()): String = buildString {
        appendLine("#maxplayerpro-settings v1")
        all.forEach { s -> store.getString(s.key)?.let { appendLine("${s.key}=${escape(it)}") } }
        userData.forEach { (k, v) -> appendLine("@$k=${escape(v)}") }
    }

    /** Returns the user-data lines (prefixed `@`) for the caller to restore; settings are applied directly. */
    fun import(text: String): Map<String, String> {
        val known = all.associateBy { it.key }
        val user = mutableMapOf<String, String>()
        text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val k = line.substringBefore('=')
            val v = unescape(line.substringAfter('=', ""))
            if (k.startsWith("@")) user[k.drop(1)] = v
            else known[k]?.let { s -> if (s.parse(v) != null) store.putString(k, v) }
        }
        return user
    }

    private fun escape(v: String) = v.replace("\\", "\\\\").replace("\n", "\\n")
    private fun unescape(v: String) = v.replace("\\n", "\n").replace("\\\\", "\\")
}

/** [Settings] that bumps [version] on every write so screens can re-read. */
class ObservableSettings(store: KeyValueStore, registries: List<SettingRegistry>) {
    val raw = Settings(store, registries)
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    operator fun <T> get(s: Setting<T>): T = raw[s]
    operator fun <T> set(s: Setting<T>, value: T) { raw[s] = value; _version.value++ }
    fun reset() { raw.reset(); _version.value++ }
    fun import(text: String): Map<String, String> = raw.import(text).also { _version.value++ }
}
