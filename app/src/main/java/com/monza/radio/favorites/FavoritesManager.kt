
package com.monza.radio.favorites

import android.content.Context
import android.content.SharedPreferences

class FavoritesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("monza_favs", Context.MODE_PRIVATE)
    private val KEY = "favorites"

    fun getFavorites(): List<Float> {
        val set = prefs.getStringSet(KEY, emptySet()) ?: emptySet()
        return set.mapNotNull { it.toFloatOrNull() }.sorted()
    }

    fun addFavorite(freq: Float) {
        val set = prefs.getStringSet(KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        set.add("%.1f".format(freq))
        prefs.edit().putStringSet(KEY, set).apply()
    }

    fun removeFavorite(freq: Float) {
        val set = prefs.getStringSet(KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        set.remove("%.1f".format(freq))
        prefs.edit().putStringSet(KEY, set).apply()
    }

    fun isFavorite(freq: Float): Boolean {
        val set = prefs.getStringSet(KEY, emptySet()) ?: emptySet()
        return set.contains("%.1f".format(freq))
    }
}
