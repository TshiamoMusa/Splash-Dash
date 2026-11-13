
/*
 * Splash & Dash - Admin Dashboard
 * Retrieves booking information from the shared API.
 */

document.addEventListener("DOMContentLoaded", loadDashboard);

// Retrieves bookings and updates the dashboard.
async function loadDashboard() {
    const table = document.getElementById("recent-bookings");
    const message = document.getElementById("dashboard-message");

    try {
        // Retrieve all bookings using the logged-in administrator's token.
        const bookings = await Portal.get("/Bookings/all");

        if (!Array.isArray(bookings)) {
            throw new Error("Invalid booking information received.");
        }

        updateStatistics(bookings);
        displayRecentBookings(bookings);

        message.textContent = "";

    } catch (error) {
        message.textContent = error.message;

        table.innerHTML = `
            <tr>
                <td colspan="6">Unable to load bookings.</td>
            </tr>
        `;
    }
}

// Calculates dashboard statistics from the booking records.
function updateStatistics(bookings) {
    const today = new Date();

    const todaysBookings = bookings.filter(booking => {
        const date = new Date(booking.bookingDateTime);

        return date.getFullYear() === today.getFullYear() &&
            date.getMonth() === today.getMonth() &&
            date.getDate() === today.getDate();
    });

    const pendingBookings = bookings.filter(booking =>
        booking.bookingStatus === "Pending"
    );

    const completedBookings = bookings.filter(booking =>
        booking.bookingStatus === "Completed"
    );

    // Revenue only includes completed bookings.
    const revenue = completedBookings.reduce((total, booking) => {
        return total + Number(booking.totalAmount || 0);
    }, 0);

    document.getElementById("total-bookings").textContent =
        bookings.length;

    document.getElementById("todays-bookings").textContent =
        todaysBookings.length;

    document.getElementById("pending-bookings").textContent =
        pendingBookings.length;

    document.getElementById("completed-bookings").textContent =
        completedBookings.length;

    document.getElementById("total-revenue").textContent =
        formatCurrency(revenue);
}

// Displays the five most recently scheduled bookings.
function displayRecentBookings(bookings) {
    const table = document.getElementById("recent-bookings");

    table.replaceChildren();

    const recentBookings = [...bookings]
        .sort((a, b) =>
            new Date(b.bookingDateTime) -
            new Date(a.bookingDateTime)
        )
        .slice(0, 5);

    if (recentBookings.length === 0) {
        const row = document.createElement("tr");
        const cell = document.createElement("td");

        cell.colSpan = 6;
        cell.textContent = "No bookings available.";

        row.appendChild(cell);
        table.appendChild(row);
        return;
    }

    recentBookings.forEach(booking => {
        const row = document.createElement("tr");

        const values = [
            booking.bookingId,
            booking.customerName,
            booking.service,
            booking.locationType,
            formatDate(booking.bookingDateTime),
            booking.bookingStatus
        ];

        values.forEach(value => {
            const cell = document.createElement("td");
            cell.textContent = value ?? "N/A";
            row.appendChild(cell);
        });

        table.appendChild(row);
    });
}

// Formats booking dates using South African date conventions.
function formatDate(value) {
    const date = new Date(value);

    if (isNaN(date.getTime())) {
        return "Invalid date";
    }

    return date.toLocaleString("en-ZA", {
        day: "2-digit",
        month: "short",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
        hour12: true
    });
}

// Formats completed booking revenue in South African Rand.
function formatCurrency(amount) {
    return new Intl.NumberFormat("en-ZA", {
        style: "currency",
        currency: "ZAR"
    }).format(amount);
}
