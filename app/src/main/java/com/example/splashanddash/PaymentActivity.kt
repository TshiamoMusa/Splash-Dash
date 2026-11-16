package com.example.splashanddash

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Checkout: pay for a pending booking online, by EFT or with cash. */
class PaymentActivity : BaseActivity() {

    // Holds the loading text / payment form; cleared and refilled on every load
    private lateinit var body: LinearLayout
    private var isPaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val screen = content()
        val main = column()

        // (The old in-page back link was removed: the header has a back arrow now.)
        main.addView(pageHeader("Checkout", "Pay for a pending booking"))

        body = column(0)
        main.addView(body)

        main.addView(footer())

        screen.addView(main)

        // Fixed header (back arrow) + scrolling content
        showScreen(screen)
    }

    /** Reload every time the screen comes back so the pending list is current. */
    override fun onResume() {
        super.onResume()
        if (!ApiClient.getToken(this).isNullOrBlank()) {
            fetchBookings()
        }
    }

    /** Loads the customer's bookings and keeps only the ones waiting for payment. */
    private fun fetchBookings() {
        body.removeAllViews()
        body.addView(text("Loading your bookings…", 15f, false, muted))

        lifecycleScope.launch {
            val result = ApiClient.getCustomerBookings(this@PaymentActivity)
            if (isFinishing || isDestroyed) return@launch

            body.removeAllViews()

            // Error state with a retry button
            if (result.isFailure) {
                body.addView(
                    text(result.exceptionOrNull()?.message ?: "Could not load your bookings.", 15f, false, danger)
                )
                body.addView(outlineButton("Retry") { fetchBookings() })
                return@launch
            }

            val pending = (result.getOrNull() ?: emptyList())
                .filter { it.bookingStatus.equals("Pending", ignoreCase = true) }

            if (pending.isEmpty()) {
                // Nothing to pay
                body.addView(
                    card(
                        "Nothing to pay",
                        "You have no pending bookings. Book a wash and it will appear here.",
                        "Book now"
                    ) { go(BookingActivity::class.java) }
                )
            } else {
                renderPaymentForm(pending)
            }
        }
    }

    /** Builds the booking picker, payment method chips, card fields and Pay button. */
    private fun renderPaymentForm(bookings: List<BookingItem>) {

        // BOOKING
        body.addView(sectionTitle("Booking"))
        val bookingLabels = bookings.map {
            "${it.service}  ·  ${ApiClient.displayDate(it.bookingDateTime)}  ·  ${money(it.totalAmount)}"
        }
        val bookingGroup = choiceGroup(bookingLabels, 1)
        body.addView(bookingGroup.view)

        // PAYMENT METHOD (values must match the API: PayFast, EFT, Cash)
        body.addView(sectionTitle("Payment method"))
        val methodLabels = listOf("Pay online", "EFT", "Cash")
        val methodValues = listOf("PayFast", "EFT", "Cash")

        val cardFields = column(0)

        // Card fields are only shown for "Pay online" (index 0)
        val methodGroup = choiceGroup(methodLabels, 3) { index ->
            cardFields.visibility = if (index == 0) View.VISIBLE else View.GONE
        }
        body.addView(methodGroup.view)

        // Card details are only needed for online payment.
        val cardName = input("Name on card")
        val cardNumber = input("Card number")
        cardNumber.inputType = InputType.TYPE_CLASS_NUMBER
        val cardExpiry = input("Expiry (MM/YY)")
        val cardCvv = input("CVV")
        cardCvv.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD

        cardFields.addView(cardName)
        cardFields.addView(cardNumber)
        cardFields.addView(cardExpiry)
        cardFields.addView(cardCvv)
        body.addView(cardFields)

        addSpacing(body, 8)

        body.addView(
            button("Pay now") {
                if (isPaying) return@button

                val selected = bookings.getOrNull(bookingGroup.index) ?: return@button
                val method = methodValues[methodGroup.index]

                // Validate the card details only for online payment
                if (methodGroup.index == 0) {
                    val digits = cardNumber.text.toString().filter { it.isDigit() }
                    val expiryOk = Regex("^(0[1-9]|1[0-2])/\\d{2}$")
                        .matches(cardExpiry.text.toString().trim())
                    val cvv = cardCvv.text.toString().trim()

                    val problem = when {
                        cardName.text.toString().isBlank() -> "Please enter the name on the card."
                        digits.length !in 13..19 -> "Please enter a valid card number."
                        !expiryOk -> "Enter the expiry date as MM/YY."
                        cvv.length !in 3..4 -> "Please enter the CVV."
                        else -> null
                    }

                    if (problem != null) {
                        showAppMessage(problem)
                        return@button
                    }
                }

                pay(selected, method)
            }
        )
    }

    /** Sends the payment to the API and shows the receipt details. */
    private fun pay(booking: BookingItem, method: String) {
        isPaying = true

        lifecycleScope.launch {
            val result = ApiClient.makePayment(this@PaymentActivity, booking.bookingId, method)

            isPaying = false
            if (isFinishing || isDestroyed) return@launch

            if (result.isFailure) {
                showAppMessage(result.exceptionOrNull()?.message ?: "Payment failed.")
                return@launch
            }

            // Receipt details returned by the API
            val json = result.getOrNull()
            val reference = json?.optString("referenceNumber", "") ?: ""
            val transaction = json?.optString("transactionId", "") ?: ""

            AlertDialog.Builder(this@PaymentActivity)
                .setTitle("Payment received")
                .setMessage(
                    "${money(booking.totalAmount)} for your ${booking.service} booking.\n\n" +
                            "Reference: $reference\nTransaction: $transaction\n\n" +
                            "Your booking is now confirmed."
                )
                .setCancelable(false)
                .setPositiveButton("My bookings") { _, _ ->
                    go(BookingHistoryActivity::class.java)
                    finish()
                }
                .show()
        }
    }
}