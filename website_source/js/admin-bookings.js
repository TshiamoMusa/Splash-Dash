
/*
 * Splash & Dash - Admin Booking Management
 * Retrieves and manages bookings through the shared API.
 */

let allBookings = [];

document.addEventListener("DOMContentLoaded", () => {
    loadBookings();

    document.getElementById("booking-filter-form")
        .addEventListener("submit", event => {
            event.preventDefault();
            applyBookingFilters();
        });

    document.getElementById("clear-booking-filters")
        .addEventListener("click", () => {
            setTimeout(applyBookingFilters, 0);
        });
});

// Retrieves all bookings available to administrators.
async function loadBookings() {
    const message = document.getElementById("booking-message");
    const table = document.getElementById("booking-records");

    try {
        message.textContent = "Loading bookings...";

        allBookings = await Portal.get("/Bookings/all");

        if (!Array.isArray(allBookings)) {
            throw new Error("Invalid booking information received.");
        }

        updateBookingStatistics(allBookings);
        applyBookingFilters();

        message.textContent = "";

    } catch (error) {
        message.textContent = error.message;

        table.replaceChildren();

        const row = document.createElement("tr");
        const cell = document.createElement("td");

        cell.colSpan = 9;
        cell.textContent = "Unable to retrieve booking records.";

        row.appendChild(cell);
        table.appendChild(row);
    }
}

// Calculates the totals for each booking status.
function updateBookingStatistics(bookings) {
    const countStatus = status =>
        bookings.filter(booking =>
            booking.bookingStatus === status
        ).length;

    document.getElementById("total-bookings").textContent =
        bookings.length;

    document.getElementById("pending-bookings").textContent =
        countStatus("Pending");

    document.getElementById("confirmed-bookings").textContent =
        countStatus("Confirmed");

    document.getElementById("in-progress-bookings").textContent =
        countStatus("In Progress");

    document.getElementById("completed-bookings").textContent =
        countStatus("Completed");

    document.getElementById("cancelled-bookings").textContent =
        countStatus("Cancelled");
}

// Applies the selected search and filter options.
function applyBookingFilters() {
    const search = document.getElementById("booking-search")
        .value.trim().toLowerCase();

    const status = document.getElementById("booking-status").value;
    const type = document.getElementById("booking-type").value;
    const date = document.getElementById("booking-date").value;

    const filteredBookings = allBookings.filter(booking => {
        const matchesSearch = [
            booking.bookingId,
            booking.customerName,
            booking.customerPhone,
            booking.vehicleType,
            booking.service
        ].some(value =>
            String(value ?? "").toLowerCase().includes(search)
        );

        const matchesStatus =
            !status || booking.bookingStatus === status;

        const matchesType =
            !type || booking.locationType === type;

        const matchesDate =
            !date || getLocalDate(booking.bookingDateTime) === date;

        return matchesSearch &&
            matchesStatus &&
            matchesType &&
            matchesDate;
    });

    displayBookings(filteredBookings);
}

// Displays booking records and their available actions.
function displayBookings(bookings) {
    const table = document.getElementById("booking-records");

    table.replaceChildren();

    if (bookings.length === 0) {
        const row = document.createElement("tr");
        const cell = document.createElement("td");

        cell.colSpan = 9;
        cell.textContent = "No matching bookings found.";

        row.appendChild(cell);
        table.appendChild(row);
        return;
    }

    bookings.forEach(booking => {
        const row = document.createElement("tr");

        addCell(row, booking.bookingId);
        addCustomerCell(row, booking);
        addCell(row, booking.vehicleType);
        addCell(row, booking.service);
        addLocationCell(row, booking);
        addDateCell(row, booking.bookingDateTime);
        addCell(row, formatCurrency(booking.totalAmount));
        addCell(row, booking.bookingStatus);

        const actionsCell = document.createElement("td");

        if (booking.bookingStatus !== "Cancelled") {
            actionsCell.appendChild(createStatusControls(booking));
            actionsCell.appendChild(createCancelButton(booking));
        } else {
            actionsCell.textContent = "No actions available";
        }

        row.appendChild(actionsCell);
        table.appendChild(row);
    });
}

