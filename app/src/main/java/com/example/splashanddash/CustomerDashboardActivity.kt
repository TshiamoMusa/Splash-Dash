package com.example.splashanddash

import android.os.Bundle
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class CustomerDashboardActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d(
            "SplashDashDashboard",
            "Customer Dashboard onCreate called"
        )

        if (ApiClient.getToken(this).isNullOrBlank()) {
            go(LoginActivity::class.java)
            finish()
            return
        }

        showDashboard(
            total = null,
            pending = null,
            completed = null
        )
    }

    override fun onResume() {
        super.onResume()

        if (!ApiClient.getToken(this).isNullOrBlank()) {
            loadDashboardStatistics()
        }
    }

    private fun loadDashboardStatistics() {
        lifecycleScope.launch {
            val result = ApiClient.getCustomerBookings(this@CustomerDashboardActivity)

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                if (result.isSuccess) {
                    val bookings = result.getOrNull() ?: emptyList()
                    val totalBookings = bookings.size
                    val pendingBookings = bookings.count { it.bookingStatus.equals("Pending", ignoreCase = true) || it.bookingStatus.equals("Confirmed", ignoreCase = true) }
                    val completedBookings = bookings.count { it.bookingStatus.equals("Completed", ignoreCase = true) }

                    showDashboard(
                        total = totalBookings,
                        pending = pendingBookings,
                        completed = completedBookings
                    )
                } else {
                    showDashboard(
                        total = null,
                        pending = null,
                        completed = null,
                        error = result.exceptionOrNull()?.message ?: "Could not load stats"
                    )
                }
            }
        }
    }

    private fun showDashboard(
        total: Int?,
        pending: Int?,
        completed: Int?,
        error: String? = null
    ) {

        val screen = content()
        val main = column()

        val customerName = AppData.currentUser
            .takeIf { it.isNotBlank() && it != "Guest" }
            ?: "Valued Customer"

        // STYLISH HEADER BANNER
        main.addView(
            sectionTitle(
                "🚗 SplashDash Member Portal",
                "Welcome back, $customerName!\nProfessional car wash & detailing services."
            )
        )

        // LIVE STATS SECTION
        main.addView(
            text(
                "📊 Live Account Metrics",
                20f,
                true
            )
        )

        val totalText = total?.toString() ?: "..."
        val pendingText = pending?.toString() ?: "..."
        val completedText = completed?.toString() ?: "..."

        main.addView(
            card(
                "📁 Total Bookings",
                "You have made $totalText total bookings with us."
            )
        )

        main.addView(
            card(
                "⏳ Active / Pending Bookings",
                "$pendingText bookings currently awaiting service or confirmation."
            )
        )

        main.addView(
            card(
                "✨ Completed Washes",
                "$completedText vehicles successfully washed and detailed."
            )
        )

        if (error != null) {
            main.addView(
                text(
                    "⚠️ Database Sync Notice: $error",
                    14f
                )
            )

            main.addView(
                outlineButton("🔄 RETRY SYNC") {
                    loadDashboardStatistics()
                }
            )
        }

        // STYLISH QUICK ACTIONS
        main.addView(
            sectionTitle(
                "⚡ Quick Actions",
                "Schedule, manage, and pay for services in seconds"
            )
        )

        main.addView(
            card(
                "✨ Book a New Service",
                "Choose from our premium Exterior, Interior, or Full Detail car wash packages."
            )
        )

        main.addView(
            button("📅 BOOK A CAR WASH NOW") {
                go(BookingActivity::class.java)
            }
        )

        main.addView(
            card(
                "📝 Manage Bookings (Edit & Cancel)",
                "View your history, update vehicle/date details on pending bookings, or cancel anytime."
            )
        )

        main.addView(
            button("🔍 VIEW & EDIT MY BOOKINGS") {
                go(BookingHistoryActivity::class.java)
            }
        )

        main.addView(
            card(
                "💳 Secure Checkout",
                "Complete encrypted payments via PayFast, EFT, or Cash for pending bookings."
            )
        )

        main.addView(
            button("🔒 PROCEED TO SECURE CHECKOUT") {
                go(PaymentActivity::class.java)
            }
        )

        main.addView(
            card(
                "⚙️ Account & Profile Settings",
                "Update your home address, phone number, and account preferences."
            )
        )

        main.addView(
            outlineButton("SETTINGS & PROFILE") {
                go(SettingsActivity::class.java)
            }
        )

        main.addView(
            outlineButton("🔄 REFRESH DASHBOARD") {
                loadDashboardStatistics()
            }
        )

        main.addView(
            outlineButton("🚪 LOG OUT") {
                ApiClient.clearSession(this@CustomerDashboardActivity)

                go(LoginActivity::class.java)
                finish()
            }
        )

        main.addView(footer())

        screen.addView(main)
        setContentView(screen)
    }
}
