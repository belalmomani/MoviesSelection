package com.tvseries.Movies.data

import android.content.SharedPreferences
import com.tvseries.Movies.core.Selection
import com.tvseries.Movies.core.TasteProfile
import com.tvseries.Movies.core.UserIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
data class SavedFilter(val name: String, val selection: Selection)

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

    private val savedSer = ListSerializer(SavedFilter.serializer())

    private val _saved = MutableStateFlow(
        runCatching { store.getString(K_SAVED)?.let { json.decodeFromString(savedSer, it) } }.getOrNull() ?: emptyList(),
    )
    val savedFilters: StateFlow<List<SavedFilter>> = _saved.asStateFlow()

    private val _watched = MutableStateFlow(readStrings(K_WATCHED).toSet())
    val watched: StateFlow<Set<String>> = _watched.asStateFlow()

    private val _hideWatched = MutableStateFlow(store.getString(K_HIDE_WATCHED) != "false")
    val hideWatched: StateFlow<Boolean> = _hideWatched.asStateFlow()

    private val _intent = MutableStateFlow(
        runCatching { store.getString(K_INTENT)?.let { json.decodeFromString(UserIntent.serializer(), it) } }.getOrNull() ?: UserIntent(),
    )
    val intent: StateFlow<UserIntent> = _intent.asStateFlow()

    private val _taste = MutableStateFlow(
        runCatching { store.getString(K_TASTE)?.let { json.decodeFromString(TasteProfile.serializer(), it) } }.getOrNull() ?: TasteProfile(),
    )
    val taste: StateFlow<TasteProfile> = _taste.asStateFlow()

    private val _weeklyReminder = MutableStateFlow(store.getString(K_REMINDER) == "true")
    val weeklyReminder: StateFlow<Boolean> = _weeklyReminder.asStateFlow()

    private val _watchlist = MutableStateFlow(readCards(K_WATCH))
    val watchlist: StateFlow<List<WorkCard>> = _watchlist.asStateFlow()

    fun saveFilter(name: String, sel: Selection) {
        val n = name.trim()
        if (n.isEmpty() || sel.isEmpty) return
        _saved.value = listOf(SavedFilter(n, sel)) + _saved.value.filterNot { it.name == n }
        store.putString(K_SAVED, json.encodeToString(savedSer, _saved.value))
    }

    fun deleteFilter(name: String) {
        _saved.value = _saved.value.filterNot { it.name == name }
        store.putString(K_SAVED, json.encodeToString(savedSer, _saved.value))
    }

    fun toggleWatched(key: String) {
        _watched.value = if (key in _watched.value) _watched.value - key else _watched.value + key
        store.putString(K_WATCHED, json.encodeToString(stringsSer, _watched.value.toList()))
    }

    fun setIntent(i: UserIntent) {
        _intent.value = i
        store.putString(K_INTENT, json.encodeToString(UserIntent.serializer(), i))
    }

    fun setTaste(t: TasteProfile) {
        _taste.value = t
        store.putString(K_TASTE, json.encodeToString(TasteProfile.serializer(), t))
    }

    fun setWeeklyReminder(v: Boolean) { _weeklyReminder.value = v; store.putString(K_REMINDER, v.toString()) }

    fun setHideWatched(v: Boolean) { _hideWatched.value = v; store.putString(K_HIDE_WATCHED, v.toString()) }

    /** Erases everything the user created (lists, taste, filters, history). Language / theme / key stay. */
    fun clearUserData() {
        _favorites.value = emptyList()
        store.putString(K_FAV, "[]")
        _watchlist.value = emptyList()
        store.putString(K_WATCH, "[]")
        _watched.value = emptySet()
        store.putString(K_WATCHED, "[]")
        _saved.value = emptyList()
        store.putString(K_SAVED, "[]")
        setTaste(TasteProfile())
        setIntent(UserIntent())
        clearHistory()
        saveSelection(Selection())
    }

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
        const val K_INTENT = "user_intent"
        const val K_TASTE = "taste_profile"
        const val K_REMINDER = "weekly_reminder"
        const val K_SAVED = "saved_filters"
        const val K_WATCHED = "watched"
        const val K_HIDE_WATCHED = "hide_watched"
    }
}
