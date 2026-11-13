package com.example.splashanddash

import android.app.Activity
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
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
            val googleOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(
                    activity.getString(R.string.default_web_client_id)
                )
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build()

            val credentialManager =
                CredentialManager.create(activity)

            val result = credentialManager.getCredential(
                context = activity,
                request = request
            )

            val credential = result.credential

            if (
                credential !is CustomCredential ||
                credential.type !=
                GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                onResult(false, "Unsupported Google credential.")
                return
            }

            val googleCredential =
                GoogleIdTokenCredential.createFrom(
                    credential.data
                )

            val firebaseCredential =
                GoogleAuthProvider.getCredential(
                    googleCredential.idToken,
                    null
                )

            FirebaseAuth.getInstance()
                .signInWithCredential(firebaseCredential)
                .addOnSuccessListener { authResult ->
                    val user = authResult.user
                    if (user == null) {
                        onResult(false, "Google authentication failed.")
                    } else {
                        Log.d(TAG, "Google authentication successful")
                        onResult(true, "Google login successful.")
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Firebase Google authentication failed", exception)
                    onResult(false, exception.localizedMessage ?: "Google login failed.")
                }

        } catch (exception: Exception) {
            Log.e(TAG, "Google credential selection failed", exception)
            onResult(false, exception.localizedMessage ?: "Google Sign-In was cancelled or failed.")
        }
    }
}
