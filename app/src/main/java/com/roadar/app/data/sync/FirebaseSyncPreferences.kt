package com.roadar.app.data.sync

import android.content.Context

object FirebaseSyncPreferences {
    private const val PREFERENCES_NAME = "roadar_firebase_sync"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        val saved = context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .commit()
        check(saved) { "Could not save the Firebase synchronization setting." }
    }
}
