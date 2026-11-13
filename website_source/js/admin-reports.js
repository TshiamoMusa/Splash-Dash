
document.addEventListener("DOMContentLoaded", function () {
    const filterForm = document.getElementById("report-filter-form");
    const startDateInput = document.getElementById("start-date");
    const endDateInput = document.getElementById("end-date");
    const generateButton = document.getElementById("generate-report-button");
    const message = document.getElementById("report-message");

    let allBookings = [];

    setDefaultDates();
    loadBookings();

    filterForm.addEventListener("submit", function (event) {
        event.preventDefault();

        if (!startDateInput.value || !endDateInput.value) {
            showMessage("Please select both dates.", "error");
            return;
        }

        if (startDateInput.value > endDateInput.value) {
            showMessage("The start date cannot be later than the end date.", "error");
            return;
        }

        generateReports();
    });

    document.getElementById("print-reports").addEventListener("click", function () {
        window.print();
    });

    async function loadBookings() {
        generateButton.disabled = true;
        generateButton.textContent = "Loading reports...";

        try {
            const result = await Portal.get("/Bookings/all");

            if (!Array.isArray(result)) {
                throw new Error("The API returned an unexpected booking list.");
            }

            allBookings = result;
            generateReports();
            showMessage("");
        } catch (error) {
            showMessage(error.message, "error");
            clearReports();
        } finally {
            generateButton.disabled = false;
            generateButton.textContent = "Generate Reports";
        }
    }

    function setDefaultDates() {
        const today = new Date();

        const firstDay = new Date(
            today.getFullYear(),
            today.getMonth(),
            1
        );

        startDateInput.value = formatInputDate(firstDay);
        endDateInput.value = formatInputDate(today);
    }

    function formatInputDate(date) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, "0");
        const day = String(date.getDate()).padStart(2, "0");

        return year + "-" + month + "-" + day;
    }

    function generateReports() {
        const startDate = startDateInput.value;
        const endDate = endDateInput.value;

        if (!startDate || !endDate || startDate > endDate) {
            clearReports();
            return;
        }

        const periodBookings = allBookings.filter(booking => {
            if (!booking.bookingDateTime) return false;

            const bookingDate = new Date(booking.bookingDateTime);

            if (Number.isNaN(bookingDate.getTime())) return false;

            const localBookingDate = formatInputDate(bookingDate);

            return localBookingDate >= startDate &&
                localBookingDate <= endDate;
        });

        const completedBookings = periodBookings.filter(
            booking => booking.bookingStatus === "Completed"
        );

        updateSummary(periodBookings, completedBookings, startDate, endDate);
        updateServiceReport(periodBookings);
        updateRevenueReport(completedBookings);
        updateStatusReport(periodBookings);
    }

    function updateSummary(bookings, completedBookings, startDate, endDate) {
        const revenue = calculateRevenue(completedBookings);
        const serviceCounts = countServices(bookings);

        const popularService = serviceCounts.length > 0
            ? serviceCounts[0].service
            : "N/A";

        document.getElementById("period-bookings").textContent =
            completedBookings.length;

        document.getElementById("period-revenue").textContent =
            formatCurrency(revenue);

        document.getElementById("popular-service").textContent =
            popularService;

        document.getElementById("report-period").textContent =
            "Reporting period: " + formatDisplayDate(startDate) +
            " to " + formatDisplayDate(endDate);
    }

    function updateServiceReport(bookings) {
        const table = document.getElementById("service-report");
        table.replaceChildren();

        const serviceCounts = countServices(bookings);
        const totalBookings = bookings.length;

        if (serviceCounts.length === 0) {
            addEmptyRow(table, 3, "No service bookings found for this period.");
            return;
        }

        serviceCounts.forEach(item => {
            const percentage = totalBookings === 0
                ? 0
                : (item.count / totalBookings) * 100;

            const row = document.createElement("tr");

            addCell(row, item.service);
            addCell(row, item.count);
            addCell(row, percentage.toFixed(1) + "%");

            table.appendChild(row);
        });
    }

    function updateRevenueReport(completedBookings) {
        const table = document.getElementById("revenue-report");
        table.replaceChildren();

        const serviceRevenue = {};

        completedBookings.forEach(booking => {
            const service = booking.service || "Unknown Service";

            if (!serviceRevenue[service]) {
                serviceRevenue[service] = {
                    count: 0,
                    revenue: 0
                };
            }

            serviceRevenue[service].count++;
            serviceRevenue[service].revenue += Number(booking.totalAmount || 0);
        });

        const rows = Object.entries(serviceRevenue)
            .map(([service, values]) => ({
                service: service,
                count: values.count,
                revenue: values.revenue
            }))
            .sort((a, b) => b.revenue - a.revenue);

        if (rows.length === 0) {
            addEmptyRow(table, 3, "No completed bookings found for this period.");
        } else {
            rows.forEach(item => {
                const row = document.createElement("tr");

                addCell(row, item.service);
                addCell(row, item.count);
                addCell(row, formatCurrency(item.revenue));

                table.appendChild(row);
            });
        }

        const totalRevenue = calculateRevenue(completedBookings);

        document.getElementById("total-completed-bookings").textContent =
            completedBookings.length;

        document.getElementById("total-revenue").textContent =
            formatCurrency(totalRevenue);
    }

    function updateStatusReport(bookings) {
        const table = document.getElementById("status-report");
        table.replaceChildren();

        const statuses = [
            "Pending",
            "Confirmed",
            "In Progress",
            "Completed",
            "Cancelled"
        ];

        const statusCounts = {};

        statuses.forEach(status => {
            statusCounts[status] = 0;
        });

        bookings.forEach(booking => {
            const status = booking.bookingStatus || "Unknown";

            if (statusCounts[status] === undefined) {
                statusCounts[status] = 0;
            }

            statusCounts[status]++;
        });

        const totalBookings = bookings.length;

        Object.entries(statusCounts).forEach(([status, count]) => {
            const percentage = totalBookings === 0
                ? 0
                : (count / totalBookings) * 100;

            const row = document.createElement("tr");

            addCell(row, status);
            addCell(row, count);
            addCell(row, percentage.toFixed(1) + "%");

            table.appendChild(row);
        });
    }

    function countServices(bookings) {
        const counts = {};

        bookings.forEach(booking => {
            const service = booking.service || "Unknown Service";
            counts[service] = (counts[service] || 0) + 1;
        });

        return Object.entries(counts)
            .map(([service, count]) => ({
                service: service,
                count: count
            }))
            .sort((a, b) => b.count - a.count);
    }

    function calculateRevenue(bookings) {
        return bookings.reduce((total, booking) => {
            const amount = Number(booking.totalAmount || 0);
            return total + (Number.isFinite(amount) ? amount : 0);
        }, 0);
    }

    function addCell(row, value) {
        const cell = document.createElement("td");
        cell.textContent = value;
        row.appendChild(cell);
    }

    function addEmptyRow(table, colspan, text) {
        const row = document.createElement("tr");
        const cell = document.createElement("td");

        cell.colSpan = colspan;
        cell.textContent = text;

        row.appendChild(cell);
        table.appendChild(row);
    }

    function formatCurrency(amount) {
        return new Intl.NumberFormat("en-ZA", {
            style: "currency",
            currency: "ZAR"
        }).format(amount);
    }

    function formatDisplayDate(value) {
        const parts = value.split("-");

        return new Date(
            Number(parts[0]),
            Number(parts[1]) - 1,
            Number(parts[2])
        ).toLocaleDateString("en-ZA");
    }

    function showMessage(text, type) {
        message.textContent = text;
        message.className = type ? "message " + type : "";
    }

    function clearReports() {
        document.getElementById("period-bookings").textContent = "0";
        document.getElementById("period-revenue").textContent = formatCurrency(0);
        document.getElementById("popular-service").textContent = "N/A";
        document.getElementById("total-completed-bookings").textContent = "0";
        document.getElementById("total-revenue").textContent = formatCurrency(0);

        document.getElementById("report-period").textContent =
            "No report data available.";

        document.getElementById("service-report").replaceChildren();
        document.getElementById("revenue-report").replaceChildren();
        document.getElementById("status-report").replaceChildren();

        addEmptyRow(
            document.getElementById("service-report"),
            3,
            "Report data unavailable."
        );

        addEmptyRow(
            document.getElementById("revenue-report"),
            3,
            "Report data unavailable."
        );

        addEmptyRow(
            document.getElementById("status-report"),
            3,
            "Report data unavailable."
        );
    }
});
