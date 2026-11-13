
/*
 * Splash & Dash - Admin Customer Management
 * Retrieves and manages customer accounts through the API.
 */

let allCustomers = [];

document.addEventListener("DOMContentLoaded", () => {
    loadCustomers();

    document.getElementById("customer-filter-form")
        .addEventListener("submit", event => {
            event.preventDefault();
            applyCustomerFilters();
        });
});

// Retrieves all registered customers.
async function loadCustomers() {
    const message = document.getElementById("customer-message");
    const table = document.getElementById("customer-records");

    try {
        message.textContent = "Loading customer records...";

        allCustomers = await Portal.get("/Customers/all");

        if (!Array.isArray(allCustomers)) {
            throw new Error("Invalid customer information received.");
        }

        updateCustomerStatistics(allCustomers);
        applyCustomerFilters();

        message.textContent = "";

    } catch (error) {
        message.textContent = error.message;

        table.replaceChildren();

        const row = document.createElement("tr");
        const cell = document.createElement("td");

        cell.colSpan = 7;
        cell.textContent = "Unable to retrieve customer records.";

        row.appendChild(cell);
        table.appendChild(row);
    }
}

// Calculates the total, active and inactive customer counts.
function updateCustomerStatistics(customers) {
    const active = customers.filter(customer =>
        customer.isActive
    ).length;

    const inactive = customers.length - active;

    document.getElementById("total-customers").textContent =
        customers.length;

    document.getElementById("active-customers").textContent =
        active;

    document.getElementById("inactive-customers").textContent =
        inactive;
}

// Filters customers by their name, email, phone number and status.
function applyCustomerFilters() {
    const search = document.getElementById("customer-search")
        .value.trim().toLowerCase();

    const status = document.getElementById("account-status").value;

    const filteredCustomers = allCustomers.filter(customer => {
        const matchesSearch = [
            customer.customerId,
            customer.fullName,
            customer.email,
            customer.phoneNumber
        ].some(value =>
            String(value ?? "").toLowerCase().includes(search)
        );

        const customerStatus = customer.isActive
            ? "Active"
            : "Inactive";

        const matchesStatus =
            !status || customerStatus === status;

        return matchesSearch && matchesStatus;
    });

    displayCustomers(filteredCustomers);
}

// Displays the filtered customer records.
function displayCustomers(customers) {
    const table = document.getElementById("customer-records");
    table.replaceChildren();

    if (customers.length === 0) {
        const row = document.createElement("tr");
        const cell = document.createElement("td");

        cell.colSpan = 7;
        cell.textContent = "No matching customer records found.";

        row.appendChild(cell);
        table.appendChild(row);
        return;
    }

    customers.forEach(customer => {
        const row = document.createElement("tr");

        addCell(row, customer.customerId);
        addCell(row, customer.fullName);
        addCell(row, customer.email);
        addCell(row, customer.phoneNumber);
        addCell(row, customer.totalBookings);

        const statusCell = document.createElement("td");
        statusCell.textContent = customer.isActive
            ? "Active"
            : "Inactive";

        row.appendChild(statusCell);

        const actionsCell = document.createElement("td");
        actionsCell.appendChild(createStatusButton(customer));

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

// Creates the activate or deactivate button.
function createStatusButton(customer) {
    const button = document.createElement("button");

    button.type = "button";
    button.textContent = customer.isActive
        ? "Deactivate"
        : "Reactivate";

    button.addEventListener("click", async () => {
        const action = customer.isActive
            ? "deactivate"
            : "reactivate";

        const confirmed = window.confirm(
            customer.isActive
                ? "Deactivate this customer account? Their booking history will be kept."
                : "Reactivate this customer account?"
        );

        if (!confirmed) return;

        await updateCustomerStatus(
            customer.customerId,
            !customer.isActive,
            button
        );
    });

    return button;
}

// Updates a customer's active status through the API.
async function updateCustomerStatus(customerId, isActive, button) {
    const message = document.getElementById("customer-message");

    try {
        button.disabled = true;
        button.textContent = "Updating...";

        const result = await Portal.put(
            `/Customers/${customerId}/status`,
            { isActive: isActive }
        );

        message.textContent =
            result.message || "Customer account updated successfully.";

        await loadCustomers();

    } catch (error) {
        message.textContent = error.message;
        button.disabled = false;
        button.textContent = isActive
            ? "Reactivate"
            : "Deactivate";
    }
}
