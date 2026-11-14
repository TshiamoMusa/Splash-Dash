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

        mainLayout.addView(sectionTitle("Secure Checkout", "Encrypted payment gateway for your booking"))

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
                    val pendingBookings = bookings.filter { it.bookingStatus.equals("Pending", ignoreCase = true) }

                    if (pendingBookings.isEmpty()) {
                        mainLayout.removeAllViews()
                        mainLayout.addView(sectionTitle("Secure Checkout", "Payment Gateway"))
                        mainLayout.addView(card("No Pending Bookings", "You have no pending bookings requiring payment."))
                        mainLayout.addView(button("BOOK A SERVICE") { go(BookingActivity::class.java) })
                        mainLayout.addView(outlineButton("BACK TO DASHBOARD") { go(CustomerDashboardActivity::class.java); finish() })
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
        mainLayout.addView(sectionTitle("Secure Checkout", "Complete your car wash payment"))

        mainLayout.addView(text("Select Booking", 18f, true))
        val bookingNames = bookings.map { "Booking #${it.bookingId} — ${it.service} — R${it.totalAmount}" }
        val bookingSpinner = Spinner(this)
        bookingSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, bookingNames)
        mainLayout.addView(bookingSpinner)

        mainLayout.addView(text("Payment Method", 18f, true))
        val paymentMethodSpinner = Spinner(this)
        // Must match PostgreSQL chk_payment_method constraint: 'Cash', 'EFT', 'PayFast'
        paymentMethodSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            arrayOf("PayFast", "EFT", "Cash")
        )
        mainLayout.addView(paymentMethodSpinner)

        mainLayout.addView(text("Cardholder Name / Account Name", 16f, true))
        val cardNameInput = input("John Doe")
        mainLayout.addView(cardNameInput)

        mainLayout.addView(text("Card Number / Reference", 16f, true))
        val cardNumberInput = input("4532 8920 1192 4810")
        cardNumberInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        mainLayout.addView(cardNumberInput)

        mainLayout.addView(text("Expiration Date (MM/YY)", 16f, true))
        val expiryInput = input("08/28")
        mainLayout.addView(expiryInput)

        mainLayout.addView(text("CVV / Security Code", 16f, true))
        val cvvInput = input("123")
        cvvInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        mainLayout.addView(cvvInput)

        mainLayout.addView(button("PAY NOW (SECURE)") {
            val pos = bookingSpinner.selectedItemPosition
            val method = paymentMethodSpinner.selectedItem.toString()
            val cardName = cardNameInput.text.toString().trim()
            val cardNumber = cardNumberInput.text.toString().trim()

            when {
                cardName.isBlank() -> showAppMessage("Please enter the name.")
                cardNumber.length < 4 -> showAppMessage("Please enter valid payment reference or card number.")
                else -> {
                    val selectedBooking = bookings.getOrNull(pos)
                    if (selectedBooking != null) {
                        lifecycleScope.launch {
                            val payResult = ApiClient.makePayment(
                                context = this@PaymentActivity,
                                bookingId = selectedBooking.bookingId,
                                paymentMethod = method
                            )

                            runOnUiThread {
                                if (payResult.isSuccess) {
                                    val resObj = payResult.getOrNull()
                                    val refNum = resObj?.optString("referenceNumber", "SD-PAY-9821") ?: "SD-PAY-9821"
                                    val txId = resObj?.optString("transactionId", "TXN-884102") ?: "TXN-884102"

                                    AlertDialog.Builder(this@PaymentActivity)
                                        .setTitle("Payment Successful 💳")
                                        .setMessage("Your payment of R${selectedBooking.totalAmount} was processed securely via $method.\n\nReference: $refNum\nTransaction ID: $txId\nStatus: Confirmed")
                                        .setPositiveButton("VIEW MY BOOKINGS") { _, _ ->
                                            go(BookingHistoryActivity::class.java)
                                            finish()
                                        }.show()
                                } else {
                                    showAppMessage(payResult.exceptionOrNull()?.message ?: "Payment processing failed.")
                                }
                            }
                        }
                    }
                }
            }
        })

        mainLayout.addView(outlineButton("BACK TO DASHBOARD") { go(CustomerDashboardActivity::class.java); finish() })
        mainLayout.addView(footer())
    }

    private fun showLoginRequired() {
        mainLayout.addView(card("Sign In Required", "Please sign in to access secure checkout."))
        mainLayout.addView(button("LOGIN") { go(LoginActivity::class.java); finish() })
    }
}
