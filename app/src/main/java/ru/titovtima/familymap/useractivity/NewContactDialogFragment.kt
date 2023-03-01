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
                if (!postNewContact()) {
                    Toast.makeText(parentActivity, "Ошибка при добавлении конакта",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }

        builder.setView(binding.root)
        return builder.create()
    }

    private suspend fun postNewContact(): Boolean {
        val authString = Settings.user?.authString ?: return false
        val login = binding.loginInput.text.toString()
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
            this.dialog?.dismiss()
            parentActivity.showUserSection()
            true
        } else {
            false
        }
    }
}