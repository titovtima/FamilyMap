package ru.titovtima.familymap.useractivity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ru.titovtima.familymap.databinding.ActivityUserBinding
import ru.titovtima.familymap.model.Settings


class UserActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUserBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user = Settings.user
        if (user == null) {
            showLogInSection()
        } else {
            showUserSection()
        }
    }

    fun showLogInSection() {
        binding.userLoginTextView.text = "Вы не представились"
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(binding.fragmentsContainer.id, LogInFragment::class.java, null)
            .commit()
    }

    fun showUserSection() {
        val user = Settings.user
        if (user == null) {
            showLogInSection()
            return
        }
        binding.userLoginTextView.text = user.name
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(binding.fragmentsContainer.id, UserInfoFragment::class.java, null)
            .commit()
    }

    fun showRegistrationSection() {
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(binding.fragmentsContainer.id, RegistrationFragment::class.java, null)
            .commit()
    }
}