package ru.titovtima.familymap.useractivity

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import ru.titovtima.familymap.R
import ru.titovtima.familymap.databinding.FragmentRegistrationBinding
import ru.titovtima.familymap.model.Settings
import ru.titovtima.familymap.model.SharedPrefsKeys
import ru.titovtima.familymap.model.User
import java.util.Base64

class RegistrationFragment : Fragment() {
    private lateinit var parentActivity: UserActivity

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentRegistrationBinding.inflate(inflater, container, false)

        parentActivity = activity as UserActivity

        binding.logInButton.setOnClickListener {
            parentActivity.showLogInSection()
        }

        binding.registrationButton.setOnClickListener {
            val login = binding.inputLogin.text.toString()
            if (!Settings.loginRegex.matches(login)) {
                Toast.makeText(parentActivity,
                    getString(R.string.login_fails_regex_match),
                    Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val password = binding.inputPassword.text.toString()
            val repeatPassword = binding.inputRepeatPassword.text.toString()
            if (!Settings.loginRegex.matches(password) || Settings.loginRegex.matches(repeatPassword)) {
                Toast.makeText(parentActivity,
                    getString(R.string.password_fails_regex_match),
                    Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password != repeatPassword) {
                Toast.makeText(parentActivity, getString(R.string.password_and_repetition_are_not_equal),
                    Toast.LENGTH_SHORT).show()
                binding.inputPassword.setText("")
                binding.inputRepeatPassword.setText("")
                return@setOnClickListener
            }
            val name = binding.inputName.text.toString()
            val stringToPost = "{\"login\":\"$login\",\"password\":\"$password\",\"name\":\"$name\"}"
            runBlocking {
                val response = Settings.httpClient
                    .post("https://familymap.titovtima.ru/auth/registration") {
                        headers {
                            append("Content-Type", "application/json")
                        }
                        setBody(stringToPost)
                    }
                if (response.status.value == 201) {
                    val user = User(login, name)
                    user.authString = Base64.getEncoder()
                        .encodeToString("$login:$password".toByteArray())
                    Settings.user = user
                    Settings.sharedPreferencesObject?.edit()
                        ?.putString(SharedPrefsKeys.KEY_USER_AUTH_STRING.string, user.authString)
                        ?.apply()
                    parentActivity.showUserSection()
                } else {
                    Toast.makeText(parentActivity, getString(R.string.registration_error),
                        Toast.LENGTH_SHORT).show()
                }
            }
        }

        return binding.root
    }
}