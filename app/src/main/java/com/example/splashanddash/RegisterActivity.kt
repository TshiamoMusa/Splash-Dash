package com.example.splashanddash

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.Patterns
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Customer registration screen. Creates the account through the API. */
class RegisterActivity : BaseActivity() {

    companion object {
        // Minimum letters for the name and surname (change here if needed)
        private const val MIN_NAME_LENGTH = 3

        // Letters only (any language), no spaces, digits or special characters
        private val NAME_REGEX = Regex("^\\p{L}{$MIN_NAME_LENGTH,}$")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val screen = content()
        val main = column()

        main.addView(
            sectionTitle(
                "Create account",
                "Register securely with Splash & Dash"
            )
        )

        // PERSONAL INFORMATION
        main.addView(sectionTitle("Personal information"))

        val nameField = input("Name")
        val surnameField = input("Surname")
        val emailField = input("Email address")
        val phoneField = input("Phone number")
        val addressField = input("Home address")

        // Name and surname: capitalised words, and the keyboard cannot type
        // anything except letters (blocks digits and special characters).
        listOf(nameField, surnameField).forEach { field ->
            field.inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_WORDS or
                    InputType.TYPE_TEXT_VARIATION_PERSON_NAME
            field.filters = arrayOf(lettersOnlyFilter())
        }

        emailField.inputType =
            InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

        phoneField.inputType = InputType.TYPE_CLASS_PHONE

        main.addView(nameField)
        main.addView(surnameField)
        main.addView(emailField)
        main.addView(phoneField)
        main.addView(addressField)

        // SECURITY
        main.addView(sectionTitle("Security"))

        val passwordField = input("Password")
        passwordField.inputType = 129 // text + password (hidden characters)

        val confirmPassword = input("Confirm password")
        confirmPassword.inputType = 129

        main.addView(passwordField)
        main.addView(confirmPassword)
        addSpacing(main, 4)

        // CREATE ACCOUNT
        val registerButton = button("Create account") {

            val firstName = nameField.text.toString().trim()
            val surname = surnameField.text.toString().trim()
            val userEmail = emailField.text.toString().trim()
            val userPhone = phoneField.text.toString().trim()
            val userAddress = addressField.text.toString().trim()
            val userPassword = passwordField.text.toString()
            val confirm = confirmPassword.text.toString()

            // Validate in order; the first problem found is shown on that field.
            when {
                !NAME_REGEX.matches(firstName) ->
                    fail(nameField, "Name must be at least $MIN_NAME_LENGTH letters, with no spaces, numbers or special characters.")

                !NAME_REGEX.matches(surname) ->
                    fail(surnameField, "Surname must be at least $MIN_NAME_LENGTH letters, with no spaces, numbers or special characters.")

                !Patterns.EMAIL_ADDRESS.matcher(userEmail).matches() ->
                    fail(emailField, "Please enter a valid email address.")

                userPhone.filter { it.isDigit() }.length < 10 ->
                    fail(phoneField, "Please enter a valid phone number (at least 10 digits).")

                userAddress.isBlank() ->
                    fail(addressField, "Please enter your home address.")

                userPassword.length < 8 ->
                    fail(passwordField, "Your password must contain at least 8 characters.")

                userPassword != confirm ->
                    fail(confirmPassword, "Passwords do not match.")

                else -> {
                    lifecycleScope.launch {
                        // Same API call as before: first name and surname are now separate inputs.
                        val result = ApiClient.registerCustomer(
                            this@RegisterActivity,
                            firstName,
                            surname,
                            userEmail,
                            userPhone,
                            userAddress,
                            userPassword
                        )

                        runOnUiThread {
                            if (result.isSuccess) {
                                // After OK, send the new customer to the Login screen.
                                showMessage(
                                    "Account created successfully! Please log in.",
                                    "Registration successful"
                                ) {
                                    go(LoginActivity::class.java)
                                    finish()
                                }
                            } else {
                                showMessage(result.exceptionOrNull()?.message ?: "Registration failed.")
                            }
                        }
                    }
                }
            }
        }

        main.addView(registerButton)

        addSpacing(main, 12)

        main.addView(
            outlineButton("Already have an account? Log in") {
                go(LoginActivity::class.java)
            }
        )

        main.addView(footer())
        screen.addView(main)

        // Fixed header (back arrow) + scrolling content
        showScreen(screen)
    }

    /** Blocks any typed or pasted character that is not a letter. */
    private fun lettersOnlyFilter(): InputFilter {
        return InputFilter { source, start, end, _, _, _ ->
            val typed = source.subSequence(start, end)
            if (typed.all { it.isLetter() }) null else ""
        }
    }

    /** Shows the error on the field itself and puts the cursor there. */
    private fun fail(field: EditText, message: String) {
        field.error = message
        field.requestFocus()
    }

    /** Dialog with an optional action when the user taps OK. */
    private fun showMessage(
        message: String,
        title: String = "Splash & Dash",
        onOk: (() -> Unit)? = null
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> onOk?.invoke() }
            .show()
    }
}