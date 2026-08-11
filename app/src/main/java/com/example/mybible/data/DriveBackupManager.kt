package com.example.mybible.data

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Talks to Drive's `appDataFolder` (a hidden, per-app space Drive gives
 * every signed-in user — invisible in the user's normal Drive UI, cleared
 * only if the app itself deletes it or the user revokes access) using raw
 * REST calls over [HttpURLConnection]. Deliberately avoids the
 * `com.google.android.gms:play-services-drive` / `google-api-client`
 * libraries: they pull in a lot of weight (and, for the newer Java client,
 * a full HTTP stack of their own) for what is here just three endpoints
 * (list / create+upload / update+upload) against one fixed-name file.
 *
 * Auth is Google Sign-In (`play-services-auth`) for the picker UI, then
 * [GoogleAuthUtil.getToken] for a short-lived OAuth2 access token scoped
 * to `drive.appdata` — no server, no client secret, matches how the
 * Capacitor app's Google Drive sign-in worked from the user's perspective.
 */
class DriveBackupManager(private val context: Context) {

    companion object {
        private const val SCOPE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
        private const val OAUTH_SCOPE_PREFIX = "oauth2:$SCOPE_APPDATA"
        private const val BACKUP_FILE_NAME = "mybible_backup.json"
        private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
    }

