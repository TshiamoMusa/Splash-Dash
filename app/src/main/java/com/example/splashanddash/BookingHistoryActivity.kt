package com.example.splashanddash

import android.os.Bundle
import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Lists the customer's bookings (upcoming and past) with pay, edit and cancel actions. */
class BookingHistoryActivity : BaseActivity() {

    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val screen = content()
        val main = column()

        main.addView(backLink())
        main.addView(pageHeader("My bookings", "View, edit or cancel your bookings"))

        main.addView(
            button("Book a car wash") { go(BookingActivity::class.java) }
        )

        listContainer = column(0)
        main.addView(listContainer)

        main.addView(footer())

        screen.addView(main)
        setContentView(screen)
    }

    override fun onResume() {
        super.onResume()
        if (!ApiClient.getToken(this).isNullOrBlank()) {
            loadBookings()
        }
    }

    private fun gap(top: Int = 12): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(top), 0, 0) }
    }

    private fun loadBookings() {
        listContainer.removeAllViews()
        listContainer.addView(text("Loading your bookings…", 15f, false, muted))

        lifecycleScope.launch {
            val result = ApiClient.getCustomerBookings(this@BookingHistoryActivity)
            if (isFinishing || isDestroyed) return@launch

            listContainer.removeAllViews()

            if (result.isFailure) {
                listContainer.addView(
                    text(result.exceptionOrNull()?.message ?: "Could not load your bookings.", 15f, false, danger)
                )
                listContainer.addView(outlineButton("Retry") { loadBookings() })
                return@launch
            }

            val bookings = result.getOrNull() ?: emptyList()

            if (bookings.isEmpty()) {
                listContainer.addView(
                    card(
                        "No bookings yet",
                        "Book your first wash and it will show up here.",
                        "Book now"
                    ) { go(BookingActivity::class.java) }
                )
                return@launch
            }

            val activeStatuses = setOf("pending", "confirmed", "in progress")

            val upcoming = bookings
                .filter { it.bookingStatus.lowercase() in activeStatuses }
                .sortedBy { ApiClient.parseApiDate(it.bookingDateTime)?.time ?: Long.MAX_VALUE }

            val past = bookings.filter { it.bookingStatus.lowercase() !in activeStatuses }

            if (upcoming.isNotEmpty()) {
                listContainer.addView(sectionTitle("Upcoming"))
                upcoming.forEach { listContainer.addView(bookingCard(it)) }
            }

            if (past.isNotEmpty()) {
                listContainer.addView(sectionTitle("Past"))
                past.forEach { listContainer.addView(bookingCard(it)) }
            }
        }
    }

    private fun bookingCard(booking: BookingItem): LinearLayout {
        val container = card().apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(2), dp(6), dp(2), dp(14)) }
        }

        // Service name + status
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = booking.service
            textSize = 18f
            setTextColor(heading)
            typeface = Typeface.DEFAULT_BOLD
        }
        header.addView(
            title,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(statusBadge(booking.bookingStatus))
        container.addView(header)

        // Details
        container.addView(text(ApiClient.displayDate(booking.bookingDateTime), 15f, true))
        container.addView(text("${booking.vehicleType}  ·  ${booking.locationType}", 14f, false, muted))

        if (!booking.bookingAddress.isNullOrBlank()) {
            container.addView(text(booking.bookingAddress, 14f, false, muted))
        }

        container.addView(text(money(booking.totalAmount), 17f, true, accent))

        val status = booking.bookingStatus.lowercase()
        val canEdit = status == "pending"
        val canCancel = status == "pending" || status == "confirmed"

        if (canEdit) {
            container.addView(
                button("Pay now") { go(PaymentActivity::class.java) },
                gap()
            )
        }

        if (canEdit || canCancel) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

            if (canEdit) {
                row.addView(
                    outlineButton("Edit") {
                        startActivity(BookingActivity.editIntent(this, booking))
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { setMargins(0, 0, dp(6), 0) }
                )
            }

            if (canCancel) {
                row.addView(
                    dangerButton("Cancel") { confirmCancel(booking) },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { setMargins(if (canEdit) dp(6) else 0, 0, 0, 0) }
                )
            }

            container.addView(row, gap(8))
        }

        return container
    }

    private fun confirmCancel(booking: BookingItem) {
        AlertDialog.Builder(this)
            .setTitle("Cancel booking")
            .setMessage("Are you sure you want to cancel this ${booking.service} booking?")
            .setPositiveButton("Yes, cancel") { _, _ ->
                lifecycleScope.launch {
                    val result = ApiClient.cancelBooking(this@BookingHistoryActivity, booking.bookingId)
                    if (isFinishing || isDestroyed) return@launch

                    if (result.isSuccess) {
                        loadBookings()
                    } else {
                        showAppMessage(result.exceptionOrNull()?.message ?: "Could not cancel booking.")
                    }
                }
            }
            .setNegativeButton("Keep it", null)
            .show()
    }
}