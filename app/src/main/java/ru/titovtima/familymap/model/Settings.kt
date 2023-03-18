package ru.titovtima.familymap.model

import android.content.SharedPreferences
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import ru.titovtima.familymap.LocationService
import java.util.Date

class Settings {
    companion object {
        var user: User? = null
        val httpClient = HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                })
            }
        }
        var sharedPreferencesObject: SharedPreferences? = null
        val loginRegex = Regex("[a-zA-Z0-9а-яА-Я_.-]+")

        private var ignoredContactsAsks_ = mutableSetOf<String>()
        val ignoredContactsAsks
        get() = ignoredContactsAsks_.toSet()
        fun ignoreContactAsk(login: String) {
//            if (ignoredContactsAsks.contains(login)) return
            ignoredContactsAsks_.add(login)
            sharedPreferencesObject?.edit()
                ?.putStringSet(SharedPrefsKeys.KEY_IGNORED_CONTACTS_ASKS.string, ignoredContactsAsks)
                ?.apply()
        }
        fun setIgnoredContactsAsks(set: Set<String>) { ignoredContactsAsks_ = set.toMutableSet() }
        val contactAsksNotifications = mutableMapOf<String, Int>()

        var locationService: LocationService? = null
    }
}

enum class SharedPrefsKeys(val string: String) {
    KEY_USER_AUTH_STRING("authString"),
    KEY_IGNORED_CONTACTS_ASKS("ignoredContactsAsks")
}

fun Date.toMyFormatString(): String =
    "${this.hours.div(10)}${this.hours.mod(10)}:" +
            "${this.minutes.div(10)}${this.minutes.mod(10)} " +
            "${this.date.div(10)}${this.date.mod(10)}." +
            "${(this.month + 1).div(10)}${(this.month + 1).mod(10)}.${this.year + 1900}"
