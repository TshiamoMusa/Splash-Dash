package com.example.splashanddash

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Book a car wash. The same screen is used to edit a pending booking
 * (open it with BookingActivity.editIntent(...)).
 */
class BookingActivity : BaseActivity() {

    companion object {
        // Keys used to pass an existing booking in when editing
        private const val EXTRA_BOOKING_ID = "booking_id"
        private const val EXTRA_SERVICE = "booking_service"
        private const val EXTRA_VEHICLE = "booking_vehicle"
        private const val EXTRA_DATETIME = "booking_datetime"
        private const val EXTRA_LOCATION = "booking_location"
        private const val EXTRA_ADDRESS = "booking_address"

        // Working hours (minutes since midnight)
        private const val OPEN_MINUTES = 8 * 60
        private const val LAST_START_ON_SITE = 18 * 60
        private const val LAST_START_HOUSE_CALL = 17 * 60

        /** Intent that opens this screen pre-filled to edit an existing booking. */
        fun editIntent(context: Context, booking: BookingItem): Intent {
            return Intent(context, BookingActivity::class.java).apply {
                putExtra(EXTRA_BOOKING_ID, booking.bookingId)
                putExtra(EXTRA_SERVICE, booking.service)
                putExtra(EXTRA_VEHICLE, booking.vehicleType)
                putExtra(EXTRA_DATETIME, booking.bookingDateTime)
                putExtra(EXTRA_LOCATION, booking.locationType)
                putExtra(EXTRA_ADDRESS, booking.bookingAddress ?: "")
            }
        }
    }

    private var services: List<ServiceItem> = emptyList()
    private var serviceGroup: ChoiceGroup? = null
    private var isSubmitting = false

    // Date and time chosen by the customer
    private val chosenDate: Calendar = Calendar.getInstance()
    private var dateChosen = false
    private var chosenHour = -1
    private var chosenMinute = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // editId is -1 when creating a new booking
        val editId = intent.getIntExtra(EXTRA_BOOKING_ID, -1)
        val isEdit = editId != -1

        val prefillService = intent.getStringExtra(EXTRA_SERVICE) ?: ""
        val prefillVehicle = intent.getStringExtra(EXTRA_VEHICLE) ?: ""
        val prefillLocation = intent.getStringExtra(EXTRA_LOCATION) ?: ""
        val prefillAddress = intent.getStringExtra(EXTRA_ADDRESS) ?: ""

