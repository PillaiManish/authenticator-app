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
    
    override fun onResume() {
        super.onResume()
        // Refresh all OTPs when app resumes (handles device restart case)
        if (::otpAdapter.isInitialized) {
            otpAdapter.refreshAllOtps()
        }
    }

    private fun setupRecyclerView() {
        otpAdapter = OtpAdapter(requireContext()) { otpItem ->
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

    private fun observeQrScanResult() {
        // Listen for QR scan results from the scanner fragment
        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<Bundle>("qr_result")
            ?.observe(viewLifecycleOwner) { bundle: Bundle? ->
                bundle?.let { result: Bundle ->
                    val issuer = result.getString("issuer") ?: "Unknown Service"
                    val accountName = result.getString("account_name") ?: "User Account"
                    val email = result.getString("email") ?: ""
                    val secret = result.getString("secret") ?: ""
                    val period = result.getInt("period", 30)
                    val algorithm = result.getString("algorithm") ?: "SHA1"
                    val digits = result.getInt("digit", 6)

                    if (secret.isNotEmpty()) {
                        val newOtpItem = OtpItem(
                            id = java.util.UUID.randomUUID().toString(),
                            issuer = issuer,
                            accountName = accountName,
                            secret = secret,
                            period = period,
                            algorithm = algorithm,
                            digits = digits
                        )
                        
                        // Add new OTP item to the list
                        otpAdapter.updateOtpItems(newOtpItem)
                        
                        // Show success message
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Added: $issuer - $accountName",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
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