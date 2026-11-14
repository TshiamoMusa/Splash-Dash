package com.example.splashanddash

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
                "View, edit, or cancel your bookings"
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
                        "Your database booking history & management"
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

                            val bookingCard = card("Booking #${booking.bookingId}", details)

                            // Allow editing or cancelling pending bookings
                            if (booking.bookingStatus.equals("Pending", ignoreCase = true)) {

                                // EDIT BUTTON
                                bookingCard.addView(
                                    button("EDIT BOOKING") {
                                        showEditDialog(booking)
                                    }
                                )

                                // CANCEL BUTTON
                                bookingCard.addView(
                                    outlineButton("CANCEL BOOKING") {
                                        AlertDialog.Builder(this@BookingHistoryActivity)
                                            .setTitle("Cancel Booking")
                                            .setMessage("Are you sure you want to cancel Booking #${booking.bookingId}?")
                                            .setPositiveButton("YES, CANCEL") { _, _ ->
                                                lifecycleScope.launch {
                                                    val cancelResult = ApiClient.cancelBooking(
                                                        this@BookingHistoryActivity,
                                                        booking.bookingId
                                                    )
                                                    runOnUiThread {
                                                        if (cancelResult.isSuccess) {
                                                            showAppMessage("Booking #${booking.bookingId} cancelled successfully.")
                                                            loadBookings()
                                                        } else {
                                                            showAppMessage(cancelResult.exceptionOrNull()?.message ?: "Could not cancel booking.")
                                                        }
                                                    }
                                                }
                                            }
                                            .setNegativeButton("NO", null)
                                            .show()
                                    }
                                )
                            }

                            main.addView(bookingCard)
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

    private fun showEditDialog(booking: BookingItem) {
        lifecycleScope.launch {
            val servicesResult = ApiClient.getServices()
            runOnUiThread {
                if (!servicesResult.isSuccess) {
                    showAppMessage("Could not load services for editing.")
                    return@runOnUiThread
                }

                val services = servicesResult.getOrNull() ?: emptyList()
                if (services.isEmpty()) {
                    showAppMessage("No services available.")
                    return@runOnUiThread
                }

                val dialogLayout = LinearLayout(this@BookingHistoryActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(32, 24, 32, 24)
                }

                val vehicleTypes = arrayOf("Small Car", "Large Car", "SUV", "Bakkie")
                val vehicleSpinner = Spinner(this@BookingHistoryActivity)
                vehicleSpinner.adapter = ArrayAdapter(this@BookingHistoryActivity, android.R.layout.simple_spinner_dropdown_item, vehicleTypes)
                dialogLayout.addView(text("Vehicle Type", 14f, true))
                dialogLayout.addView(vehicleSpinner)

                val serviceNames = services.map { "${it.serviceName} — R${it.price}" }
                val serviceSpinner = Spinner(this@BookingHistoryActivity)
                serviceSpinner.adapter = ArrayAdapter(this@BookingHistoryActivity, android.R.layout.simple_spinner_dropdown_item, serviceNames)
                dialogLayout.addView(text("Service Package", 14f, true))
                dialogLayout.addView(serviceSpinner)

                val dateInput = EditText(this@BookingHistoryActivity).apply {
                    hint = "Date (YYYY-MM-DD)"
                    setText(booking.bookingDateTime.take(10))
                }
                dialogLayout.addView(text("Booking Date", 14f, true))
                dialogLayout.addView(dateInput)

                val timeInput = EditText(this@BookingHistoryActivity).apply {
                    hint = "Time (HH:mm)"
                    setText(booking.bookingDateTime.substringAfter("T").take(5))
                }
                dialogLayout.addView(text("Booking Time", 14f, true))
                dialogLayout.addView(timeInput)

                AlertDialog.Builder(this@BookingHistoryActivity)
                    .setTitle("Edit Booking #${booking.bookingId}")
                    .setView(dialogLayout)
                    .setPositiveButton("SAVE CHANGES") { _, _ ->
                        val vehicle = vehicleSpinner.selectedItem.toString()
                        val serviceItem = services[serviceSpinner.selectedItemPosition]
                        val date = dateInput.text.toString().trim()
                        val time = timeInput.text.toString().trim()
                        val dateTimeIso = "${date}T${time}:00Z"

                        lifecycleScope.launch {
                            val updateResult = ApiClient.updateBooking(
                                context = this@BookingHistoryActivity,
                                bookingId = booking.bookingId,
                                serviceId = serviceItem.serviceId,
                                vehicleType = vehicle,
                                bookingDateTime = dateTimeIso,
                                locationType = booking.locationType,
                                bookingAddress = booking.bookingAddress
                            )

                            runOnUiThread {
                                if (updateResult.isSuccess) {
                                    showAppMessage("Booking updated successfully!")
                                    loadBookings()
                                } else {
                                    showAppMessage(updateResult.exceptionOrNull()?.message ?: "Could not update booking.")
                                }
                            }
                        }
                    }
                    .setNegativeButton("CANCEL", null)
                    .show()
            }
        }
    }
}
