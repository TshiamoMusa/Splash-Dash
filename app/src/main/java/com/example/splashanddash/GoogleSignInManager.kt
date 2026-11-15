package com.example.splashanddash

import android.app.Activity
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

object GoogleSignInManager {

    private const val TAG = "SplashDashGoogle"

    suspend fun signIn(
        activity: Activity,
        onResult: (Boolean, String) -> Unit
    ) {
        try {
            // Retrieve the Web Client ID from google-services.json values
            val serverClientId = try {
                activity.getString(R.string.default_web_client_id)
            } catch (e: Exception) {
                Log.e(TAG, "default_web_client_id string resource missing", e)
                onResult(false, "Google Sign-In configuration missing. Please use email/password.")
                return
            }

            // Configure Google ID option for Credential Manager
            val googleOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false) // Set to false so users can pick any account on the device
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build()

            val credentialManager = CredentialManager.create(activity)

            // Request credentials via Android Credential Manager
            val result = credentialManager.getCredential(
                context = activity,
                request = request
            )

            val credential = result.credential

            if (
                credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                onResult(false, "Unsupported Google credential type received.")
                return
            }

            // Extract Google ID token
            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleCredential.idToken

            if (idToken.isBlank()) {
                onResult(false, "Failed to retrieve Google ID token.")
                return
            }

            // Authenticate with Firebase using the Google ID token
            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)

            FirebaseAuth.getInstance()
                .signInWithCredential(firebaseCredential)
                .addOnSuccessListener { authResult ->
                    val user = authResult.user
                    if (user == null) {
                        onResult(false, "Google authentication failed on Firebase.")
                    } else {
                        Log.d(TAG, "Google authentication successful: ${user.email}")
                        onResult(true, "Google login successful.")
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Firebase Google authentication failed", exception)
                    onResult(false, exception.localizedMessage ?: "Firebase Google sign-in failed.")
                }

        } catch (e: GetCredentialException) {
            // Handles user cancellation or system credential manager errors gracefully
            Log.w(TAG, "GetCredentialException during Google sign-in: ${e.type} - ${e.localizedMessage}")
            val message = if (e.localizedMessage?.contains("cancelled", true) == true) {
                "Google Sign-In was cancelled."
            } else {
                e.localizedMessage ?: "Google Sign-In failed."
            }
            onResult(false, message)
        } catch (exception: Exception) {
            Log.e(TAG, "Unexpected Google sign-in error", exception)
            onResult(false, exception.localizedMessage ?: "Google Sign-In failed unexpectedly.")
        }
    }
}