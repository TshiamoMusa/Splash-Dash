/*
 * Splash & Dash - shared helpers for the staff and admin website.
 * Loaded on every portal page. Exposes one global: Portal.
 *
 * Note: the role check here only controls what the browser SHOWS.
 * The real protection is [Authorize(Roles = ...)] on the API controllers.
 */
(function () {
    "use strict";

    var API_BASE = "http://localhost:5160/api";
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
        } catch (e) { /* storage blocked: the user will be asked to log in again */ }
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

    // Sends the visitor to login.html unless a valid session exists.
    // Pass "Admin" or "Staff" to restrict the page, or nothing to allow both.
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

    // Calls the API. Resolves with the parsed JSON, rejects with an Error whose
    // message is safe to show to the user.
    async function request(path, options) {
        options = options || {};
        var headers = Object.assign({ "Content-Type": "application/json" }, options.headers || {});
        var token = getToken();
        if (token) headers.Authorization = "Bearer " + token;

        var res;
        try {
            res = await fetch(API_BASE + path, Object.assign({}, options, { headers: headers }));
        } catch (e) {
            throw new Error("Cannot reach the server. Check that the API is running.");
        }

        var data = null;
        try { data = await res.json(); } catch (e) { }

        if (res.status === 401 && token) {
            // Session expired or token rejected.
            clear();
            window.location.replace("login.html");
            throw new Error("Your session has expired. Please log in again.");
        }

        if (res.status === 403) {
            throw new Error("You do not have permission to do that.");
        }

        if (!res.ok) {
            throw new Error((data && data.message) || "Request failed (" + res.status + ").");
        }

        return data;
    }

    function get(path) {
        return request(path);
    }

    function post(path, body) {
        return request(path, { method: "POST", body: JSON.stringify(body || {}) });
    }

    function put(path, body) {
        return request(path, { method: "PUT", body: JSON.stringify(body || {}) });
    }

    // Logs in a staff or admin account. Customers are refused: they use the app.
    async function login(identifier, password) {
        var data = await request("/auth/login", {
            method: "POST",
            body: JSON.stringify({ identifier: identifier, password: password })
        });

        var role = data.account.role;
        if (role !== "Admin" && role !== "Staff") {
            throw new Error("Customers use the Splash & Dash mobile app.");
        }

        save(data);
        return data.account;
    }

    // Fills in the welcome name and makes every "Logout" link end the session.
    document.addEventListener("DOMContentLoaded", function () {
        var account = getAccount();

        if (account) {
            ["adminName", "staffName"].forEach(function (id) {
                var el = document.getElementById(id);
                if (el) el.textContent = account.fullName || account.firstName;
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