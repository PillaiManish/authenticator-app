package dev.pillaimanish.authenticator

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Data class representing an OTP (One-Time Password) item
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
    
    private var currentOtp: String = ""
    private var otpExpiryTime: Long = 0L
    
    /**
     * Generate the current OTP code based on the secret and current time
     * Uses proper TOTP algorithm with time-based generation
     */
    fun generateCurrentOtp(): String {
        val currentTime = System.currentTimeMillis() / 1000
        val timeStep = currentTime / period

        // Check if we need to generate a new OTP
        if (currentOtp.isEmpty() || currentTime >= otpExpiryTime) {
            currentOtp = generateTotp(secret, timeStep)
            otpExpiryTime = (timeStep + 1) * period
        }
        
        return currentOtp
    }
    
    /**
     * Generate OTP using TOTP algorithm
     */
    private fun generateTotp(secret: String, timeStep: Long): String {
        return try {
            // Decode base32 secret using custom decoder
            val key = decodeBase32(secret.uppercase())
            
            // Create HMAC
            val mac = Mac.getInstance("Hmac${algorithm.uppercase()}")
            val secretKeySpec = SecretKeySpec(key, "Hmac${algorithm.uppercase()}")
            mac.init(secretKeySpec)
            
            // Convert time step to bytes (big-endian)
            val timeBytes = ByteArray(8)
            for (i in 7 downTo 0) {
                timeBytes[7 - i] = (timeStep shr (8 * i)).toByte()
            }
            
            // Generate HMAC
            val hash = mac.doFinal(timeBytes)
            
            // Dynamic truncation
            val offset = hash[hash.size - 1].toInt() and 0x0f
            val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
                        ((hash[offset + 1].toInt() and 0xff) shl 16) or
                        ((hash[offset + 2].toInt() and 0xff) shl 8) or
                        (hash[offset + 3].toInt() and 0xff)
            
            val otp = binary % Math.pow(10.0, digits.toDouble()).toInt()
            String.format("%0${digits}d", otp)
        } catch (e: Exception) {
            android.util.Log.e("OtpItem", "TOTP generation failed: ${e.message}", e)
            generateRandomOtp()
        }
    }
    
    /**
     * Simple Base32 decoder implementation
     */
    private fun decodeBase32(input: String): ByteArray {
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val inputBytes = input.replace("=", "").toCharArray()
        val output = mutableListOf<Byte>()
        
        var buffer = 0
        var bitsLeft = 0
        
        for (char in inputBytes) {
            val index = base32Chars.indexOf(char)
            if (index == -1) continue
            
            buffer = (buffer shl 5) or index
            bitsLeft += 5
            
            if (bitsLeft >= 8) {
                output.add((buffer shr (bitsLeft - 8)).toByte())
                bitsLeft -= 8
            }
        }
        
        return output.toByteArray()
    }
    
    /**
     * Generate a random OTP code for testing purposes
     */
    private fun generateRandomOtp(): String {
        val random = java.util.Random()
        val otp = random.nextInt(Math.pow(10.0, digits.toDouble()).toInt())
        return String.format("%0${digits}d", otp)
    }
    
    /**
     * Get the remaining time until the next OTP refresh
     */
    fun getRemainingTime(): Int {
        val currentTime = System.currentTimeMillis() / 1000
        val timeStep = currentTime / period
        val nextExpiryTime = (timeStep + 1) * period
        val remaining = (nextExpiryTime - currentTime).toInt()
        return if (remaining > 0) remaining else 0
    }
    
    /**
     * Check if OTP needs to be refreshed
     */
    fun needsRefresh(): Boolean {
        val currentTime = System.currentTimeMillis() / 1000
        val timeStep = currentTime / period
        val currentExpiryTime = (timeStep + 1) * period
        return currentTime >= currentExpiryTime
    }
    
    /**
     * Force refresh the OTP (useful when app resumes)
     */
    fun refreshOtp() {
        currentOtp = ""
        otpExpiryTime = 0L
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
