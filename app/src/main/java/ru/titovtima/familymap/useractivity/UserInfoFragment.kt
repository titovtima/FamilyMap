package ru.titovtima.familymap.useractivity

import android.graphics.Typeface
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.setPadding
import ru.titovtima.familymap.R
import ru.titovtima.familymap.databinding.FragmentUserInfoBinding
import ru.titovtima.familymap.model.Settings
import ru.titovtima.familymap.model.SharedPrefsKeys

class UserInfoFragment : Fragment() {
    private lateinit var parentActivity: UserActivity

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentUserInfoBinding.inflate(inflater, container, false)

        parentActivity = requireActivity() as UserActivity
        val user = Settings.user
        if (user == null) {
            parentActivity.showLogInSection()
            return binding.root
        }

        binding.userName.text = user.name
        binding.loginView.text = user.login

        binding.logout.setOnClickListener {
            Settings.user = null
            Settings.sharedPreferencesObject?.edit()
                ?.putString(SharedPrefsKeys.KEY_USER_AUTH_STRING.string, null)
                ?.apply()
            parentActivity.showLogInSection()
        }

        for (contact in user.contacts) {
            val textView = TextView(parentActivity)
            textView.text = contact.name
            textView.textSize = 25f
            textView.setBackgroundResource(R.drawable.border)
            textView.setPadding(10)
            textView.setOnClickListener {
                parentActivity.showContactFragment(contact.contactId)
            }
            binding.contactsListLayout.addView(textView)
        }

        val textView = TextView(parentActivity)
        textView.setText(R.string.add_contact)
        textView.textSize = 25f
        textView.setTypeface(null, Typeface.ITALIC)
        textView.setBackgroundResource(R.drawable.border)
        textView.setPadding(10)
        textView.setOnClickListener {
            NewContactDialogFragment().show(childFragmentManager, "newContact")
        }
        binding.contactsListLayout.addView(textView)

        return binding.root
    }
}