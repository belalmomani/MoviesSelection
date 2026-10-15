package com.tvseries.Movies.data

import android.content.SharedPreferences
import com.tvseries.Movies.core.Selection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Tiny key/value abstraction so settings logic can be unit-tested without Android. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

class MemoryStore : KeyValueStore {
    private val map = HashMap<String, String>()
    override fun getString(key: String): String? = map[key]
    override fun putString(key: String, value: String) {
        map[key] = value
    }
}

class SharedPrefsStore(private val sp: SharedPreferences) : KeyValueStore {
    override fun getString(key: String): String? = sp.getString(key, null)
    override fun putString(key: String, value: String) {
        sp.edit().putString(key, value).apply()
    }
}

/**
 * Local storage: language, theme, content settings, favorites, watch-later, search history (opt-in)
 * and the last filter selection. Everything is stored locally as JSON; no account, no cloud.
 */
class Prefs(private val store: KeyValueStore) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val cardsSer = ListSerializer(WorkCard.serializer())
    private val stringsSer = ListSerializer(String.serializer())

    private val _language = MutableStateFlow(store.getString(K_LANG) ?: "ar")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _theme = MutableStateFlow(store.getString(K_THEME) ?: "system")
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _showAdult = MutableStateFlow(store.getString(K_ADULT) == "true")
    val showAdult: StateFlow<Boolean> = _showAdult.asStateFlow()

    private val _minVotes = MutableStateFlow(store.getString(K_MIN_VOTES)?.toIntOrNull() ?: 100)
    val minVotes: StateFlow<Int> = _minVotes.asStateFlow()

    private val _apiKey = MutableStateFlow(store.getString(K_API_KEY) ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _historyEnabled = MutableStateFlow(store.getString(K_HISTORY_ON) == "true")
    val historyEnabled: StateFlow<Boolean> = _historyEnabled.asStateFlow()

    private val _history = MutableStateFlow(readStrings(K_HISTORY))
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private val _favorites = MutableStateFlow(readCards(K_FAV))
    val favorites: StateFlow<List<WorkCard>> = _favorites.asStateFlow()

    private val _watchlist = MutableStateFlow(readCards(K_WATCH))
    val watchlist: StateFlow<List<WorkCard>> = _watchlist.asStateFlow()

    fun setLanguage(v: String) { _language.value = v; store.putString(K_LANG, v) }
    fun setTheme(v: String) { _theme.value = v; store.putString(K_THEME, v) }
    fun setShowAdult(v: Boolean) { _showAdult.value = v; store.putString(K_ADULT, v.toString()) }
    fun setMinVotes(v: Int) { _minVotes.value = v; store.putString(K_MIN_VOTES, v.toString()) }
    fun setApiKey(v: String) { _apiKey.value = v.trim(); store.putString(K_API_KEY, v.trim()) }

    fun setHistoryEnabled(v: Boolean) {
        _historyEnabled.value = v
        store.putString(K_HISTORY_ON, v.toString())
        if (!v) clearHistory()
    }

    fun addHistory(query: String) {
        if (!_historyEnabled.value) return
        val q = query.trim()
        if (q.length < 2) return
        val next = (listOf(q) + _history.value.filterNot { it.equals(q, ignoreCase = true) }).take(30)
        _history.value = next
        store.putString(K_HISTORY, json.encodeToString(stringsSer, next))
    }

    fun clearHistory() {
        _history.value = emptyList()
        store.putString(K_HISTORY, json.encodeToString(stringsSer, emptyList()))
    }

    fun toggleFavorite(card: WorkCard) {
        _favorites.value = toggle(_favorites.value, card)
        store.putString(K_FAV, json.encodeToString(cardsSer, _favorites.value))
    }

    fun toggleWatchlist(card: WorkCard) {
        _watchlist.value = toggle(_watchlist.value, card)
        store.putString(K_WATCH, json.encodeToString(cardsSer, _watchlist.value))
    }

    fun saveSelection(sel: Selection) {
        store.putString(K_SELECTION, json.encodeToString(Selection.serializer(), sel))
    }

    fun loadSelection(): Selection =
        runCatching { store.getString(K_SELECTION)?.let { json.decodeFromString(Selection.serializer(), it) } }
            .getOrNull() ?: Selection()

    private fun toggle(list: List<WorkCard>, c: WorkCard): List<WorkCard> =
        if (list.any { it.key == c.key }) list.filterNot { it.key == c.key } else listOf(c) + list

    private fun readCards(key: String): List<WorkCard> =
        runCatching { store.getString(key)?.let { json.decodeFromString(cardsSer, it) } }.getOrNull() ?: emptyList()

    private fun readStrings(key: String): List<String> =
        runCatching { store.getString(key)?.let { json.decodeFromString(stringsSer, it) } }.getOrNull() ?: emptyList()

    companion object {
        const val K_LANG = "language"
        const val K_THEME = "theme"
        const val K_ADULT = "show_adult"
        const val K_MIN_VOTES = "min_votes"
        const val K_API_KEY = "tmdb_api_key"
        const val K_HISTORY_ON = "history_enabled"
        const val K_HISTORY = "history"
        const val K_FAV = "favorites"
        const val K_WATCH = "watchlist"
        const val K_SELECTION = "last_selection"
    }
}
