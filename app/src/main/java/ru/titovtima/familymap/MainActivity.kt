package ru.titovtima.familymap

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.*
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import ru.titovtima.familymap.databinding.ActivityMainBinding
import ru.titovtima.familymap.model.Settings
import ru.titovtima.familymap.model.SharedPrefsKeys
import ru.titovtima.familymap.model.User
import ru.titovtima.familymap.useractivity.UserActivity
import kotlin.math.max

class MainActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private var myLocationPlacemark: PlacemarkMapObject? = null
    private var contactsPlacemarks = mutableMapOf<Int, PlacemarkMapObject?>()
    private var contactsPlacemarksClusterizedCollection: ClusterizedPlacemarkCollection? = null
    private val serviceConnection = MyServiceConnection()
    private var mapLoadedFirstTime = true
    private var binder: LocationService.MyBinder? = null
    var isOnForeground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        try {
            MapKitFactory.setApiKey(getString(R.string.yandex_mapkit_apiKey))
            MapKitFactory.initialize(this)
        } catch (_: Error) {}

        val binding = ActivityMainBinding.inflate(layoutInflater)

        mapView = binding.mapview

        setContentView(binding.root)

        requestForegroundServicePermission{
            requestLocationPermissions()
        }

        if (Settings.sharedPreferencesObject == null)
            Settings.sharedPreferencesObject = getSharedPreferences("settings", MODE_PRIVATE)
        runBlocking {
            val getUser = async {
                if (Settings.user == null) {
                    val authString = Settings.sharedPreferencesObject
                        ?.getString(SharedPrefsKeys.KEY_USER_AUTH_STRING.string, null)
                    if (authString != null) {
                        getUserFromServer(authString)
                    } else
                        null
                } else
                    Settings.user
            }.await()
            if (getUser == null) {
                val userActivityIntent = Intent(this@MainActivity, UserActivity::class.java)
                startActivity(userActivityIntent)
            } else {
                requestForegroundServicePermission{
                    requestLocationPermissions()
                }
            }
        }

        binding.userButton.setOnClickListener {
            val intent = Intent(this, UserActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        }

        val moveMapLatitude = intent.getIntExtra("moveMapLatitude", Int.MAX_VALUE)
        val moveMapLongitude = intent.getIntExtra("moveMapLongitude", Int.MAX_VALUE)
        if (moveMapLatitude != Int.MAX_VALUE && moveMapLongitude != Int.MAX_VALUE) {
            mapLoadedFirstTime = false
            val point = Point(moveMapLatitude.toDouble() / 1000000,
                moveMapLongitude.toDouble() / 1000000)
            moveMapToLocation(point)
        }
    }

    private suspend fun getUserFromServer(authString: String): User? {
        try {
            val response = Settings.httpClient
                .get("https://familymap.titovtima.ru/auth/login") {
                    headers {
                        append("Authorization", "Basic $authString")
                    }
                }
            if (response.status.value == 200) {
                val user = Json.decodeFromString<User>(response.body())
                user.authString = authString
                Settings.user = user
                return user
            } else {
                return null
            }
        } catch (_: Exception) {
            return null
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        requestForegroundServicePermission{
            requestLocationPermissions()
        }
    }

    private fun requestForegroundServicePermission(callback: Runnable) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.FOREGROUND_SERVICE
            )!= PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )!= PackageManager.PERMISSION_GRANTED) {
            val foregroundServicePermissionRequest = registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                if (permissions.getOrDefault(Manifest.permission.FOREGROUND_SERVICE, false) &&
                    permissions.getOrDefault(Manifest.permission.POST_NOTIFICATIONS, false)) {
                    val startServiceIntent = Intent(this, LocationService::class.java)
                    this.startForegroundService(startServiceIntent)
                }
                callback.run()
            }
            val permissionsArray = mutableListOf(Manifest.permission.FOREGROUND_SERVICE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsArray.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            foregroundServicePermissionRequest.launch(permissionsArray.toTypedArray())
        } else {
            val startServiceIntent = Intent(this, LocationService::class.java)
            this.startForegroundService(startServiceIntent)
            callback.run()
        }
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)) {
            val authString = Settings.user?.authString
            if (authString != null)
                binder?.service?.postLocation(authString)
        }
        requestBackgroundLocationPermission()
    }

    private fun requestLocationPermissions() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            binder?.lastKnownLocation = null
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            requestBackgroundLocationPermission()
        }
    }

    private fun requestBackgroundLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            )
        }
    }

    @RequiresApi(34)
    private fun requestForegroundServicePermission() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.FOREGROUND_SERVICE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            registerForActivityResult(ActivityResultContracts.RequestPermission())
            { isGranted ->
                if (isGranted) {
                    val intentBindService = Intent(this, LocationService::class.java)
                    bindService(intentBindService, serviceConnection, 0)
                }
            }.launch(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        } else {
            val intentBindService = Intent(this, LocationService::class.java)
            bindService(intentBindService, serviceConnection, 0)
        }
    }

    fun moveMapToLocation(point: Point) {
        val mapView = this.mapView
        val prevZoom = mapView.map.cameraPosition.zoom
        mapView.map.move(
            CameraPosition(point, max(14.0f, prevZoom), 0.0f, 0.0f),
            Animation(Animation.Type.SMOOTH, 1f),
            null
        )
    }

    fun updateMyLocationPlacemark(point: Point) {
        val mapView = this.mapView
        if (myLocationPlacemark == null) {
            runOnUiThread {
                val placemark = mapView.map.mapObjects.addPlacemark(point,
                    ImageProvider.fromResource(this, R.drawable.my_location_placemark_img))
                placemark.setIconStyle(IconStyle(null, null, 0f, null, null, 0.02f, null))
                myLocationPlacemark = placemark
            }
        } else {
            runOnUiThread {
                myLocationPlacemark?.geometry = point
            }
        }
    }

    fun deleteContactLocationPlacemark(contactId: Int) {
        val placemark = contactsPlacemarks[contactId]
        if (placemark != null) {
            runOnUiThread {
                contactsPlacemarksClusterizedCollection?.remove(placemark)
                contactsPlacemarks[contactId] = null
                contactsPlacemarksClusterizedCollection?.clusterPlacemarks(20f.toDouble(), 15)
            }
        }
    }

    fun updateAllContactsPlacemarks() {
        runOnUiThread {
        contactsPlacemarksClusterizedCollection?.let{ mapView.map.mapObjects.remove(it) }
        contactsPlacemarksClusterizedCollection = mapView.map.mapObjects
            .addClusterizedPlacemarkCollection { cluster ->
                val string = cluster.placemarks.joinToString("\n") { it.userData.toString() }
                cluster.appearance.setText(string,
                    TextStyle(15f, null, null, TextStyle.Placement.TOP, 10f, false, false))
                cluster.appearance.setIcon(ImageProvider.fromResource(this, R.drawable.cluster_placemark_img))
                cluster.appearance.setIconStyle(IconStyle(null, null, -1f, null, null, 0.03f, null))
            }
        }
        contactsPlacemarks = mutableMapOf()
        val user = Settings.user ?: return
        val authString = user.authString ?: return
        val service = binder?.service ?: return
        user.contacts.forEach { contact ->
            if (contact.login != null && contact.showLocation) {
                runBlocking {
                    launch {
                        if (service.getContactLocationFromServer(contact.contactId, authString))
                            updateContactLocationPlacemark(contact.contactId)
                    }
                }
            }
        }
    }

    private fun updateContactLocationPlacemark(contactId: Int) {
        val contact = Settings.user?.contacts?.find { it.contactId == contactId } ?: return
        if (!contact.showLocation) return
        val location = contact.lastKnownLocation ?: return
        val placemark = contactsPlacemarks[contactId]
        val point = Point(location.latitude.toDouble() / 1000000,
            location.longitude.toDouble() / 1000000)
        val clusterizedCollection = contactsPlacemarksClusterizedCollection ?: return
        runOnUiThread {
            if (placemark != null)
                clusterizedCollection.remove(placemark)
            val newPlacemark = clusterizedCollection.addPlacemark(point)
            newPlacemark.setText(
                contact.name,
                TextStyle(15f, null, null, TextStyle.Placement.TOP, 10f, false, false)
            )
            newPlacemark.userData = contact.name
            newPlacemark.setIconStyle(IconStyle(null, null, 1f, null, null, null, null))
            contactsPlacemarks[contactId] = newPlacemark
            clusterizedCollection.clusterPlacemarks(20f.toDouble(), 15)
        }

    }

    private fun bindService() {

    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        mapView.onStart()
        if (Build.VERSION.SDK_INT >= 34) {
            requestForegroundServicePermission()
        } else {
            val intentBindService = Intent(this, LocationService::class.java)
            bindService(intentBindService, serviceConnection, 0)
        }
    }

    override fun onStop() {
        super.onStop()
        MapKitFactory.getInstance().onStop()
        mapView.onStop()
        if (binder != null)
            unbindService(serviceConnection)
    }

    override fun onResume() {
        super.onResume()
        isOnForeground = true
        updateAllContactsPlacemarks()
    }

    override fun onPause() {
        super.onPause()
        isOnForeground = false
    }

    companion object {
        private var instance: MainActivity? = null
        fun getInstance() = instance
    }

    inner class MyServiceConnection: ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, binder: IBinder?) {
            if (binder == null) return
            val myBinder = binder as LocationService.MyBinder
            myBinder.activity = this@MainActivity
            this@MainActivity.binder = myBinder
            if (mapLoadedFirstTime) {
                mapLoadedFirstTime = false
                myBinder.lastKnownLocation = null
            }
            val user = Settings.user ?: return
            val authString = user.authString ?: return
            myBinder.service.postLocation(authString)
            updateAllContactsPlacemarks()
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
        }
    }
}