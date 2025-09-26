package dev.pillaimanish.authenticator

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import dev.pillaimanish.authenticator.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.fab.setOnClickListener { view ->
            // Navigate to QR scanner to add new OTP account
            val navController = findNavController(R.id.nav_host_fragment_content_main)
            navController.navigate(R.id.QrScannerFragment)
        }
    }
}