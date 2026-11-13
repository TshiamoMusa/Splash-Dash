package com.example.splashanddash

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class BookingActivity : BaseActivity() {

    private var servicesList: List<ServiceItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure user is logged in
        if (ApiClient.getToken(this).isNullOrBlank()) {
            go(LoginActivity::class.java)
            finish()
            return
        }

        val screen = content()
        val main = column()

        main.addView(
            sectionTitle(
                "Book a Car Wash",
                "Schedule your SplashDash service"
            )
        )

        // Vehicle Selection
        main.addView(text("Vehicle Type", 17f, true))
        val vehicles = listOf("Small Car", "Large Car", "SUV", "Bakkie")
        val vehicleSpinner = Spinner(this)
        vehicleSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, vehicles)
        main.addView(vehicleSpinner)

        val registration = input("Vehicle Registration (e.g. AB12CD GP)")
        main.addView(registration)

        // Location Type Selection
        main.addView(text("Service Location Type", 17f, true))
        val locations = listOf("On-site", "House Call")
        val locationSpinner = Spinner(this)
        locationSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, locations)
        main.addView(locationSpinner)

        val addressField = input("House Call Address (Optional)")
        main.addView(addressField)

        // Service Selection Spinner Placeholder
        main.addView(text("Select Service", 17f, true))
        val serviceSpinner = Spinner(this)
        main.addView(serviceSpinner)

        val serviceInfoText = text("Loading services...", 14f)
        main.addView(serviceInfoText)

        // Fetch services from API
        lifecycleScope.launch {
            val result = ApiClient.getServices()
            runOnUiThread {
                if (result.isSuccess) {
                    servicesList = result.getOrNull() ?: emptyList()
                    if (servicesList.isNotEmpty()) {
                        val names = servicesList.map { "${it.serviceName} - R${it.price}" }
                        serviceSpinner.adapter = ArrayAdapter(
                            this@BookingActivity,
                            android.R.layout.simple_spinner_dropdown_item,
                            names
                        )
                        serviceInfoText.text = "Select one of our professional services above."
                    } else {
                        serviceInfoText.text = "No services available."
                    }
                } else {
                    serviceInfoText.text = "Could not load services from server."
                }
            }
        }

        // Date Picker
        val dateField = input("Booking Date (YYYY-MM-DD)")
        dateField.isFocusable = false
        dateField.isClickable = true
        val calendar = Calendar.getInstance()

        dateField.setOnClickListener {
            val today = Calendar.getInstance()
            val picker = DatePickerDialog(
                this,
                { _, year, month, day ->
                    calendar.set(year, month, day, 9, 0, 0)
                    dateField.setText(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time))
                },
                today.get(Calendar.YEAR),
                today.get(Calendar.MONTH),
                today.get(Calendar.DAY_OF_MONTH)
            )
            picker.datePicker.minDate = today.timeInMillis
            picker.show()
        }
        main.addView(dateField)

        // Time Input
        val timeField = input("Time (HH:mm, e.g. 10:30)")
        main.addView(timeField)

        // Submit Button
        val submitButton = button("CONFIRM BOOKING") {
            val vehicleType = vehicleSpinner.selectedItem?.toString() ?: "Small Car"
            val reg = registration.text.toString().trim()
            val locationType = locationSpinner.selectedItem?.toString() ?: "On-site"
            val address = addressField.text.toString().trim()
            val date = dateField.text.toString().trim()
            val time = timeField.text.toString().trim()
            val selectedServiceIndex = serviceSpinner.selectedItemPosition

            val timePattern = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

            when {
                servicesList.isEmpty() -> showMessage("Services are still loading. Please wait.")
                reg.isBlank() -> showMessage("Enter your vehicle registration.")
                date.isBlank() -> showMessage("Please select a booking date.")
                !timePattern.matches(time) -> showMessage("Enter a valid time in HH:mm format (e.g. 10:30).")
                else -> {
                    val serviceItem = servicesList.getOrNull(selectedServiceIndex) ?: servicesList.first()
                    val dateTimeIso = "${date}T${time}:00Z"

                    lifecycleScope.launch {
                        val bookingResult = ApiClient.createBooking(
                            context = this@BookingActivity,
                            serviceId = serviceItem.serviceId,
                            vehicleType = vehicleType,
                            bookingDateTime = dateTimeIso,
                            locationType = locationType,
                            bookingAddress = if (address.isBlank()) null else address
                        )

                        runOnUiThread {
                            if (bookingResult.isSuccess) {
                                android.app.AlertDialog.Builder(this@BookingActivity)
                                    .setTitle("Booking Confirmed")
                                    .setMessage("Your booking has been saved to the shared PostgreSQL database successfully!")
                                    .setPositiveButton("VIEW MY BOOKINGS") { _, _ ->
                                        go(BookingHistoryActivity::class.java)
                                        finish()
                                    }
                                    .show()
                            } else {
                                showMessage(bookingResult.exceptionOrNull()?.message ?: "Could not create booking.")
                            }
                        }
                    }
                }
            }
        }

        main.addView(submitButton)

        main.addView(outlineButton("VIEW MY BOOKINGS") {
            go(BookingHistoryActivity::class.java)
        })

        main.addView(outlineButton("BACK TO DASHBOARD") {
            go(CustomerDashboardActivity::class.java)
            finish()
        })

        main.addView(footer())
        screen.addView(main)
        setContentView(screen)
    }

    private fun showMessage(message: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("SplashDash")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
