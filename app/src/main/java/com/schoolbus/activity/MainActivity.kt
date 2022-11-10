package com.schoolbus.activity

import android.view.LayoutInflater
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.schoolbus.R
import com.schoolbus.base.BaseActivity
import com.schoolbus.databinding.ActivityMainBinding

class MainActivity : BaseActivity<ActivityMainBinding>() {

    private lateinit var navController: NavController

    override fun setBinding(layoutInflater: LayoutInflater): ActivityMainBinding = ActivityMainBinding.inflate(layoutInflater)

    override fun InitView() {
        navController = Navigation.findNavController(this, R.id.authHostFragment)
        navController.navInflater.inflate(R.navigation.main_navigation)
        navController.addOnDestinationChangedListener(onDestinationChanged)
    }

    private val onDestinationChanged =
        NavController.OnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {

            }
        }

    override fun onBackPressed() {
        when (navController.currentDestination?.id) {
            R.id.mainFragment ->{
                finishAffinity()
            }
        }
    }
}