package ru.titovtima.familymap.useractivity

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        parentActivity = activity as UserActivity
        val user = Settings.user
        if (user == null) {
            parentActivity.showLogInSection()
            return binding.root
        }

        binding.loginView.text = user.login

        binding.logout.setOnClickListener {
            Settings.user = null
            Settings.sharedPreferencesObject?.edit()
                ?.putString(SharedPrefsKeys.KEY_USER_AUTH_STRING.string, null)
                ?.apply()
            parentActivity.showLogInSection()
        }

        return binding.root
    }
}