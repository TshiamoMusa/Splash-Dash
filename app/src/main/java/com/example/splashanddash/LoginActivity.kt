package com.example.splashanddash

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class LoginActivity : BaseActivity() {

    companion object {
        private const val TAG = "SplashDashLogin"
    }

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

        main.addView(
            logo,
            LinearLayout.LayoutParams(-1, dp(140))
        )

        main.addView(
            sectionTitle(
                "Welcome Back",
                "Sign in to your SplashDash account"
            )
        )

        // LOGIN FIELDS
        val email = input("Email or Username")

        email.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

        val password = input("Password")

        password.inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

        main.addView(email)
        main.addView(password)

        // EMAIL AND PASSWORD LOGIN
        main.addView(
            button("LOGIN") {

                if (isLoggingIn || hasNavigated) {
                    return@button
                }

                val identifier = email.text.toString().trim()
                val userPassword = password.text.toString()

                if (identifier.isBlank()) {
                    showMessage("Enter your email or username.")
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
                            showMessage("Login failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}")
                        }
                    }
                }
            }
        )

        main.addView(
            text("OR", 16f, true)
        )

        // GOOGLE SIGN-IN
        main.addView(
            outlineButton("CONTINUE WITH GOOGLE") {

                if (isLoggingIn || hasNavigated) {
                    return@outlineButton
                }

                isLoggingIn = true

                lifecycleScope.launch {

                    try {

                        GoogleSignInManager.signIn(
                            this@LoginActivity
                        ) { success, message ->

                            runOnUiThread {
                                if (success) {
                                    val user = FirebaseAuth.getInstance().currentUser
                                    if (user != null) {
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
                                                    showMessage(apiResult.exceptionOrNull()?.message ?: "Google login sync failed.")
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

        // REGISTRATION
        main.addView(
            outlineButton("CREATE ACCOUNT") {

                if (!isLoggingIn) {
                    go(RegisterActivity::class.java)
                }
            }
        )

        // BACK TO HOME
        main.addView(
            outlineButton("BACK TO HOME") {

                if (!isLoggingIn) {
                    go(MainActivity::class.java)
                }
            }
        )

        main.addView(footer())

        screen.addView(main)
        setContentView(screen)
    }

    private fun openDashboard() {
        if (hasNavigated) return
        hasNavigated = true

        val role = AppData.role
        val destination = when (role) {
            "Admin" -> AdminDashboardActivity::class.java
            "Staff" -> StaffDashboardActivity::class.java
            else -> CustomerDashboardActivity::class.java
        }

        val intent = Intent(this, destination)
        startActivity(intent)
        finish()
    }

    private fun showMessage(message: String) {
        if (isFinishing || isDestroyed) return
        android.app.AlertDialog.Builder(this)
            .setTitle("SplashDash")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
