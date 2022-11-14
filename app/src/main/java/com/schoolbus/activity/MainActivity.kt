package com.schoolbus.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.florent37.runtimepermission.kotlin.askPermission
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.schoolbus.BuildConfig
import com.schoolbus.R
import com.schoolbus.base.BaseActivity
import com.schoolbus.databinding.ActivityMainBinding
import com.schoolbus.model.RouteResponseItem
import com.schoolbus.utility.*
import com.tomtom.sdk.common.location.GeoCoordinate
import com.tomtom.sdk.common.measures.UnitSystem
import com.tomtom.sdk.common.route.Route
import com.tomtom.sdk.common.vehicle.Vehicle
import com.tomtom.sdk.location.android.AndroidLocationEngine
import com.tomtom.sdk.location.mapmatched.MapMatchedLocationEngine
import com.tomtom.sdk.maps.display.MapOptions
import com.tomtom.sdk.maps.display.TomTomMap
import com.tomtom.sdk.maps.display.camera.CameraOptions
import com.tomtom.sdk.maps.display.camera.CameraTrackingMode
import com.tomtom.sdk.maps.display.camera.OnCameraChangeListener
import com.tomtom.sdk.maps.display.location.LocationMarkerOptions
import com.tomtom.sdk.maps.display.route.Instruction
import com.tomtom.sdk.maps.display.route.RouteOptions
import com.tomtom.sdk.maps.display.ui.MapFragment
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
import com.tomtom.sdk.routing.common.options.guidance.GuidanceOptions
import com.tomtom.sdk.routing.common.options.guidance.InstructionType
import com.tomtom.sdk.routing.online.OnlineRoutingApi
import java.lang.reflect.Type
import java.util.*

class MainActivity : BaseActivity<ActivityMainBinding>() {

    private var mLoaded = false

    /**
     * Map
     */
    private lateinit var tomtomNavigation: TomTomNavigation
    private lateinit var route: Route
    private lateinit var planRouteOptions: RoutePlanningOptions
    private lateinit var locationEngine: AndroidLocationEngine
    private lateinit var tomTomMap: TomTomMap
    private lateinit var routingApi: RoutingApi
    private lateinit var dynamicRoutingApi: DynamicRoutingApi
    private lateinit var navigationFragment: NavigationFragment

    private val mapOptions = MapOptions(
        mapKey = BuildConfig.MAPS_API_KEY
    )
    val mapFragment = MapFragment.newInstance(mapOptions)

    var coordinate: ArrayList<RouteResponseItem> = arrayListOf()

    override fun setBinding(layoutInflater: LayoutInflater): ActivityMainBinding =
        ActivityMainBinding.inflate(layoutInflater)

