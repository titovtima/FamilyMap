package ru.titovtima.familymap.useractivity

import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import ru.titovtima.familymap.R
import ru.titovtima.familymap.databinding.FragmentNewContactDialogBinding
import ru.titovtima.familymap.model.Contact
import ru.titovtima.familymap.model.Settings

class NewContactDialogFragment : DialogFragment() {
    private lateinit var binding: FragmentNewContactDialogBinding
    lateinit var parentActivity: UserActivity

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        parentActivity = requireActivity() as UserActivity
        val builder = AlertDialog.Builder(parentActivity)

        binding = FragmentNewContactDialogBinding.inflate(layoutInflater)

        binding.submit.setOnClickListener {
            runBlocking {
                if (postNewContact()) {
                    this@NewContactDialogFragment.dialog?.dismiss()
                    parentActivity.showUserSection()
                }
            }
        }

        builder.setView(binding.root)
        return builder.create()
    }

    private suspend fun postNewContact(): Boolean {
        val authString = Settings.user?.authString
        if (authString == null) {
            Toast.makeText(parentActivity, getString(R.string.no_auth_string), Toast.LENGTH_SHORT).show()
            return false
        }
        val login = binding.loginInput.text.toString()
        if (!Settings.loginRegex.matches(login)) {
            Toast.makeText(parentActivity,
                getString(R.string.login_fails_regex_match),
                Toast.LENGTH_SHORT).show()
            return false
        }
        val showLocation = binding.checkboxShowLocation.isChecked
        val shareLocation = binding.checkboxShareLocation.isChecked
        val jsonString = "{\"login\":\"$login\"," +
                "\"shareLocation\":$shareLocation," +
                "\"showLocation\":$showLocation}"
        val response = Settings.httpClient.post(
            "https://familymap.titovtima.ru/contacts/add") {
            headers {
                append("Authorization", "Basic $authString")
                append("Content-Type", "application/json")
            }
            setBody(jsonString)
        }
        return if (response.status.value == 201) {
            val contact = Json.decodeFromString<Contact>(response.body())
            Settings.user?.contacts?.add(contact)
            true
        } else {
            Toast.makeText(parentActivity, getString(R.string.error_adding_contact),
                Toast.LENGTH_SHORT).show()
            false
        }
    }
}