package com.roadar.app.data.sync

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.roadar.app.BuildConfig
import kotlinx.coroutines.tasks.await

class FirebaseAnonymousIdentityProvider(
    private val context: Context
) : AnonymousIdentityProvider {
    override suspend fun getUid(existingOwnerUid: String?): String {
        if (!BuildConfig.FIREBASE_CONFIGURED) {
            throw ReportSyncBlockedException(ERROR_NOT_CONFIGURED)
        }

        val firebaseApp = FirebaseApp.getApps(context).firstOrNull()
            ?: FirebaseApp.initializeApp(context)
            ?: throw PermanentReportSyncException(ERROR_FIREBASE_INITIALIZATION)
        val auth = FirebaseAuth.getInstance(firebaseApp)
        val currentUser = auth.currentUser

        if (currentUser != null) {
            if (!currentUser.isAnonymous) {
                throw ReportSyncBlockedException(ERROR_UNEXPECTED_ACCOUNT_TYPE)
            }
            if (existingOwnerUid != null && existingOwnerUid != currentUser.uid) {
                throw ReportSyncBlockedException(
                    ReportSyncCoordinator.SYNC_ERROR_ACCOUNT_MISMATCH
                )
            }
            return currentUser.uid
        }

        if (existingOwnerUid != null) {
            throw ReportSyncBlockedException(
                ReportSyncCoordinator.SYNC_ERROR_ACCOUNT_MISMATCH
            )
        }

        return try {
            auth.signInAnonymously().await().user?.uid
                ?: throw PermanentReportSyncException(ERROR_ANONYMOUS_AUTHENTICATION)
        } catch (exception: FirebaseAuthException) {
            val errorCode = if (exception.errorCode in PERMANENT_AUTH_ERRORS) {
                ERROR_ANONYMOUS_AUTHENTICATION
            } else {
                ERROR_AUTHENTICATION_UNAVAILABLE
            }
            if (exception.errorCode in PERMANENT_AUTH_ERRORS) {
                throw PermanentReportSyncException(errorCode, cause = exception)
            }
            throw RetryableReportSyncException(errorCode, exception)
        } catch (exception: FirebaseException) {
            throw RetryableReportSyncException(ERROR_AUTHENTICATION_UNAVAILABLE, exception)
        }
    }

    companion object {
        const val ERROR_NOT_CONFIGURED = "FIREBASE_NOT_CONFIGURED"
        const val ERROR_FIREBASE_INITIALIZATION = "FIREBASE_INITIALIZATION_FAILED"
        const val ERROR_UNEXPECTED_ACCOUNT_TYPE = "UNEXPECTED_ACCOUNT_TYPE"
        const val ERROR_ANONYMOUS_AUTHENTICATION = "ANONYMOUS_AUTHENTICATION_FAILED"
        const val ERROR_AUTHENTICATION_UNAVAILABLE = "AUTHENTICATION_TEMPORARILY_UNAVAILABLE"

        private val PERMANENT_AUTH_ERRORS = setOf(
            "ERROR_OPERATION_NOT_ALLOWED",
            "ERROR_INVALID_API_KEY",
            "ERROR_APP_NOT_AUTHORIZED",
            "ERROR_INVALID_APP_CREDENTIAL"
        )
    }
}
