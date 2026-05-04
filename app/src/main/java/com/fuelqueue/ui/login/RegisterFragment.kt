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
import com.fuelqueue.databinding.FragmentRegisterBinding
import com.fuelqueue.ui.MainActivity
import com.fuelqueue.utils.SessionManager
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private var otpSent = false
    private var currentMobileNumber = ""
    private var currentName = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRegister.setOnClickListener { handleRegistrationFlow() }

        binding.tvGoToLogin.setOnClickListener {
            findNavController().popBackStack()
        }

        // Hide email field (not needed for OTP-based registration)
        binding.etEmail.visibility = View.GONE

        // Restore state
        if (savedInstanceState != null) {
            otpSent = savedInstanceState.getBoolean("otpSent", false)
            currentMobileNumber = savedInstanceState.getString("currentMobileNumber", "")
            currentName = savedInstanceState.getString("currentName", "")
            updateUIState()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("otpSent", otpSent)
        outState.putString("currentMobileNumber", currentMobileNumber)
        outState.putString("currentName", currentName)
    }

    private fun handleRegistrationFlow() {
        if (!otpSent) {
            sendOtp()
        } else {
            verifyOtp()
        }
    }

    private fun sendOtp() {
        val name     = binding.etName.text.toString().trim()
        val phoneNumber = binding.etPassword.text.toString().trim()

        if (name.isEmpty() || phoneNumber.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
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
                    currentName = name
                    otpSent = true
                    updateUIState()
                    Toast.makeText(requireContext(), "OTP sent to $phoneNumber", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Phone number already registered", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun verifyOtp() {
        val otp = binding.etConfirmPassword.text.toString().trim()

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
                // Pass name during registration verification
                val response = RetrofitClient.api.verifyOtp(
                    VerifyOtpRequest(currentMobileNumber, otp, currentName)
                )
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    SessionManager.saveSession(body.token, body.userId, body.name, body.phoneNumber)
                    (requireActivity() as MainActivity).requestLocationAndStartTracking()
                    findNavController().navigate(R.id.action_register_to_map)
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
            binding.etName.isEnabled = false
            binding.etPassword.isEnabled = false
            binding.etPassword.hint = "Awaiting OTP verification..."
            binding.tilConfirmPassword.visibility = View.VISIBLE
            binding.etConfirmPassword.hint = "Enter 6-digit OTP"
            binding.etConfirmPassword.text?.clear()
            binding.btnRegister.text = "Verify OTP"
        } else {
            // Show registration form
            binding.etName.isEnabled = true
            binding.etPassword.isEnabled = true
            binding.etPassword.hint = "Enter 10-digit mobile number"
            binding.etPassword.text?.clear()
            binding.tilConfirmPassword.visibility = View.GONE
            binding.etConfirmPassword.text?.clear()
            binding.btnRegister.text = "Send OTP"
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnRegister.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
