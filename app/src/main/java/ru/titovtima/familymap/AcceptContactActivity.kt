package ru.titovtima.familymap

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import ru.titovtima.familymap.databinding.ActivityAcceptContactBinding
import ru.titovtima.familymap.model.Contact
import ru.titovtima.familymap.model.Settings

class AcceptContactActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAcceptContactBinding
    private lateinit var contactLogin: String

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        contactLogin = intent.getStringExtra("contactLogin")
            ?: throw RuntimeException("AcceptContactActivity with no contactLogin")

        val notificationId = intent.getIntExtra("notificationId", -1)

        binding = ActivityAcceptContactBinding.inflate(layoutInflater)

        binding.loginView.text = contactLogin
        var contactName = contactLogin
        GlobalScope.launch {
            contactName = usernameRequest() ?: contactLogin
            runOnUiThread {
                binding.contactNameView.text = contactName
            }
        }

        binding.save.setOnClickListener {
            runBlocking {
                if (postToServer(contactName)) {
                    Settings.ignoreContactAsk(contactLogin)
                    if (notificationId != -1) {
                        NotificationManagerCompat.from(this@AcceptContactActivity)
                            .cancel(notificationId)
                    }
                    Settings.contactAsksNotifications.remove(contactLogin)

                    val mainActivityIntent = Intent(this@AcceptContactActivity, MainActivity::class.java)
                    startActivity(mainActivityIntent)
                }
            }
        }

        binding.reject.setOnClickListener {
            Settings.ignoreContactAsk(contactLogin)
            if (notificationId != -1) {
                NotificationManagerCompat.from(this@AcceptContactActivity)
                    .cancel(notificationId)
            }
            Settings.contactAsksNotifications.remove(contactLogin)

            val mainActivityIntent = Intent(this@AcceptContactActivity, MainActivity::class.java)
            startActivity(mainActivityIntent)
        }

        setContentView(binding.root)
    }

    private suspend fun usernameRequest(): String? {
        return try {
            val response = Settings.httpClient.get(
                "https://familymap.titovtima.ru/username/$contactLogin")
            if (response.status.value == 200) {
                response.body()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun postToServer(contactName: String): Boolean {
        val authString = Settings.user?.authString ?: return false
        val showLocation = binding.checkboxShowLocation.isChecked
        val shareLocation = binding.checkboxShareLocation.isChecked
        val jsonString = "{\"login\":$contactLogin," +
                "\"name\":\"$contactName\"," +
                "\"showLocation\":$showLocation," +
                "\"shareLocation\":$shareLocation}"
        try {
            val response = Settings.httpClient.post(
                "https://familymap.titovtima.ru/contacts/add") {
                headers {
                    append("Authorization", "Basic $authString")
                    append("Content-Type", "application/json")
                }
                setBody(jsonString)
            }
            return if (response.status.value == 201) {
                val body = response.body<String>()
                val contact = Json.decodeFromString<Contact>(body)
                Settings.user?.contacts?.add(contact)
                true
            } else {
                Toast.makeText(this, getString(R.string.error_adding_contact), Toast.LENGTH_SHORT)
                    .show()
                false
            }
        } catch (_: Exception) {
            return false
        }
    }
}