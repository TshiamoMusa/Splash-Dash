package com.example.splashanddash

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch


class ServiceQueueActivity : BaseActivity() {

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
                "Service Queue",
                "Manage today's vehicle cleaning services"
            )
        )

        main.addView(text("Loading service queue...", 16f))
        screen.addView(main)
        setContentView(screen)

        lifecycleScope.launch {
            val result = ApiClient.getAllBookings(this@ServiceQueueActivity)

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                main.removeAllViews()

                main.addView(
                    sectionTitle(
                        "Service Queue",
                        "Manage today's vehicle cleaning services"
                    )
                )

                if (result.isSuccess) {
                    val bookings = result.getOrNull() ?: emptyList()

                    if (bookings.isEmpty()) {
                        main.addView(
                            card("Queue Empty", "There are currently no bookings waiting for service.")
                        )
                    } else {
                        bookings.forEach { booking ->
                            val details = """
                                Customer: ${booking.customerName ?: "Customer"} (${booking.customerPhone ?: "No phone"})
                                Vehicle: ${booking.vehicleType}
                                Service: ${booking.service}
                                Date/Time: ${booking.bookingDateTime}
                                Location: ${booking.locationType}
                                Current Status: ${booking.bookingStatus}
                            """.trimIndent()

                            val bookingCard = card("Booking #${booking.bookingId}", details)

                            bookingCard.addView(text("Update Service Status", 14f, true))

                            val statusSpinner = Spinner(this@ServiceQueueActivity)
                            statusSpinner.adapter = ArrayAdapter(
                                this@ServiceQueueActivity,
                                android.R.layout.simple_spinner_dropdown_item,
                                statuses
                            )

                            val position = statuses.indexOf(booking.bookingStatus)
                            if (position >= 0) {
                                statusSpinner.setSelection(position)
                            }

                            bookingCard.addView(statusSpinner)

                            bookingCard.addView(
                                button("UPDATE STATUS") {
                                    val newStatus = statusSpinner.selectedItem.toString()
                                    lifecycleScope.launch {
                                        val updateResult = ApiClient.updateBookingStatus(
                                            this@ServiceQueueActivity,
                                            booking.bookingId,
                                            newStatus
                                        )
                                        runOnUiThread {
                                            if (updateResult.isSuccess) {
                                                showAppMessage("Booking #${booking.bookingId} status updated to $newStatus.")
                                                recreate()
                                            } else {
                                                showAppMessage(updateResult.exceptionOrNull()?.message ?: "Update failed.")
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
                    outlineButton("BACK TO STAFF DASHBOARD") {
                        go(StaffDashboardActivity::class.java)
                        finish()
                    }
                )

                main.addView(footer())
            }
        }
    }
}
