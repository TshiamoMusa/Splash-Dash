document.addEventListener("DOMContentLoaded", function () {
    const addForm = document.getElementById("add-staff-form");
    const filterForm = document.getElementById("staff-filter-form");
    const records = document.getElementById("staff-records");
    const message = document.getElementById("staff-message");
    const addButton = document.getElementById("add-staff-button");
    const clearButton = document.getElementById("clear-staff-filters");

    let accounts = [];

    const currentAccount = Portal.getAccount();

    loadAccounts();

    // Create a Staff or Admin account.
    addForm.addEventListener("submit", async function (event) {
        event.preventDefault();
        showMessage("");

        const formData = new FormData(addForm);

        const account = {
            firstName: formData.get("first_name").trim(),
            surname: formData.get("surname").trim(),
            email: formData.get("email").trim(),
            phoneNumber: formData.get("contact_number").trim(),
            username: formData.get("username").trim(),
            role: formData.get("role"),
            password: formData.get("password")
        };

        addButton.disabled = true;
        addButton.textContent = "Creating account...";

        try {
            const result = await Portal.post("/staff", account);

            addForm.reset();
            showMessage(result.message || "Account created successfully.", "success");
            window.scrollTo({ top: 0, behavior: "smooth" });
            await loadAccounts();
        } catch (error) {
            showMessage(error.message, "error");
            window.scrollTo({ top: 0, behavior: "smooth" });
        } finally {
            addButton.disabled = false;
            addButton.textContent = "Add Staff Member";
        }
    });

    // Apply the search and status filters.
    filterForm.addEventListener("submit", function (event) {
        event.preventDefault();
        displayAccounts();
    });

    document.getElementById("staff-search").addEventListener("input", displayAccounts);
    document.getElementById("staff-status").addEventListener("change", displayAccounts);

    clearButton.addEventListener("click", function () {
        filterForm.reset();
        displayAccounts();
    });

    // Handle password resets and account status changes.
    records.addEventListener("submit", async function (event) {
        const form = event.target;

        if (form.dataset.action !== "reset-password") return;

        event.preventDefault();

        const accountId = form.dataset.accountId;
        const passwordInput = form.querySelector("input[name='newPassword']");
        const newPassword = passwordInput.value;

        if (newPassword.length < 8) {
            showMessage("The new password must contain at least 8 characters.", "error");
            window.scrollTo({ top: 0, behavior: "smooth" });
            return;
        }

        const button = form.querySelector("button");
        button.disabled = true;
        button.textContent = "Resetting...";

        try {
            const result = await Portal.put(
                "/staff/" + accountId + "/password",
                { newPassword: newPassword }
            );

            form.reset();
            showMessage(result.message || "Password reset successfully.", "success");
            window.scrollTo({ top: 0, behavior: "smooth" });
            form.closest("details").open = false;
        } catch (error) {
            showMessage(error.message, "error");
            window.scrollTo({ top: 0, behavior: "smooth" });
        } finally {
            button.disabled = false;
            button.textContent = "Reset";
        }
    });

    records.addEventListener("click", async function (event) {
        const button = event.target.closest("button[data-action]");
        if (!button) return;

        const action = button.dataset.action;

        if (action !== "deactivate" && action !== "reactivate") return;

        const accountId = Number(button.dataset.accountId);
        const account = accounts.find(item => item.accountId === accountId);

        if (!account) return;

        const activating = action === "reactivate";

        const confirmation = activating
            ? "Reactivate this account?"
            : "Deactivate this account?";

        if (!confirm(confirmation)) return;

        button.disabled = true;

        try {
            const result = await Portal.put(
                "/staff/" + accountId + "/status",
                { isActive: activating }
            );

            showMessage(result.message || "Account status updated.", "success");
            window.scrollTo({ top: 0, behavior: "smooth" });
            await loadAccounts();
        } catch (error) {
            showMessage(error.message, "error");
            window.scrollTo({ top: 0, behavior: "smooth" });
            button.disabled = false;
        }
    });

    async function loadAccounts() {
        records.innerHTML = "<tr><td colspan='8'>Loading accounts...</td></tr>";

        try {
            const result = await Portal.get("/staff/all");

            if (!Array.isArray(result)) {
                throw new Error("The API returned an unexpected account list.");
            }

            accounts = result;
            updateStatistics();
            displayAccounts();
        } catch (error) {
            accounts = [];
            updateStatistics();
            records.innerHTML = "<tr><td colspan='8'>Unable to load accounts.</td></tr>";
            showMessage(error.message, "error");
        }
    }

    function updateStatistics() {
        const active = accounts.filter(account => account.isActive).length;
        const inactive = accounts.length - active;

        document.getElementById("total-staff").textContent = accounts.length;
        document.getElementById("active-staff").textContent = active;
        document.getElementById("inactive-staff").textContent = inactive;
    }

    function displayAccounts() {
        const searchValue = document.getElementById("staff-search")
            .value.trim().toLowerCase();

        const statusValue = document.getElementById("staff-status").value;

        const filteredAccounts = accounts.filter(account => {
            const searchableText = [
                account.fullName,
                account.username,
                account.email,
                account.accountId
            ].join(" ").toLowerCase();

            const matchesSearch = searchableText.includes(searchValue);

            const accountStatus = account.isActive ? "Active" : "Inactive";
            const matchesStatus = !statusValue || accountStatus === statusValue;

            return matchesSearch && matchesStatus;
        });

        records.replaceChildren();

        if (filteredAccounts.length === 0) {
            const row = document.createElement("tr");
            const cell = document.createElement("td");

            cell.colSpan = 8;
            cell.textContent = accounts.length === 0
                ? "No Staff or Admin accounts have been created yet."
                : "No accounts match your search.";

            row.appendChild(cell);
            records.appendChild(row);
            return;
        }

        filteredAccounts.forEach(account => {
            records.appendChild(createAccountRow(account));
        });
    }

    function createAccountRow(account) {
        const row = document.createElement("tr");

        addCell(row, account.accountId);
        addCell(row, account.fullName);
        addCell(row, account.email);
        addCell(row, account.phoneNumber);
        addCell(row, account.username);
        addCell(row, account.role);
        addCell(row, account.isActive ? "Active" : "Inactive");

        const actionsCell = document.createElement("td");
        const actions = document.createElement("div");
        actions.className = "row-actions";

        // Password reset.
        const details = document.createElement("details");
        const summary = document.createElement("summary");
        summary.textContent = "Reset password";

        const resetForm = document.createElement("form");
        resetForm.dataset.action = "reset-password";
        resetForm.dataset.accountId = account.accountId;

        const passwordInput = document.createElement("input");
        passwordInput.type = "text";
        passwordInput.name = "newPassword";
        passwordInput.placeholder = "New temporary password";
        passwordInput.minLength = 8;
        passwordInput.autocomplete = "off";
        passwordInput.required = true;
        passwordInput.setAttribute("aria-label", "New temporary password");

        const resetButton = document.createElement("button");
        resetButton.type = "submit";
        resetButton.textContent = "Reset";

        resetForm.append(passwordInput, resetButton);
        details.append(summary, resetForm);
        actions.appendChild(details);

        // Activate or deactivate the account.
        const statusButton = document.createElement("button");
        statusButton.type = "button";
        statusButton.dataset.accountId = account.accountId;

        if (account.isActive) {
            statusButton.textContent = "Deactivate";
            statusButton.dataset.action = "deactivate";

            const isCurrentAdmin =
                account.role === "Admin" &&
                currentAccount &&
                Number(currentAccount.id) === Number(account.accountId);

            if (isCurrentAdmin) {
                statusButton.disabled = true;
                statusButton.title = "You cannot deactivate your own account.";
            }
        } else {
            statusButton.textContent = "Reactivate";
            statusButton.dataset.action = "reactivate";
        }

        actions.appendChild(statusButton);
        actionsCell.appendChild(actions);
        row.appendChild(actionsCell);

        return row;
    }

    function addCell(row, value) {
        const cell = document.createElement("td");
        cell.textContent = value === null || value === undefined || value === ""
            ? "N/A"
            : value;

        row.appendChild(cell);
    }

    function showMessage(text, type) {
        message.textContent = text;
        message.className = type ? "message " + type : "";
    }
});