    override fun InitView() {
        /**
         * request for show website
         */
        requestForWebView()
        initClick()

        /**
         * INIT Map
         */
        initMap()

        locationEngine = AndroidLocationEngine(this)

        if (ContextCompat.checkSelfPermission(this@MainActivity,
                Manifest.permission.ACCESS_FINE_LOCATION) !==
            PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this@MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                ActivityCompat.requestPermissions(this@MainActivity,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            } else {
                ActivityCompat.requestPermissions(this@MainActivity,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            }
        }

        // Lets check for FINE LOCATION permissions ...
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        locationEngine.enable()

        /**
         * INIT Routing
         */
        initRouting()

        val guidanceEngine = GuidanceEngineFactory.create(
            this,
            GuidanceEngineOptions(units = UnitSystem.METRIC)
        )
        val navigationConfiguration = NavigationConfiguration(
            context = this,
            apiKey = BuildConfig.MAPS_API_KEY,
            locationEngine = locationEngine,
            dynamicRoutingApi = dynamicRoutingApi,
            guidanceEngine = guidanceEngine
        )
        tomtomNavigation = TomTomNavigation.create(navigationConfiguration)

        mapFragment.getMapAsync { map ->
            tomTomMap = map
            val initialOptions = CameraOptions(zoom = 16.0)
            tomTomMap.moveCamera(initialOptions)
            enableUserLocation()
        }

        val navigationUiOptions = NavigationUiOptions(
            keepInBackground = true
        )
        navigationFragment = NavigationFragment.newInstance(navigationUiOptions)
        supportFragmentManager.beginTransaction()
            .add(R.id.navigation_fragment_container, navigationFragment)
            .commitNow()

        navigationFragment.setTomTomNavigation(tomtomNavigation)
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty() && grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED) {
                    if ((ContextCompat.checkSelfPermission(this@MainActivity,
                            Manifest.permission.ACCESS_FINE_LOCATION) ===
                                PackageManager.PERMISSION_GRANTED)) {
                        Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
                }
                return
            }
        }
    }


    private fun initClick() {
        binding.btnTryAgain.setOnClickListener {
            binding.webview.gone()
            binding.progressBar.visible()
            binding.layoutSplash.visible()
            binding.layoutNoInternet.gone()
            requestForWebView()
        }
    }

    private fun requestForWebView() {
        if (!mLoaded) {
            requestWebView()
            Handler(Looper.getMainLooper()).postDelayed({
                binding.progressBar.visible()
                binding.webview.visible()
            }, 3000)

        } else {
            binding.webview.visible()
            binding.progressBar.gone()
            binding.layoutSplash.gone()
            binding.layoutNoInternet.gone()
        }

    }

    @SuppressLint("SetJavaScriptEnabled")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun requestWebView() {
        /**
         * Layout of WebView screen View
         */
        if (NetworkHelper(this).isNetworkConnected()) {
            binding.webview.visible()
            binding.layoutNoInternet.gone()
            binding.webview.loadUrl(BuildConfig.WEB_URL)
        } else {
            binding.progressBar.gone()
            binding.webview.gone()
            binding.layoutSplash.gone()
            binding.layoutNoInternet.visible()
            return
        }

        binding.webview.isFocusable = true
        binding.webview.isFocusableInTouchMode = true
        binding.webview.settings.javaScriptEnabled = true
        loadWebInterface()


        binding.webview.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        binding.webview.settings.setRenderPriority(WebSettings.RenderPriority.HIGH)
        binding.webview.settings.cacheMode = WebSettings.LOAD_DEFAULT
        binding.webview.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        binding.webview.settings.domStorageEnabled = true
        binding.webview.settings.setAppCacheEnabled(true)
        binding.webview.settings.databaseEnabled = true

        /**
         * this force use ChromeWebClient
         */
        binding.webview.settings.setSupportMultipleWindows(false)
        binding.webview.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean {

                Log.e("TAG", "shouldOverrideUrlLoading: $url")
                if (NetworkHelper(this@MainActivity).isNetworkConnected()) {
                    url?.let { view.loadUrl(it) }
                } else {
                    binding.progressBar.gone()
                    binding.webview.gone()
                    binding.layoutSplash.gone()
                    binding.layoutNoInternet.visible()
                }

                return true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (!binding.progressBar.isVisible()) {
                    binding.progressBar.visible()
                }
            }

            override fun onLoadResource(view: WebView, url: String) {
                super.onLoadResource(view, url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                mLoaded = true
                if (binding.progressBar.isVisible())
                    binding.progressBar.gone()

                // check if layoutSplash is still there, get it away!
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.layoutSplash.gone()
                }, 2000)
            }
        }
    }

    private fun loadWebInterface() {
        binding.webview.addJavascriptInterface(WebAppInterface(this, onGetStop = {
            val type: Type = object : TypeToken<ArrayList<RouteResponseItem>>() {}.type
            coordinate = Gson().fromJson(it, type)

            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                Toast.makeText(this, "landscape", Toast.LENGTH_SHORT).show();
            } else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                Toast.makeText(this, "portrait", Toast.LENGTH_SHORT).show();
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                binding.clMap.visible()

            }
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
//            Toast.makeText(this, requestedOrientation.toString(), Toast.LENGTH_SHORT).show()
//            Toast.makeText(
//                this, MainActivity.getResources().getConfiguration().orientation, Toast.LENGTH_SHORT).show()
            runOnUiThread {
                binding.clMap.visible()
            }

            Handler(Looper.getMainLooper()).postDelayed({
                val departureCoordinate = GeoCoordinate(
                    coordinate[0].geometry?.coordinates?.get(1)?.toDouble()
                        ?: 0.0, coordinate[0].geometry?.coordinates?.get(0)?.toDouble() ?: 0.0
                )

                val destinationCoordinate = GeoCoordinate(
                    coordinate[1].geometry?.coordinates?.get(1)?.toDouble()
                        ?: 0.0, coordinate[1].geometry?.coordinates?.get(0)?.toDouble() ?: 0.0
                )

                planRoute(departureCoordinate, destinationCoordinate)
            }, 2000)

        }), "Android")
    }

    private fun initMap() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.map_container, mapFragment)
            .commit()
    }

    private fun enableUserLocation() {
        // Getting locations to the map
        tomTomMap.setLocationEngine(locationEngine)
        val locationMarker = LocationMarkerOptions(LocationMarkerOptions.Type.POINTER)
        tomTomMap.enableLocationMarker(locationMarker)
    }

    private fun initRouting() {
        routingApi = OnlineRoutingApi.create(this, BuildConfig.MAPS_API_KEY)
        dynamicRoutingApi = OnlineDynamicRoutingApi.create(routingApi)
    }


    private fun planRoute(
        departureCoordinate: GeoCoordinate,
        destinationCoordinate: GeoCoordinate
    ) {
        planRouteOptions = RoutePlanningOptions(
            itinerary = Itinerary(
                origin = departureCoordinate,
                destination = destinationCoordinate
            ),
            guidanceOptions = GuidanceOptions(
                instructionType = InstructionType.TEXT
            ),
            vehicle = Vehicle.Car()
        )

        routingApi.planRoute(
            planRouteOptions,
            object : RoutePlanningCallback {
                override fun onError(error: RoutingError) {
                    toast(error.message.toString())
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

        Handler(Looper.getMainLooper()).postDelayed({
            val routePlan = RoutePlan(route, planRouteOptions)
            navigationFragment.startNavigation(routePlan)
            navigationFragment.addNavigationListener(navigationListener)
            tomtomNavigation.addOnProgressUpdateListener(onProgressUpdateListener)
        }, 3000)
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

    private val navigationListener = object : NavigationFragment.NavigationListener {
        override fun onStarted() {
            tomTomMap.addOnCameraChangeListener(onCameraChangeListener)
            tomTomMap.changeCameraTrackingMode(CameraTrackingMode.FOLLOW_ROUTE)
            tomTomMap.enableLocationMarker(LocationMarkerOptions(LocationMarkerOptions.Type.CHEVRON))
            setMapMatchedLocationEngine()
        }

        override fun onFailed(error: NavigationError) {
            toast(error.message)
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
        val mapMatchedLocationEngine = MapMatchedLocationEngine(tomtomNavigation)
        tomTomMap.setLocationEngine(mapMatchedLocationEngine)
        mapMatchedLocationEngine.enable()
    }

    private fun stopNavigation() {
        navigationFragment.stopNavigation()
        navigationFragment.removeNavigationListener(navigationListener)
        tomTomMap.changeCameraTrackingMode(CameraTrackingMode.NONE)
        tomTomMap.enableLocationMarker(LocationMarkerOptions(LocationMarkerOptions.Type.POINTER))
        tomtomNavigation.removeOnProgressUpdateListener(onProgressUpdateListener)
        tomTomMap.clear()
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun checkPermissions(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
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

        }.onDeclined { e ->
            if (e.hasDenied()) {
                AlertDialog.Builder(this)
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
                AlertDialog.Builder(this)
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
            if (isLocationEnabled()) {
                if (!checkPermissions()) {
                    requestPermission()
                }
            } else {
                openSetting()
            }
        }

    private fun openSetting() {
        AlertDialog.Builder(this)
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