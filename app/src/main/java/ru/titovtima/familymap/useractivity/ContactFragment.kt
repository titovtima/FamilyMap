package ru.titovtima.familymap.useractivity

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import ru.titovtima.familymap.MainActivity
import ru.titovtima.familymap.R
import ru.titovtima.familymap.databinding.FragmentContactBinding
import ru.titovtima.familymap.model.Contact
import ru.titovtima.familymap.model.Settings
import ru.titovtima.familymap.model.toMyFormatString
import java.util.Date

class ContactFragment : Fragment() {
    private lateinit var contact: Contact
    private lateinit var parentActivity: UserActivity
    private lateinit var binding: FragmentContactBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val contactId = arguments?.getInt("contactId")
        contact = Settings.user?.contacts?.find { contact -> contact.contactId == contactId }
            ?: throw IllegalStateException("Contact fragment with no contact")
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

        binding.delete.setBackgroundColor(resources.getColor(R.color.red, parentActivity.theme))
        binding.delete.setOnClickListener {
            runBlocking {
                postDeletingToServer()
            }
        }

        val lastLocation = contact.lastKnownLocation
        if (lastLocation != null) {
            val date = Date(lastLocation.date)
            binding.lastLocationText.text = date.toMyFormatString()

            binding.lastLocationLayout.setOnClickListener {
                val intent = Intent(parentActivity, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                intent.putExtra("moveMapLatitude", lastLocation.latitude)
                intent.putExtra("moveMapLongitude", lastLocation.longitude)
                startActivity(intent)
            }
        } else {
            binding.lastLocationText.text = getString(R.string.location_unknown)
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
        try {
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
                Settings.user?.contacts?.replaceAll { if (it.contactId == contactId) contact else it }
                binding.save.visibility = View.GONE
                if (!showLocation) {
                    MainActivity.getInstance()?.deleteContactLocationPlacemark(contactId)
                }
                true
            } else {
                Toast.makeText(parentActivity, getString(R.string.error_updating_contact), Toast.LENGTH_SHORT)
                    .show()
                false
            }
        } catch (_: Exception) {
            Toast.makeText(parentActivity, getString(R.string.error_updating_contact), Toast.LENGTH_SHORT)
                .show()
            return false
        }
    }

    private suspend fun postDeletingToServer(): Boolean {
        val authString = Settings.user?.authString ?: return false
        val contactId = contact.contactId
        val jsonString = "{\"contactId\":$contactId}"
        try {
            val response = Settings.httpClient.post(
                "https://familymap.titovtima.ru/contacts/delete") {
                headers {
                    append("Authorization", "Basic $authString")
                    append("Content-Type", "application/json")
                }
                setBody(jsonString)
            }
            return if (response.status.value == 200) {
                Settings.user?.contacts?.removeIf { it.contactId == contactId }
                parentActivity.showUserSection()
                MainActivity.getInstance()?.deleteContactLocationPlacemark(contactId)
                true
            } else {
                Toast.makeText(parentActivity, getString(R.string.error_deleting_contact), Toast.LENGTH_SHORT)
                    .show()
                false
            }
        } catch (_: Exception) {
            Toast.makeText(parentActivity, getString(R.string.error_deleting_contact), Toast.LENGTH_SHORT)
                .show()
            return false
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