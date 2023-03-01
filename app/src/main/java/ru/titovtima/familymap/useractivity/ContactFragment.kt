package ru.titovtima.familymap.useractivity

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import ru.titovtima.familymap.databinding.FragmentContactBinding
import ru.titovtima.familymap.model.Contact
import ru.titovtima.familymap.model.Settings

class ContactFragment : Fragment() {
    private lateinit var contact: Contact
    private lateinit var parentActivity: UserActivity
    private lateinit var binding: FragmentContactBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val contactId = arguments?.getInt("contactId")
        contact = Settings.user?.contacts?.find { contact -> contact.contactId == contactId }
            ?: throw IllegalStateException("Contact fragment with no contact")
        Log.d("myLogs", contact.toString())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        parentActivity = requireActivity() as UserActivity

        binding = FragmentContactBinding.inflate(inflater, container, false)

        binding.contactName.text = contact.name
        binding.loginView.text = contact.login
        binding.checkboxShareLocation.isChecked = contact.shareLocation
        binding.checkboxShowLocation.isChecked = contact.showLocation

        binding.checkboxShowLocation.setOnCheckedChangeListener { _, _ ->
            binding.save.visibility = View.VISIBLE
        }
        binding.checkboxShareLocation.setOnCheckedChangeListener { _, _ ->
            binding.save.visibility = View.VISIBLE
        }

        binding.save.setOnClickListener {
            runBlocking {
                postUpdateToServer()
            }
        }

        parentActivity.onBackPressedDispatcher.addCallback(this) {
            parentActivity.showUserSection()
        }

        return binding.root
    }

    private suspend fun postUpdateToServer(): Boolean {
        val authString = Settings.user?.authString ?: return false
        val contactId = contact.contactId
        val showLocation = binding.checkboxShowLocation.isChecked
        val shareLocation = binding.checkboxShareLocation.isChecked
        val jsonString = "{\"contactId\":$contactId," +
                "\"showLocation\":$showLocation," +
                "\"shareLocation\":$shareLocation}"
        val response = Settings.httpClient.post(
            "https://familymap.titovtima.ru/contacts/update") {
            headers {
                append("Authorization", "Basic $authString")
                append("Content-Type", "application/json")
            }
            setBody(jsonString)
        }
        return if (response.status.value == 200) {
            contact.showLocation = showLocation
            contact.shareLocation = shareLocation
            Log.d("myLogs", "Write: $contact")
            Settings.user?.contacts?.replaceAll { if (it.contactId == contactId) contact else it }
            binding.save.visibility = View.GONE
            true
        } else {
            Toast.makeText(parentActivity, "Ошибка при изменении контакта", Toast.LENGTH_SHORT)
                .show()
            false
        }
    }

    companion object {
        fun newInstance(contactId: Int): ContactFragment {
            val args = Bundle()
            args.putInt("contactId", contactId)
            val fragment = ContactFragment()
            fragment.arguments = args
            return fragment
        }
    }
}