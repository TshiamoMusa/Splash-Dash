
package com.example.splashanddash

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : BaseActivity() {

    private val settingsPrefs by lazy {
        getSharedPreferences("SplashDashSettings", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createSettingsScreen())
    }

    // Safely reads and migrates older saved preferences.
    private fun getBooleanPreference(key: String, defaultValue: Boolean): Boolean {
        val savedValue = settingsPrefs.all[key]

        val value = when (savedValue) {
            is Boolean -> savedValue
            is Int -> savedValue != 0
            is Long -> savedValue != 0L
            is String -> savedValue.toBooleanStrictOrNull() ?: defaultValue
            else -> defaultValue
        }

        if (savedValue != null && savedValue !is Boolean) {
            settingsPrefs.edit()
                .putBoolean(key, value)
                .apply()
        }

        return value
    }

    private fun createSettingsScreen(): LinearLayout {

        val root = createScreen()
        root.addView(navBar())

        val scroll = content()
        val page = column(20)

        page.addView(
            pageHeader(
                "Settings",
                "Manage your app preferences and account."
            )
        )

        // My Profile
        page.addView(
            createSettingsCard(
                "My Profile",
                "View your account details and update your home address."
            ) {
                go(ProfileActivity::class.java)
            }
        )

        // Application information
        page.addView(
            createSettingsCard(
                "About Splash & Dash",
                "Application information."
            ) {
                showAbout()
            }
        )

        // Appearance
        page.addView(sectionTitle("Appearance"))

        val appearanceCard = card()

        val darkModeSwitch = SwitchCompat(this).apply {
            text = "Dark Mode"
            isChecked = getBooleanPreference("theme", false)
            setTextColor(textColor)

            setOnCheckedChangeListener { _, enabled ->
                settingsPrefs.edit()
                    .putBoolean("theme", enabled)
                    .apply()

                AppCompatDelegate.setDefaultNightMode(
                    if (enabled) {
                        AppCompatDelegate.MODE_NIGHT_YES
                    } else {
                        AppCompatDelegate.MODE_NIGHT_NO
                    }
                )
            }
        }

        appearanceCard.addView(darkModeSwitch)
        page.addView(appearanceCard)

        // Notifications
        page.addView(sectionTitle("Notifications"))

        val notificationCard = card()

        val notificationSwitch = SwitchCompat(this).apply {
            text = "Enable Notifications"
            isChecked = getBooleanPreference("notifications", true)
            setTextColor(textColor)

            setOnCheckedChangeListener { _, enabled ->
                settingsPrefs.edit()
                    .putBoolean("notifications", enabled)
                    .apply()
            }
        }

        notificationCard.addView(notificationSwitch)
        page.addView(notificationCard)

        // Preferences
        page.addView(sectionTitle("Preferences"))

        page.addView(
            primaryButton("Reset Preferences").apply {
                setOnClickListener {
                    resetPreferences()
                }
            }
        )

        page.addView(footer())

        scroll.addView(page)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        return root
    }

    // Creates a clickable settings option.
    private fun createSettingsCard(
        title: String,
        description: String,
        action: () -> Unit
    ): LinearLayout {

        val option = card()

        val titleView = text(
            title,
            17f,
            true,
            textColor
        )

        val descriptionView = text(
            description,
            13f,
            false,
            muted
        )

        option.addView(titleView)
        option.addView(descriptionView)

        option.isClickable = true
        option.isFocusable = true

        option.setOnClickListener {
            action()
        }

        return option
    }

    // Displays application information.
    private fun showAbout() {

        showAppMessage(
            "Splash & Dash Car Wash\n\n" +
                    "Version 1.0\n\n" +
                    "Manage your car wash bookings and services.",
            "About Splash & Dash"
        )
    }

    // Resets the saved app preferences.
    private fun resetPreferences() {

        settingsPrefs.edit().clear().apply()

        AppCompatDelegate.setDefaultNightMode(
            AppCompatDelegate.MODE_NIGHT_NO
        )

        Toast.makeText(
            this,
            "Preferences have been reset.",
            Toast.LENGTH_SHORT
        ).show()

        recreate()
    }
}
