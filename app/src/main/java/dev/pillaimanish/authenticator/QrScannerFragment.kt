package dev.pillaimanish.authenticator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
 * Fragment for scanning QR codes to add new OTP accounts
 */
class QrScannerFragment : Fragment() {

    private var _binding: FragmentQrScannerBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var cameraProvider: androidx.camera.lifecycle.ProcessCameraProvider
    private lateinit var imageAnalyzer: androidx.camera.core.ImageAnalysis
    
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
        lifecycleScope.launch {
            try {
                val otpItem = parseQrCode(qrCode)
                if (otpItem != null) {
                    // Show success message and navigate back
                    android.widget.Toast.makeText(
                        requireContext(),
                        "QR code scanned successfully: ${otpItem.getDisplayName()}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    
                    // Navigate back to main screen
                    findNavController().popBackStack()
                } else {
                    android.widget.Toast.makeText(requireContext(), "Invalid QR code format", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(requireContext(), "Error parsing QR code: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun parseQrCode(qrCode: String): OtpItem? {
        return try {
            // Parse otpauth:// URLs (Google Authenticator format)
            if (qrCode.startsWith("otpauth://")) {
                parseOtpauthUrl(qrCode)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseOtpauthUrl(url: String): OtpItem? {
        val uri = java.net.URI(url)
        
        if (uri.scheme != "otpauth") return null
        
        val type = uri.host // totp or hotp
        val path = uri.path?.removePrefix("/") ?: return null
        
        val queryParams = uri.query?.split("&")?.associate { param ->
            val (key, value) = param.split("=", limit = 2)
            key to java.net.URLDecoder.decode(value, "UTF-8")
        } ?: emptyMap()
        
        val secret = queryParams["secret"] ?: return null
        val issuer = queryParams["issuer"] ?: ""
        val accountName = queryParams["accountname"] ?: path
        
        return OtpItem(
            id = java.util.UUID.randomUUID().toString(),
            issuer = issuer,
            accountName = accountName,
            secret = secret,
            algorithm = queryParams["algorithm"] ?: "SHA1",
            digits = queryParams["digits"]?.toIntOrNull() ?: 6,
            period = queryParams["period"]?.toIntOrNull() ?: 30
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class QrCodeAnalyzer(
        private val onQrCodeDetected: (String) -> Unit
    ) : androidx.camera.core.ImageAnalysis.Analyzer {

        private val scanner = BarcodeScanning.getClient()

        override fun analyze(imageProxy: androidx.camera.core.ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
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
