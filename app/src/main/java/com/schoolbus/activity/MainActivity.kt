package com.schoolbus.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.net.http.SslError
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.florent37.runtimepermission.kotlin.askPermission
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
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
    private val permission = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.MODIFY_AUDIO_SETTINGS,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
    private val requestCode = 1

    var context: Context? = null

    var mGeoLocationRequestOrigin: String? = null
    var mGeoLocationCallback: GeolocationPermissions.Callback? = null

    private val REQUEST_CHECK_SETTINGS: Int = 101
    private var mLoaded = false

    lateinit var locationCallback: LocationCallback
    lateinit var locationRequest: LocationRequest
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var requestingLocationUpdates = false
    var previousLocation: Location? = null
    var pastDistances = ArrayList<Float>()
    val MAX_DISTANCE_COUNTS = 5
    val HIDE_MAP_ON_MOVE_DISTANCE_THRESHOLD = 20
    val LOCATION_UPDATE_INTERVAL_MILLIS = 1000L

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

    @RequiresApi(Build.VERSION_CODES.O)
    override fun InitView() {
        val action: String? = intent?.action
        val data: Uri? = intent?.data
        startScan()
        /**
         * request for show website
         */
        requestForWebView()
        setWebClient()
        initClick()

        /**
         * INIT Map
         */
        initMap()

        locationEngine = AndroidLocationEngine(this)

        if (ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) !==
            PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this@MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            ) {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1
                )
            } else {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1
                )
            }
        }

        initLocationFeatures()

    }

    private fun isPermissionGranted(): Boolean {
        permission.forEach {
            if (ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED)
                return false
        }

        return true
    }

    private fun askPermissions() {
        ActivityCompat.requestPermissions(this, permission, requestCode)
    }

    private fun busStopped() {
        binding.tvBusMovingOverlay.gone()
    }

    private fun busMoving() {
        binding.tvBusMovingOverlay.visible()
    }

    override fun onResume() {
        super.onResume()

//        Toast.makeText(this, "onresume called main", Toast.LENGTH_SHORT).show()

//        if (requestingLocationUpdates) startLocationUpdates()
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun initLocationFeatures() {
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
            voiceLanguage = Locale.getDefault(),
//            keepInBackground = true,
            isSoundEnabled = true,
            units = UnitSystem.METRIC
        )
        navigationFragment = NavigationFragment.newInstance(navigationUiOptions)
        supportFragmentManager.beginTransaction()
            .add(R.id.navigation_fragment_container, navigationFragment)
            .commitNow()

        navigationFragment.setTomTomNavigation(tomtomNavigation)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.create().apply {
            interval = LOCATION_UPDATE_INTERVAL_MILLIS
            fastestInterval = LOCATION_UPDATE_INTERVAL_MILLIS
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener { locationSettingsResponse ->
            // All location settings are satisfied. The client can initialize
            // location requests here.
            // ...
            requestingLocationUpdates = true
            startLocationUpdates()
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    exception.startResolutionForResult(this@MainActivity, REQUEST_CHECK_SETTINGS)
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                    finish()
                }
            }
        }

