package com.roadar.app.data.sync

import com.google.firebase.FirebaseException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.Timestamp
import com.roadar.app.data.report.PotholeReport
import java.util.Date
import kotlinx.coroutines.tasks.await

class FirestoreRemoteReportStore(
    private val firestore: FirebaseFirestore
) : RemoteReportStore {
    override suspend fun upsertReport(uid: String, report: PotholeReport) {
        val document = firestore.collection(USERS_COLLECTION)
            .document(uid)
            .collection(REPORTS_COLLECTION)
            .document(report.id)
        try {
            document.set(report.toFirestorePayload()).await()
            val serverSnapshot = document.get(Source.SERVER).await()
            if (!serverSnapshot.exists() || serverSnapshot.getString(FIELD_REPORT_ID) != report.id) {
                throw RetryableReportSyncException(ERROR_SERVER_ACKNOWLEDGMENT_MISSING)
            }
        } catch (exception: FirebaseFirestoreException) {
            throw exception.toReportSyncException(report.id)
        } catch (exception: FirebaseException) {
            throw RetryableReportSyncException(ERROR_REMOTE_UNAVAILABLE, exception)
        }
    }

    override suspend fun deleteReport(uid: String, reportId: String) {
        val document = firestore.collection(USERS_COLLECTION)
            .document(uid)
            .collection(REPORTS_COLLECTION)
            .document(reportId)
        try {
            document.delete().await()
            if (document.get(Source.SERVER).await().exists()) {
                throw RetryableReportSyncException(ERROR_DELETE_NOT_CONFIRMED)
            }
        } catch (exception: FirebaseFirestoreException) {
            throw exception.toReportSyncException(reportId)
        } catch (exception: FirebaseException) {
            throw RetryableReportSyncException(ERROR_REMOTE_UNAVAILABLE, exception)
        }
    }

    private fun PotholeReport.toFirestorePayload(): Map<String, Any> = mapOf(
        FIELD_REPORT_ID to id,
        FIELD_LATITUDE to latitude,
        FIELD_LONGITUDE to longitude,
        FIELD_CONFIDENCE to detectionConfidence.toDouble(),
        FIELD_DETECTED_AT to Timestamp(Date(detectionAtUtcMillis)),
        FIELD_CREATED_AT to Timestamp(Date(createdAtUtcMillis)),
        FIELD_LOCATION_ACCURACY to horizontalAccuracyMeters.toDouble()
    )

    private fun FirebaseFirestoreException.toReportSyncException(
        reportId: String
    ): Exception = when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            PermanentReportSyncException(ERROR_PERMISSION_DENIED, reportId, this)
        FirebaseFirestoreException.Code.INVALID_ARGUMENT,
        FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
            PermanentReportSyncException(ERROR_INVALID_REMOTE_REPORT, reportId, this)
        else -> RetryableReportSyncException(ERROR_REMOTE_UNAVAILABLE, this)
    }

    companion object {
        const val USERS_COLLECTION = "users"
        const val REPORTS_COLLECTION = "reports"
        const val FIELD_REPORT_ID = "reportId"
        const val FIELD_LATITUDE = "latitude"
        const val FIELD_LONGITUDE = "longitude"
        const val FIELD_CONFIDENCE = "confidence"
        const val FIELD_DETECTED_AT = "detectedAt"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_LOCATION_ACCURACY = "locationAccuracy"
        const val ERROR_PERMISSION_DENIED = "FIRESTORE_PERMISSION_DENIED"
        const val ERROR_INVALID_REMOTE_REPORT = "FIRESTORE_REJECTED_REPORT"
        const val ERROR_REMOTE_UNAVAILABLE = "FIRESTORE_TEMPORARILY_UNAVAILABLE"
        const val ERROR_SERVER_ACKNOWLEDGMENT_MISSING = "SERVER_ACKNOWLEDGMENT_MISSING"
        const val ERROR_DELETE_NOT_CONFIRMED = "SERVER_DELETE_NOT_CONFIRMED"
    }
}
