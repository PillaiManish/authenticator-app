package dev.pillaimanish.authenticator

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.pillaimanish.authenticator.databinding.ItemOtpBinding

/**
 * RecyclerView adapter for displaying OTP items with real-time countdown timers
 */
class OtpAdapter(
    private val context: Context,
    private val onItemClick: (OtpItem) -> Unit = {}
) : RecyclerView.Adapter<OtpAdapter.OtpViewHolder>() {

    private val otpItems = mutableListOf<OtpItem>()
    private val dataManager = OtpDataManager(context)

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            // Only update if any OTP needs refresh
            var needsUpdate = false
            for (item in otpItems) {
                if (item.needsRefresh()) {
                    needsUpdate = true
                    break
                }
            }
            
            if (needsUpdate) {
                notifyDataSetChanged()
            }
            
            // Schedule next check in 1 second
            handler.postDelayed(this, 1000)
        }
    }

    init {
        loadFromCache()
        handler.post(updateRunnable)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OtpViewHolder {
        val binding = ItemOtpBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OtpViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OtpViewHolder, position: Int) {
        holder.bind(otpItems[position])
    }

    override fun getItemCount(): Int = otpItems.size

    fun updateOtpItems(newOtpItem: OtpItem) {
        otpItems.add(newOtpItem)
        dataManager.addOtpItem(newOtpItem)
        notifyDataSetChanged()
    }

    fun removeOtpItem(otpItemId: String) {
        otpItems.removeAll { it.id == otpItemId }
        dataManager.removeOtpItem(otpItemId)
        notifyDataSetChanged()
    }

    fun clearAllOtpItems() {
        otpItems.clear()
        dataManager.clearAllOtpItems()
        notifyDataSetChanged()
    }

    private fun loadFromCache() {
        val cachedItems = dataManager.loadOtpItems()
        otpItems.clear()
        otpItems.addAll(cachedItems)
    }
    
    /**
     * Refresh all OTPs (useful when app resumes after being closed)
     */
    fun refreshAllOtps() {
        for (item in otpItems) {
            item.refreshOtp()
        }
        notifyDataSetChanged()
    }

    fun stopTimer() {
        handler.removeCallbacks(updateRunnable)
    }

    inner class OtpViewHolder(private val binding: ItemOtpBinding) : 
        RecyclerView.ViewHolder(binding.root) {

        fun bind(otpItem: OtpItem) {
            binding.apply {
                // Set account information
                accountName.text = otpItem.issuer
                accountEmail.text = otpItem.accountName

                // Generate and display OTP code
                val currentOtp = otpItem.generateCurrentOtp()
                otpCode.text = formatOtpCode(currentOtp)

                // Update timer with color coding
                val remainingTime = otpItem.getRemainingTime()
                timerText.text = "${remainingTime}s"
                
                // Change timer color based on remaining time
                val context = timerText.context
                val timerColor = when {
                    remainingTime <= 5 -> context.getColor(android.R.color.holo_red_dark)
                    remainingTime <= 10 -> context.getColor(android.R.color.holo_orange_dark)
                    else -> context.getColor(android.R.color.darker_gray)
                }
                timerText.setTextColor(timerColor)

                // Set click listener
                root.setOnClickListener {
                    onItemClick(otpItem)
                }
            }
        }

        private fun formatOtpCode(otp: String): String {
            // Format OTP code with spaces for better readability
            return if (otp.length == 6) {
                "${otp.substring(0, 3)} ${otp.substring(3)}"
            } else {
                otp
            }
        }
    }
}
