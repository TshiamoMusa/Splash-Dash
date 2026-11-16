/*
 * Splash & Dash - shared helpers for the staff and admin website.
 * Loaded on every portal page. Exposes one global: Portal.
 *
 * The browser role check controls what is displayed.
 * Real security is enforced by [Authorize(Roles = ...)] on the API.
 */
(function () {
    "use strict";

    // Public address of the deployed API.
   var API_BASE = "https://splash-dash-api-vxly.onrender.com/api";

    // Free-tier hosts put idle servers to sleep; the first request can take
    // 30-60 seconds while the server starts. Allow for that before giving up.
    var REQUEST_TIMEOUT_MS = 60000;

    var TOKEN_KEY = "sd_token";
    var ACCOUNT_KEY = "sd_account";

    // ------------------------------------------------------------
    // Session storage helpers
    // ------------------------------------------------------------
    function getToken() {
        try {
            return sessionStorage.getItem(TOKEN_KEY);
        } catch (e) {
            return null;
        }
    }

    function getAccount() {
        try {
            return JSON.parse(sessionStorage.getItem(ACCOUNT_KEY));
        } catch (e) {
            return null;
        }
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

    // Merges updated details (e.g. after saving on the Edit Profile page)
    // into the stored account, so the welcome name stays current without
    // logging in again.
    function updateAccount(changes) {
        try {
            var account = getAccount() || {};

            sessionStorage.setItem(
                ACCOUNT_KEY,
                JSON.stringify(Object.assign(account, changes))
            );
        } catch (e) { }
    }

    // Reads the expiry time inside the JWT (no signature check - the API
    // does that). Anything unreadable is treated as expired.
    function isExpired(token) {
        try {
            var part = token.split(".")[1]
                .replace(/-/g, "+")
                .replace(/_/g, "/");

            var payload = JSON.parse(atob(part));

            return payload.exp * 1000 < Date.now();
        } catch (e) {
            return true;
        }
    }

    // ------------------------------------------------------------
    // Navigation
    // Super Admins are returned by the API with role "Admin"
    // (isSuperAdmin = true), so they share the admin dashboard.
    // ------------------------------------------------------------
    function homeFor(role) {
        return role === "Admin"
            ? "admin - dashboard.html"
            : "staff - dashboard.html";
    }

    function logout() {
        clear();
        window.location.href = "index.html";
    }

    // Call at the top of every protected page.
    //   Portal.requireRole("Staff") - Staff only
    //   Portal.requireRole("Admin") - Admin / Super Admin only
    //   Portal.requireRole()        - any signed-in staff or admin
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

    // ------------------------------------------------------------
    // API requests
    // Adds the JWT, applies a timeout and turns every failure into a
    // readable Error message that pages can show to the user.
    // ------------------------------------------------------------
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

        var controller = new AbortController();
        var timer = setTimeout(function () {
            controller.abort();
        }, REQUEST_TIMEOUT_MS);

        var res;

        try {
            res = await fetch(API_BASE + path, Object.assign({}, options, {
                headers: headers,
                signal: controller.signal
            }));
        } catch (e) {
            if (e && e.name === "AbortError") {
                throw new Error(
                    "The server is taking too long to respond. It may be starting up - please try again in a minute."
                );
            }

            throw new Error(
                "Cannot reach the server. Check your internet connection or try again shortly."
            );
        } finally {
            clearTimeout(timer);
        }

        // Gateway errors mean the host is up but the API is still starting.
        if (res.status === 502 || res.status === 503 || res.status === 504) {
            throw new Error(
                "The server is starting up. Please wait a moment and try again."
            );
        }

        var data = null;

        try {
            data = await res.json();
        } catch (e) { }

        // An expired or invalid session sends the user back to login.
        // (A failed login has no token yet, so it falls through to the
        // normal "Invalid username/email or password." message.)
        if (res.status === 401 && token) {
            clear();
            window.location.replace("login.html");

            throw new Error(
                "Your session has expired. Please log in again."
            );
        }

        if (res.status === 403) {
            throw new Error("You do not have permission to do that.");
        }

        if (!res.ok) {
            throw new Error(
                (data && data.message) ||
                "Request failed (" + res.status + ")."
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

    // ------------------------------------------------------------
    // Login
    // "identifier" is a username or an email address.
    // Customers use the mobile app, so they are refused here.
    // ------------------------------------------------------------
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
            throw new Error(
                "Customers use the Splash & Dash mobile app."
            );
        }

        save(data);

        return data.account;
    }

    // ------------------------------------------------------------
    // Shared page behaviour: welcome name and Logout links
    // ------------------------------------------------------------
    document.addEventListener("DOMContentLoaded", function () {
        var account = getAccount();

        if (account) {
            ["adminName", "staffName"].forEach(function (id) {
                var el = document.getElementById(id);

                if (el) {
                    el.textContent =
                        account.fullName || account.firstName || "";
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
        updateAccount: updateAccount,
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