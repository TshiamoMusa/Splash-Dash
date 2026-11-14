
/*
 * Splash & Dash - shared helpers for the staff and admin website.
 * Loaded on every portal page. Exposes one global: Portal.
 *
 * The browser role check controls what is displayed.
 * Real security is enforced by [Authorize(Roles = ...)] on the API.
 */
(function () {
    "use strict";

    // Uses the deployed API for both local and live website testing.
    var API_BASE = "https://splash-dash-api.onrender.com/api";

    var TOKEN_KEY = "sd_token";
    var ACCOUNT_KEY = "sd_account";

    function getToken() {
        try { return sessionStorage.getItem(TOKEN_KEY); } catch (e) { return null; }
    }

    function getAccount() {
        try { return JSON.parse(sessionStorage.getItem(ACCOUNT_KEY)); } catch (e) { return null; }
    }

    function save(data) {
        try {
            sessionStorage.setItem(TOKEN_KEY, data.token);
            sessionStorage.setItem(ACCOUNT_KEY, JSON.stringify(data.account));
        } catch (e) { }
    }

    function clear() {
        try {
            sessionStorage.removeItem(TOKEN_KEY);
            sessionStorage.removeItem(ACCOUNT_KEY);
        } catch (e) { }
    }

    function isExpired(token) {
        try {
            var part = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
            var payload = JSON.parse(atob(part));
            return payload.exp * 1000 < Date.now();
        } catch (e) {
            return true;
        }
    }

    function homeFor(role) {
        return role === "Admin" ? "admin - dashboard.html" : "staff - dashboard.html";
    }

    function logout() {
        clear();
        window.location.href = "index.html";
    }

    // Checks whether the user has permission to access a portal page.
    function requireRole(role) {
        var token = getToken();
        var account = getAccount();

        if (!token || !account || isExpired(token)) {
            clear();
            window.location.replace("login.html");
            return false;
        }

        if (role && account.role !== role) {
            window.location.replace(homeFor(account.role));
            return false;
        }

        return true;
    }

    // Sends requests to the deployed API.
    async function request(path, options) {
        options = options || {};

        var headers = Object.assign(
            { "Content-Type": "application/json" },
            options.headers || {}
        );

        var token = getToken();

        if (token) {
            headers.Authorization = "Bearer " + token;
        }

        var res;

        try {
            res = await fetch(API_BASE + path, Object.assign({}, options, {
                headers: headers
            }));
        } catch (e) {
            throw new Error("Cannot reach the server. Check your internet connection or API availability.");
        }

        var data = null;

        try {
            data = await res.json();
        } catch (e) { }

        if (res.status === 401 && token) {
            clear();
            window.location.replace("login.html");
            throw new Error("Your session has expired. Please log in again.");
        }

        if (res.status === 403) {
            throw new Error("You do not have permission to do that.");
        }

        if (!res.ok) {
            throw new Error(
                (data && data.message) || "Request failed (" + res.status + ")."
            );
        }

        return data;
    }

    function get(path) {
        return request(path);
    }

    function post(path, body) {
        return request(path, {
            method: "POST",
            body: JSON.stringify(body || {})
        });
    }

    function put(path, body) {
        return request(path, {
            method: "PUT",
            body: JSON.stringify(body || {})
        });
    }

    // Logs in admin and staff accounts.
    async function login(identifier, password) {
        var data = await request("/auth/login", {
            method: "POST",
            body: JSON.stringify({
                identifier: identifier,
                password: password
            })
        });

        var role = data.account.role;

        if (role !== "Admin" && role !== "Staff") {
            throw new Error("Customers use the Splash & Dash mobile app.");
        }

        save(data);
        return data.account;
    }

    // Displays the welcome name and handles logout links.
    document.addEventListener("DOMContentLoaded", function () {
        var account = getAccount();

        if (account) {
            ["adminName", "staffName"].forEach(function (id) {
                var el = document.getElementById(id);

                if (el) {
                    el.textContent = account.fullName || account.firstName;
                }
            });
        }

        var links = document.querySelectorAll("a");

        links.forEach(function (link) {
            if (link.textContent.trim() === "Logout") {
                link.addEventListener("click", function (event) {
                    event.preventDefault();
                    logout();
                });
            }
        });
    });

    window.Portal = {
        API_BASE: API_BASE,
        getAccount: getAccount,
        homeFor: homeFor,
        requireRole: requireRole,
        request: request,
        get: get,
        post: post,
        put: put,
        login: login,
        logout: logout
    };

})();
