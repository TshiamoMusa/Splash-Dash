package com.example.splashanddash

import android.app.AlertDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class PaymentActivity : BaseActivity() {

    private lateinit var mainLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val screen = content()
        mainLayout = column()

        mainLayout.addView(sectionTitle("Make Payment", "Submit payment details for your booking"))

        if (ApiClient.getToken(this).isNullOrBlank()) {
            showLoginRequired()
            screen.addView(mainLayout)
            setContentView(screen)
            return
        }

        fetchBookings()

        screen.addView(mainLayout)
        setContentView(screen)
    }

    private fun fetchBookings() {
        lifecycleScope.launch {
            val result = ApiClient.getCustomerBookings(this@PaymentActivity)

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                if (result.isSuccess) {
                    val bookings = result.getOrNull() ?: emptyList()
                    val pendingBookings = bookings.filter { it.bookingStatus.equals("Pending", ignoreCase = true) || it.bookingStatus.equals("Confirmed", ignoreCase = true) }

                    if (pendingBookings.isEmpty()) {
                        mainLayout.removeAllViews()
                        mainLayout.addView(sectionTitle("Make Payment", "Submit payment details"))
                        mainLayout.addView(card("No Pending Bookings", "You have no pending bookings to pay for."))
                        mainLayout.addView(button("BOOK A SERVICE") { go(BookingActivity::class.java) })
                        mainLayout.addView(outlineButton("BACK") { finish() })
                    } else {
                        renderPaymentUI(pendingBookings)
                    }
                } else {
                    showAppMessage("Error loading bookings: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    private fun renderPaymentUI(bookings: List<BookingItem>) {
        mainLayout.removeAllViews()
        mainLayout.addView(sectionTitle("Make Payment", "Submit payment details"))

        mainLayout.addView(text("Select Booking", 20f, true))

        val bookingNames = bookings.map { "Booking #${it.bookingId} — ${it.service} — R${it.totalAmount}" }
        val bookingSpinner = Spinner(this)
        bookingSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, bookingNames)
        mainLayout.addView(bookingSpinner)

        mainLayout.addView(text("Payment Method", 20f, true))
        val paymentMethod = Spinner(this)
        paymentMethod.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("Select Method", "Cash", "Card", "EFT"))
        mainLayout.addView(paymentMethod)

        mainLayout.addView(button("SUBMIT PAYMENT") {
            val pos = bookingSpinner.selectedItemPosition
            val method = paymentMethod.selectedItem.toString()

            if (method == "Select Method") {
                showAppMessage("Please select a payment method.")
                return@button
            }

            val selectedBooking = bookings.getOrNull(pos)
            if (selectedBooking != null) {
                showSuccessDialog(selectedBooking.bookingId.toString())
            }
        })

        mainLayout.addView(outlineButton("BACK TO DASHBOARD") { go(CustomerDashboardActivity::class.java) })
        mainLayout.addView(footer())
    }

    private fun showSuccessDialog(id: String) {
        AlertDialog.Builder(this@PaymentActivity)
            .setTitle("Payment Successful")
            .setMessage("Payment for Booking #$id has been successfully processed!")
            .setPositiveButton("OK") { _, _ ->
                go(CustomerDashboardActivity::class.java)
                finish()
            }.show()
    }

    private fun showLoginRequired() {
        mainLayout.addView(card("Sign In Required", "Please sign in to access your payments."))
        mainLayout.addView(button("LOGIN") { go(LoginActivity::class.java); finish() })
    }
}
