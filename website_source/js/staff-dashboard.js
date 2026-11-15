/*
 * Splash & Dash - Staff Dashboard
 *
 * Loads the shared booking queue from the API and summarises today's
 * bookings: four counters and a time-ordered "Next Up" table.
 *
 * Depends on js/portal-auth.js (Portal.get) and the element ids in
 * "staff - dashboard.html".
 */
document.addEventListener("DOMContentLoaded", function () {
    "use strict";

    // How often the dashboard reloads itself (milliseconds).
    const REFRESH_INTERVAL_MS = 60000;

    // Number of columns in the "Next Up" table (used for message rows).
    const NEXT_UP_COLUMNS = 6;

    // Page elements
    const pendingCount = document.getElementById("pendingBookings");
    const inProgressCount = document.getElementById("inProgressBookings");
    const completedCount = document.getElementById("completedToday");
    const houseCallsCount = document.getElementById("houseCallsToday");
    const nextUpTable = document.getElementById("next-up");
    const refreshButton = document.getElementById("refresh-dashboard");
    const lastUpdated = document.getElementById("last-updated");

    // Prevents two loads running at once (e.g. a click during auto-refresh).
    let isLoading = false;

    refreshButton.addEventListener("click", loadDashboard);

    loadDashboard();
    setInterval(loadDashboard, REFRESH_INTERVAL_MS);

    // ------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------

    // Fetches every booking, keeps today's, and updates the page.
    async function loadDashboard() {
        if (isLoading) return;

        isLoading = true;
        refreshButton.disabled = true;

        try {
            const bookings = await Portal.get("/Bookings/all");

            if (!Array.isArray(bookings)) {
                throw new Error("The API returned an unexpected booking list.");
            }

            const today = getLocalDateKey(new Date());

            // Cancelled bookings are left out of every figure on this page.
            const todaysBookings = bookings.filter(function (booking) {
                const bookingDate = new Date(booking.bookingDateTime);

                return booking.bookingStatus !== "Cancelled" &&
                    !isNaN(bookingDate.getTime()) &&
                    getLocalDateKey(bookingDate) === today;
            });

            updateStatistics(todaysBookings);
            displayNextBookings(todaysBookings);
            showLastUpdated();

        } catch (error) {
            setStatisticsUnavailable();
            showMessageRow(error.message || "Unable to load today's bookings.");

            lastUpdated.textContent = "Last update failed";
        } finally {
            isLoading = false;
            refreshButton.disabled = false;
        }
    }

    // ------------------------------------------------------------
    // Counters
    // ------------------------------------------------------------

    function countWhere(bookings, test) {
        return bookings.filter(test).length;
    }

    function updateStatistics(bookings) {
        pendingCount.textContent = countWhere(bookings, function (booking) {
            return booking.bookingStatus === "Pending";
        });

        inProgressCount.textContent = countWhere(bookings, function (booking) {
            return booking.bookingStatus === "In Progress";
        });

        completedCount.textContent = countWhere(bookings, function (booking) {
            return booking.bookingStatus === "Completed";
        });

        houseCallsCount.textContent = countWhere(bookings, function (booking) {
            return booking.locationType === "House Call";
        });
    }

    function setStatisticsUnavailable() {
        pendingCount.textContent = "--";
        inProgressCount.textContent = "--";
        completedCount.textContent = "--";
        houseCallsCount.textContent = "--";
    }

    // ------------------------------------------------------------
    // "Next Up" table
    // ------------------------------------------------------------

    function displayNextBookings(bookings) {
        const sorted = bookings.slice().sort(function (a, b) {
            return new Date(a.bookingDateTime) - new Date(b.bookingDateTime);
        });

        nextUpTable.replaceChildren();

        if (sorted.length === 0) {
            showMessageRow("There are no bookings scheduled for today.");
            return;
        }

        sorted.forEach(function (booking) {
            const row = document.createElement("tr");
            const bookingDate = new Date(booking.bookingDateTime);

            addCell(row, bookingDate.toLocaleTimeString("en-ZA", {
                hour: "2-digit",
                minute: "2-digit",
                hour12: false
            }));

            addCell(row, booking.customerName);
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

            addCell(row, booking.bookingStatus);

            nextUpTable.appendChild(row);
        });
    }

    // A single full-width row used for empty and error messages.
    function showMessageRow(text) {
        nextUpTable.replaceChildren();

        const row = document.createElement("tr");
        const cell = document.createElement("td");

        cell.colSpan = NEXT_UP_COLUMNS;
        cell.textContent = text;

        row.appendChild(cell);
        nextUpTable.appendChild(row);
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

    // Returns the browser's local calendar date as YYYY-MM-DD.
    function getLocalDateKey(date) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, "0");
        const day = String(date.getDate()).padStart(2, "0");

        return year + "-" + month + "-" + day;
    }

    function showLastUpdated() {
        lastUpdated.textContent = "Last updated " +
            new Date().toLocaleTimeString("en-ZA", {
                hour: "2-digit",
                minute: "2-digit",
                hour12: false
            });
    }
});