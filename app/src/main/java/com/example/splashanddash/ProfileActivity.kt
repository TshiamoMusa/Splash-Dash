
package com.example.splashanddash

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ProfileActivity : BaseActivity() {

    private lateinit var firstNameText: TextView
    private lateinit var surnameText: TextView
    private lateinit var emailText: TextView
    private lateinit var phoneText: TextView

    private lateinit var addressInput: EditText
    private lateinit var profileMessage: TextView
    private lateinit var saveButton: android.widget.Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(createProfileScreen())

        // Load the customer's details when the screen opens.
        loadCustomerProfile()
    }

    private fun createProfileScreen(): LinearLayout {

        val root = createScreen()

        root.addView(navBar())

        val scroll = content()
        val page = column(20)

        page.addView(pageHeader(
            "My Profile",
            "View and update your customer information."
        ))

        page.addView(sectionTitle("Customer Profile"))

        val profileCard = card()

        profileCard.addView(
            text(
                "Your account information",
                14,
                false,
                muted
            )
        )

        // These account details are read-only.
        firstNameText = createProfileField(
            profileCard,
            "FIRST NAME"
        )

        surnameText = createProfileField(
            profileCard,
            "SURNAME"
        )

        emailText = createProfileField(
            profileCard,
            "EMAIL ADDRESS"
        )

        phoneText = createProfileField(
            profileCard,
            "PHONE NUMBER"
        )

        page.addView(profileCard)

        page.addView(sectionTitle("HOME ADDRESS"))

        val addressCard = card()

        addressCard.addView(
            text(
                "Update your saved home address below.",
                13,
                false,
                muted
            )
        )

        // Only the home address can be edited.
        addressInput = input("Enter your home address")

        addressInput.minLines = 2
        addressInput.maxLines = 4
        addressInput.setSingleLine(false)

        addressCard.addView(addressInput)

        profileMessage = text(
            "",
            13,
            false,
            muted
        )

        addressCard.addView(profileMessage)

        saveButton = primaryButton("Save Changes").apply {
            isEnabled = false

            setOnClickListener {
                saveHomeAddress()
            }
        }

        addressCard.addView(saveButton)

        page.addView(addressCard)

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

    // Adds a read-only customer information field.
    private fun createProfileField(
        container: LinearLayout,
        label: String
    ): TextView {

        val field = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }

        field.addView(
            text(
                label,
                11,
                true,
                muted
            )
        )

        val value = text(
            "Loading...",
            15,
            false,
            textColor
        )

        field.addView(value)
        container.addView(field)

        return value
    }

    // Gets the customer's details from the API.
    private fun loadCustomerProfile() {

        saveButton.isEnabled = false

        lifecycleScope.launch {

            val result = ApiClient.getProfile(this@ProfileActivity)

            result.onSuccess { profile ->

                firstNameText.text = profile.firstName
                surnameText.text = profile.surname
                emailText.text = profile.email
                phoneText.text = profile.phoneNumber

                addressInput.setText(profile.homeAddress)

                profileMessage.text = "Profile loaded successfully."
                profileMessage.setTextColor(success)

                saveButton.isEnabled = true
            }

            result.onFailure { error ->

                profileMessage.text =
                    error.message ?: "Unable to load your profile."

                profileMessage.setTextColor(danger)
            }
        }
    }

    // Sends only the updated home address to the API.
    private fun saveHomeAddress() {

        val newAddress = addressInput.text.toString().trim()

        if (newAddress.isBlank()) {

            addressInput.error = "Please enter your home address."
            return
        }

        saveButton.isEnabled = false
        profileMessage.text = "Saving address..."
        profileMessage.setTextColor(muted)

        lifecycleScope.launch {

            val result = ApiClient.updateAddress(
                this@ProfileActivity,
                newAddress
            )

            result.onSuccess {

                profileMessage.text =
                    "Your home address has been updated."

                profileMessage.setTextColor(success)

                saveButton.isEnabled = true
            }

            result.onFailure { error ->

                profileMessage.text =
                    error.message ?: "Unable to update your address."

                profileMessage.setTextColor(danger)

                saveButton.isEnabled = true
            }
        }
    }
}
