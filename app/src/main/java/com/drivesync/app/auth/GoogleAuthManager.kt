package com.drivesync.app.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class GoogleAuthManager(private val context: Context) {

    companion object {
        const val SIGN_IN_REQUEST_CODE = 9001
    }

    private val _signInState = MutableStateFlow<SignInState>(SignInState.Unknown)
    val signInState: StateFlow<SignInState> = _signInState.asStateFlow()

    private val googleSignInClient: GoogleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(DriveScopes.DRIVE_FILE),
                Scope(DriveScopes.DRIVE)
            )
            .build()
        GoogleSignIn.getClient(context, options)
    }

    init {
        checkCurrentSignIn()
    }

    private fun checkCurrentSignIn() {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        _signInState.value = if (account != null && hasRequiredScopes(account)) {
            SignInState.SignedIn(account)
        } else {
            SignInState.SignedOut
        }
    }

    private fun hasRequiredScopes(account: GoogleSignInAccount): Boolean {
        return GoogleSignIn.hasPermissions(
            account,
            Scope(DriveScopes.DRIVE_FILE),
            Scope(DriveScopes.DRIVE)
        )
    }

    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

    suspend fun handleSignInResult(data: Intent?): Result<GoogleSignInAccount> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            _signInState.value = SignInState.SignedIn(account)
            Result.success(account)
        } catch (e: ApiException) {
            _signInState.value = SignInState.SignedOut
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        try {
            googleSignInClient.signOut().await()
        } catch (_: Exception) {}
        _signInState.value = SignInState.SignedOut
    }

    fun getSignedInAccount(): GoogleSignInAccount? {
        return (signInState.value as? SignInState.SignedIn)?.account
            ?: GoogleSignIn.getLastSignedInAccount(context)
    }

    fun isSignedIn(): Boolean = signInState.value is SignInState.SignedIn

    fun refreshSignInState() = checkCurrentSignIn()
}

sealed class SignInState {
    object Unknown : SignInState()
    object SignedOut : SignInState()
    data class SignedIn(val account: GoogleSignInAccount) : SignInState()
}
