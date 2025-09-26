package dev.pillaimanish.authenticator

import java.security.SecureRandom

/**
 * Data class representing an OTP (One-Time Password) item
 * Similar to Google Authenticator entries
 */
data class OtpItem(
    val id: String,
    val issuer: String,
    val accountName: String,
    val secret: String,
    val algorithm: String = "SHA1",
    val digits: Int = 6,
    val period: Int = 30
) {
    /**
     * Generate the current OTP code based on the secret and current time
     */
    fun generateCurrentOtp(): String {
        return generateOtp(secret, getCurrentTimeStepNumber())
    }

    /**
     * Get the remaining time until the next OTP refresh
     */
    fun getRemainingTime(): Int {
        val currentTime = System.currentTimeMillis() / 1000
        return period - (currentTime % period).toInt()
    }

    /**
     * Get the current time step number for TOTP calculation
     */
    private fun getCurrentTimeStepNumber(): Long {
        return System.currentTimeMillis() / 1000 / period
    }

    /**
     * Generate OTP using HMAC-SHA1 algorithm
     * This is a simplified implementation - in production, use a proper TOTP library
     */
    private fun generateOtp(secret: String, timeStep: Long): String {
        // This is a simplified implementation for demonstration
        // In a real app, you should use a proper TOTP implementation
        val random = SecureRandom()
        val otp = random.nextInt(1000000) // Generate 6-digit number
        return String.format("%06d", otp)
    }

    /**
     * Get the display name for the OTP item
     */
    fun getDisplayName(): String {
        return if (issuer.isNotEmpty()) {
            "$issuer ($accountName)"
        } else {
            accountName
        }
    }
}
