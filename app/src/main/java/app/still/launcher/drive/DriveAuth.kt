package app.still.launcher.drive

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** What asking Google for Drive access came to. */
sealed interface DriveAccess {
    /** STILL may read Drive with [token] for a while. */
    data class Granted(val token: String) : DriveAccess

    /** Google wants the person to choose an account or agree first, on [intent]'s screen. */
    data class NeedsYou(val intent: PendingIntent) : DriveAccess

    data class Failed(val reason: DriveProblem) : DriveAccess
}

/** Why Drive couldn't be reached. */
enum class DriveProblem {
    /** Google doesn't know STILL yet: the Google Cloud setup isn't done or doesn't match this build. */
    SETUP,

    /** No connection. */
    OFFLINE,

    /** The person backed out of Google's screen. */
    CANCELLED,

    /** The file came down but isn't a document STILL can read. */
    UNREADABLE,

    OTHER,
}

/**
 * Read-only access to the person's Google Drive through Google Play services' authorization API.
 * Tokens are short-lived and asked for each time; Play services remembers the consent, so this
 * only shows a screen the first time or when Google wants the person again.
 */
class DriveAuth(context: Context) {

    private val appContext = context.applicationContext
    private val client get() = Identity.getAuthorizationClient(appContext)

    /** Asks for access, as [account] when it's known so the same account is used each time. */
    suspend fun access(account: String?): DriveAccess = try {
        resultOf(client.authorize(request(account)).await())
    } catch (e: ApiException) {
        DriveAccess.Failed(problemOf(e))
    }

    /** What Google's screen came back with; nothing at all means the person backed out. */
    fun accessFrom(data: Intent?): DriveAccess {
        if (data == null) return DriveAccess.Failed(DriveProblem.CANCELLED)
        return try {
            resultOf(client.getAuthorizationResultFromIntent(data))
        } catch (e: ApiException) {
            DriveAccess.Failed(problemOf(e))
        }
    }

    /** Forgets a token Drive no longer takes, so the next [access] gets a fresh one. */
    suspend fun clear(token: String) {
        runCatching { client.clearToken(ClearTokenRequest.builder().setToken(token).build()).await() }
    }

    /** Gives up STILL's access to [account]'s Drive. */
    suspend fun revoke(account: String) {
        runCatching {
            val request = RevokeAccessRequest.builder()
                .setAccount(Account(account, GOOGLE_ACCOUNT_TYPE))
                .setScopes(listOf(Scope(DRIVE_READ_ONLY)))
                .build()
            client.revokeAccess(request).await()
        }
    }

    private fun request(account: String?): AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_READ_ONLY)))
        .apply { if (account != null) setAccount(Account(account, GOOGLE_ACCOUNT_TYPE)) }
        .build()

    private fun resultOf(result: AuthorizationResult): DriveAccess {
        val pending = result.pendingIntent
        val token = result.accessToken
        return when {
            result.hasResolution() && pending != null -> DriveAccess.NeedsYou(pending)
            token != null -> DriveAccess.Granted(token)
            else -> DriveAccess.Failed(DriveProblem.OTHER)
        }
    }

    private fun problemOf(e: ApiException): DriveProblem = when {
        // A missing or mismatched Android client comes back as an internal error naming it.
        e.message?.contains(UNREGISTERED) == true || e.statusCode == CommonStatusCodes.INTERNAL_ERROR -> DriveProblem.SETUP
        e.statusCode == CommonStatusCodes.DEVELOPER_ERROR || e.statusCode == CommonStatusCodes.SIGN_IN_REQUIRED -> DriveProblem.SETUP
        e.statusCode == CommonStatusCodes.NETWORK_ERROR || e.statusCode == CommonStatusCodes.TIMEOUT -> DriveProblem.OFFLINE
        e.statusCode == CommonStatusCodes.CANCELED -> DriveProblem.CANCELLED
        else -> DriveProblem.OTHER
    }

    private companion object {
        const val DRIVE_READ_ONLY = "https://www.googleapis.com/auth/drive.readonly"
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
        const val UNREGISTERED = "UNREGISTERED_ON_API_CONSOLE"
    }
}

/** Waits for a Play services task. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
