package dev.pillaimanish.authenticator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import dev.pillaimanish.authenticator.databinding.FragmentTabularDataBinding

/**
 * Fragment displaying OTP codes with real-time countdown timers
 * Similar to Google Authenticator's main screen
 */
class TabularDataFragment : Fragment() {

    private var _binding: FragmentTabularDataBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var otpAdapter: OtpAdapter
    private val sampleOtpItems = createSampleOtpItems().toMutableList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTabularDataBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeQrScanResult()
    }

    private fun setupRecyclerView() {
        otpAdapter = OtpAdapter(sampleOtpItems) { otpItem ->
            // Handle OTP item click - could copy to clipboard, show details, etc.
            copyOtpToClipboard(otpItem.generateCurrentOtp())
        }
        
        binding.tabularRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = otpAdapter
        }
    }

    private fun copyOtpToClipboard(otpCode: String) {
        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) 
            as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("OTP Code", otpCode)
        clipboard.setPrimaryClip(clip)
        
        // Show a toast or snackbar to confirm copy
        android.widget.Toast.makeText(
            requireContext(), 
            "OTP code copied to clipboard", 
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    private fun createSampleOtpItems(): List<OtpItem> {
        return listOf(
            OtpItem(
                id = "1",
                issuer = "Google",
                accountName = "user@gmail.com",
                secret = "JBSWY3DPEHPK3PXP"
            ),
            OtpItem(
                id = "2",
                issuer = "Microsoft",
                accountName = "user@outlook.com",
                secret = "JBSWY3DPEHPK3PXP"
            ),
            OtpItem(
                id = "3",
                issuer = "GitHub",
                accountName = "developer@github.com",
                secret = "JBSWY3DPEHPK3PXP"
            ),
            OtpItem(
                id = "4",
                issuer = "Facebook",
                accountName = "user@facebook.com",
                secret = "JBSWY3DPEHPK3PXP"
            ),
            OtpItem(
                id = "5",
                issuer = "Twitter",
                accountName = "@username",
                secret = "JBSWY3DPEHPK3PXP"
            )
        )
    }

    private fun observeQrScanResult() {
        // For now, we'll handle QR results through a simpler approach
        // This can be enhanced later with proper navigation state handling
    }

    override fun onDestroyView() {
        super.onDestroyView()
        otpAdapter.stopTimer()
        _binding = null
    }

    companion object {
        /**
         * Factory method to create a new instance of TabularDataFragment
         */
        @JvmStatic
        fun newInstance() = TabularDataFragment()
    }
}