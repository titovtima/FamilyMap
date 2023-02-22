package ru.titovtima.familymap.useractivity

import android.os.Bundle
import android.util.Log
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
import ru.titovtima.familymap.databinding.FragmentLogInBinding
import ru.titovtima.familymap.model.Settings
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

        binding.logInButton.setOnClickListener {
            Log.d("myLogs", "onClickListener")
            val login = binding.inputLogin.text.toString()
            val password = binding.inputPassword.text.toString()
            if (login.contains(':') || password.contains(':')) {
                Toast.makeText(
                    this.activity, "Логин и пароль не должны включать символ ':'",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            val authString = Base64.getEncoder().encodeToString(("$login:$password").toByteArray())
            runBlocking {
                Log.d("myLogs", authString)
                val response = Settings.httpClient
                    .post("https://familymap.titovtima.ru/auth/login") {
                        headers {
                            append("Authorization", "Basic $authString")
                        }
                    }
                if (response.status.value == 200) {
                    val user = Json.decodeFromString<User>(response.body())
                    user.authString = authString
                    Settings.user = user
                    parentActivity.showUserSection(user)
                    Log.d("myLogs", user.toString())
                } else {
                    Log.d("myLogs", response.toString())
                }
            }
        }

        return binding.root
    }
}