# Implementation Plan - Android API Integration & PostgreSQL Backend

Connect the Android application to the ASP.NET Core backend API and shared PostgreSQL database, remove Firebase Firestore and email/password authentication (keeping Firebase Auth strictly for Google sign-in), and connect all functionality (services, bookings, authentication, status updates).

## User Review Required

> [!IMPORTANT]
> - Firebase email/password authentication and Cloud Firestore dependencies are being completely removed from the Android app.
> - Firebase Auth will be retained solely for Google sign-in authentication, with credentials synchronized to the PostgreSQL database via a new backend API endpoint.
> - The Android app will communicate with the ASP.NET Core API running at `http://10.0.2.2:5160/api/` (for Android emulators).

## Proposed Changes

### Backend API (`SplashDash.Api`)

#### [NEW] [ServicesController.cs](file:///C:/Users/Student/AndroidStudioProjects/Splash-Dash-mine-/Controllers/ServicesController.cs)
- Implement `GET /api/services` endpoint to return active car wash services, descriptions, and prices from PostgreSQL.

#### [MODIFY] [AuthController.cs](file:///C:/Users/Student/AndroidStudioProjects/Splash-Dash-mine-/Controllers/AuthController.cs)
- Add a Google login/sync endpoint (`POST /api/Auth/google-login`) to support authenticating Google Sign-In users against PostgreSQL and issuing JWT tokens.

#### [MODIFY] [schema.sql](file:///C:/Users/Student/AndroidStudioProjects/Splash-Dash-mine-/schema.sql)
- Seed default car wash services with active status and standard pricing (e.g., Exterior, Interior, Full Detail).

---

### Android Application (`app`)

#### [MODIFY] [build.gradle](file:///C:/Users/Student/AndroidStudioProjects/Splash-Dash-mine-/app/build.gradle)
- Remove `com.google.firebase:firebase-firestore`. Retain `com.google.firebase:firebase-auth` strictly for Google sign-in.

#### [NEW] [ApiClient.kt](file:///C:/Users/Student/AndroidStudioProjects/Splash-Dash-mine-/app/src/main/java/com/example/splashanddash/ApiClient.kt)
- Implement API communication helper using `HttpURLConnection` and Coroutines for:
  - Customer registration (`/api/Customers/register`)
  - Login (`/api/Auth/login`, `/api/Auth/google-login`)
  - Fetching services (`/api/services`)
  - Creating bookings (`/api/Bookings`)
  - Viewing customer bookings (`/api/Bookings/my-bookings`)
  - Viewing all bookings for staff/admin (`/api/Bookings/all`)
  - Updating booking status (`/api/Bookings/{id}/status`)
  - Managing customer profile & address (`/api/Customers/profile`, `/api/Customers/address`)

#### [MODIFY] [LoginActivity.kt](file:///C:/Users/Student/AndroidStudioProjects/Splash-Dash-mine-/app/src/main/java/com/example/splashanddash/LoginActivity.kt)
- Update login flow to call `ApiClient.login(...)` instead of Firebase email/password auth. Keep Google sign-in using Firebase Auth + API synchronization.

#### [MODIFY] [RegisterActivity.kt](file:///C:/Users/Student/AndroidStudioProjects/Splash-Dash-mine-/app/src/main/java/com/example/splashanddash/RegisterActivity.kt)
- Update registration flow to call `ApiClient.registerCustomer(...)`.

#### [MODIFY] [BookingActivity.kt](file:///C:/Users/Student/AndroidStudioProjects/Splash-Dash-mine-/app/src/main/java/com/example/splashanddash/BookingActivity.kt)
- Fetch services dynamically from `ApiClient.getServices()` and submit bookings to `ApiClient.createBooking(...)`.

#### [MODIFY] [BookingHistoryActivity.kt](file:///C:/Users/Student/AndroidStudioProjects/Splash-Dash-mine-/app/src/main/java/com/example/splashanddash/BookingHistoryActivity.kt)
- Load customer bookings from `ApiClient.getCustomerBookings(...)`.

#### [MODIFY] [ServiceQueueActivity.kt](file:///C:/Users/Student/AndroidStudioProjects/Splash-Dash-mine-/app/src/main/java/com/example/splashanddash/ServiceQueueActivity.kt) & [ManageBookingsActivity.kt](file:///C:/Users/Student/AndroidStudioProjects/Splash-Dash-mine-/app/src/main/java/com/example/splashanddash/ManageBookingsActivity.kt)
- Load all bookings from `ApiClient.getAllBookings()` and update booking status via `ApiClient.updateBookingStatus(...)`.

#### [DELETE] [FirestoreBookingRepository.kt](file:///C:/Users/Student/AndroidStudioProjects/Splash-Dash-mine-/app/src/main/java/com/example/splashanddash/FirestoreBookingRepository.kt) & [FirebaseAccountManager.kt](file:///C:/Users/Student/AndroidStudioProjects/Splash-Dash-mine-/app/src/main/java/com/example/splashanddash/FirebaseAccountManager.kt)
- Remove Firestore repository and Firebase account manager (email/password auth).

## Verification Plan

### Automated Tests
- Run Gradle build (`app:assembleDebug`) to verify compilation and dependencies.

### Manual Verification
- Test customer registration, login, viewing services, creating bookings, viewing booking history, and staff/admin status updates against PostgreSQL database and website integration.
