package com.marlonpra.cameraviewertv

import android.content.Context
import org.json.JSONArray

class RtspStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getUrls(): List<String> {
        val raw = prefs.getString(KEY_URLS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val v = arr.optString(i, "").trim()
            if (v.isNotEmpty()) out.add(v)
        }
        return out
    }

    fun addUrl(url: String) {
        val current = getUrls().toMutableList()
        if (current.contains(url)) return
        current.add(0, url)
        save(current)
    }

    fun removeUrl(url: String) {
        val current = getUrls().toMutableList()
        current.removeAll { it == url }
        save(current)
    }

    private fun save(urls: List<String>) {
        val arr = JSONArray()
        for (u in urls) arr.put(u)
        prefs.edit().putString(KEY_URLS, arr.toString()).apply()
    }

    fun getLastMode(): Int = prefs.getInt(KEY_LAST_MODE, 0)

    fun setLastMode(mode: Int) {
        prefs.edit().putInt(KEY_LAST_MODE, mode).apply()
    }

    fun getLastSingleIndex(): Int = prefs.getInt(KEY_LAST_SINGLE_INDEX, 0)

    fun setLastSingleIndex(index: Int) {
        prefs.edit().putInt(KEY_LAST_SINGLE_INDEX, index).apply()
    }

    fun getSelectedIndicesForMode(mode: Int): IntArray? {
        val key = when (mode) {
            2 -> KEY_SELECTED_MODE_2
            4 -> KEY_SELECTED_MODE_4
            else -> return null
        }
        val raw = prefs.getString(key, null) ?: return null
        return try {
            val arr = JSONArray(raw)
            IntArray(arr.length()) { i -> arr.optInt(i, 0) }
        } catch (_: Exception) {
            null
        }
    }

    fun setSelectedIndicesForMode(mode: Int, indices: IntArray) {
        val key = when (mode) {
            2 -> KEY_SELECTED_MODE_2
            4 -> KEY_SELECTED_MODE_4
            else -> return
        }
        val arr = JSONArray()
        for (i in indices) arr.put(i)
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private companion object {
        private const val PREFS_NAME = "rtsp_store"
        private const val KEY_URLS = "urls"
        private const val KEY_LAST_MODE = "last_mode"
        private const val KEY_LAST_SINGLE_INDEX = "last_single_index"
        private const val KEY_SELECTED_MODE_2 = "selected_mode_2"
        private const val KEY_SELECTED_MODE_4 = "selected_mode_4"
    }
}