        val locationValues = listOf(ApiClient.LOCATION_ON_SITE, ApiClient.LOCATION_HOUSE_CALL)
        val locationLabels = listOf("On-site", "House call")
        val dateFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())

        val screen = content()
        val main = column()

        // (The old in-page back link was removed: the header has a back arrow now.)
        main.addView(
            pageHeader(
                if (isEdit) "Edit booking" else "Book a car wash",
                "Choose your vehicle, service and time"
            )
        )

        // VEHICLE
        main.addView(sectionTitle("Vehicle"))
        val vehicleGroup = choiceGroup(
            ApiClient.VEHICLE_TYPES,
            2,
            ApiClient.VEHICLE_TYPES.indexOf(prefillVehicle).coerceAtLeast(0)
        )
        main.addView(vehicleGroup.view)

        // LOCATION (address field only appears for house calls)
        main.addView(sectionTitle("Location"))
        val initialLocation = locationValues.indexOf(prefillLocation).coerceAtLeast(0)
        val addressField = input("House-call address (blank = your home address)")
        val hoursHint = text(hoursMessage(initialLocation == 1), 13f, false, muted)

        addressField.setText(prefillAddress)
        addressField.visibility = if (initialLocation == 1) View.VISIBLE else View.GONE

        val locationGroup = choiceGroup(locationLabels, 2, initialLocation) { index ->
            addressField.visibility = if (index == 1) View.VISIBLE else View.GONE
            hoursHint.text = hoursMessage(index == 1)
        }
        main.addView(locationGroup.view)
        main.addView(addressField)

        // SERVICE (loaded from the API)
        main.addView(sectionTitle("Service"))
        val serviceStatus = text("Loading services…", 14f, false, muted)
        val serviceHolder = column(0)
        main.addView(serviceStatus)
        main.addView(serviceHolder)

        lifecycleScope.launch {
            val result = ApiClient.getServices()
            if (isFinishing || isDestroyed) return@launch

            if (result.isSuccess) {
                services = result.getOrNull() ?: emptyList()

                if (services.isEmpty()) {
                    serviceStatus.text = "No services available right now."
                } else {
                    serviceStatus.visibility = View.GONE
                    val labels = services.map { "${it.serviceName}  ·  ${money(it.price)}" }
                    val initial = services.indexOfFirst { it.serviceName == prefillService }
                        .coerceAtLeast(0)
                    val group = choiceGroup(labels, 1, initial)
                    serviceGroup = group
                    serviceHolder.addView(group.view)
                }
            } else {
                serviceStatus.text = result.exceptionOrNull()?.message ?: "Could not load services."
            }
        }

        // DATE & TIME (read-only fields that open pickers)
        main.addView(sectionTitle("Date & time"))
        val dateField = input("Choose a date")
        val timeField = input("Choose a time")
        listOf(dateField, timeField).forEach {
            it.isFocusable = false
            it.isClickable = true
            it.isCursorVisible = false
        }

        // Pre-fill date and time when editing
        ApiClient.parseApiDate(intent.getStringExtra(EXTRA_DATETIME) ?: "")?.let { date ->
            val cal = Calendar.getInstance().apply { time = date }
            chosenDate.timeInMillis = cal.timeInMillis
            dateChosen = true
            chosenHour = cal.get(Calendar.HOUR_OF_DAY)
            chosenMinute = cal.get(Calendar.MINUTE)
            dateField.setText(dateFormat.format(date))
            timeField.setText(String.format(Locale.US, "%02d:%02d", chosenHour, chosenMinute))
        }

        dateField.setOnClickListener {
            val picker = DatePickerDialog(
                this,
                { _, year, month, day ->
                    chosenDate.set(year, month, day)
                    dateChosen = true
                    dateField.setText(dateFormat.format(chosenDate.time))
                },
                chosenDate.get(Calendar.YEAR),
                chosenDate.get(Calendar.MONTH),
                chosenDate.get(Calendar.DAY_OF_MONTH)
            )
            // No dates in the past
            picker.datePicker.minDate = System.currentTimeMillis() - 1000
            picker.show()
        }

        timeField.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    chosenHour = hour
                    chosenMinute = minute
                    timeField.setText(String.format(Locale.US, "%02d:%02d", hour, minute))
                },
                if (chosenHour >= 0) chosenHour else 9,
                chosenMinute,
                true
            ).show()
        }

        main.addView(dateField)
        main.addView(timeField)
        main.addView(hoursHint)
        addSpacing(main, 12)

        // SUBMIT
        main.addView(
            button(if (isEdit) "Save changes" else "Confirm booking") {

                if (isSubmitting) return@button

                val isHouseCall = locationGroup.index == 1
                val locationType = locationValues[locationGroup.index]
                val minutes = chosenHour * 60 + chosenMinute
                val lastStart = if (isHouseCall) LAST_START_HOUSE_CALL else LAST_START_ON_SITE
                val group = serviceGroup
                val service = group?.let { services.getOrNull(it.index) }

                // The date + time the customer picked, as one Calendar
                val scheduled = Calendar.getInstance().apply {
                    set(
                        chosenDate.get(Calendar.YEAR),
                        chosenDate.get(Calendar.MONTH),
                        chosenDate.get(Calendar.DAY_OF_MONTH),
                        chosenHour.coerceAtLeast(0),
                        chosenMinute,
                        0
                    )
                    set(Calendar.MILLISECOND, 0)
                }

                // Validate in order; the first problem found is shown.
                when {
                    service == null ->
                        showAppMessage("Services are still loading. Please wait a moment.")

                    !dateChosen -> showAppMessage("Please choose a date.")

                    chosenHour < 0 -> showAppMessage("Please choose a time.")

                    minutes < OPEN_MINUTES || minutes > lastStart ->
                        showAppMessage(hoursMessage(isHouseCall))

                    scheduled.timeInMillis <= System.currentTimeMillis() ->
                        showAppMessage("Please choose a time in the future.")

                    else -> {
                        // Only house calls send an address (blank = use the customer's home address)
                        val address = if (isHouseCall) {
                            addressField.text.toString().trim().ifBlank { null }
                        } else {
                            null
                        }

                        submitBooking(
                            editId = editId,
                            service = service,
                            vehicle = ApiClient.VEHICLE_TYPES[vehicleGroup.index],
                            scheduled = scheduled,
                            locationType = locationType,
                            address = address
                        )
                    }
                }
            }
        )

        main.addView(footer())
        screen.addView(main)

        // Fixed header (back arrow) + scrolling content
        showScreen(screen)
    }

    /** Opening-hours text shown under the date and time fields. */
    private fun hoursMessage(isHouseCall: Boolean): String {
        return if (isHouseCall) {
            "House calls are available from 08:00 to 17:00."
        } else {
            "On-site bookings are available from 08:00 to 18:00."
        }
    }

    /** Sends the new booking (or the edit) to the API and shows the result. */
    private fun submitBooking(
        editId: Int,
        service: ServiceItem,
        vehicle: String,
        scheduled: Calendar,
        locationType: String,
        address: String?
    ) {
        isSubmitting = true

        // Includes the time zone so the booking keeps the time the customer picked.
        val dateTime = ApiClient.formatForApi(scheduled.time)

        lifecycleScope.launch {
            val result = if (editId != -1) {
                ApiClient.updateBooking(
                    this@BookingActivity, editId, service.serviceId,
                    vehicle, dateTime, locationType, address
                )
            } else {
                ApiClient.createBooking(
                    this@BookingActivity, service.serviceId,
                    vehicle, dateTime, locationType, address
                )
            }

            isSubmitting = false
            if (isFinishing || isDestroyed) return@launch

            if (result.isFailure) {
                showAppMessage(result.exceptionOrNull()?.message ?: "Could not save your booking.")
                return@launch
            }

            if (editId != -1) {
                // Edit finished: just close the screen after OK.
                AlertDialog.Builder(this@BookingActivity)
                    .setTitle("Booking updated")
                    .setMessage("Your changes were saved.")
                    .setCancelable(false)
                    .setPositiveButton("OK") { _, _ -> finish() }
                    .show()
            } else {
                // New booking: offer to pay now or go to the bookings list.
                AlertDialog.Builder(this@BookingActivity)
                    .setTitle("Booking created")
                    .setMessage("Your booking is pending until you pay for it.")
                    .setCancelable(false)
                    .setPositiveButton("Pay now") { _, _ ->
                        go(PaymentActivity::class.java)
                        finish()
                    }
                    .setNegativeButton("My bookings") { _, _ ->
                        go(BookingHistoryActivity::class.java)
                        finish()
                    }
                    .show()
            }
        }
    }
}