    private val signInOptions: GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(SCOPE_APPDATA))
        .build()

    val signInClient: GoogleSignInClient = GoogleSignIn.getClient(context, signInOptions)

    fun getSignInIntent(): Intent = signInClient.signInIntent

    /** Result of the sign-in activity — unlike a bare nullable account,
     * [Failure] carries the actual [ApiException.statusCode] so a
     * misconfigured OAuth client (wrong package name / SHA-1 registered in
     * Google Cloud Console — surfaces as DEVELOPER_ERROR, code 10) shows up
     * as a diagnosable message instead of looking identical to the user
     * just cancelling the picker. */
    sealed class SignInResult {
        data class Success(val account: GoogleSignInAccount) : SignInResult()
        data class Failure(val statusCode: Int, val message: String) : SignInResult()
    }

    fun signInResultFromIntent(data: Intent?): SignInResult {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                SignInResult.Success(account)
            } else {
                SignInResult.Failure(-1, "No account returned")
            }
        } catch (e: ApiException) {
            SignInResult.Failure(e.statusCode, GoogleSignInStatusCodes.getStatusCodeString(e.statusCode))
        } catch (e: Exception) {
            SignInResult.Failure(-1, e.message ?: "Unknown error")
        }
    }

    /** Extracts the signed-in account from the activity result data, or
     * null if the user cancelled / sign-in failed. Kept for callers that
     * only care about the account, not the failure reason — prefer
     * [signInResultFromIntent] when the reason matters (e.g. surfacing it
     * to the user for diagnosis). */
    fun accountFromSignInResult(data: Intent?): GoogleSignInAccount? =
        (signInResultFromIntent(data) as? SignInResult.Success)?.account

    fun getLastSignedInAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    fun signOut(onComplete: () -> Unit) {
        signInClient.signOut().addOnCompleteListener { onComplete() }
    }

    /** Result of a Drive access-token request. [RecoverableConsent] means
     * Google needs the user to approve the drive.appdata scope through a
     * system dialog — the caller should launch [intent] and retry. */
    sealed class TokenResult {
        data class Success(val token: String) : TokenResult()
        data class RecoverableConsent(val intent: Intent) : TokenResult()
        data object Failure : TokenResult()
    }

    private suspend fun getAccessToken(account: GoogleSignInAccount): TokenResult = withContext(Dispatchers.IO) {
        val androidAccount: Account = account.account ?: return@withContext TokenResult.Failure
        try {
            val token = GoogleAuthUtil.getToken(context, androidAccount, OAUTH_SCOPE_PREFIX)
            TokenResult.Success(token)
        } catch (e: UserRecoverableAuthException) {
            e.intent?.let { TokenResult.RecoverableConsent(it) } ?: TokenResult.Failure
        } catch (e: Exception) {
            TokenResult.Failure
        }
    }

    sealed class BackupResult {
        data object Success : BackupResult()
        data class NeedsConsent(val intent: Intent) : BackupResult()
        data class Failure(val message: String) : BackupResult()
    }

    sealed class RestoreResult {
        data class Success(val json: String) : RestoreResult()
        data object NotFound : RestoreResult()
        data class NeedsConsent(val intent: Intent) : RestoreResult()
        data class Failure(val message: String) : RestoreResult()
    }

    /** Uploads [jsonContent] as the single backup file in the appdata
     * folder, replacing whatever was there before (Drive's appdata space
     * is meant for exactly this — one small app-owned blob, not a folder
     * of historical versions). */
    suspend fun uploadBackup(account: GoogleSignInAccount, jsonContent: String): BackupResult =
        withContext(Dispatchers.IO) {
            when (val tokenResult = getAccessToken(account)) {
                is TokenResult.RecoverableConsent -> return@withContext BackupResult.NeedsConsent(tokenResult.intent)
                is TokenResult.Failure -> return@withContext BackupResult.Failure("Couldn't get Drive access token")
                is TokenResult.Success -> {
                    try {
                        val existingId = findBackupFileId(tokenResult.token)
                        if (existingId != null) {
                            updateFileMedia(tokenResult.token, existingId, jsonContent)
                        } else {
                            createFileWithMedia(tokenResult.token, jsonContent)
                        }
                        BackupResult.Success
                    } catch (e: Exception) {
                        BackupResult.Failure(e.message ?: "Upload failed")
                    }
                }
            }
        }

    /** Downloads the backup file's content, or [RestoreResult.NotFound] if
     * this Google account has never backed up from this app before. */
    suspend fun downloadBackup(account: GoogleSignInAccount): RestoreResult = withContext(Dispatchers.IO) {
        when (val tokenResult = getAccessToken(account)) {
            is TokenResult.RecoverableConsent -> return@withContext RestoreResult.NeedsConsent(tokenResult.intent)
            is TokenResult.Failure -> return@withContext RestoreResult.Failure("Couldn't get Drive access token")
            is TokenResult.Success -> {
                try {
                    val fileId = findBackupFileId(tokenResult.token)
                        ?: return@withContext RestoreResult.NotFound
                    val content = downloadFileMedia(tokenResult.token, fileId)
                    RestoreResult.Success(content)
                } catch (e: Exception) {
                    RestoreResult.Failure(e.message ?: "Download failed")
                }
            }
        }
    }

    // ---- Raw REST plumbing ----

    private fun findBackupFileId(accessToken: String): String? {
        val q = URLEncoder.encode("name = '$BACKUP_FILE_NAME'", "UTF-8")
        val url = "$DRIVE_FILES_URL?spaces=appDataFolder&q=$q&fields=files(id,name)"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = 20_000
            readTimeout = 20_000
        }
        val body = conn.readBodyOrThrow()
        val files = JSONObject(body).optJSONArray("files") ?: return null
        if (files.length() == 0) return null
        return files.getJSONObject(0).getString("id")
    }

    private fun createFileWithMedia(accessToken: String, content: String) {
        val boundary = "mybible-backup-${System.currentTimeMillis()}"
        val metadata = JSONObject().apply {
            put("name", BACKUP_FILE_NAME)
            put("parents", org.json.JSONArray().put("appDataFolder"))
        }
        val body = buildMultipartBody(boundary, metadata.toString(), content)

        val conn = (URL("$DRIVE_UPLOAD_URL?uploadType=multipart").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            connectTimeout = 20_000
            readTimeout = 20_000
        }
        BufferedOutputStream(conn.outputStream).use { it.write(body); it.flush() }
        conn.readBodyOrThrow()
    }

    private fun updateFileMedia(accessToken: String, fileId: String, content: String) {
        val conn = (URL("$DRIVE_UPLOAD_URL/$fileId?uploadType=media").openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connectTimeout = 20_000
            readTimeout = 20_000
        }
        BufferedOutputStream(conn.outputStream).use { it.write(content.toByteArray(Charsets.UTF_8)); it.flush() }
        conn.readBodyOrThrow()
    }

    private fun downloadFileMedia(accessToken: String, fileId: String): String {
        val conn = (URL("$DRIVE_FILES_URL/$fileId?alt=media").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = 20_000
            readTimeout = 20_000
        }
        return conn.readBodyOrThrow()
    }

    private fun buildMultipartBody(boundary: String, metadataJson: String, mediaContent: String): ByteArray {
        val sb = StringBuilder()
        sb.append("--$boundary\r\n")
        sb.append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        sb.append(metadataJson).append("\r\n")
        sb.append("--$boundary\r\n")
        sb.append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        sb.append(mediaContent).append("\r\n")
        sb.append("--$boundary--")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    /** Reads the response body on success, or throws with the server's
     * error body included so failures surface something diagnosable
     * instead of a bare HTTP status code. */
    private fun HttpURLConnection.readBodyOrThrow(): String {
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            throw IOException("Drive request failed ($code): $text")
        }
        return text
    }
}
