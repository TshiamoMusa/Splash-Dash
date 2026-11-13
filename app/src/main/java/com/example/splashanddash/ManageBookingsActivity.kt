package com.example.splashanddash

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ManageBookingsActivity : BaseActivity() {

    private val statuses = arrayOf(
        "Pending",
        "Confirmed",
        "In Progress",
        "Completed",
        "Cancelled"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val screen = content()
        val main = column()

        main.addView(
            sectionTitle(
                "Manage Bookings",
                "Review and update customer bookings"
            )
        )

        main.addView(text("Loading bookings...", 16f))
        screen.addView(main)
        setContentView(screen)

        lifecycleScope.launch {
            val result = ApiClient.getAllBookings(this@ManageBookingsActivity)

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                main.removeAllViews()

                main.addView(
                    sectionTitle(
                        "Manage Bookings",
                        "Review and update customer bookings"
                    )
                )

                if (result.isSuccess) {
                    val bookings = result.getOrNull() ?: emptyList()

                    if (bookings.isEmpty()) {
                        main.addView(
                            card("No Bookings", "There are currently no bookings in the system.")
                        )
                    } else {
                        bookings.forEach { booking ->
                            val details = """
                                Customer: ${booking.customerName ?: "Customer"}
                                Vehicle: ${booking.vehicleType}
                                Service: ${booking.service}
                                Date/Time: ${booking.bookingDateTime}
                                Location: ${booking.locationType}
                                Price: R${booking.totalAmount}
                                Status: ${booking.bookingStatus}
                            """.trimIndent()

                            val bookingCard = card("Booking #${booking.bookingId}", details)

                            val statusSpinner = Spinner(this@ManageBookingsActivity)
                            statusSpinner.adapter = ArrayAdapter(
                                this@ManageBookingsActivity,
                                android.R.layout.simple_spinner_dropdown_item,
                                statuses
                            )

                            val currentPosition = statuses.indexOf(booking.bookingStatus)
                            if (currentPosition >= 0) {
                                statusSpinner.setSelection(currentPosition)
                            }

                            bookingCard.addView(text("Booking Status", 14f, true))
                            bookingCard.addView(statusSpinner)

                            bookingCard.addView(
                                button("UPDATE STATUS") {
                                    val newStatus = statusSpinner.selectedItem.toString()
                                    lifecycleScope.launch {
                                        val updateResult = ApiClient.updateBookingStatus(
                                            this@ManageBookingsActivity,
                                            booking.bookingId,
                                            newStatus
                                        )
                                        runOnUiThread {
                                            if (updateResult.isSuccess) {
                                                Toast.makeText(
                                                    this@ManageBookingsActivity,
                                                    "Booking #${booking.bookingId} updated to $newStatus.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                recreate()
                                            } else {
                                                Toast.makeText(
                                                    this@ManageBookingsActivity,
                                                    updateResult.exceptionOrNull()?.message ?: "Update failed.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            )

                            main.addView(bookingCard)
                        }
                    }
                } else {
                    main.addView(text("Error: ${result.exceptionOrNull()?.message ?: "Could not load bookings"}", 16f))
                }

                main.addView(
                    outlineButton("BACK TO ADMIN DASHBOARD") {
                        go(AdminDashboardActivity::class.java)
                        finish()
                    }
                )

                main.addView(footer())
            }
        }
    }
}
