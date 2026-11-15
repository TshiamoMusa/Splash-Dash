package com.example.splashanddash

import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Customer registration screen. Creates the account through the API. */
class RegisterActivity : BaseActivity() {

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

        main.addView(sectionTitle("Personal information"))

        val fullNameField = input("Full name")
        val emailField = input("Email address")
        val phoneField = input("Phone number")
        val addressField = input("Home address")

        emailField.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

        phoneField.inputType = android.text.InputType.TYPE_CLASS_PHONE

        main.addView(fullNameField)
        main.addView(emailField)
        main.addView(phoneField)
        main.addView(addressField)

        main.addView(sectionTitle("Security"))

        val passwordField = input("Password")
        passwordField.inputType = 129

        val confirmPassword = input("Confirm password")
        confirmPassword.inputType = 129

        main.addView(passwordField)
        main.addView(confirmPassword)
        addSpacing(main, 4)

        val registerButton = button("Create account") {

            val fullName = fullNameField.text.toString().trim()
            val userEmail = emailField.text.toString().trim()
            val userPhone = phoneField.text.toString().trim()
            val userAddress = addressField.text.toString().trim()
            val userPassword = passwordField.text.toString()
            val confirm = confirmPassword.text.toString()

            val nameParts = fullName.split(" ", limit = 2)
            val firstName = nameParts.getOrNull(0) ?: fullName
            val surname = nameParts.getOrNull(1) ?: "Customer"

            when {
                fullName.isBlank() -> showMessage("Please enter your full name.")

                !Patterns.EMAIL_ADDRESS.matcher(userEmail).matches() ->
                    showMessage("Please enter a valid email address.")

                userPhone.isBlank() -> showMessage("Please enter your phone number.")

                userAddress.isBlank() -> showMessage("Please enter your home address.")

                userPassword.length < 8 ->
                    showMessage("Your password must contain at least 8 characters.")

                userPassword != confirm -> showMessage("Passwords do not match.")

                else -> {
                    lifecycleScope.launch {
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

        addSpacing(main, 12)

        main.addView(
            outlineButton("Back to home") {
                go(MainActivity::class.java)
            }
        )

        main.addView(footer())
        screen.addView(main)
        setContentView(screen)
    }

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