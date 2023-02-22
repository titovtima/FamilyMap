package ru.titovtima.familymap.useractivity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ru.titovtima.familymap.databinding.ActivityUserBinding
import ru.titovtima.familymap.model.Settings
import ru.titovtima.familymap.model.User


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
            showUserSection(user)
        }
    }

    fun showLogInSection() {
        binding.userLoginTextView.text = "Вы не представились"
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .add(binding.fragmentsContainer.id, LogInFragment::class.java, null)
            .commit()
    }

    fun showUserSection(user: User) {
        binding.userLoginTextView.text = user.name
    }
}