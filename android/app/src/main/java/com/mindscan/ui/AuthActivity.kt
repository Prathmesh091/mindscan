package com.mindscan.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.mindscan.databinding.ActivityAuthBinding
import com.mindscan.databinding.FragmentLoginBinding
import com.mindscan.databinding.FragmentRegisterBinding
import com.mindscan.network.ApiClient
import com.mindscan.network.LoginRequest
import com.mindscan.network.RegisterRequest
import com.mindscan.util.TokenManager
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewPager.adapter = AuthPagerAdapter(this)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = if (pos == 0) "Login" else "Register"
        }.attach()
    }

    class AuthPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount() = 2
        override fun createFragment(position: Int): Fragment =
            if (position == 0) LoginFragment() else RegisterFragment()
    }
}

// ── Login ─────────────────────────────────────────────────────────────────────
class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnLogin.setOnClickListener { doLogin() }
    }

    private fun doLogin() {
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnLogin.isEnabled     = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = ApiClient.getApiService().login(LoginRequest(username, password))
                if (response.isSuccessful && response.body()?.success == true) {
                    val body = response.body()!!
                    val tm   = TokenManager(requireContext())
                    tm.saveToken(body.token!!)
                    tm.saveUserInfo(body.username ?: username, body.email ?: "")
                    startActivity(Intent(requireActivity(), MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    requireActivity().finish()
                } else {
                    Toast.makeText(requireContext(),
                        response.body()?.message ?: "Invalid username or password",
                        Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(),
                    "Connection error. Make sure backend is running.",
                    Toast.LENGTH_LONG).show()
            } finally {
                binding.btnLogin.isEnabled     = true
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ── Register ──────────────────────────────────────────────────────────────────
class RegisterFragment : Fragment() {
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnRegister.setOnClickListener { doRegister() }
    }

    private fun doRegister() {
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirm  = binding.etConfirmPassword.text.toString().trim()

        // Validation
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }
        if (username.length < 3) {
            Toast.makeText(requireContext(), "Username must be at least 3 characters", Toast.LENGTH_SHORT).show()
            return
        }
        if (username.length > 20) {
            Toast.makeText(requireContext(), "Username must be less than 20 characters", Toast.LENGTH_SHORT).show()
            return
        }
        if (!username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            Toast.makeText(requireContext(), "Username can only contain letters, numbers and underscore", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.length < 6) {
            Toast.makeText(requireContext(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            return
        }
        if (password != confirm) {
            Toast.makeText(requireContext(), "Passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnRegister.isEnabled   = false
        binding.progressBar.visibility  = View.VISIBLE

        lifecycleScope.launch {
            try {
                // Send username as both username and email (backend requires email)
                val fakeEmail = "${username}@mindscan.app"
                val response  = ApiClient.getApiService()
                    .register(RegisterRequest(username, fakeEmail, password))
                if (response.isSuccessful && response.body()?.success == true) {
                    val body = response.body()!!
                    val tm   = TokenManager(requireContext())
                    tm.saveToken(body.token!!)
                    tm.saveUserInfo(body.username ?: username, "")
                    startActivity(Intent(requireActivity(), MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    requireActivity().finish()
                } else {
                    Toast.makeText(requireContext(),
                        response.body()?.message ?: "Registration failed. Username may already exist.",
                        Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(),
                    "Connection error. Make sure backend is running.",
                    Toast.LENGTH_LONG).show()
            } finally {
                binding.btnRegister.isEnabled  = true
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
