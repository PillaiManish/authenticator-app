package dev.pillaimanish.authenticator

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Utility class for persisting OTP data using SharedPreferences
 * Stores OTP metadata (username, secret, issuer, etc.) in cache
 */
class OtpDataManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "otp_data_cache"
        private const val KEY_OTP_ITEMS = "otp_items"
    }
    
    /**
     * Save OTP items to cache
     */
    fun saveOtpItems(otpItems: List<OtpItem>) {
        val jsonArray = JSONArray()
        for (item in otpItems) {
            val jsonObject = JSONObject().apply {
                put("id", item.id)
                put("issuer", item.issuer)
                put("accountName", item.accountName)
                put("secret", item.secret)
                put("algorithm", item.algorithm)
                put("digits", item.digits)
                put("period", item.period)
            }
            jsonArray.put(jsonObject)
        }
        prefs.edit().putString(KEY_OTP_ITEMS, jsonArray.toString()).apply()
    }
    
    /**
     * Load OTP items from cache
     */
    fun loadOtpItems(): List<OtpItem> {
        val jsonString = prefs.getString(KEY_OTP_ITEMS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val items = mutableListOf<OtpItem>()
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val item = OtpItem(
                    id = jsonObject.getString("id"),
                    issuer = jsonObject.getString("issuer"),
                    accountName = jsonObject.getString("accountName"),
                    secret = jsonObject.getString("secret"),
                    algorithm = jsonObject.getString("algorithm"),
                    digits = jsonObject.getInt("digits"),
                    period = jsonObject.getInt("period")
                )
                items.add(item)
            }
            items
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Add a single OTP item to cache
     */
    fun addOtpItem(otpItem: OtpItem) {
        val currentItems = loadOtpItems().toMutableList()
        currentItems.add(otpItem)
        saveOtpItems(currentItems)
    }
    
    /**
     * Remove an OTP item from cache
     */
    fun removeOtpItem(otpItemId: String) {
        val currentItems = loadOtpItems().toMutableList()
        currentItems.removeAll { it.id == otpItemId }
        saveOtpItems(currentItems)
    }
    
    /**
     * Clear all OTP data from cache
     */
    fun clearAllOtpItems() {
        prefs.edit().remove(KEY_OTP_ITEMS).apply()
    }
    
    /**
     * Check if cache has any OTP items
     */
    fun hasOtpItems(): Boolean {
        return loadOtpItems().isNotEmpty()
    }
}
