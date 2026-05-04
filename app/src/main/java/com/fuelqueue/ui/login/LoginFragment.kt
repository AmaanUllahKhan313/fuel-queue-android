package com.fuelqueue.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.fuelqueue.R
import com.fuelqueue.data.api.RetrofitClient
import com.fuelqueue.data.model.SendOtpRequest
import com.fuelqueue.data.model.VerifyOtpRequest
import com.fuelqueue.databinding.FragmentLoginBinding
import com.fuelqueue.ui.MainActivity
import com.fuelqueue.utils.SessionManager
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private var otpSent = false
    private var currentMobileNumber = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // If already logged in, skip to map
        if (SessionManager.isLoggedIn()) {
            findNavController().navigate(R.id.action_login_to_map)
            return
        }

        binding.btnLogin.setOnClickListener { handleLoginFlow() }

        binding.tvGoToRegister.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }

        // Restore state
        if (savedInstanceState != null) {
            otpSent = savedInstanceState.getBoolean("otpSent", false)
            currentMobileNumber = savedInstanceState.getString("currentMobileNumber", "")
            updateUIState()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("otpSent", otpSent)
        outState.putString("currentMobileNumber", currentMobileNumber)
    }

    private fun handleLoginFlow() {
        if (!otpSent) {
            sendOtp()
        } else {
            verifyOtp()
        }
    }

    private fun sendOtp() {
        val phoneNumber = binding.etEmail.text.toString().trim()

        if (phoneNumber.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter phone number", Toast.LENGTH_SHORT).show()
            return
        }

        // Basic validation: must be 10 digits
        if (!phoneNumber.matches(Regex("^[0-9]{10}$"))) {
            Toast.makeText(requireContext(), "Please enter a valid 10-digit phone number", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.sendOtp(SendOtpRequest(phoneNumber))
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    currentMobileNumber = phoneNumber
                    otpSent = true
                    updateUIState()
                    Toast.makeText(requireContext(), "OTP sent to $phoneNumber", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Failed to send OTP", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun verifyOtp() {
        val otp = binding.etPassword.text.toString().trim()

        if (otp.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter OTP", Toast.LENGTH_SHORT).show()
            return
        }

        if (otp.length != 6) {
            Toast.makeText(requireContext(), "OTP must be 6 digits", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.verifyOtp(
                    VerifyOtpRequest(currentMobileNumber, otp)
                )
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    SessionManager.saveSession(body.token, body.userId, body.name, body.phoneNumber)
                    (requireActivity() as MainActivity).requestLocationAndStartTracking()
                    findNavController().navigate(R.id.action_login_to_map)
                } else {
                    Toast.makeText(requireContext(), "Invalid OTP", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun updateUIState() {
        if (otpSent) {
            // Show OTP verification screen
            binding.etEmail.isEnabled = false
            binding.etEmail.text?.clear()
            binding.etEmail.hint = "Awaiting OTP verification..."
            binding.tilPassword.visibility = View.VISIBLE
            binding.etPassword.hint = "Enter 6-digit OTP"
            binding.etPassword.text?.clear()
            binding.btnLogin.text = "Verify OTP"
        } else {
            // Show mobile number entry screen
            binding.etEmail.isEnabled = true
            binding.etEmail.hint = "Enter 10-digit mobile number"
            binding.etEmail.text?.clear()
            binding.tilPassword.visibility = View.GONE
            binding.etPassword.text?.clear()
            binding.btnLogin.text = "Send OTP"
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled    = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
