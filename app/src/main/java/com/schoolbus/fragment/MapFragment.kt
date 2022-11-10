package com.schoolbus.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.schoolbus.BuildConfig
import com.schoolbus.R
import com.schoolbus.base.BaseFragment
import com.schoolbus.databinding.FragmentMapBinding
import com.schoolbus.fragment.NavigationFragment.Companion.dynamicRoutingApi
import com.schoolbus.fragment.NavigationFragment.Companion.routePlan
import com.schoolbus.utility.gone
import com.schoolbus.utility.toast
import com.tomtom.sdk.common.measures.UnitSystem
import com.tomtom.sdk.location.LocationEngine
import com.tomtom.sdk.location.android.AndroidLocationEngine
import com.tomtom.sdk.location.mapmatched.MapMatchedLocationEngine
import com.tomtom.sdk.maps.display.camera.CameraTrackingMode
import com.tomtom.sdk.maps.display.camera.OnCameraChangeListener
import com.tomtom.sdk.maps.display.location.LocationMarkerOptions
import com.tomtom.sdk.navigation.NavigationConfiguration
import com.tomtom.sdk.navigation.NavigationError
import com.tomtom.sdk.navigation.OnProgressUpdateListener
import com.tomtom.sdk.navigation.TomTomNavigation
import com.tomtom.sdk.navigation.dynamicrouting.api.DynamicRoutingApi
import com.tomtom.sdk.navigation.guidance.GuidanceEngineFactory
import com.tomtom.sdk.navigation.guidance.GuidanceEngineOptions
import com.tomtom.sdk.navigation.ui.NavigationFragment
import com.tomtom.sdk.navigation.ui.NavigationUiOptions
import java.util.*

class MapFragment : BaseFragment<FragmentMapBinding>() {

    private lateinit var locationEngine: LocationEngine
    private val navigationUiOptions = NavigationUiOptions(
        voiceLanguage = Locale.getDefault(),
        keepInBackground = true,
        isSoundEnabled = true,
        units = UnitSystem.METRIC
    )
    private val navigationFragment = NavigationFragment.newInstance(navigationUiOptions)
    private var tomTomNavigation: TomTomNavigation? = null

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentMapBinding = FragmentMapBinding.inflate(inflater,container,false)

    override fun Init(view: View) {
        /**
         * INIT Location Engine
         */
        initLocationEngine()

        /**
         * INIT Navigation
         */
        initNavigation()

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        /**
         * Start Navigation
         */
        startNavigation()
    }

    private fun initLocationEngine() {
        locationEngine = AndroidLocationEngine(context = requireContext())
    }

    private fun initNavigation() {
        val guidanceEngine = GuidanceEngineFactory.create(
            requireActivity(),
            GuidanceEngineOptions(units = UnitSystem.METRIC)
        )
        val navigationConfiguration = NavigationConfiguration(
            context = requireContext(),
            apiKey = BuildConfig.ROUTING_API_KEY,
            locationEngine = locationEngine,
            dynamicRoutingApi = dynamicRoutingApi,
            guidanceEngine = guidanceEngine
        )
        tomTomNavigation = TomTomNavigation.create(navigationConfiguration)
    }

    private fun startNavigation(){
        val navigationFragment =
            childFragmentManager.findFragmentById(R.id.navigation_fragment) as NavigationFragment

        tomTomNavigation?.let { navigationFragment.setTomTomNavigation(it) }
        navigationFragment.startNavigation(routePlan)
        navigationFragment.addNavigationListener(navigationListener)
    }

    private val navigationListener = object : NavigationFragment.NavigationListener {
        override fun onStarted() {
            Log.e("TAG", "onStarted: Started")
        }

        override fun onFailed(error: NavigationError) {
            requireContext().toast(error.message)
            Log.e("TAG", "onFailed: ${error.message}")
            stopNavigation()
        }

        override fun onStopped() {
            stopNavigation()
        }
    }

    private fun stopNavigation() {
        navigationFragment.stopNavigation()
        navigationFragment.removeNavigationListener(navigationListener)
    }
}