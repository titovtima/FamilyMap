package ru.titovtima.familymap.model

import android.content.SharedPreferences
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

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
    }
}

enum class SharedPrefsKeys(val string: String) {
    KEY_USER_AUTH_STRING("authString")
}
