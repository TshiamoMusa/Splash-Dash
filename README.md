# Splash & Dash Car Wash Management System

## Project Overview

Splash & Dash is a Car Wash Management System. The system consists of two connected platforms: an informational website and a native Android application.

The purpose of the system is to simplify car wash bookings, improve customer convenience and provide staff and administrators with tools to manage daily car wash operations.

Both the website and Android application are connected to the same backend API and use the same central database. This allows information, including customer accounts and bookings, to be shared between the two platforms.

## 1. System Components

### 1.1 Website

The Splash & Dash website serves as an informational platform for customers. It provides information about the car wash business, available services, company details and contact information.

Unlike the Android application, the public-facing website does not provide customer registration, customer login or customer booking management. Customers use the Android application to access these features.

The website also contains separate, restricted portals for staff and administrators to manage car wash operations.

**Public Website Features:**

- Home page with company information and an introduction to Splash & Dash.
- Information about available car wash services and packages.
- Gallery showcasing car wash services.
- Contact page with business contact information.
- Information about accessing the Android application.
- Get the App link intended to redirect customers to the Android application's Google Play Store download page.

**Get the App Link**

The website includes a Get the App link that was intended to redirect customers to the Android application's Google Play Store download page. However, the application could not be published on Google Play, so the link is currently inactive and does not redirect anywhere.

**Staff and Admin Portal Features:**

- Staff dashboard and shared service queue.
- Viewing customer and booking information.
- Updating booking and service statuses.
- Admin dashboard.
- Managing customers, bookings and staff accounts.
- Viewing administrative reports.

**Hidden Staff and Admin Login Access**

The staff and administrator login panel is intentionally hidden from the public website navigation.

To access it, authorised staff members and administrators must click the Splash & Dash logo five times. This reveals the login panel, where they can enter their account credentials.

This provides a less visible entry point to the administrative area while keeping the public website focused on customer information.

The website is developed using HTML, CSS and JavaScript. Its administrative portals communicate with the same backend API used by the Android application.

### 1.2 Android Application

The Android application provides customers with a mobile platform to access Splash & Dash services.

It is developed natively using Kotlin and Android Studio.

**Main Features:**

- Customer registration and login.
- Google Sign-In.
- Customer dashboard.
- Viewing available car wash services.
- Creating car wash bookings.
- Selecting vehicle types and entering vehicle information.
- Selecting booking dates and times.
- Calculating booking totals.
- Viewing booking history.
- Tracking booking statuses.
- Viewing and managing customer profile information.
- Payment options and payment confirmation.
- Settings and application preferences.

The Android application communicates with the same backend API used by the website. Therefore, customer accounts and booking information are not restricted to one platform.

## 2. User Roles and Access Control

The system supports four account types, each with different responsibilities and access permissions.

**Customer**

- Register and log in through the Android application.
- Manage personal profile information.
- Create and view bookings.
- View booking history and statuses.
- Access payment information.

**Staff**

- Access the hidden website login panel by clicking the company logo five times.
- Log in using an account created by an administrator.
- Access the shared service queue.
- View relevant customer and booking information.
- Update service statuses.

**Normal Admin**

- Access the hidden website login panel.
- Manage customers.
- Manage bookings.
- Manage staff accounts.
- Monitor business operations.
- Access administrative reports.

**Super Admin**

- Access the hidden website login panel.
- Has additional administrative privileges.
- Can create Super Admin, normal Admin and Staff accounts.
- Can manage administrative accounts, including other Super Admin accounts.
- Is subject to the system's maximum limit of three Super Admin accounts.

The distinction between Super Admin and normal Admin is important. Although both use the Admin role for API authentication, the system uses an additional Super Admin indicator to determine their permissions.

Normal Admin accounts cannot create or manage Super Admin accounts.

The existing account structure allows for a maximum of three Super Admin accounts, while normal Admin and Staff accounts are not subject to this limit.

## 3. Backend and Database

The website and Android application both communicate with a central backend API.

The backend is developed using ASP.NET Core Web API and C#.

**The system uses the following technologies:**

- **Backend:** ASP.NET Core Web API
- **Programming language:** C#
- **Database:** PostgreSQL
- **Database hosting:** Neon
- **API hosting:** Render
- **Android authentication integration:** Firebase for Google Sign-In
- **API authentication:** JWT-based authentication

Neon provides the hosted PostgreSQL database, which stores the system's information, including user accounts, customers, staff, services, bookings and payments.

The ASP.NET Core API handles communication between the website, Android application and database. It also handles authentication, authorization, validation and data operations.

Firebase is used for the Google Sign-In process. It is not the system's primary database or backend. The API remains responsible for verifying authentication and issuing the system's own access token.

### 3.1 Shared API and Database

Both platforms use the same API and database rather than maintaining separate customer accounts or booking records.

The Android application is the customer-facing platform for account registration, authentication and booking management. The website provides public business information and restricted staff and administrator portals.

For example, a customer can create an account and submit a booking through the Android application. Staff can then access the booking through the website's service queue and update its status. Administrators can also view and manage the relevant information through their respective portals.

All these operations use the same backend API and PostgreSQL database hosted on Neon.

## 4. Hosting and Performance Considerations

The backend API is hosted on Render, while the PostgreSQL database is hosted on Neon.

An important consideration when testing the system is that the API may take some time to respond, particularly when it has not received requests for a while.

Render's hosting environment can place an inactive service into a sleeping state. When a new request is received, the service may need time to start up again.

As a result:

