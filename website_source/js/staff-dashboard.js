
document.addEventListener("DOMContentLoaded", function () {
    const pendingCount = document.getElementById("pendingBookings");
    const inProgressCount = document.getElementById("inProgressBookings");
    const completedCount = document.getElementById("completedToday");
    const houseCallsCount = document.getElementById("houseCallsToday");
    const nextUpTable = document.getElementById("next-up");

    loadDashboard();

    async function loadDashboard() {
        try {
            const bookings = await Portal.get("/Bookings/all");

            if (!Array.isArray(bookings)) {
                throw new Error("The API returned an unexpected booking list.");
            }

            const today = getLocalDateKey(new Date());

            const todaysBookings = bookings.filter(function (booking) {
                const bookingDate = new Date(booking.bookingDateTime);

                return !isNaN(bookingDate.getTime()) &&
                    getLocalDateKey(bookingDate) === today;
            });

            updateStatistics(todaysBookings);
            displayNextBookings(todaysBookings);

        } catch (error) {
            setStatisticsUnavailable();
            nextUpTable.innerHTML =
                "<tr><td colspan='6'>Unable to load today's bookings.</td></tr>";

            console.error("Staff dashboard error:", error);
        }
    }

    function updateStatistics(bookings) {
        pendingCount.textContent = bookings.filter(function (booking) {
            return booking.bookingStatus === "Pending";
        }).length;

        inProgressCount.textContent = bookings.filter(function (booking) {
            return booking.bookingStatus === "In Progress";
        }).length;

        completedCount.textContent = bookings.filter(function (booking) {
            return booking.bookingStatus === "Completed";
        }).length;

        houseCallsCount.textContent = bookings.filter(function (booking) {
            return booking.locationType === "House Call";
        }).length;
    }

    function displayNextBookings(bookings) {
        const upcomingBookings = bookings
            .filter(function (booking) {
                return booking.bookingStatus !== "Cancelled";
            })
            .sort(function (a, b) {
                return new Date(a.bookingDateTime) -
                    new Date(b.bookingDateTime);
            });

        nextUpTable.replaceChildren();

        if (upcomingBookings.length === 0) {
            const row = document.createElement("tr");
            const cell = document.createElement("td");

            cell.colSpan = 6;
            cell.textContent = "There are no bookings scheduled for today.";

            row.appendChild(cell);
            nextUpTable.appendChild(row);
            return;
        }

        upcomingBookings.forEach(function (booking) {
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
            addCell(row, booking.locationType);
            addCell(row, booking.bookingStatus);

            nextUpTable.appendChild(row);
        });
    }

    function addCell(row, value) {
        const cell = document.createElement("td");

        cell.textContent = value === null || value === undefined || value === ""
            ? "N/A"
            : value;

        row.appendChild(cell);
    }

    function getLocalDateKey(date) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, "0");
        const day = String(date.getDate()).padStart(2, "0");

        return year + "-" + month + "-" + day;
    }

    function setStatisticsUnavailable() {
        pendingCount.textContent = "--";
        inProgressCount.textContent = "--";
        completedCount.textContent = "--";
        houseCallsCount.textContent = "--";
    }
});
