package com.nativewinruntime

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Simple persisted settings + EXE library, backed by SharedPreferences. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("nwr_prefs", Context.MODE_PRIVATE)

    var gpuDriverIndex: Int
        get() = sp.getInt("gpu_driver_index", 0)
        set(v) = sp.edit().putInt("gpu_driver_index", v).apply()

    var dxWrapperIndex: Int
        get() = sp.getInt("dx_wrapper_index", 0)
        set(v) = sp.edit().putInt("dx_wrapper_index", v).apply()

    var dynarecEnabled: Boolean
        get() = sp.getBoolean("dynarec_enabled", true)
        set(v) = sp.edit().putBoolean("dynarec_enabled", v).apply()

    var resolutionScale: Int
        get() = sp.getInt("resolution_scale", 100)
        set(v) = sp.edit().putInt("resolution_scale", v).apply()

    var showConsole: Boolean
        get() = sp.getBoolean("show_console", true)
        set(v) = sp.edit().putBoolean("show_console", v).apply()

    var allowRotation: Boolean
        get() = sp.getBoolean("allow_rotation", true)
        set(v) = sp.edit().putBoolean("allow_rotation", v).apply()

    data class LibraryEntry(val name: String, val uri: String)

    fun library(): List<LibraryEntry> {
        val raw = sp.getString("library", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            LibraryEntry(o.getString("name"), o.getString("uri"))
        }
    }

    fun addToLibrary(name: String, uri: String) {
        val list = library().toMutableList()
        list.removeAll { it.uri == uri }
        list.add(0, LibraryEntry(name, uri))
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply { put("name", e.name); put("uri", e.uri) })
        }
        sp.edit().putString("library", arr.toString()).apply()
    }

    fun removeFromLibrary(uri: String) {
        val list = library().toMutableList()
        list.removeAll { it.uri == uri }
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply { put("name", e.name); put("uri", e.uri) })
        }
        sp.edit().putString("library", arr.toString()).apply()
    }
}
