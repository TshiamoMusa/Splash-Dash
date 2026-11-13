
document.addEventListener("DOMContentLoaded", function () {
    const filterForm = document.getElementById("booking-filter-form");
    const bookingTable = document.getElementById("bookingQueue");
    const clearButton = document.getElementById("clear-booking-filters");

    let bookings = [];

    loadBookings();

    filterForm.addEventListener("submit", function (event) {
        event.preventDefault();
        displayBookings();
    });

    document.getElementById("booking-search")
        .addEventListener("input", displayBookings);

    document.getElementById("booking-status")
        .addEventListener("change", displayBookings);

    document.getElementById("booking-type")
        .addEventListener("change", displayBookings);

    document.getElementById("booking-date")
        .addEventListener("change", displayBookings);

    clearButton.addEventListener("click", function () {
        filterForm.reset();
        displayBookings();
    });

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

    bookingTable.addEventListener("click", async function (event) {
        const button = event.target.closest("button[data-action='cancel']");
        if (!button) return;

        const bookingId = button.dataset.bookingId;

        if (!confirm("Are you sure you want to cancel this booking?")) {
            return;
        }

        button.disabled = true;
        button.textContent = "Cancelling...";

        try {
            const result = await Portal.put(
                "/Bookings/" + bookingId + "/status",
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

    async function loadBookings() {
        bookingTable.innerHTML =
            "<tr><td colspan='8'>Loading bookings...</td></tr>";

        try {
            const result = await Portal.get("/Bookings/all");

            if (!Array.isArray(result)) {
                throw new Error("The API returned an unexpected booking list.");
            }

            bookings = result;
            displayBookings();

        } catch (error) {
            bookings = [];
            bookingTable.innerHTML =
                "<tr><td colspan='8'>Unable to load bookings.</td></tr>";

            showMessage(error.message, "error");
        }
    }

    function displayBookings() {
        const searchValue = document.getElementById("booking-search")
            .value.trim().toLowerCase();

        const statusValue = document.getElementById("booking-status").value;
        const typeValue = document.getElementById("booking-type").value;
        const dateValue = document.getElementById("booking-date").value;

        const filteredBookings = bookings.filter(function (booking) {
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

        filteredBookings.sort(function (a, b) {
            return new Date(a.bookingDateTime) - new Date(b.bookingDateTime);
        });

        bookingTable.replaceChildren();

        if (filteredBookings.length === 0) {
            const row = document.createElement("tr");
            const cell = document.createElement("td");

            cell.colSpan = 8;
            cell.textContent = "No bookings match your selected filters.";

            row.appendChild(cell);
            bookingTable.appendChild(row);
            return;
        }

        filteredBookings.forEach(function (booking) {
            bookingTable.appendChild(createBookingRow(booking));
        });
    }

    function createBookingRow(booking) {
        const row = document.createElement("tr");
        const bookingDate = new Date(booking.bookingDateTime);

        addCell(row, booking.bookingId);

        const customerCell = document.createElement("td");
        addStackedText(customerCell, booking.customerName, booking.customerPhone);
        row.appendChild(customerCell);

        const vehicleCell = document.createElement("td");
        addStackedText(vehicleCell, booking.vehicleType, null);
        row.appendChild(vehicleCell);

        addCell(row, booking.service);

        const typeCell = document.createElement("td");
        const bookingType = booking.locationType === "On-site"
            ? "At Location"
            : booking.locationType;

        const serviceAddress = booking.locationType === "House Call"
            ? booking.bookingAddress
            : null;

        addStackedText(typeCell, bookingType, serviceAddress);
        row.appendChild(typeCell);

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

        addCell(row, booking.bookingStatus);

        const actionsCell = document.createElement("td");
        const actions = document.createElement("div");
        actions.className = "row-actions";

        if (booking.bookingStatus !== "Cancelled") {
            actions.appendChild(createStatusForm(booking));

            const cancelButton = document.createElement("button");
            cancelButton.type = "button";
            cancelButton.textContent = "Cancel Booking";
            cancelButton.dataset.action = "cancel";
            cancelButton.dataset.bookingId = booking.bookingId;

            actions.appendChild(cancelButton);
        } else {
            const cancelledText = document.createElement("span");
            cancelledText.textContent = "Cancelled";
            actions.appendChild(cancelledText);
        }

        actionsCell.appendChild(actions);
        row.appendChild(actionsCell);

        return row;
    }

    function createStatusForm(booking) {
        const form = document.createElement("form");
        form.dataset.action = "update-status";
        form.dataset.bookingId = booking.bookingId;

        const statusSelect = document.createElement("select");
        statusSelect.name = "status";
        statusSelect.setAttribute("aria-label", "Update booking status");

        const statuses = [
            "Pending",
            "Confirmed",
            "In Progress",
            "Completed"
        ];

        statuses.forEach(function (status) {
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

    function addCell(row, value) {
        const cell = document.createElement("td");

        cell.textContent = value === null || value === undefined || value === ""
            ? "N/A"
            : value;

        row.appendChild(cell);
    }

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

    function getLocalDateKey(date) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, "0");
        const day = String(date.getDate()).padStart(2, "0");

        return year + "-" + month + "-" + day;
    }

    function showMessage(message, type) {
        let messageElement = document.getElementById("queue-message");

        if (!messageElement) {
            messageElement = document.createElement("p");
            messageElement.id = "queue-message";
            messageElement.setAttribute("role", "status");
            bookingTable.parentElement.before(messageElement);
        }

        messageElement.textContent = message;
        messageElement.className = type ? "message " + type : "";
    }
});
