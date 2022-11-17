package com.schoolbus.activity

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.schoolbus.R

class SplashActivity : AppCompatActivity() {
    private val PermissionsRequestCode = 123
    private lateinit var managePermissions: ManagePermissions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

//        val list = listOf<String>(
//            Manifest.permission.CAMERA,
//            Manifest.permission.READ_EXTERNAL_STORAGE
//        )
//
//        // Initialize a new instance of ManagePermissions class
//        managePermissions = ManagePermissions(this, list, PermissionsRequestCode)
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
//            managePermissions.checkPermissions()

        Handler(Looper.getMainLooper()).postDelayed({
            val mainIntent = Intent(this, MainActivity::class.java)
            startActivity(mainIntent)
            finish()
        }, 3000)
    }
}