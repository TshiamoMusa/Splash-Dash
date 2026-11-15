
document.addEventListener("DOMContentLoaded", function () {
    const addForm = document.getElementById("add-staff-form");
    const filterForm = document.getElementById("staff-filter-form");
    const records = document.getElementById("staff-records");
    const message = document.getElementById("staff-message");
    const addButton = document.getElementById("add-staff-button");
    const clearButton = document.getElementById("clear-staff-filters");
    const roleSelect = document.getElementById("staff-role");
    const superAdminOption = document.getElementById("super-admin-option");
    const roleHint = document.getElementById("role-hint");
    const genderGroup = document.getElementById("gender-group");
    const genderSelect = document.getElementById("staff-gender");

    let accounts = [];
    let currentAccount = Portal.getAccount() || {};

    const isSuperAdmin =
        currentAccount.isSuperAdmin === true ||
        currentAccount.isSuperAdmin === "true" ||
        currentAccount.displayRole === "Super Admin" ||
        currentAccount.role === "Super Admin";

    const currentAccountId = getAccountId(currentAccount);

    if (superAdminOption) {
        superAdminOption.hidden = !isSuperAdmin;
        superAdminOption.disabled = !isSuperAdmin;
    }

    if (roleHint) {
        roleHint.textContent = isSuperAdmin
            ? "You can create Staff, Admin and Super Admin accounts."
            : "You can create Staff and normal Admin accounts.";
    }

    updateGenderField();
    loadAccounts();

    document.addEventListener("visibilitychange", function () {
        if (!document.hidden) {
            loadAccounts();
        }
    });

    window.addEventListener("focus", function () {
        loadAccounts();
    });

    roleSelect.addEventListener("change", updateGenderField);

    // Gender is available for every account type.
    function updateGenderField() {
        genderGroup.hidden = false;
        genderSelect.required = false;
    }

    // Create a Staff, Admin or Super Admin account.
    addForm.addEventListener("submit", async function (event) {
        event.preventDefault();
        showMessage("");

        const formData = new FormData(addForm);
        const selectedRole = formData.get("role");

        if (!selectedRole) {
            showMessage("Please select an account role.", "error");
            return;
        }

        if (selectedRole === "SuperAdmin" && !isSuperAdmin) {
            showMessage(
                "Only Super Admins can create Super Admin accounts.",
                "error"
            );
            return;
        }

        const selectedGender = formData.get("gender");

        const account = {
            firstName: formData.get("first_name").trim(),
            surname: formData.get("surname").trim(),
            email: formData.get("email").trim(),
            phoneNumber: formData.get("contact_number").trim(),
            username: formData.get("username").trim(),
            role: selectedRole,
            password: formData.get("password"),
            gender: selectedGender || "Undefined"
        };

        addButton.disabled = true;
        addButton.textContent = "Creating account...";

        try {
            const result = await Portal.post("/staff", account);

            addForm.reset();
            updateGenderField();

            showMessage(
                result.message || "Account created successfully.",
                "success"
            );

            window.scrollTo({ top: 0, behavior: "smooth" });

            await loadAccounts();
        } catch (error) {
            showMessage(error.message, "error");
            window.scrollTo({ top: 0, behavior: "smooth" });
        } finally {
            addButton.disabled = false;
            addButton.textContent = "Create Account";
        }
    });

    filterForm.addEventListener("submit", function (event) {
        event.preventDefault();
        displayAccounts();
    });

    document.getElementById("staff-search")
        .addEventListener("input", displayAccounts);

    document.getElementById("staff-status")
        .addEventListener("change", displayAccounts);

    clearButton.addEventListener("click", function () {
        filterForm.reset();
        displayAccounts();
    });

    // Handle account password resets.
    records.addEventListener("submit", async function (event) {
        const form = event.target;

        if (form.dataset.action !== "reset-password") {
            return;
        }

        event.preventDefault();

        const accountId = Number(form.dataset.accountId);
        const account = findAccount(accountId);

        if (!account || !canManageAccount(account)) {
            showMessage(
                "You do not have permission to reset this account's password.",
                "error"
            );
            return;
        }

        const passwordInput = form.querySelector(
            "input[name='newPassword']"
        );

        const newPassword = passwordInput.value;

        if (newPassword.length < 8) {
            showMessage(
                "The new password must contain at least 8 characters.",
                "error"
            );
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

            showMessage(
                result.message || "Password reset successfully.",
                "success"
            );

            window.scrollTo({ top: 0, behavior: "smooth" });
            form.closest("details").open = false;
        } catch (error) {
            showMessage(error.message, "error");
        } finally {
            button.disabled = false;
            button.textContent = "Reset";
        }
    });

    // Handle account activation, deactivation and role changes.
    records.addEventListener("click", async function (event) {
        const button = event.target.closest("button[data-action]");

        if (!button) {
            return;
        }

        const action = button.dataset.action;

        const supportedActions = [
            "deactivate",
            "reactivate",
            "promote",
            "demote"
        ];

        if (!supportedActions.includes(action)) {
            return;
        }

        const accountId = Number(button.dataset.accountId);
        const account = findAccount(accountId);

        if (!account || !canManageAccount(account)) {
            showMessage(
                "You do not have permission to manage this account.",
                "error"
            );
            return;
        }

        if (
            (action === "promote" || action === "demote") &&
            !isSuperAdmin
        ) {
            showMessage(
                "Only Super Admins can change Admin roles.",
                "error"
            );
            return;
        }

        const isSuperAdminAccount =
            account.isSuperAdmin === true ||
            account.isSuperAdmin === "true";

        if (
            (action === "promote" && isSuperAdminAccount) ||
            (action === "demote" && !isSuperAdminAccount)
        ) {
            return;
        }

        const confirmations = {
            deactivate: "Deactivate this account?",
            reactivate: "Reactivate this account?",
            promote: "Promote this Admin to Super Admin?",
            demote: "Demote this Super Admin to normal Admin?"
        };

        if (!confirm(confirmations[action])) {
            return;
        }

        button.disabled = true;

        try {
            let result;

            if (action === "promote" || action === "demote") {
                result = await Portal.put(
                    "/staff/" + accountId + "/super-admin",
                    {
                        isSuperAdmin: action === "promote"
                    }
                );
            } else {
                result = await Portal.put(
                    "/staff/" + accountId + "/status",
                    {
                        isActive: action === "reactivate"
                    }
                );
            }

            showMessage(
                result.message || "Account updated successfully.",
                "success"
            );

            window.scrollTo({ top: 0, behavior: "smooth" });

            await loadAccounts();
        } catch (error) {
            showMessage(error.message, "error");
            button.disabled = false;
        }
    });

    async function loadAccounts() {
        records.innerHTML =
            "<tr><td colspan='9'>Loading accounts...</td></tr>";

        try {
            const result = await Portal.get("/staff/all");

            if (!Array.isArray(result)) {
                throw new Error(
                    "The API returned an unexpected account list."
                );
            }

            accounts = result;

            updateStatistics();
            displayAccounts();
        } catch (error) {
            accounts = [];

            updateStatistics();

            records.innerHTML =
                "<tr><td colspan='9'>Unable to load accounts.</td></tr>";

            showMessage(error.message, "error");
        }
    }

    function updateStatistics() {
        const active = accounts.filter(
            account => account.isActive
        ).length;

        const inactive = accounts.length - active;

        document.getElementById("total-staff").textContent =
            accounts.length;

        document.getElementById("active-staff").textContent =
            active;

        document.getElementById("inactive-staff").textContent =
            inactive;
    }

    function displayAccounts() {
        const searchValue = document.getElementById("staff-search")
            .value.trim().toLowerCase();

        const statusValue =
            document.getElementById("staff-status").value;

        const filteredAccounts = accounts.filter(account => {
            const searchableText = [
                account.fullName,
                account.username,
                account.email,
                account.accountId,
                getDisplayRole(account),
                getDisplayGender(account)
            ].join(" ").toLowerCase();

            const matchesSearch =
                searchableText.includes(searchValue);

            const accountStatus =
                account.isActive ? "Active" : "Inactive";

            const matchesStatus =
                !statusValue || accountStatus === statusValue;

            return matchesSearch && matchesStatus;
        });

        records.replaceChildren();

        if (filteredAccounts.length === 0) {
            const row = document.createElement("tr");
            const cell = document.createElement("td");

            cell.colSpan = 9;

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
        const accountType = getAccountType(account);

        const isSuperAdminAccount =
            account.isSuperAdmin === true ||
            account.isSuperAdmin === "true";

        addCell(row, account.accountId);
        addCell(row, account.fullName);
        addCell(row, account.email);
        addCell(row, account.phoneNumber);
        addCell(row, account.username);
        addCell(row, getDisplayRole(account));
        addCell(row, getDisplayGender(account));
        addCell(row, account.isActive ? "Active" : "Inactive");

        const actionsCell = document.createElement("td");
        const actions = document.createElement("div");

        actions.className = "row-actions";

        const isOwnAccount =
            Number(getAccountId(account)) === Number(currentAccountId);

        if (isOwnAccount) {
            const ownMessage = document.createElement("span");

            ownMessage.textContent = "Your account";
            ownMessage.className = "protected-account";

            actions.appendChild(ownMessage);
        } else if (isSuperAdminAccount) {
            if (isSuperAdmin) {
                addPasswordReset(actions, account);
                addStatusButton(actions, account);

                const demoteButton = document.createElement("button");

                demoteButton.type = "button";
                demoteButton.textContent = "Demote to Admin";
                demoteButton.dataset.accountId = account.accountId;
                demoteButton.dataset.action = "demote";

                actions.appendChild(demoteButton);
            } else {
                addProtectedMessage(actions, "Protected Super Admin account");
            }
        } else if (accountType === "Admin") {
            if (isSuperAdmin) {
                addPasswordReset(actions, account);
                addStatusButton(actions, account);

                const promoteButton = document.createElement("button");

                promoteButton.type = "button";
                promoteButton.textContent = "Promote to Super Admin";
                promoteButton.dataset.accountId = account.accountId;
                promoteButton.dataset.action = "promote";

                actions.appendChild(promoteButton);
            } else {
                addProtectedMessage(actions, "Protected Admin account");
            }
        } else if (accountType === "Staff") {
            addPasswordReset(actions, account);
            addStatusButton(actions, account);
        } else {
            addProtectedMessage(actions, "Protected account");
        }

        actionsCell.appendChild(actions);
        row.appendChild(actionsCell);

        return row;
    }

    // Display the saved Gender for every account.
    function getDisplayGender(account) {
        return account.gender || "Undefined";
    }

    function addProtectedMessage(actions, text) {
        const protectedMessage = document.createElement("span");

        protectedMessage.textContent = text;
        protectedMessage.className = "protected-account";

        actions.appendChild(protectedMessage);
    }

    function addPasswordReset(actions, account) {
        const details = document.createElement("details");
        const summary = document.createElement("summary");

        summary.textContent = "Reset password";

        const resetForm = document.createElement("form");

        resetForm.dataset.action = "reset-password";
        resetForm.dataset.accountId = account.accountId;

        const passwordInput = document.createElement("input");

        passwordInput.type = "password";
        passwordInput.name = "newPassword";
        passwordInput.placeholder = "New temporary password";
        passwordInput.minLength = 8;
        passwordInput.autocomplete = "new-password";
        passwordInput.required = true;

        passwordInput.setAttribute(
            "aria-label",
            "New temporary password"
        );

        const resetButton = document.createElement("button");

        resetButton.type = "submit";
        resetButton.textContent = "Reset";

        resetForm.append(passwordInput, resetButton);
        details.append(summary, resetForm);

        actions.appendChild(details);
    }

    function addStatusButton(actions, account) {
        const statusButton = document.createElement("button");

        statusButton.type = "button";
        statusButton.dataset.accountId = account.accountId;

        if (account.isActive) {
            statusButton.textContent = "Deactivate";
            statusButton.dataset.action = "deactivate";
        } else {
            statusButton.textContent = "Reactivate";
            statusButton.dataset.action = "reactivate";
        }

        actions.appendChild(statusButton);
    }

    function getAccountType(account) {
        if (
            account.isSuperAdmin === true ||
            account.isSuperAdmin === "true"
        ) {
            return "Admin";
        }

        return account.accountType ||
            (account.role === "Super Admin" ? "Admin" : account.role);
    }

    function getDisplayRole(account) {
        if (
            account.isSuperAdmin === true ||
            account.isSuperAdmin === "true"
        ) {
            return "Super Admin";
        }

        return account.accountType || account.role || "Unknown";
    }

    function getAccountId(account) {
        return account.id ??
            account.accountId ??
            account.userId ??
            account.user_id ??
            null;
    }

    function findAccount(accountId) {
        return accounts.find(
            item => Number(getAccountId(item)) === Number(accountId)
        );
    }

    function canManageAccount(account) {
        const accountType = getAccountType(account);

        const isOwnAccount =
            Number(getAccountId(account)) === Number(currentAccountId);

        if (isOwnAccount) {
            return false;
        }

        if (isSuperAdmin) {
            return accountType === "Staff" || accountType === "Admin";
        }

        return accountType === "Staff";
    }

    function addCell(row, value) {
        const cell = document.createElement("td");

        cell.textContent =
            value === null || value === undefined || value === ""
                ? "N/A"
                : value;

        row.appendChild(cell);
    }

    function showMessage(text, type) {
        message.textContent = text;
        message.className = type ? "message " + type : "";
    }
});
