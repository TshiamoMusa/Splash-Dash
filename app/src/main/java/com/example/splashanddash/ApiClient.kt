package com.example.splashanddash

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------
// Data models (match the JSON returned by SplashDash.Api)
// ---------------------------------------------------------

data class ServiceItem(
    val serviceId: Int,
    val serviceName: String,
    val description: String?,
    val price: Double
)

data class BookingItem(
    val bookingId: Int,
    val vehicleType: String,
    val service: String,
    val bookingDateTime: String,
    val locationType: String,
    val bookingAddress: String?,
    val bookingStatus: String,
    val totalAmount: Double
)

data class CustomerProfile(
    val firstName: String,
    val surname: String,
    val email: String,
    val phoneNumber: String,
    val homeAddress: String
)

/**
 * Talks to the same Splash & Dash API (and database) as the website.
 * This app is for customers only, so staff/admin calls are not included.
 */
object ApiClient {

    // Tag used for every log line in this file (filter Logcat by "SplashDashApi")
    private const val TAG = "SplashDashApi"

    // Live Render backend (same one the website uses).
    private const val BASE_URL = "https://splash-dash-api-vxly.onrender.com/api"

    // Render's free tier sleeps when idle; the first request can take 30-60 seconds.
    private const val TIMEOUT_MS = 60_000

    // Exact values the API accepts.
    val VEHICLE_TYPES = listOf("Small Car", "Large Car", "SUV", "Bakkie")
    const val LOCATION_ON_SITE = "On-site"
    const val LOCATION_HOUSE_CALL = "House Call"

    // Friendly messages shown to the user
    private const val MSG_STARTING_UP =
        "The server is starting up. Please wait a moment and try again."
    private const val MSG_TIMEOUT =
        "The server is taking too long to respond. It may be starting up. Please try again in a minute."
    private const val MSG_OFFLINE =
        "Cannot reach the server. Check your internet connection or try again shortly."
    private const val MSG_SESSION_EXPIRED =
        "Your session has expired. Please log in again."

    /** Raw HTTP answer: status code + body text. */
    private class ApiResponse(val code: Int, val body: String) {
        val isOk: Boolean get() = code in 200..299
    }


