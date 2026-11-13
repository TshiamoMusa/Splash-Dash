package com.example.splashanddash

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

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
    val totalAmount: Double,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val staffName: String? = null
)

object ApiClient {
    private const val TAG = "SplashDashApi"
    // Use 10.0.2.2 for Android emulator to reach host machine localhost:5160
    private const val BASE_URL = "http://10.0.2.2:5160/api"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences("SplashDashPrefs", Context.MODE_PRIVATE)
    }

    fun getToken(context: Context): String? {
        return getPrefs(context).getString("auth_token", null)
    }

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

    fun clearSession(context: Context) {
        getPrefs(context).edit().clear().apply()
        AppData.signOut()
    }

    suspend fun login(
        context: Context,
        identifier: String,
        password: String
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/Auth/login")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
            }

            val jsonBody = JSONObject().apply {
                put("identifier", identifier.trim())
                put("password", password)
            }

            conn.outputStream.use { os ->
                os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseString = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            val jsonResponse = JSONObject(responseString)

            if (responseCode in 200..299) {
                val token = jsonResponse.getString("token")
                val account = jsonResponse.getJSONObject("account")
                val role = account.getString("role")
                val name = account.getString("fullName")
                val userId = account.getInt("id")

                saveSession(context, token, role, name, userId)
                Result.success(jsonResponse)
            } else {
                Result.failure(Exception(jsonResponse.optString("message", "Login failed.")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login error", e)
            Result.failure(Exception("Could not connect to server: ${e.localizedMessage}"))
        }
    }

    suspend fun googleLogin(
        context: Context,
        email: String,
        name: String,
        firebaseUid: String,
        phoneNumber: String = "",
        address: String = ""
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/Auth/google-login")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
            }

            val jsonBody = JSONObject().apply {
                put("email", email.trim())
                put("name", name.trim())
                put("firebaseUid", firebaseUid)
                put("phoneNumber", phoneNumber)
                put("address", address)
            }

            conn.outputStream.use { os ->
                os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseString = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            val jsonResponse = JSONObject(responseString)

            if (responseCode in 200..299) {
                val token = jsonResponse.getString("token")
                val account = jsonResponse.getJSONObject("account")
                val role = account.getString("role")
                val userName = account.getString("fullName")
                val userId = account.getInt("id")

                saveSession(context, token, role, userName, userId)
                Result.success(jsonResponse)
            } else {
                Result.failure(Exception(jsonResponse.optString("message", "Google login failed.")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google login error", e)
            Result.failure(Exception("Could not connect to server: ${e.localizedMessage}"))
        }
    }

    suspend fun registerCustomer(
        context: Context,
        firstName: String,
        surname: String,
        email: String,
        phoneNumber: String,
        homeAddress: String,
        password: String
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/Customers/register")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
            }

            val jsonBody = JSONObject().apply {
                put("firstName", firstName.trim())
                put("surname", surname.trim())
                put("email", email.trim())
                put("phoneNumber", phoneNumber.trim())
                put("homeAddress", homeAddress.trim())
                put("password", password)
            }

            conn.outputStream.use { os ->
                os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseString = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            val jsonResponse = JSONObject(responseString)

            if (responseCode in 200..299) {
                Result.success(jsonResponse)
            } else {
                Result.failure(Exception(jsonResponse.optString("message", "Registration failed.")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Register error", e)
            Result.failure(Exception("Could not connect to server: ${e.localizedMessage}"))
        }
    }

    suspend fun getServices(): Result<List<ServiceItem>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/Services")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseString = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            if (responseCode in 200..299) {
                val jsonArray = JSONArray(responseString)
                val list = mutableListOf<ServiceItem>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        ServiceItem(
                            serviceId = obj.getInt("serviceId"),
                            serviceName = obj.getString("serviceName"),
                            description = obj.optString("description", ""),
                            price = obj.getDouble("price")
                        )
                    )
                }
                Result.success(list)
            } else {
                Result.failure(Exception("Could not load services."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get services error", e)
            Result.failure(Exception("Could not connect to server: ${e.localizedMessage}"))
        }
    }

    suspend fun createBooking(
        context: Context,
        serviceId: Int,
        vehicleType: String,
        bookingDateTime: String, // ISO format e.g. 2026-06-01T10:30:00Z
        locationType: String,
        bookingAddress: String?
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val token = getToken(context)
            val url = URL("$BASE_URL/Bookings")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
            }

            val jsonBody = JSONObject().apply {
                put("serviceId", serviceId)
                put("vehicleType", vehicleType)
                put("bookingDateTime", bookingDateTime)
                put("locationType", locationType)
                if (!bookingAddress.isNullOrBlank()) {
                    put("bookingAddress", bookingAddress)
                }
            }

            conn.outputStream.use { os ->
                os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseString = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            val jsonResponse = JSONObject(responseString)

            if (responseCode in 200..299) {
                Result.success(jsonResponse)
            } else {
                Result.failure(Exception(jsonResponse.optString("message", "Could not create booking.")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Create booking error", e)
            Result.failure(Exception("Could not connect to server: ${e.localizedMessage}"))
        }
    }

    suspend fun getCustomerBookings(context: Context): Result<List<BookingItem>> = withContext(Dispatchers.IO) {
        try {
            val token = getToken(context)
            val url = URL("$BASE_URL/Bookings/my-bookings")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                connectTimeout = 10000
                readTimeout = 10000
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseString = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            if (responseCode in 200..299) {
                val jsonArray = JSONArray(responseString)
                val list = mutableListOf<BookingItem>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        BookingItem(
                            bookingId = obj.getInt("bookingId"),
                            vehicleType = obj.getString("vehicleType"),
                            service = obj.getString("service"),
                            bookingDateTime = obj.getString("bookingDateTime"),
                            locationType = obj.getString("locationType"),
                            bookingAddress = obj.optString("bookingAddress", null),
                            bookingStatus = obj.getString("bookingStatus"),
                            totalAmount = obj.getDouble("totalAmount")
                        )
                    )
                }
                Result.success(list)
            } else {
                val jsonErr = JSONObject(responseString)
                Result.failure(Exception(jsonErr.optString("message", "Could not load bookings.")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get customer bookings error", e)
            Result.failure(Exception("Could not connect to server: ${e.localizedMessage}"))
        }
    }

    suspend fun getAllBookings(context: Context): Result<List<BookingItem>> = withContext(Dispatchers.IO) {
        try {
            val token = getToken(context)
            val url = URL("$BASE_URL/Bookings/all")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                connectTimeout = 10000
                readTimeout = 10000
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseString = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            if (responseCode in 200..299) {
                val jsonArray = JSONArray(responseString)
                val list = mutableListOf<BookingItem>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        BookingItem(
                            bookingId = obj.getInt("bookingId"),
                            vehicleType = obj.getString("vehicleType"),
                            service = obj.getString("service"),
                            bookingDateTime = obj.getString("bookingDateTime"),
                            locationType = obj.getString("locationType"),
                            bookingAddress = obj.optString("bookingAddress", null),
                            bookingStatus = obj.getString("bookingStatus"),
                            totalAmount = obj.getDouble("totalAmount"),
                            customerName = obj.optString("customerName", null),
                            customerPhone = obj.optString("customerPhone", null),
                            staffName = obj.optString("staffName", null)
                        )
                    )
                }
                Result.success(list)
            } else {
                val jsonErr = JSONObject(responseString)
                Result.failure(Exception(jsonErr.optString("message", "Could not load all bookings.")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get all bookings error", e)
            Result.failure(Exception("Could not connect to server: ${e.localizedMessage}"))
        }
    }

    suspend fun updateBookingStatus(
        context: Context,
        bookingId: Int,
        status: String
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val token = getToken(context)
            val url = URL("$BASE_URL/Bookings/$bookingId/status")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                setRequestProperty("Content-Type", "application/json")
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
            }

            val jsonBody = JSONObject().apply {
                put("status", status)
            }

            conn.outputStream.use { os ->
                os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseString = BufferedReader(InputStreamReader(stream)).use { it.readText() }

            val jsonResponse = JSONObject(responseString)

            if (responseCode in 200..299) {
                Result.success(jsonResponse)
            } else {
                Result.failure(Exception(jsonResponse.optString("message", "Could not update booking status.")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update booking status error", e)
            Result.failure(Exception("Could not connect to server: ${e.localizedMessage}"))
        }
    }
}
