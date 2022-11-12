package com.schoolbus.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.navigation.fragment.findNavController
import com.github.florent37.runtimepermission.kotlin.askPermission
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource
import com.schoolbus.BuildConfig
import com.schoolbus.R
import com.schoolbus.base.BaseFragment
import com.schoolbus.databinding.FragmentNavigationBinding
import com.schoolbus.utility.gone
import com.schoolbus.utility.toast
import com.schoolbus.utility.visible
import com.tomtom.sdk.common.location.GeoCoordinate
import com.tomtom.sdk.common.measures.UnitSystem
import com.tomtom.sdk.common.route.Route
import com.tomtom.sdk.common.vehicle.Vehicle
import com.tomtom.sdk.location.LocationEngine
import com.tomtom.sdk.location.android.AndroidLocationEngine
import com.tomtom.sdk.location.mapmatched.MapMatchedLocationEngine
import com.tomtom.sdk.location.simulation.SimulationLocationEngine
import com.tomtom.sdk.maps.display.MapOptions
import com.tomtom.sdk.maps.display.TomTomMap
import com.tomtom.sdk.maps.display.camera.AnimationDuration
import com.tomtom.sdk.maps.display.camera.CameraOptions
import com.tomtom.sdk.maps.display.camera.CameraTrackingMode
import com.tomtom.sdk.maps.display.camera.OnCameraChangeListener
import com.tomtom.sdk.maps.display.gesture.OnMapLongClickListener
import com.tomtom.sdk.maps.display.location.LocationMarkerOptions
import com.tomtom.sdk.maps.display.route.Instruction
import com.tomtom.sdk.maps.display.route.RouteOptions
import com.tomtom.sdk.maps.display.ui.MapFragment
import com.tomtom.sdk.maps.display.ui.OnMapReadyCallback
import com.tomtom.sdk.navigation.*
import com.tomtom.sdk.navigation.dynamicrouting.api.DynamicRoutingApi
import com.tomtom.sdk.navigation.dynamicrouting.online.OnlineDynamicRoutingApi
import com.tomtom.sdk.navigation.guidance.GuidanceEngineFactory
import com.tomtom.sdk.navigation.guidance.GuidanceEngineOptions
import com.tomtom.sdk.navigation.ui.NavigationFragment
import com.tomtom.sdk.navigation.ui.NavigationUiOptions
import com.tomtom.sdk.routing.api.RoutePlanningCallback
import com.tomtom.sdk.routing.api.RoutePlanningResult
import com.tomtom.sdk.routing.api.RoutingApi
import com.tomtom.sdk.routing.common.RoutingError
import com.tomtom.sdk.routing.common.options.Itinerary
import com.tomtom.sdk.routing.common.options.RoutePlanningOptions
import com.tomtom.sdk.routing.common.options.calculation.ConsiderTraffic
import com.tomtom.sdk.routing.common.options.calculation.CostModel
import com.tomtom.sdk.routing.common.options.calculation.RouteType
import com.tomtom.sdk.routing.common.options.guidance.*
import com.tomtom.sdk.routing.online.OnlineRoutingApi
import java.util.*
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class NavigationFragment : BaseFragment<FragmentNavigationBinding>(),OnMapReadyCallback {

    private lateinit var tomTomMap: TomTomMap
    private var tomTomNavigation: TomTomNavigation? = null
    private lateinit var locationEngine: LocationEngine
    lateinit var routePlan: RoutePlan
    lateinit var dynamicRoutingApi: DynamicRoutingApi

    private val navigationUiOptions = NavigationUiOptions(
        voiceLanguage = Locale.getDefault(),
        keepInBackground = true,
        isSoundEnabled = true,
        units = UnitSystem.METRIC
    )
    private val navigationFragment = NavigationFragment.newInstance(navigationUiOptions)

    private val mapOptions = MapOptions(
        mapKey = BuildConfig.MAPS_API_KEY
    )
    val mapFragment = MapFragment.newInstance(mapOptions)

    private lateinit var route: Route
    private lateinit var routingApi: RoutingApi
    private lateinit var routePlanningOptions: RoutePlanningOptions
    var currentLocation: GeoCoordinate? = null

    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentNavigationBinding = FragmentNavigationBinding.inflate(inflater,container,false)

    override fun Init(view: View) {
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        /**
         * INIT Location Engine
         */
        initLocationEngine()

        /**
         * INIT Routing
         */
        initRouting()

        /**
         * INIT Navigation
         */
        initNavigation()

        /**
         * INIT Map
         */
        initMap()

        initClick()
    }

    private fun initLocationEngine() {
        locationEngine = AndroidLocationEngine(context = requireContext())
    }

    private fun initMap() {
        childFragmentManager.beginTransaction()
            .replace(R.id.map_container, mapFragment)
            .commit()
        mapFragment.getMapAsync(this)
    }

    private fun initRouting() {
        routingApi = OnlineRoutingApi.create(requireContext(), BuildConfig.MAPS_API_KEY)
        dynamicRoutingApi = OnlineDynamicRoutingApi.create(routingApi)
    }

    override fun onMapReady(map: TomTomMap) {
        this.tomTomMap = map
        if (isLocationEnabled()) {
            if (checkPermissions()){
                getCurrentLocation()
                showUserLocation()
            }else {
                requestPermission()
            }
        }else {
            openSetting()
        }
    }

    @SuppressLint("MissingPermission", "SetTextI18n")
    private fun getCurrentLocation(){
        /**
         * Get Current Location
         */
        mFusedLocationClient.getCurrentLocation(100, CancellationTokenSource().token).addOnSuccessListener {
            currentLocation = GeoCoordinate(it.latitude, it.longitude)
            val newCameraOptions = CameraOptions(
                position = currentLocation,
                zoom = 13.5
            )
            tomTomMap.animateCamera(newCameraOptions, AnimationDuration(3.toDuration(DurationUnit.SECONDS)))
            Log.e("TAG", "getLocation: ${it.longitude} -- ${it.latitude}")
        }.addOnFailureListener {
            Log.e("TAG", "getLocation: ${it.message}")
        }
    }

    private fun showUserLocation() {
        /**
         * Set Location
         */
        tomTomMap.setLocationEngine(locationEngine)

        /**
         * INIT LongClick
         */
        tomTomMap.addOnMapLongClickListener(onMapLongClickListener)

        /**
         * Enable Current Location Marker
         */
        tomTomMap.enableLocationMarker(LocationMarkerOptions(LocationMarkerOptions.Type.POINTER))

        /**
         * Enable Location Engine
         */
        locationEngine.enable()
    }

    private val onMapLongClickListener = OnMapLongClickListener { geoCoordinate ->
        tomTomMap.clear()
        currentLocation?.let { planRoute(it, geoCoordinate) }
        true
    }

    private fun planRoute(
        departureCoordinate: GeoCoordinate,
        destinationCoordinate: GeoCoordinate
    ) {
        routePlanningOptions = RoutePlanningOptions(
            itinerary = Itinerary(
                origin = departureCoordinate,
                destination = destinationCoordinate
            ),
            costModel = CostModel(routeType = RouteType.Fast, considerTraffic = ConsiderTraffic.YES),
            guidanceOptions = GuidanceOptions(
                instructionType = InstructionType.TEXT
            ),
            vehicle = Vehicle.Car()
        )

        routingApi.planRoute(
            routePlanningOptions,
            object : RoutePlanningCallback {
                override fun onError(error: RoutingError) {
                    requireContext().toast(error.message.toString())
                    Log.e("TAG", "onError: ${error.printStackTrace()}")
                }

                override fun onSuccess(result: RoutePlanningResult) {
                    route = result.routes.firstOrNull() ?: return
                    drawRoute()
                }

                override fun onRoutePlanned(route: Route) = Unit
            }
        )
    }

    private fun drawRoute() {
        val instructions = route.mapInstructions()
        val geometry = route.legs.flatMap { it.points }
        val routeOptions = RouteOptions(
            geometry = geometry,
            destinationMarkerVisible = true,
            departureMarkerVisible = true,
            instructions = instructions
        )
        tomTomMap.addRoute(routeOptions)
        tomTomMap.zoomToRoutes(40)

        binding.btnStart.visible()
    }

    private fun Route.mapInstructions(): List<Instruction> {
        val routeInstructions = legs.flatMap { routeLeg -> routeLeg.instructions }
        return routeInstructions.map {
            Instruction(
                routeOffset = it.routeOffset,
                combineWithNext = it.isPossibleToCombineWithNext
            )
        }
    }

    private fun initClick(){
        binding.btnStart.setOnClickListener {
            routePlan = RoutePlan(route, routePlanningOptions)
//            findNavController().navigate(R.id.action_navigationFragment_to_mapFragment)
//            startNavigation()
            tomTomNavigation?.start(routePlan)
        }
    }

    private fun initNavigation() {
        val guidanceEngine = GuidanceEngineFactory.create(
            requireActivity(),
            GuidanceEngineOptions(units = UnitSystem.METRIC)
        )
        val navigationConfiguration = NavigationConfiguration(
            context = requireContext(),
            apiKey = BuildConfig.MAPS_API_KEY,
            locationEngine = locationEngine,
            dynamicRoutingApi = dynamicRoutingApi,
            guidanceEngine = guidanceEngine
        )
        tomTomNavigation = TomTomNavigation.create(navigationConfiguration)
    }

    private fun initNavigationFragment() {
        childFragmentManager.beginTransaction()
            .replace(R.id.navigation_fragment_container, navigationFragment)
            .commit()
    }

    private fun startNavigation(){
        initNavigationFragment()
        tomTomNavigation?.let { navigationFragment.setTomTomNavigation(it) }
        val routePlan = RoutePlan(route, routePlanningOptions)
        navigationFragment.startNavigation(routePlan)

        navigationFragment.addNavigationListener(navigationListener)
        tomTomNavigation?.addOnProgressUpdateListener(onProgressUpdateListener)
    }

    private val navigationListener = object : NavigationFragment.NavigationListener {
        override fun onStarted() {
            binding.btnStart.gone()

            tomTomMap.addOnCameraChangeListener(onCameraChangeListener)
            tomTomMap.changeCameraTrackingMode(CameraTrackingMode.FOLLOW_ROUTE)
            tomTomMap.enableLocationMarker(LocationMarkerOptions(LocationMarkerOptions.Type.CHEVRON))
            setMapMatchedLocationEngine()
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

    private val onProgressUpdateListener = OnProgressUpdateListener {
        tomTomMap.routes.first().progress = it.distanceAlongRoute
    }

    private val onCameraChangeListener by lazy {
        OnCameraChangeListener {
            val cameraTrackingMode = tomTomMap.cameraTrackingMode()
            if (cameraTrackingMode == CameraTrackingMode.FOLLOW_ROUTE) {
                navigationFragment.navigationView?.showSpeedView()
            } else {
                navigationFragment.navigationView?.hideSpeedView()
            }
        }
    }

    private fun setMapMatchedLocationEngine() {
        val mapMatchedLocationEngine = tomTomNavigation?.let { MapMatchedLocationEngine(it) }
        mapMatchedLocationEngine?.let { tomTomMap.setLocationEngine(it) }
        mapMatchedLocationEngine?.enable()
    }

    private fun stopNavigation() {
        binding.btnStart.gone()

        navigationFragment.stopNavigation()
        tomTomMap.changeCameraTrackingMode(CameraTrackingMode.NONE)
        tomTomMap.enableLocationMarker(LocationMarkerOptions(LocationMarkerOptions.Type.POINTER))
        navigationFragment.removeNavigationListener(navigationListener)
        tomTomNavigation?.removeOnProgressUpdateListener(onProgressUpdateListener)
        tomTomMap.clear()
        showUserLocation()
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            requireActivity().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun checkPermissions(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return false
    }

    private fun requestPermission() {
        askPermission(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) {
            getCurrentLocation()
            showUserLocation()
        }.onDeclined { e ->
            if (e.hasDenied()) {
                AlertDialog.Builder(requireActivity())
                    .setMessage("Permission is required for map")
                    .setPositiveButton("yes") { _, _ ->
                        e.askAgain()
                    } //ask again
                    .setNegativeButton("no") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }

            if (e.hasForeverDenied()) {
                AlertDialog.Builder(requireActivity())
                    .setTitle("Permission is required for map")
                    .setMessage("You denied us more than two times our permission, Please you can manually allow permission in setting")
                    .setPositiveButton("yes") { _, _ ->
                        e.goToSettings()
                    } //ask again
                    .setNegativeButton("no") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        }
    }

    private var checkPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Log.e("TAG", "After Setting: ")

            if (isLocationEnabled()) {
                if (checkPermissions()){
                    showUserLocation()
                }else {
                    requestPermission()
                }
            }else {
                openSetting()
            }
        }

    private fun openSetting(){
        AlertDialog.Builder(requireActivity())
            .setMessage("Please on your GPS for getting the current location")
            .setPositiveButton("yes") { _, _ ->
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                checkPermissionLauncher.launch(intent)
            } //ask again
            .setNegativeButton("no") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}