    // ---------------------------------------------------------
    // Session (JWT saved on the phone)
    // ---------------------------------------------------------

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences("SplashDashPrefs", Context.MODE_PRIVATE)
    }

    /** Returns the saved token, or null if there is none or it has expired. */
    fun getToken(context: Context): String? {
        val token = getPrefs(context).getString("auth_token", null) ?: return null

        // An expired token is wiped so the app treats the user as logged out.
        if (isExpired(token)) {
            Log.d(TAG, "Saved token is expired or unreadable. Clearing session.")
            clearSession(context)
            return null
        }
        return token
    }

    /** Stores the token and basic account info after a successful login. */
    fun saveSession(context: Context, token: String, role: String, name: String, userId: Int) {
        getPrefs(context).edit()
            .putString("auth_token", token)
            .putString("user_role", role)
            .putString("user_name", name)
            .putInt("user_id", userId)
            .apply()

        AppData.currentUser = name
        AppData.role = role
    }

    /** Removes everything saved for the user (used for log out and expired sessions). */
    fun clearSession(context: Context) {
        getPrefs(context).edit().clear().apply()
        AppData.signOut()
    }

    /**
     * Reads the expiry time (exp) inside the JWT.
     *  - No exp claim  -> treated as still valid (the server decides).
     *  - Unreadable    -> treated as expired.
     * Note: the phone's clock is used, so a wrong emulator date can make a fresh
     * token look expired. Check the emulator's date and time if login keeps bouncing.
     */
    private fun isExpired(token: String): Boolean {
        return try {
            val payload = String(
                Base64.decode(
                    token.split(".")[1],
                    // JWTs use URL-safe base64 with no padding
                    Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                ),
                Charsets.UTF_8
            )
            val json = JSONObject(payload)

            if (!json.has("exp")) return false

            val expiresAtMs = json.getLong("exp") * 1000
            val now = System.currentTimeMillis()
            Log.d(TAG, "Token expires at $expiresAtMs, phone time is $now")
            expiresAtMs < now
        } catch (e: Exception) {
            Log.e(TAG, "Could not read token expiry", e)
            true
        }
    }


    // ---------------------------------------------------------
    // Shared request helpers
    // ---------------------------------------------------------

    /** Turns the response text into a JSONObject, even if the server sent plain text. */
    private fun parseJsonSafe(text: String): JSONObject {
        val trimmed = text.trim()
        return when {
            trimmed.startsWith("{") -> JSONObject(trimmed)
            trimmed.isEmpty() -> JSONObject()
            else -> JSONObject().apply { put("message", trimmed) }
        }
    }

    /** Turns the response text into a JSONArray (empty if it is not an array). */
    private fun parseArray(text: String): JSONArray {
        val trimmed = text.trim()
        return if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONArray()
    }

    /** JSON null becomes Kotlin null (org.json would otherwise return the text "null"). */
    private fun JSONObject.stringOrNull(name: String): String? {
        return if (isNull(name)) null else getString(name)
    }

    /**
     * Sends one HTTP request on a background thread and returns the raw response.
     * Network problems and server start-up delays are turned into friendly messages.
     */
    private suspend fun send(
        context: Context?,
        method: String,
        path: String,
        body: JSONObject? = null,
        authenticated: Boolean = false
    ): Result<ApiResponse> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val connection = URL("$BASE_URL$path").openConnection() as HttpURLConnection
            conn = connection

            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")

            // Logged-in calls send the saved token
            if (authenticated && context != null) {
                val token = getToken(context)
                if (!token.isNullOrBlank()) {
                    connection.setRequestProperty("Authorization", "Bearer $token")
                }
            }

            // Send the JSON body, if there is one
            if (body != null) {
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }

            val code = connection.responseCode
            Log.d(TAG, "$method $path -> HTTP $code")

            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""

            when {
                // Render is waking up
                code == 502 || code == 503 || code == 504 ->
                    Result.failure(Exception(MSG_STARTING_UP))

                // Token rejected on a logged-in call: wipe it so the user logs in again
                authenticated && code == 401 -> {
                    if (context != null) clearSession(context)
                    Result.failure(Exception(MSG_SESSION_EXPIRED))
                }

                else -> Result.success(ApiResponse(code, text))
            }
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Timeout: $method $path", e)
            Result.failure(Exception(MSG_TIMEOUT))
        } catch (e: IOException) {
            Log.e(TAG, "Network error: $method $path", e)
            Result.failure(Exception(MSG_OFFLINE))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: $method $path", e)
            Result.failure(Exception("Something went wrong: ${e.localizedMessage}"))
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Sends a request and returns the JSON answer.
     * On an error status, the API's own "message" is used as the error text.
     */
    private suspend fun callJson(
        context: Context?,
        method: String,
        path: String,
        body: JSONObject?,
        authenticated: Boolean,
        errorFallback: String
    ): Result<JSONObject> {
        val res = send(context, method, path, body, authenticated)
            .getOrElse { return Result.failure(it) }

        if (res.isOk) return Result.success(parseJsonSafe(res.body))

        val message = if (res.code == 403) {
            "You do not have permission to do that."
        } else {
            parseJsonSafe(res.body).optString("message", errorFallback)
        }

        // The status code is added so a failed login shows exactly what the server said
        return Result.failure(Exception("$message (HTTP ${res.code})"))
    }

    /** Saves the session only for customer accounts; staff/admin use the website. */
    private fun startCustomerSession(
        context: Context,
        json: JSONObject,
        fallbackName: String
    ): Result<JSONObject> {
        val token = json.optString("token", "")
        val account = json.optJSONObject("account")

        // Shows what the server returned (safe: the token itself is not logged)
        Log.d(
            TAG,
            "Login response keys=${json.keys().asSequence().toList()} " +
                    "hasToken=${token.isNotBlank()} role=${account?.optString("role")}"
        )

        if (token.isBlank() || account == null) {
            return Result.failure(Exception("Login failed. The server did not return a token."))
        }

        if (account.optString("role", "") != "Customer") {
            return Result.failure(
                Exception("Staff and admin accounts use the Splash & Dash website. Please log in there.")
            )
        }

        saveSession(
            context,
            token,
            "Customer",
            account.optString("fullName", fallbackName),
            account.optInt("id", 0)
        )
        return Result.success(json)
    }

    /** Reads a date returned by the API, e.g. 2026-11-20T08:00:00+00:00 or ...Z. */
    fun parseApiDate(text: String): Date? {
        val cleaned = text.trim().replace(Regex("\\.\\d+"), "")
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(cleaned)
        } catch (e: Exception) {
            null
        }
    }

    /** Friendly local date/time for showing in the app, e.g. Fri, 20 Nov 2026 · 10:00. */
    fun displayDate(text: String): String {
        val date = parseApiDate(text) ?: return text
        return SimpleDateFormat("EEE, d MMM yyyy · HH:mm", Locale.getDefault()).format(date)
    }

    /** Formats a date the way the API expects, e.g. 2026-11-20T10:00:00+02:00. */
    fun formatForApi(date: Date): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(date)
    }


    // ---------------------------------------------------------
    // Account
    // ---------------------------------------------------------

    /** Email + password login. The API accepts an email (or a username for staff). */
    suspend fun login(
        context: Context,
        identifier: String,
        password: String
    ): Result<JSONObject> {
        val body = JSONObject().apply {
            put("identifier", identifier.trim())
            put("password", password)
        }

        return callJson(context, "POST", "/Auth/login", body, false, "Login failed.")
            .fold(
                onSuccess = { startCustomerSession(context, it, "Customer") },
                onFailure = { Result.failure(it) }
            )
    }

    /** Google Sign-In: tells the API about the Firebase user so it issues a token. */
    suspend fun googleLogin(
        context: Context,
        email: String,
        name: String,
        firebaseUid: String,
        phoneNumber: String = "",
        address: String = ""
    ): Result<JSONObject> {
        val body = JSONObject().apply {
            put("email", email.trim())
            put("name", name.trim())
            put("firebaseUid", firebaseUid)
            put("phoneNumber", phoneNumber)
            put("address", address)
        }

        return callJson(context, "POST", "/Auth/google-login", body, false, "Google login failed.")
            .fold(
                onSuccess = { startCustomerSession(context, it, name) },
                onFailure = { Result.failure(it) }
            )
    }

    /** Creates a customer account. Field names match RegisterCustomerRequest in the API. */
    suspend fun registerCustomer(
        context: Context,
        firstName: String,
        surname: String,
        email: String,
        phoneNumber: String,
        homeAddress: String,
        password: String
    ): Result<JSONObject> {
        val body = JSONObject().apply {
            put("firstName", firstName.trim())
            put("surname", surname.trim())
            put("email", email.trim())
            put("phoneNumber", phoneNumber.trim())
            put("homeAddress", homeAddress.trim())
            put("password", password)
        }

        return callJson(context, "POST", "/Customers/register", body, false, "Registration failed.")
    }

    /** Loads the logged-in customer's profile. */
    suspend fun getProfile(context: Context): Result<CustomerProfile> {
        return callJson(context, "GET", "/Customers/profile", null, true, "Could not load your profile.")
            .map { obj ->
                CustomerProfile(
                    firstName = obj.optString("firstName", ""),
                    surname = obj.optString("surname", ""),
                    email = obj.optString("email", ""),
                    phoneNumber = obj.optString("phoneNumber", ""),
                    homeAddress = obj.optString("homeAddress", "")
                )
            }
    }

    /** Updates the saved home address. */
    suspend fun updateAddress(context: Context, homeAddress: String): Result<JSONObject> {
        val body = JSONObject().apply { put("homeAddress", homeAddress.trim()) }
        return callJson(context, "PUT", "/Customers/address", body, true, "Could not update your address.")
    }


    // ---------------------------------------------------------
    // Services & bookings
    // ---------------------------------------------------------

    /** Loads the list of services (public, no login needed). */
    suspend fun getServices(): Result<List<ServiceItem>> {
        val res = send(null, "GET", "/Services")
            .getOrElse { return Result.failure(it) }

        if (!res.isOk) return Result.failure(Exception("Could not load services."))

        return try {
            val array = parseArray(res.body)
            val list = (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                ServiceItem(
                    serviceId = obj.getInt("serviceId"),
                    serviceName = obj.getString("serviceName"),
                    description = obj.stringOrNull("description"),
                    price = obj.getDouble("price")
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Could not read services", e)
            Result.failure(Exception("Could not load services."))
        }
    }

    /**
     * bookingDateTime must include the time zone, e.g. from formatForApi().
     * vehicleType must be one of VEHICLE_TYPES; locationType LOCATION_ON_SITE or LOCATION_HOUSE_CALL.
     */
    suspend fun createBooking(
        context: Context,
        serviceId: Int,
        vehicleType: String,
        bookingDateTime: String,
        locationType: String,
        bookingAddress: String?
    ): Result<JSONObject> {
        val body = bookingBody(serviceId, vehicleType, bookingDateTime, locationType, bookingAddress)
        return callJson(context, "POST", "/Bookings", body, true, "Could not create booking.")
    }

    /** Loads every booking that belongs to the logged-in customer. */
    suspend fun getCustomerBookings(context: Context): Result<List<BookingItem>> {
        val res = send(context, "GET", "/Bookings/my-bookings", null, true)
            .getOrElse { return Result.failure(it) }

        if (!res.isOk) {
            return Result.failure(
                Exception(parseJsonSafe(res.body).optString("message", "Could not load bookings."))
            )
        }

        return try {
            val array = parseArray(res.body)
            val list = (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                BookingItem(
                    bookingId = obj.getInt("bookingId"),
                    vehicleType = obj.getString("vehicleType"),
                    service = obj.getString("service"),
                    bookingDateTime = obj.getString("bookingDateTime"),
                    locationType = obj.getString("locationType"),
                    bookingAddress = obj.stringOrNull("bookingAddress"),
                    bookingStatus = obj.getString("bookingStatus"),
                    totalAmount = obj.getDouble("totalAmount")
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Could not read bookings", e)
            Result.failure(Exception("Could not load bookings."))
        }
    }

    /** Only Pending bookings can be edited (API rule). */
    suspend fun updateBooking(
        context: Context,
        bookingId: Int,
        serviceId: Int,
        vehicleType: String,
        bookingDateTime: String,
        locationType: String,
        bookingAddress: String?
    ): Result<JSONObject> {
        val body = bookingBody(serviceId, vehicleType, bookingDateTime, locationType, bookingAddress)
        return callJson(context, "PUT", "/Bookings/$bookingId", body, true, "Could not update booking.")
    }

    /** Only Pending or Confirmed bookings can be cancelled (API rule). */
    suspend fun cancelBooking(context: Context, bookingId: Int): Result<JSONObject> {
        return callJson(context, "PUT", "/Bookings/$bookingId/cancel", null, true, "Could not cancel booking.")
    }

    /** Builds the JSON body shared by create and update booking. */
    private fun bookingBody(
        serviceId: Int,
        vehicleType: String,
        bookingDateTime: String,
        locationType: String,
        bookingAddress: String?
    ): JSONObject {
        return JSONObject().apply {
            put("serviceId", serviceId)
            put("vehicleType", vehicleType)
            put("bookingDateTime", bookingDateTime)
            put("locationType", locationType)
            if (!bookingAddress.isNullOrBlank()) put("bookingAddress", bookingAddress)
        }
    }


    // ---------------------------------------------------------
    // Payments
    // ---------------------------------------------------------

    /** Pays for a booking. paymentMethod must be PayFast, EFT or Cash. */
    suspend fun makePayment(
        context: Context,
        bookingId: Int,
        paymentMethod: String
    ): Result<JSONObject> {
        val body = JSONObject().apply {
            put("bookingId", bookingId)
            put("paymentMethod", paymentMethod)
        }
        return callJson(context, "POST", "/Payments", body, true, "Payment processing failed.")
    }
}