//        val lm: LocationManager
//        val location: Location?
//        lm = applicationContext.getSystemService(LOCATION_SERVICE) as LocationManager
//        location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
//        location!!.bearing //speed
//        println("Show Speed - 293" + location)
//
//        val currentSpeed = location.speed * 3600 / 1000
//        println("Show currentSpeed - 296" + currentSpeed)


        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {

                val currentLocation = locationResult.lastLocation
                var distance = 0.0F
                previousLocation?.let { previousLocation ->
                    currentLocation?.let { currentLocation ->
                        distance = previousLocation.distanceTo(currentLocation)
                    }
                }

                pastDistances.add(distance)
                previousLocation = currentLocation
                while (pastDistances.size > MAX_DISTANCE_COUNTS) {
                    pastDistances.removeAt(0)
                }
                var totalDistance = pastDistances.reduce { acc, fl -> acc + fl }

                if (totalDistance > HIDE_MAP_ON_MOVE_DISTANCE_THRESHOLD) {
                    busMoving()
                } else {
                    busStopped()
                }
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun initClick() {
        binding.btnTryAgain.setOnClickListener {
            binding.webview.gone()
            binding.progressBar.visible()
            binding.layoutSplash.visible()
            binding.layoutNoInternet.gone()
            requestForWebView()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
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

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("SetJavaScriptEnabled")
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
        binding.webview.settings.setGeolocationEnabled(true)

        binding.webview.settings.allowFileAccessFromFileURLs = true;
        binding.webview.settings.allowUniversalAccessFromFileURLs = true;

        binding.webview.settings.javaScriptCanOpenWindowsAutomatically = true
        binding.webview.settings.domStorageEnabled = true
        binding.webview.settings.allowContentAccess = true
        binding.webview.settings.setAllowFileAccessFromFileURLs(true)
        binding.webview.settings.setAllowUniversalAccessFromFileURLs(true)
        binding.webview.settings.safeBrowsingEnabled = true
        binding.webview.settings.mediaPlaybackRequiresUserGesture = false

        binding.webview.webViewClient = WebViewClient()
        binding.webview.setWebChromeClient(object : WebChromeClient() {
            // Grant permissions for cam
            override fun onPermissionRequest(request: PermissionRequest) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    request.grant(request.resources);
                }
            }
        })

        binding.webview.webViewClient = object : WebViewClient() {
            override
            fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.proceed()
            }
        }

        loadWebInterface()


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


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty() && grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    if ((ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) ===
                                PackageManager.PERMISSION_GRANTED)
                    ) {
                        Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show()
                        // init permissions related features
                        initLocationFeatures()
                    }
                } else {
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
                    finish()
                }
                return
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)


        if (requestCode == REQUEST_CHECK_SETTINGS) {
            if (resultCode == RESULT_OK) {
                startLocationUpdates()
            } else {
                finish()
            }
        }
    }

    private fun setWebClient() {

        binding.webview.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {

                if (ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                    != PackageManager.PERMISSION_GRANTED
                ) {

                    if (ActivityCompat.shouldShowRequestPermissionRationale(
                            this@MainActivity,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    ) {
                        AlertDialog.Builder(this@MainActivity)
                            .setMessage("Please turn ON the GPS to make app work smoothly")
                            .setNeutralButton(
                                android.R.string.ok,
                                DialogInterface.OnClickListener { dialogInterface, i ->
                                    mGeoLocationCallback = callback
                                    mGeoLocationRequestOrigin = origin
                                    ActivityCompat.requestPermissions(
                                        this@MainActivity,
                                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001
                                    )

                                })
                            .show()

                    } else {
                        //no explanation need we can request the locatio
                        mGeoLocationCallback = callback
                        mGeoLocationRequestOrigin = origin
                        ActivityCompat.requestPermissions(
                            this@MainActivity,
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001
                        )
                    }
                } else {
                    callback!!.invoke(origin, true, true)
                }

            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
            }


        }
    }

    private fun loadWebInterface() {
        binding.webview.addJavascriptInterface(
            WebAppInterface(
                mContext = this,
                onGetStop = { loadStops(it) },
                hideTomTom = { hideTomTom() },
                showTomTom = { showTomTom() }), "Android"
        )
    }

    private fun loadStops(it: String) {
        val type: Type = object : TypeToken<ArrayList<RouteResponseItem>>() {}.type
        coordinate = Gson().fromJson(it, type)

        runOnUiThread {
            binding.clMap.visible()
        }

        Handler(Looper.getMainLooper()).postDelayed({

            val srcAdd = GeoCoordinate(
                coordinate[0].geometry?.coordinates?.get(1)?.toDouble() ?: 0.0,
                coordinate[0].geometry?.coordinates?.get(0)?.toDouble() ?: 0.0
            )

            val desAdd = GeoCoordinate(
                coordinate[0].geometry?.coordinates?.get(1)?.toDouble() ?: 0.0,
                coordinate[1].geometry?.coordinates?.get(0)?.toDouble() ?: 0.0
            )

//            for (i in 0 until coordinate.size) {
//                coords_markers.add(GeoCoordinate(
//                    coordinate[i].geometry?.coordinates?.get(1)?.toDouble()
//                        ?: 0.0, coordinate[i].geometry?.coordinates?.get(0)?.toDouble() ?: 0.0
//                ))
//            }

//            val wayPoints = GeoCoordinate
//
//            for (j in 1 until coordinate.size - 1) {
//                wayPoints = wayPoints + (if (wayPoints == "") "" else "%7C") + coordinate.get(j) + "," + coordinate.get(j)
//            }
//            wayPoints = "&waypoints=$wayPoints"
//            println("wayPoints" + wayPoints)
//            fun main(args: Array<String>) {
//
//            for (i in coordinate.indices) {
//                println(coordinate[i])
//            }
//        }

            val departureCoordinate = GeoCoordinate(
                coordinate[0].geometry?.coordinates?.get(1)?.toDouble()
                    ?: 0.0, coordinate[0].geometry?.coordinates?.get(0)?.toDouble() ?: 0.0
            )

            val customCoordinate = GeoCoordinate(
                coordinate[1].geometry?.coordinates?.get(1)?.toDouble()
                    ?: 0.0, coordinate[1].geometry?.coordinates?.get(0)?.toDouble() ?: 0.0
            )
            val customCoordinate1 = GeoCoordinate(
                coordinate[2].geometry?.coordinates?.get(1)?.toDouble()
                    ?: 0.0, coordinate[2].geometry?.coordinates?.get(0)?.toDouble() ?: 0.0
            )

            val customCoordinate2 = GeoCoordinate(
                coordinate[3].geometry?.coordinates?.get(1)?.toDouble()
                    ?: 0.0, coordinate[3].geometry?.coordinates?.get(0)?.toDouble() ?: 0.0
            )

            val customCoordinate3 = GeoCoordinate(
                coordinate[4].geometry?.coordinates?.get(1)?.toDouble()
                    ?: 0.0, coordinate[4].geometry?.coordinates?.get(0)?.toDouble() ?: 0.0
            )

            val destinationCoordinate = GeoCoordinate(
                coordinate[6].geometry?.coordinates?.get(1)?.toDouble()
                    ?: 0.0, coordinate[6].geometry?.coordinates?.get(0)?.toDouble() ?: 0.0
            )

            planRoute(
                departureCoordinate,
                destinationCoordinate,
                customCoordinate,
                customCoordinate1,
                customCoordinate2,
                customCoordinate3
            )
        }, 2000)
        println("Show co-ordinate 582" + coordinate)

    }

    fun startScan() {
        Handler().postDelayed({
            if (!isPermissionGranted()) {

                askPermissions()

            }
        }, 5 * 1000)
    }

    private fun hideTomTom() {
        runOnUiThread {
            binding.clMap.gone()
        }
    }

    private fun showTomTom() {
        runOnUiThread {
            binding.clMap.visible()
        }
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

    private fun navigate() {
        val routePlan = RoutePlan(route, planRouteOptions)
        navigationFragment.startNavigation(routePlan)
        navigationFragment.addNavigationListener(navigationListener)
    }

    private fun initRouting() {
        routingApi = OnlineRoutingApi.create(this, BuildConfig.MAPS_API_KEY)
        dynamicRoutingApi = OnlineDynamicRoutingApi.create(routingApi)
    }

    private fun planRoute(
        departureCoordinate: GeoCoordinate,
        destinationCoordinate: GeoCoordinate,
        customCoordinate: GeoCoordinate,
        customCoordinate1: GeoCoordinate,
        customCoordinate2: GeoCoordinate,
        customCoordinate3: GeoCoordinate,
    ) {
        planRouteOptions = RoutePlanningOptions(


            itinerary = Itinerary(
                origin = departureCoordinate,
                destination = destinationCoordinate,
                waypoints = listOf(
                    customCoordinate,
                    customCoordinate1,
                    customCoordinate2,
                    customCoordinate3,
                )
            ),
            guidanceOptions = GuidanceOptions(
                instructionType = InstructionType.TEXT
            ),
            vehicle = Vehicle.Car()
        )

//        val locationMarker = LocationMarkerOptions(type = LocationMarkerOptions.Type.POINTER)
//        tomTomMap.enableLocationMarker(locationMarker)
//
//        val markerBuilder: MarkerBuilder = MarkerBuilder(position)
//            .markerBalloon(balloon)
//
//        tomTomMap.addMarker(options = MarkerOptions(customCoordinate, balloonText = "1"))
//
//        val balloon = SimpleMarkerBalloon("babylon")
//        map.addMarker(MarkerBuilder(hague).markerBalloon(balloon))
//
//
//        val markerBalloon = BaseMarkerBalloon()
//        markerBalloon.addProperty("key", "value")
//
//        tomTomMap.markerSettings.setMarkersClustering(true)


        println("customCoordinate" + customCoordinate)
        println("customCoordinate1" + customCoordinate1)
        println("customCoordinate2" + customCoordinate2)
        println("customCoordinate3" + customCoordinate3)
        println("departureCoordinate" + departureCoordinate)
        println("destinationCoordinate" + destinationCoordinate)


        routingApi.planRoute(planRouteOptions, object : RoutePlanningCallback {
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
        val instructions = this.route.mapInstructions()
        val geometry = this.route.legs.flatMap { it.points }
        val routeOptions = RouteOptions(
            geometry = geometry,
            destinationMarkerVisible = true,
            departureMarkerVisible = true,
            instructions = instructions
        )
        tomTomMap.addRoute(routeOptions)
        tomTomMap.zoomToRoutes(100)

        Handler(Looper.getMainLooper()).postDelayed({
            val routePlan = RoutePlan(this.route, planRouteOptions)
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

                println("speed" + navigationFragment.navigationView?.showSpeedView())
//                navigationFragment.navigationView?.showSpeedView()

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
        navigationFragment.removeNavigationListener(navigationListener)
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
