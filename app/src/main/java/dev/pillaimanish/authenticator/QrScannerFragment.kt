package dev.pillaimanish.authenticator

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import dev.pillaimanish.authenticator.databinding.FragmentQrScannerBinding
import kotlinx.coroutines.launch

/**
 * Data class to hold parsed QR code information
 */
data class QrCodeData(
    val issuer: String,
    val accountName: String,
    val email: String,
    val secret: String,
    val digit: Int,
    val algorithm: String,
    val period: Int,
)

/**
 * Fragment for scanning QR codes to add new OTP accounts
 */
class QrScannerFragment : Fragment() {

    private var _binding: FragmentQrScannerBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var cameraProvider: androidx.camera.lifecycle.ProcessCameraProvider
    private lateinit var imageAnalyzer: androidx.camera.core.ImageAnalysis
    private var isScanning = true
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(requireContext(), "Camera permission is required to scan QR codes", Toast.LENGTH_LONG).show()
            findNavController().popBackStack()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQrScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.btnCancel.setOnClickListener {
            findNavController().popBackStack()
        }
        
        checkCameraPermission()
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(requireContext())
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to start camera: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases() {
        val preview = androidx.camera.core.Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

        imageAnalyzer = androidx.camera.core.ImageAnalysis.Builder()
            .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(ContextCompat.getMainExecutor(requireContext()), QrCodeAnalyzer { qrCode ->
                    handleQrCodeResult(qrCode)
                })
            }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalyzer
            )
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to bind camera: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleQrCodeResult(qrCode: String) {
        if (!isScanning) return
        
        isScanning = false
        lifecycleScope.launch {
            try {
                val otpData = parseQrCode(qrCode)
                if (otpData != null) {
                    // Pass the result back to the main fragment
                    val bundle = Bundle().apply {
                        putString("qr_code", qrCode)
                        putString("issuer", otpData.issuer)
                        putString("account_name", otpData.accountName)
                        putString("email", otpData.email)
                        putString("secret", otpData.secret)
                        putString("algorithm", otpData.algorithm)
                        putInt("digit", otpData.digit)
                        putInt("period", otpData.period)
                    }
                    
                    // Use navigation result to pass data back
                    findNavController().previousBackStackEntry?.savedStateHandle?.set("qr_result", bundle)
                    
                    // Show success message and navigate back
                    android.widget.Toast.makeText(
                        requireContext(),
                        "QR code scanned: ${otpData.issuer} - ${otpData.accountName}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    
                    // Navigate back to main screen
                    findNavController().popBackStack()
                } else {
                    android.widget.Toast.makeText(requireContext(), "Invalid QR code format", android.widget.Toast.LENGTH_SHORT).show()
                    isScanning = true // Re-enable scanning for invalid codes
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(requireContext(), "Error parsing QR code: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                isScanning = true // Re-enable scanning for errors
            }
        }
    }

    private fun parseQrCode(qrCode: String): QrCodeData? {
        return try {
            // Parse otpauth://
            if (qrCode.startsWith("otpauth://")) {
                parseTotpQrCode(qrCode)
            } else {
                // For non-otpauth URLs, try to extract basic info
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseTotpQrCode(qrCode: String): QrCodeData? {
        if (!qrCode.startsWith("otpauth://totp/")) return null

        try {
            // Remove the prefix and split into path + query
            val uri = Uri.parse(qrCode)

            // Extract the label part: "Issuer:AccountName"
            val label = uri.path?.removePrefix("/") ?: ""
            val issuerFromLabel = label.substringBefore(":", "")
            val accountName = label.substringAfter(":", label)

            // Extract query parameters
            val secret = uri.getQueryParameter("secret") ?: return null
            val issuerFromQuery = uri.getQueryParameter("issuer") ?: issuerFromLabel
            val algorithm = uri.getQueryParameter("algorithm") ?: "SHA1"
            val digits = uri.getQueryParameter("digits")?.toIntOrNull() ?: 6
            val period = uri.getQueryParameter("period")?.toIntOrNull() ?: 30

            return QrCodeData(
                issuer = issuerFromQuery.ifEmpty { "Unknown Service" },
                accountName = accountName.ifEmpty { "User Account" },
                email = "", // optional, might not exist in TOTP
                secret = secret,
                algorithm = algorithm,
                digit = digits,
                period = period
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isScanning = false
        try {
            if (::cameraProvider.isInitialized) {
                cameraProvider.unbindAll()
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
        _binding = null
    }

    private inner class QrCodeAnalyzer(
        private val onQrCodeDetected: (String) -> Unit
    ) : androidx.camera.core.ImageAnalysis.Analyzer {

        private val scanner = BarcodeScanning.getClient()

        @OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: androidx.camera.core.ImageProxy) {
            if (!isScanning) {
                imageProxy.close()
                return
            }
            
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        if (!isScanning) return@addOnSuccessListener
                        
                        for (barcode in barcodes) {
                            barcode.rawValue?.let { value ->
                                if (barcode.valueType == Barcode.TYPE_URL || barcode.valueType == Barcode.TYPE_TEXT) {
                                    onQrCodeDetected(value)
                                    return@addOnSuccessListener
                                }
                            }
                        }
                    }
                    .addOnFailureListener { }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
}