- The first request may take longer than subsequent requests.
- The application may appear slow while the server is starting.
- Some screens may take additional time to retrieve information.
- Users may occasionally need to wait and retry if the server is temporarily unavailable.

This behaviour is related to the hosting environment and should not automatically be interpreted as an application or database error.

Once the API is running, requests should generally respond more quickly.

## 5. Technologies Used

| Component | Technology |
|---|---|
| Website | HTML, CSS, JavaScript |
| Android | Kotlin |
| Android Development Environment | Android Studio |
| Backend | ASP.NET Core Web API |
| Backend Language | C# |
| Database | PostgreSQL |
| Database Hosting | Neon |
| API Hosting | Render |
| Authentication | JWT and Firebase Google Sign-In |
| Android UI | XML |
| Website Styling | CSS |
| Build System | Gradle |
| Version Control | Git and GitHub |

## 6. Running the Projects

### 6.1 Website

1. Clone or download the project repository.
2. Open the project folder in Visual Studio Code.
3. Open the `splash_dash_html` folder.
4. Open `index.html` using a browser or the Visual Studio Code Live Server extension.
5. Ensure that the backend API is accessible when testing the administrative portals.

The website uses relative paths for stylesheets and images. Therefore, the existing folder structure should be maintained.

The public website can be accessed normally. To access the staff and administrator login panel, click the Splash & Dash logo five times.

Customer registration, login and booking functionality are accessed through the Android application.

### 6.2 Android Application

1. Clone or download the project repository.
2. Open Android Studio.
3. Select Open Project and choose the Android project folder.
4. Allow Android Studio to synchronise the Gradle dependencies.
5. Select an Android emulator or connect a compatible Android device.
6. Run the application using the app configuration.

The Android application requires an internet connection to communicate with the hosted API and retrieve or submit information.

## 7. Testing

The following functionality should be tested across the system.

### 7.1 Customer Testing

- Registering a customer account through the Android application.
- Logging in with valid and invalid credentials.
- Testing Google Sign-In.
- Viewing and updating profile information.
- Creating bookings.
- Selecting different services and vehicle types.
- Viewing booking history.
- Checking booking statuses.
- Accessing payment options.

### 7.2 Staff Testing

- Accessing the hidden website login panel.
- Logging in with staff credentials.
- Accessing the service queue.
- Viewing booking information.
- Updating service statuses.

### 7.3 Admin Testing

- Logging in as a normal Admin.
- Logging in as a Super Admin.
- Creating staff and administrator accounts.
- Managing customer accounts.
- Managing bookings.
- Managing staff accounts.
- Viewing reports.
- Verifying that normal Admins cannot manage Super Admin accounts.
- Verifying the maximum limit of three Super Admin accounts.

### 7.4 Integration Testing

- Creating a customer account through the Android application.
- Creating a booking through the Android application and verifying that staff can access it through the website.
- Updating booking statuses through the staff portal and verifying that the changes are reflected in the Android application.
- Checking that information is correctly retrieved from the shared API and database.
- Verifying that the hidden login panel appears after five logo clicks.
- Verifying that customers cannot access restricted staff and administrator functionality through the public website.
- Testing the system when the Render server has been inactive and needs to start up.

## 8. Security

Security is implemented through several mechanisms:

- Password hashing for stored account passwords.
- JWT-based authentication.
- Role-based authorization.
- Server-side validation.
- Protected API endpoints.
- Separate permissions for Customers, Staff, normal Admins and Super Admins.
- Secure handling of authentication tokens.
- API-based access to database information.

The five-click logo interaction is a hidden navigation mechanism and is not intended to replace authentication or authorization. Actual access to restricted functionality is controlled through the backend API and its authentication and authorization mechanisms.

Sensitive information, including passwords, API keys and database connection strings, should never be committed to the public GitHub repository.

## 9. Limitations and Considerations

The following considerations apply to the current implementation:

- API response times may be affected by Render's server startup behaviour.
- An active internet connection is required for communication with the hosted API.
- The public website is informational and does not provide customer registration, login or booking management.
- Customer registration, login and booking management are accessed through the Android application.
- The Get the App link on the website was intended to redirect customers to the application's Google Play Store download page. Since the application could not be published, the link is currently inactive and has no functionality.
- Payment options are included in the system, but payment processing should not be confused with a fully integrated third-party payment gateway.
- The YouTube demonstration video focuses on the application's interface and available features rather than demonstrating the complete live functionality of the connected system.

## 10. Project Demonstration Video

A YouTube demonstration video is available to provide a visual overview of the Splash & Dash Android application.

**Watch the demonstration:**

https://youtube.com/shorts/Vy4Up-i2g5M?si=WHnuyC-d0VB4iCUZ 

**Important:** The video demonstrates the application's user interface (UI), layout and available features. It is intended to give viewers an idea of how the application looks and what functionality it offers.

It does not demonstrate the complete working application, including its actual API communication, database operations, authentication processes or live booking workflows.

The video should therefore be treated as a visual feature demonstration rather than a recording of the application's complete operational functionality.

## 11. Project Purpose

The purpose of Splash & Dash is to provide a centralised digital solution for managing car wash operations.

By combining an informational website and an Android application with a shared backend API and database, the system provides customers with access to business information through the website and booking services through the Android application.

At the same time, staff and administrators can use the website's restricted portals to manage bookings, monitor service progress and oversee daily business operations.

The project demonstrates the application of software development concepts, database management, API integration, authentication, role-based access control and cross-platform application development in a practical business environment.
