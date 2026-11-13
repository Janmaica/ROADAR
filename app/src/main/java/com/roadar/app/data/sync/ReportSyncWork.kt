package com.roadar.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.roadar.app.BuildConfig
import com.roadar.app.data.report.RoadarDatabase
import java.util.concurrent.TimeUnit

fun interface ReportSyncScheduler {
    fun enqueueSync()
}

class WorkManagerReportSyncScheduler(
    context: Context
) : ReportSyncScheduler {
    private val applicationContext = context.applicationContext

    override fun enqueueSync() {
        if (!BuildConfig.FIREBASE_CONFIGURED ||
            !FirebaseSyncPreferences.isEnabled(applicationContext)
        ) return

        val request = OneTimeWorkRequestBuilder<ReportSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                INITIAL_BACKOFF_SECONDS,
                TimeUnit.SECONDS
            )
            .build()

        try {
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "Could not enqueue report synchronization.", exception)
        }
    }

    fun cancelSync() {
        if (!BuildConfig.FIREBASE_CONFIGURED) return
        WorkManager.getInstance(applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "roadar-report-synchronization"
        private const val INITIAL_BACKOFF_SECONDS = 30L
        private const val TAG = "ROADAR-Sync"
    }
}

class ReportSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        if (!BuildConfig.FIREBASE_CONFIGURED ||
            !FirebaseSyncPreferences.isEnabled(applicationContext)
        ) return Result.success()

        val database = RoadarDatabase.getInstance(applicationContext)
        val localStore = RoomReportSyncLocalStore(database.potholeReportDao())
        return try {
            val firebaseApp = FirebaseApp.getApps(applicationContext).firstOrNull()
                ?: FirebaseApp.initializeApp(applicationContext)
            if (firebaseApp == null) {
                localStore.recordError(
                    FirebaseAnonymousIdentityProvider.ERROR_FIREBASE_INITIALIZATION
                )
                return Result.success()
            }

            val coordinator = ReportSyncCoordinator(
                localStore = localStore,
                identityProvider = FirebaseAnonymousIdentityProvider(applicationContext),
                remoteStore = FirestoreRemoteReportStore(
                    FirebaseFirestore.getInstance(firebaseApp)
                ),
                isSyncEnabled = {
                    FirebaseSyncPreferences.isEnabled(applicationContext)
                }
            )
            when (coordinator.synchronize()) {
                SyncRunResult.COMPLETE,
                SyncRunResult.DISABLED,
                SyncRunResult.BLOCKED -> Result.success()
                SyncRunResult.MORE_WORK -> {
                    WorkManagerReportSyncScheduler(applicationContext).enqueueSync()
                    Result.success()
                }
            }
        } catch (exception: RetryableReportSyncException) {
            Log.w(TAG, "Report synchronization will retry (${exception.errorCode}).")
            Result.retry()
        } catch (exception: IllegalStateException) {
            localStore.recordError(FirebaseAnonymousIdentityProvider.ERROR_FIREBASE_INITIALIZATION)
            Log.e(TAG, "Firebase synchronization could not be initialized.", exception)
            Result.success()
        }
    }

    companion object {
        private const val TAG = "ROADAR-Sync"
    }
}
