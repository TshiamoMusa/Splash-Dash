package com.example.splashanddash

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * Login screen of the customer app.
 * Supports email/password login through the API and Google Sign-In
 * (Firebase Auth, then synced to the API).
 */
class LoginActivity : BaseActivity() {

    // Guards against double taps while a login is running / after success.
    private var isLoggingIn = false
    private var hasNavigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val screen = content()
        val main = column()

        // APP LOGO
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.splash_dash_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        main.addView(logo, LinearLayout.LayoutParams(-1, dp(140)))

        main.addView(
            sectionTitle(
                "Welcome back",
                "Sign in to your Splash & Dash account"
            )
        )

        // LOGIN FIELDS
        val email = input("Email address")
        email.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

        val password = input("Password")
        password.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

        main.addView(email)
        main.addView(password)
        addSpacing(main, 4)

        // EMAIL AND PASSWORD LOGIN
        main.addView(
            button("Log in") {

                if (isLoggingIn || hasNavigated) return@button

                val identifier = email.text.toString().trim()
                val userPassword = password.text.toString()

                // Basic validation before calling the API
                if (identifier.isBlank()) {
                    showMessage("Enter your email address.")
                    return@button
                }

                if (userPassword.isBlank()) {
                    showMessage("Enter your password.")
                    return@button
                }

                isLoggingIn = true

                lifecycleScope.launch {
                    val result = ApiClient.login(this@LoginActivity, identifier, userPassword)

                    runOnUiThread {
                        isLoggingIn = false
                        if (result.isSuccess) {
                            openDashboard()
                        } else {
                            showMessage(
                                "Login failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
                            )
                        }
                    }
                }
            }
        )

        addSpacing(main, 8)

        main.addView(
            text("or", 14f, false, muted).apply { gravity = Gravity.CENTER }
        )

        addSpacing(main, 8)

        // GOOGLE SIGN-IN (Firebase first, then synced to our API)
        main.addView(
            outlineButton("Continue with Google") {

                if (isLoggingIn || hasNavigated) return@outlineButton

                isLoggingIn = true

                lifecycleScope.launch {
                    try {
                        GoogleSignInManager.signIn(this@LoginActivity) { success, message ->
                            runOnUiThread {
                                if (success) {
                                    val user = FirebaseAuth.getInstance().currentUser
                                    if (user != null) {
                                        // Tell our API about the Google user so it issues a token.
                                        lifecycleScope.launch {
                                            val apiResult = ApiClient.googleLogin(
                                                this@LoginActivity,
                                                user.email ?: "",
                                                user.displayName ?: "Google Customer",
                                                user.uid
                                            )
                                            runOnUiThread {
                                                isLoggingIn = false
                                                if (apiResult.isSuccess) {
                                                    openDashboard()
                                                } else {
                                                    showMessage(
                                                        apiResult.exceptionOrNull()?.message
                                                            ?: "Google login sync failed."
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        isLoggingIn = false
                                        showMessage("Google authentication user not found.")
                                    }
                                } else {
                                    isLoggingIn = false
                                    showMessage(message)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        isLoggingIn = false
                        showMessage(e.localizedMessage ?: "Google Sign-In failed.")
                    }
                }
            }
        )

        addSpacing(main, 12)

        // REGISTRATION
        main.addView(
            outlineButton("Create account") {
                if (!isLoggingIn) go(RegisterActivity::class.java)
            }
        )

        // The old "Back to home" button was removed: the header has a back arrow now.

        main.addView(footer())

        screen.addView(main)

        // Fixed header (back arrow) + scrolling content
        showScreen(screen)
    }

    /** The app is customer-only, so every successful login lands on the customer dashboard. */
    private fun openDashboard() {
        if (hasNavigated) return
        hasNavigated = true

        startActivity(Intent(this, CustomerDashboardActivity::class.java))
        finish()
    }

    /** Shows a dialog, but only if the screen is still alive. */
    private fun showMessage(message: String) {
        if (isFinishing || isDestroyed) return
        showAppMessage(message)
    }
}