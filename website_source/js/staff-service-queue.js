/*
 * Splash & Dash - Staff Service Queue
 *
 * Shows the shared booking queue, lets staff search and filter it, move a
 * booking to a new status, or cancel it.
 *
 * Depends on js/portal-auth.js (Portal.get / Portal.put) and the element ids
 * in "staff - service_queue.html".
 *
 * Booking rules (also enforced by the API, so they cannot be bypassed):
 *   - Cancelled bookings are final.
 *   - Completed bookings can only be changed by an administrator, so staff
 *     see no actions for them.
 */
document.addEventListener("DOMContentLoaded", function () {
    "use strict";

    // Number of columns in the queue table (used for full-width message rows).
    const TABLE_COLUMNS = 9;

    // Statuses staff can move a booking to. Cancelling has its own button.
    const STATUS_OPTIONS = ["Pending", "Confirmed", "In Progress", "Completed"];

    // Page elements
    const filterForm = document.getElementById("booking-filter-form");
    const searchInput = document.getElementById("booking-search");
    const statusFilter = document.getElementById("booking-status");
    const typeFilter = document.getElementById("booking-type");
    const dateFilter = document.getElementById("booking-date");
    const clearButton = document.getElementById("clear-booking-filters");
    const refreshButton = document.getElementById("refresh-bookings");
    const bookingTable = document.getElementById("bookingQueue");
    const messageBox = document.getElementById("queue-message");

    // Every booking returned by the API. Filtering happens in the browser.
    let bookings = [];

    loadBookings();

    // ------------------------------------------------------------
    // Filters
    // ------------------------------------------------------------

    filterForm.addEventListener("submit", function (event) {
        event.preventDefault();
        displayBookings();
    });

    searchInput.addEventListener("input", displayBookings);
    statusFilter.addEventListener("change", displayBookings);
    typeFilter.addEventListener("change", displayBookings);
    dateFilter.addEventListener("change", displayBookings);

    clearButton.addEventListener("click", function () {
        filterForm.reset();
        displayBookings();
    });

    refreshButton.addEventListener("click", loadBookings);

    // ------------------------------------------------------------
    // Actions (event delegation: one listener covers every table row)
    // ------------------------------------------------------------

    // "Save Status" form inside a row.
    bookingTable.addEventListener("submit", async function (event) {
        const form = event.target;

        if (form.dataset.action !== "update-status") return;

        event.preventDefault();

        const bookingId = form.dataset.bookingId;
        const status = form.querySelector("select[name='status']").value;
        const button = form.querySelector("button");

        button.disabled = true;
        button.textContent = "Saving...";

        try {
            const result = await Portal.put(
                "/Bookings/" + bookingId + "/status",
                { status: status }
            );

            showMessage(result.message || "Booking status updated.", "success");
            await loadBookings();

        } catch (error) {
            showMessage(error.message, "error");
            button.disabled = false;
            button.textContent = "Save Status";
        }
    });

    // "Cancel Booking" button inside a row.
    bookingTable.addEventListener("click", async function (event) {
        const button = event.target.closest("button[data-action='cancel']");

        if (!button) return;

        if (!confirm("Are you sure you want to cancel this booking? This cannot be undone.")) {
            return;
        }

        button.disabled = true;
        button.textContent = "Cancelling...";

        try {
            const result = await Portal.put(
                "/Bookings/" + button.dataset.bookingId + "/status",
                { status: "Cancelled" }
            );

            showMessage(result.message || "Booking cancelled successfully.", "success");
            await loadBookings();

        } catch (error) {
            showMessage(error.message, "error");
            button.disabled = false;
            button.textContent = "Cancel Booking";
        }
    });

    // ------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------

    // Fetches the full queue from the API and redraws the table.
    async function loadBookings() {
        refreshButton.disabled = true;
        showMessageRow("Loading bookings...");

        try {
            const result = await Portal.get("/Bookings/all");

            if (!Array.isArray(result)) {
                throw new Error("The API returned an unexpected booking list.");
            }

            bookings = result;
            displayBookings();

        } catch (error) {
            bookings = [];
            showMessageRow("Unable to load bookings.");
            showMessage(error.message, "error");

        } finally {
            refreshButton.disabled = false;
        }
    }

    // ------------------------------------------------------------
    // Table
    // ------------------------------------------------------------

    // Applies the search box and filters, then redraws the table.
    function displayBookings() {
        const searchValue = searchInput.value.trim().toLowerCase();
        const statusValue = statusFilter.value;
        const typeValue = typeFilter.value;
        const dateValue = dateFilter.value;

        const filtered = bookings.filter(function (booking) {
            const bookingStatus = booking.bookingStatus || "";
            const bookingDate = new Date(booking.bookingDateTime);

            const searchableText = [
                booking.bookingId,
                booking.customerName,
                booking.customerPhone,
                booking.vehicleType,
                booking.service
            ].join(" ").toLowerCase();

            const matchesSearch = searchableText.includes(searchValue);

            // With no status chosen ("Active Bookings"), cancelled ones are hidden.
            const matchesStatus = statusValue
                ? bookingStatus === statusValue
                : bookingStatus !== "Cancelled";

            const matchesType = !typeValue ||
                booking.locationType === typeValue;

            const matchesDate = !dateValue ||
                (!isNaN(bookingDate.getTime()) &&
                    getLocalDateKey(bookingDate) === dateValue);

            return matchesSearch && matchesStatus && matchesType && matchesDate;
        });

        // Earliest booking first.
        filtered.sort(function (a, b) {
            return new Date(a.bookingDateTime) - new Date(b.bookingDateTime);
        });

        bookingTable.replaceChildren();

        if (filtered.length === 0) {
            showMessageRow("No bookings match your selected filters.");
            return;
        }

        filtered.forEach(function (booking) {
            bookingTable.appendChild(createBookingRow(booking));
        });
    }

    // Builds one table row for a booking.
    function createBookingRow(booking) {
        const row = document.createElement("tr");
        const bookingDate = new Date(booking.bookingDateTime);

        addCell(row, booking.bookingId);

        // Customer name with their phone number underneath.
        const customerCell = document.createElement("td");
        addStackedText(customerCell, booking.customerName, booking.customerPhone);
        row.appendChild(customerCell);

        addCell(row, booking.vehicleType);
        addCell(row, booking.service);

        // House calls also show the address the team must travel to.
        const typeCell = document.createElement("td");
        addStackedText(
            typeCell,
            booking.locationType === "On-site"
                ? "At Location"
                : booking.locationType,
            booking.locationType === "House Call"
                ? booking.bookingAddress
                : null
        );
        row.appendChild(typeCell);

        // Date with the time underneath.
        const dateCell = document.createElement("td");

        if (!isNaN(bookingDate.getTime())) {
            addStackedText(
                dateCell,
                bookingDate.toLocaleDateString("en-ZA"),
                bookingDate.toLocaleTimeString("en-ZA", {
                    hour: "2-digit",
                    minute: "2-digit",
                    hour12: false
                })
            );
        } else {
            addStackedText(dateCell, "Invalid date", null);
        }

        row.appendChild(dateCell);

        // Payment: status with the method underneath. A booking has no payment
        // record until the customer pays in the app.
        const paymentCell = document.createElement("td");
        addStackedText(
            paymentCell,
            booking.paymentStatus || "No payment yet",
            booking.paymentMethod
        );
        row.appendChild(paymentCell);

        addCell(row, booking.bookingStatus);

        const actionsCell = document.createElement("td");
        actionsCell.appendChild(createActions(booking));
        row.appendChild(actionsCell);

        return row;
    }

    // Staff may change a booking only while it is not Cancelled or Completed.
    function createActions(booking) {
        const actions = document.createElement("div");
        actions.className = "row-actions";

        if (booking.bookingStatus === "Cancelled" ||
            booking.bookingStatus === "Completed") {

            const note = document.createElement("span");
            note.textContent = booking.bookingStatus === "Cancelled"
                ? "Cancelled"
                : "Completed";

            actions.appendChild(note);
            return actions;
        }

        actions.appendChild(createStatusForm(booking));

        const cancelButton = document.createElement("button");
        cancelButton.type = "button";
        cancelButton.textContent = "Cancel Booking";
        cancelButton.dataset.action = "cancel";
        cancelButton.dataset.bookingId = booking.bookingId;

        actions.appendChild(cancelButton);

        return actions;
    }

    // Status drop-down and "Save Status" button for one booking.
    function createStatusForm(booking) {
        const form = document.createElement("form");
        form.dataset.action = "update-status";
        form.dataset.bookingId = booking.bookingId;

        const statusSelect = document.createElement("select");
        statusSelect.name = "status";
        statusSelect.setAttribute("aria-label", "Update booking status");

        STATUS_OPTIONS.forEach(function (status) {
            const option = document.createElement("option");
            option.value = status;
            option.textContent = status;
            option.selected = status === booking.bookingStatus;
            statusSelect.appendChild(option);
        });

        const saveButton = document.createElement("button");
        saveButton.type = "submit";
        saveButton.textContent = "Save Status";

        form.append(statusSelect, saveButton);

        return form;
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    // Adds a plain text cell. Text is inserted with textContent, never as HTML.
    function addCell(row, value) {
        const cell = document.createElement("td");

        cell.textContent = value === null || value === undefined || value === ""
            ? "N/A"
            : value;

        row.appendChild(cell);
    }

    // Fills a cell with a main line and an optional smaller second line.
    function addStackedText(cell, mainText, subText) {
        const main = document.createElement("span");
        main.className = "cell-main";
        main.textContent = mainText || "N/A";
        cell.appendChild(main);

        if (subText) {
            const sub = document.createElement("span");
            sub.className = "cell-sub";
            sub.textContent = subText;
            cell.appendChild(sub);
        }
    }

    // A single full-width row used for loading, empty and error messages.
    function showMessageRow(text) {
        bookingTable.replaceChildren();

        const row = document.createElement("tr");
        const cell = document.createElement("td");

        cell.colSpan = TABLE_COLUMNS;
        cell.textContent = text;

        row.appendChild(cell);
        bookingTable.appendChild(row);
    }

    // Returns the browser's local calendar date as YYYY-MM-DD.
    function getLocalDateKey(date) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, "0");
        const day = String(date.getDate()).padStart(2, "0");

        return year + "-" + month + "-" + day;
    }

    // Success / error message above the table. type: "success" or "error".
    function showMessage(message, type) {
        messageBox.textContent = message;
        messageBox.className = type ? "message " + type : "";
    }
});