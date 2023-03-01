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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.*
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import ru.titovtima.familymap.databinding.ActivityMainBinding
import ru.titovtima.familymap.model.Settings
import ru.titovtima.familymap.useractivity.UserActivity
import kotlin.math.max

class MainActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private var myLocationPlacemark: PlacemarkMapObject? = null
    private var contactsPlacemarks = mutableMapOf<Int, PlacemarkMapObject?>()
    private lateinit var contactsPlacemarksClusterizedCollection: ClusterizedPlacemarkCollection
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
        contactsPlacemarksClusterizedCollection = mapView.map.mapObjects
            .addClusterizedPlacemarkCollection { cluster ->
                val string = cluster.placemarks.joinToString("\n") { it.userData.toString() }
                cluster.appearance.setText(string,
                    TextStyle(15f, null, null, TextStyle.Placement.TOP, 10f, false, false))
                cluster.appearance.setIcon(ImageProvider.fromResource(this, R.drawable.cluster_placemark_img))
                cluster.appearance.setIconStyle(IconStyle(null, null, -1f, null, null, 0.03f, null))
            }

        setContentView(binding.root)

        requestLocationPermissions()
        requestForegroundServicePermission()

        binding.userButton.setOnClickListener {
            val intent = Intent(this, UserActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        }
    }

    private fun requestForegroundServicePermission() {
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
            }
            val permissionsArray = mutableListOf(Manifest.permission.FOREGROUND_SERVICE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsArray.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            foregroundServicePermissionRequest.launch(permissionsArray.toTypedArray())
        } else {
            val startServiceIntent = Intent(this, LocationService::class.java)
            this.startForegroundService(startServiceIntent)
        }
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
            val locationPermissionRequest = registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                    permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)) {
                    val authString = Settings.user?.authString ?: return@registerForActivityResult
                    binder?.service?.postLocation(authString)
                }
            }
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
            val locationPermissionRequest = registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { }
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            )
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
                contactsPlacemarksClusterizedCollection.remove(placemark)
                contactsPlacemarks[contactId] = null
                contactsPlacemarksClusterizedCollection.clusterPlacemarks(20f.toDouble(), 15)
            }
        }
    }

    fun updateContactLocationPlacemark(contactId: Int) {
        val contact = Settings.user?.contacts?.find { it.contactId == contactId } ?: return
        if (!contact.showLocation) return
        val location = contact.lastKnownLocation ?: return
        val placemark = contactsPlacemarks[contactId]
        val point = Point(location.latitude.toDouble() / 1000000,
            location.longitude.toDouble() / 1000000)
        if (placemark == null) {
            runOnUiThread {
                val newPlacemark = contactsPlacemarksClusterizedCollection.addPlacemark(point)
                newPlacemark.setText(
                    contact.name,
                    TextStyle(15f, null, null, TextStyle.Placement.TOP, 10f, false, false)
                )
                newPlacemark.userData = contact.name
                newPlacemark.setIconStyle(IconStyle(null, null, 1f, null, null, null, null))
                contactsPlacemarks[contactId] = newPlacemark
                contactsPlacemarksClusterizedCollection.clusterPlacemarks(20f.toDouble(), 15)
            }
        } else {
            runOnUiThread {
                contactsPlacemarks[contactId]?.geometry = point
                contactsPlacemarksClusterizedCollection.clusterPlacemarks(20f.toDouble(), 15)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        mapView.onStart()
        val intentBindService = Intent(this, LocationService::class.java)
        bindService(intentBindService, serviceConnection, 0)
    }

    override fun onStop() {
        super.onStop()
        MapKitFactory.getInstance().onStop()
        mapView.onStop()
        unbindService(serviceConnection)
    }

    override fun onResume() {
        super.onResume()
        isOnForeground = true
        binder?.service?.updateContactsLocations()
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
            myBinder.service.updateContactsLocations()
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
        }
    }
}