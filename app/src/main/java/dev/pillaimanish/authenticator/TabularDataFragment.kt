package dev.pillaimanish.authenticator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
        
        // Only setup if not already initialized (prevents duplication on theme changes)
        if (!::otpAdapter.isInitialized) {
            setupRecyclerView()
        }
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
        
        // Setup swipe-to-delete functionality
        setupSwipeToDelete()
    }
    
    private fun setupSwipeToDelete() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false // We don't support drag and drop
            }
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return
                
                val otpItem = otpAdapter.getItemAt(position)
                
                // Show confirmation dialog
                showDeleteConfirmationDialog(otpItem) {
                    // User confirmed deletion
                    otpAdapter.removeOtpItem(otpItem.id)
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Deleted: ${otpItem.getDisplayName()}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                
                // Restore the item if user cancels (handled in dialog)
                otpAdapter.notifyItemChanged(position)
            }
            
            override fun onChildDraw(
                c: android.graphics.Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val itemView = viewHolder.itemView
                    val background = android.graphics.drawable.ColorDrawable(
                        requireContext().getColor(android.R.color.holo_red_dark)
                    )
                    val deleteIcon = requireContext().getDrawable(android.R.drawable.ic_menu_delete)
                    
                    // Draw background
                    background.setBounds(
                        itemView.left,
                        itemView.top,
                        itemView.left + dX.toInt(),
                        itemView.bottom
                    )
                    background.draw(c)
                    
                    // Draw delete icon
                    deleteIcon?.let { icon ->
                        val iconSize = icon.intrinsicHeight
                        val iconMargin = (itemView.height - iconSize) / 2
                        icon.setBounds(
                            itemView.left + iconMargin,
                            itemView.top + iconMargin,
                            itemView.left + iconMargin + iconSize,
                            itemView.top + iconMargin + iconSize
                        )
                        icon.draw(c)
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        })
        
        itemTouchHelper.attachToRecyclerView(binding.tabularRecyclerView)
    }
    
    private fun showDeleteConfirmationDialog(otpItem: OtpItem, onConfirm: () -> Unit) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete OTP")
            .setMessage("Are you sure you want to delete \"${otpItem.getDisplayName()}\"?")
            .setPositiveButton("Delete") { _, _ ->
                onConfirm()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
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