// Creates a normal table cell.
function addCell(row, value) {
    const cell = document.createElement("td");
    cell.textContent = value ?? "N/A";
    row.appendChild(cell);
}

// Displays the customer name and phone number.
function addCustomerCell(row, booking) {
    const cell = document.createElement("td");

    const name = document.createElement("span");
    name.className = "cell-main";
    name.textContent = booking.customerName ?? "N/A";

    const phone = document.createElement("span");
    phone.className = "cell-sub";
    phone.textContent = booking.customerPhone ?? "N/A";

    cell.append(name, phone);
    row.appendChild(cell);
}

// Displays the booking location and house-call address.
function addLocationCell(row, booking) {
    const cell = document.createElement("td");

    const location = document.createElement("span");
    location.className = "cell-main";
    location.textContent = booking.locationType ?? "N/A";

    cell.appendChild(location);

    if (booking.locationType === "House Call") {
        const address = document.createElement("span");
        address.className = "cell-sub";
        address.textContent = booking.bookingAddress ?? "Address unavailable";

        cell.appendChild(address);
    }

    row.appendChild(cell);
}

// Displays the booking date and time.
function addDateCell(row, value) {
    const cell = document.createElement("td");
    const date = new Date(value);

    if (isNaN(date.getTime())) {
        cell.textContent = "Invalid date";
    } else {
        const dateText = document.createElement("span");
        dateText.className = "cell-main";
        dateText.textContent = date.toLocaleDateString("en-ZA");

        const timeText = document.createElement("span");
        timeText.className = "cell-sub";
        timeText.textContent = date.toLocaleTimeString("en-ZA", {
            hour: "2-digit",
            minute: "2-digit"
        });

        cell.append(dateText, timeText);
    }

    row.appendChild(cell);
}

// Creates controls for updating a booking's status.
function createStatusControls(booking) {
    const container = document.createElement("div");
    container.className = "row-actions";

    const form = document.createElement("form");

    const select = document.createElement("select");
    select.setAttribute("aria-label", "Booking status");

    const statuses = [
        "Pending",
        "Confirmed",
        "In Progress",
        "Completed"
    ];

    statuses.forEach(status => {
        const option = document.createElement("option");
        option.value = status;
        option.textContent = status;
        select.appendChild(option);
    });

    select.value = booking.bookingStatus;

    const button = document.createElement("button");
    button.type = "submit";
    button.textContent = "Save Status";

    form.append(select, button);

    form.addEventListener("submit", async event => {
        event.preventDefault();

        await updateBookingStatus(booking.bookingId, select.value, button);
    });

    container.appendChild(form);

    return container;
}

// Sends the selected booking status to the API.
async function updateBookingStatus(bookingId, status, button) {
    const message = document.getElementById("booking-message");

    try {
        button.disabled = true;
        button.textContent = "Saving...";

        await Portal.put(
            `/Bookings/${bookingId}/status`,
            { status: status }
        );

        message.textContent = "Booking status updated successfully.";

        await loadBookings();

    } catch (error) {
        message.textContent = error.message;
        button.disabled = false;
        button.textContent = "Save Status";
    }
}

// Creates the cancellation action for a booking.
function createCancelButton(booking) {
    const button = document.createElement("button");

    button.type = "button";
    button.textContent = "Cancel Booking";

    button.addEventListener("click", async () => {
        const confirmed = window.confirm(
            "Are you sure you want to cancel this booking?"
        );

        if (!confirmed) return;

        try {
            button.disabled = true;

            await Portal.put(
                `/Bookings/${booking.bookingId}/status`,
                { status: "Cancelled" }
            );

            document.getElementById("booking-message").textContent =
                "Booking cancelled successfully.";

            await loadBookings();

        } catch (error) {
            document.getElementById("booking-message").textContent =
                error.message;

            button.disabled = false;
        }
    });

    return button;
}

// Converts a booking date to the browser's local calendar date.
function getLocalDate(value) {
    const date = new Date(value);

    if (isNaN(date.getTime())) return "";

    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");

    return `${year}-${month}-${day}`;
}

// Formats amounts in South African Rand.
function formatCurrency(amount) {
    return new Intl.NumberFormat("en-ZA", {
        style: "currency",
        currency: "ZAR"
    }).format(Number(amount || 0));
}
