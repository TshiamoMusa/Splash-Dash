
package com.example.splashanddash

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : BaseActivity() {

    private val settingsPrefs by lazy {
        getSharedPreferences("SplashDashSettings", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(createSettingsScreen())
    }

    private fun createSettingsScreen(): LinearLayout {

        val root = createScreen()

        // Add the shared navigation bar.
        root.addView(navBar())

        val scroll = content()
        val page = column(20)

        page.addView(pageHeader(
            "Settings",
            "Manage your app preferences and account."
        ))

        // My Profile opens the separate ProfileActivity.
        page.addView(createSettingsCard(
            "My Profile",
            "View your account details and update your home address."
        ) {
            startActivity(Intent(this, ProfileActivity::class.java))
        })

        // Password management entry.
        page.addView(createSettingsCard(
            "Change Password",
            "Manage your account password."
        ) {
            Toast.makeText(
                this,
                "Password management is not connected yet.",
                Toast.LENGTH_LONG
            ).show()
        })

        // Application information.
        page.addView(createSettingsCard(
            "About Splash & Dash",
            "Application information."
        ) {
            showAbout()
        })

        page.addView(sectionTitle("Appearance"))

        val appearanceCard = card()

        val darkModeSwitch = SwitchMaterial(this).apply {
            text = "Dark Mode"
            isChecked = settingsPrefs.getBoolean("theme", false)
            setTextColor(textColor)

            setOnCheckedChangeListener { _, enabled ->

                // Save the selected theme.
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

        page.addView(sectionTitle("Notifications"))

        val notificationCard = card()

        val notificationSwitch = SwitchMaterial(this).apply {
            text = "Enable Notifications"
            isChecked = settingsPrefs.getBoolean("notifications", true)
            setTextColor(textColor)

            setOnCheckedChangeListener { _, enabled ->

                // Save the notification preference locally.
                settingsPrefs.edit()
                    .putBoolean("notifications", enabled)
                    .apply()
            }
        }

        notificationCard.addView(notificationSwitch)
        page.addView(notificationCard)

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
            17,
            true,
            textColor
        )

        val descriptionView = text(
            description,
            13,
            false,
            muted
        )

        option.addView(titleView)
        option.addView(descriptionView)

        option.setOnClickListener {
            action()
        }

        return option
    }

    // Displays application information.
    private fun showAbout() {

        android.app.AlertDialog.Builder(this)
            .setTitle("About Splash & Dash")
            .setMessage(
                "Splash & Dash Car Wash\n\n" +
                        "Version 1.0\n\n" +
                        "Manage your car wash bookings and services."
            )
            .setPositiveButton("Close", null)
            .show()
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
