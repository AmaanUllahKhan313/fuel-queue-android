package com.fuelqueue.utils

import android.content.Context
import android.content.SharedPreferences

object SessionManager {

    private const val PREF_NAME    = "fuel_queue_session"
    private const val KEY_TOKEN    = "jwt_token"
    private const val KEY_USER_ID  = "user_id"
    private const val KEY_NAME     = "user_name"
    private const val KEY_PHONE   = "user_phone"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveSession(token: String, userId: Long, name: String, phoneNumber: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putLong(KEY_USER_ID, userId)
            .putString(KEY_NAME, name)
            .putString(KEY_PHONE, phoneNumber)
            .apply()
    }

    fun getToken(): String?      = prefs.getString(KEY_TOKEN, null)
    fun getUserId(): Long        = prefs.getLong(KEY_USER_ID, -1L)
    fun getName(): String        = prefs.getString(KEY_NAME, "") ?: ""
    fun getPhoneNumber(): String = prefs.getString(KEY_PHONE, "") ?: ""
    fun isLoggedIn(): Boolean    = getToken() != null

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
