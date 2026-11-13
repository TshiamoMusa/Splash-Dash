package com.example.splashanddash

import android.os.Bundle
import android.util.Patterns
import android.widget.EditText
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class RegisterActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val screen = content()
        val main = column()

        main.addView(
            sectionTitle(
                "Create Account",
                "Register securely with SplashDash"
            )
        )

        main.addView(text("Personal Information", 20f, true))

        val fullNameField = input("Full Name")
        val emailField = input("Email Address")
        val phoneField = input("Phone Number")
        val addressField = input("Home Address")

        emailField.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

        phoneField.inputType =
            android.text.InputType.TYPE_CLASS_PHONE

        main.addView(fullNameField)
        main.addView(emailField)
        main.addView(phoneField)
        main.addView(addressField)

        main.addView(text("Security", 20f, true))

        val passwordField = input("Password")
        passwordField.inputType = 129

        val confirmPassword = input("Confirm Password")
        confirmPassword.inputType = 129

        main.addView(passwordField)
        main.addView(confirmPassword)

        val registerButton = button("CREATE ACCOUNT") {

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
                fullName.isBlank() -> {
                    showMessage("Please enter your full name.")
                }

                !Patterns.EMAIL_ADDRESS.matcher(userEmail).matches() -> {
                    showMessage("Please enter a valid email address.")
                }

                userPhone.isBlank() -> {
                    showMessage("Please enter your phone number.")
                }

                userAddress.isBlank() -> {
                    showMessage("Please enter your home address.")
                }

                userPassword.length < 8 -> {
                    showMessage(
                        "Your password must contain at least 8 characters."
                    )
                }

                userPassword != confirm -> {
                    showMessage("Passwords do not match.")
                }

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
                                    "Account created successfully! Please login.",
                                    "Registration Successful"
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

        main.addView(
            outlineButton("ALREADY HAVE AN ACCOUNT? LOGIN") {
                go(LoginActivity::class.java)
            }
        )

        main.addView(
            outlineButton("BACK TO HOME") {
                go(MainActivity::class.java)
            }
        )

        main.addView(footer())
        screen.addView(main)
        setContentView(screen)
    }

    private fun showMessage(
        message: String,
        title: String = "SplashDash",
        onOk: (() -> Unit)? = null
    ) {
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { _, _ ->
                onOk?.invoke()
            }
            .show()
    }
}
