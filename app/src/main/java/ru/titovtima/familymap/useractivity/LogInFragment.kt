package ru.titovtima.familymap.useractivity

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import ru.titovtima.familymap.R
import ru.titovtima.familymap.databinding.FragmentLogInBinding
import ru.titovtima.familymap.model.Settings
import ru.titovtima.familymap.model.SharedPrefsKeys
import ru.titovtima.familymap.model.User
import java.util.Base64

class LogInFragment : Fragment() {
    private lateinit var parentActivity: UserActivity

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentLogInBinding.inflate(inflater, container, false)

        parentActivity = activity as UserActivity

        binding.registrationButton.setOnClickListener {
            parentActivity.showRegistrationSection()
        }

        binding.logInButton.setOnClickListener {
            val login = binding.inputLogin.text.toString()
            if (!Settings.loginRegex.matches(login)) {
                Toast.makeText(parentActivity,
                    getString(R.string.login_fails_regex_match),
                    Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val password = binding.inputPassword.text.toString()
            if (!Settings.loginRegex.matches(password)) {
                Toast.makeText(parentActivity,
                    getString(R.string.password_fails_regex_match),
                    Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val authString = Base64.getEncoder().encodeToString(("$login:$password").toByteArray())
            runBlocking {
                try {
                    val response = Settings.httpClient
                        .get("https://familymap.titovtima.ru/auth/login") {
                            headers {
                                append("Authorization", "Basic $authString")
                            }
                        }
                    if (response.status.value == 200) {
                        val user = Json.decodeFromString<User>(response.body())
                        user.authString = authString
                        Settings.user = user
                        Settings.sharedPreferencesObject?.edit()
                            ?.putString(SharedPrefsKeys.KEY_USER_AUTH_STRING.string, authString)
                            ?.apply()
                        Settings.locationService?.postLocation(authString)
                        parentActivity.showUserSection()
                    } else {
                        Toast.makeText(parentActivity, getString(R.string.log_in_error),
                            Toast.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) {
                    Toast.makeText(parentActivity, getString(R.string.log_in_error),
                        Toast.LENGTH_SHORT).show()
                }
            }
        }

        return binding.root
    }
}