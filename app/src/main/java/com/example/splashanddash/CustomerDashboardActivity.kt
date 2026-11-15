package com.example.splashanddash

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Home screen for a logged-in customer: live booking stats and quick actions. */
class CustomerDashboardActivity : BaseActivity() {

    private lateinit var totalTile: LinearLayout
    private lateinit var activeTile: LinearLayout
    private lateinit var completedTile: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val screen = content()
        val main = column()

        main.addView(navBar())

        val firstName = AppData.currentUser
            .takeIf { it.isNotBlank() && it != "Guest" }
            ?.substringBefore(" ")
            ?: "there"

        main.addView(pageHeader("Hi, $firstName", "What would you like to do today?"))

        // Stats (filled in once the bookings load)
        totalTile = metricCardValueLabel("…", "Total")
        activeTile = metricCardValueLabel("…", "Active")
        completedTile = metricCardValueLabel("…", "Completed")
        main.addView(metricRow(totalTile, activeTile, completedTile))

        errorText = text("", 14f, false, danger).apply { visibility = View.GONE }
        retryButton = outlineButton("Retry") { loadStats() }.apply { visibility = View.GONE }
        main.addView(errorText)
        main.addView(retryButton)

        // Quick actions
        main.addView(sectionTitle("Quick actions"))

        main.addView(
            card(
                "Book a car wash",
                "Pick your vehicle, service, date and time.",
                "Book now"
            ) { go(BookingActivity::class.java) }
        )

        main.addView(
            card(
                "My bookings",
                "View, edit or cancel your bookings.",
                "View"
            ) { go(BookingHistoryActivity::class.java) }
        )

        main.addView(
            card(
                "Checkout",
                "Pay for a pending booking online, by EFT or with cash.",
                "Pay"
            ) { go(PaymentActivity::class.java) }
        )

        addSpacing(main, 4)

        main.addView(
            outlineButton("Log out") {
                ApiClient.clearSession(this@CustomerDashboardActivity)
                go(LoginActivity::class.java)
                finish()
            }
        )

        main.addView(footer())

        screen.addView(main)
        setContentView(screen)
    }

    override fun onResume() {
        super.onResume()
        if (!ApiClient.getToken(this).isNullOrBlank()) {
            loadStats()
        }
    }

    private fun setTile(tile: LinearLayout, value: String) {
        (tile.getChildAt(0) as TextView).text = value
    }

    private fun loadStats() {
        errorText.visibility = View.GONE
        retryButton.visibility = View.GONE

        lifecycleScope.launch {
            val result = ApiClient.getCustomerBookings(this@CustomerDashboardActivity)
            if (isFinishing || isDestroyed) return@launch

            result.onSuccess { bookings ->
                setTile(totalTile, bookings.size.toString())
                setTile(
                    activeTile,
                    bookings.count {
                        it.bookingStatus.equals("Pending", ignoreCase = true) ||
                                it.bookingStatus.equals("Confirmed", ignoreCase = true)
                    }.toString()
                )
                setTile(
                    completedTile,
                    bookings.count { it.bookingStatus.equals("Completed", ignoreCase = true) }.toString()
                )
            }.onFailure { error ->
                errorText.text = error.message ?: "Could not load your stats."
                errorText.visibility = View.VISIBLE
                retryButton.visibility = View.VISIBLE
            }
        }
    }
}