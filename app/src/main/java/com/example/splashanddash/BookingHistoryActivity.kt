package com.example.splashanddash

import android.os.Bundle
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class BookingHistoryActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ApiClient.getToken(this).isNullOrBlank()) {
            go(LoginActivity::class.java)
            finish()
            return
        }

        loadBookings()
    }

    override fun onResume() {
        super.onResume()

        if (!ApiClient.getToken(this).isNullOrBlank()) {
            loadBookings()
        }
    }

    private fun loadBookings() {

        val screen = content()
        val main = column()

        main.addView(
            sectionTitle(
                "My Bookings",
                "View your SplashDash booking history"
            )
        )

        main.addView(
            text("Loading your bookings from PostgreSQL database...", 16f)
        )

        screen.addView(main)
        setContentView(screen)

        lifecycleScope.launch {
            val result = ApiClient.getCustomerBookings(this@BookingHistoryActivity)

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                main.removeAllViews()

                main.addView(
                    sectionTitle(
                        "My Bookings",
                        "Your database booking history"
                    )
                )

                if (result.isSuccess) {
                    val bookings = result.getOrNull() ?: emptyList()

                    if (bookings.isEmpty()) {
                        main.addView(
                            text("You have no bookings yet.", 17f)
                        )
                    } else {
                        main.addView(
                            text("Total Bookings: ${bookings.size}", 18f, true)
                        )

                        bookings.forEach { booking ->
                            val details = """
                                Service: ${booking.service}
                                Vehicle: ${booking.vehicleType}
                                Date/Time: ${booking.bookingDateTime}
                                Location: ${booking.locationType} ${if (!booking.bookingAddress.isNullOrBlank()) "(${booking.bookingAddress})" else ""}
                                Amount: R${booking.totalAmount}
                                Status: ${booking.bookingStatus}
                            """.trimIndent()

                            main.addView(
                                card("Booking #${booking.bookingId}", details)
                            )
                        }
                    }
                } else {
                    main.addView(
                        text("Error: ${result.exceptionOrNull()?.message ?: "Could not load bookings"}", 16f)
                    )
                }

                main.addView(
                    button("BOOK ANOTHER SERVICE") {
                        go(BookingActivity::class.java)
                    }
                )

                main.addView(
                    outlineButton("REFRESH BOOKINGS") {
                        loadBookings()
                    }
                )

                main.addView(
                    outlineButton("BACK TO DASHBOARD") {
                        go(CustomerDashboardActivity::class.java)
                        finish()
                    }
                )

                main.addView(footer())
            }
        }
    }
}
