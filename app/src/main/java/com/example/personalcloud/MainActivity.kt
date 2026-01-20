package com.example.personalcloud

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {
    private lateinit var serverManager: ServerManager
    private lateinit var btnStartServer: Button
    private lateinit var btnStopServer: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvAccessUrl: TextView
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnSaveCredentials: Button
    private lateinit var tvLocalIp: TextView

    companion object {
        private const val STORAGE_PERMISSION_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        serverManager = ServerManager(this)

        checkPermissions()
        setupClickListeners()
        loadCredentials()
        updateUI()
    }

    private fun initViews() {
        btnStartServer = findViewById(R.id.btnStartServer)
        btnStopServer = findViewById(R.id.btnStopServer)
        tvStatus = findViewById(R.id.tvStatus)
        tvAccessUrl = findViewById(R.id.tvAccessUrl)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnSaveCredentials = findViewById(R.id.btnSaveCredentials)
        tvLocalIp = findViewById(R.id.tvLocalIp)
    }

    private fun setupClickListeners() {
        btnStartServer.setOnClickListener {
            if (hasStoragePermission()) {
                startServerService()
            } else {
                requestStoragePermission()
            }
        }

        btnStopServer.setOnClickListener {
            stopServerService()
        }

        btnSaveCredentials.setOnClickListener {
            saveCredentials()
        }
    }

    private fun checkPermissions() {
        if (!hasStoragePermission()) {
            requestStoragePermission()
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            true // Scoped storage in Android 11+
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Toast.makeText(this, "Storage access granted", Toast.LENGTH_SHORT).show()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startServerService() {
        val username = etUsername.text.toString()
        val password = etPassword.text.toString()

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please set username and password", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, ServerService::class.java).apply {
            putExtra("username", username)
            putExtra("password", password)
            action = "START"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        updateUI()
    }

    private fun stopServerService() {
        val intent = Intent(this, ServerService::class.java).apply {
            action = "STOP"
        }
        startService(intent)
        updateUI()
    }

    private fun saveCredentials() {
        val username = etUsername.text.toString()
        val password = etPassword.text.toString()

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Username and password cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences("CloudPrefs", MODE_PRIVATE)
        prefs.edit().apply {
            putString("username", username)
            putString("password", password)
            apply()
        }

        Toast.makeText(this, "Credentials saved", Toast.LENGTH_SHORT).show()
    }

    private fun loadCredentials() {
        val prefs = getSharedPreferences("CloudPrefs", MODE_PRIVATE)
        etUsername.setText(prefs.getString("username", "admin"))
        etPassword.setText(prefs.getString("password", "password"))
    }

    private fun updateUI() {
        val isRunning = ServerService.isRunning
        btnStartServer.isEnabled = !isRunning
        btnStopServer.isEnabled = isRunning

        if (isRunning) {
            tvStatus.text = "Server Status: RUNNING ✓"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            val ip = getLocalIpAddress()
            tvLocalIp.text = "Local IP: $ip"
            tvAccessUrl.text = "Access URL: http://$ip:8080"
        } else {
            tvStatus.text = "Server Status: STOPPED"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            tvLocalIp.text = "Local IP: N/A"
            tvAccessUrl.text = "Access URL: N/A"
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address.address.size == 4) {
                        return address.hostAddress ?: "Unknown"
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "Unknown"
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }
}