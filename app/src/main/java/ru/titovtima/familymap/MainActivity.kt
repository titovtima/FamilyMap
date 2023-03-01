package ru.titovtima.familymap

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.map.TextStyle
import com.yandex.mapkit.mapview.MapView
import ru.titovtima.familymap.databinding.ActivityMainBinding
import ru.titovtima.familymap.model.Settings
import ru.titovtima.familymap.useractivity.UserActivity
import kotlin.math.max

class MainActivity : AppCompatActivity() {
    private var mapView: MapView? = null
    private var myLocationPlacemark: PlacemarkMapObject? = null
    private var contactsPlacemarks = mutableMapOf<Int, PlacemarkMapObject?>()
    private val serviceConnection = MyServiceConnection()
    private var mapLoadedFirstTime = true
    private var binder: LocationService.MyBinder? = null
    var isOnForeground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            MapKitFactory.setApiKey("API-key was here")
            MapKitFactory.initialize(this)
        } catch (_: Error) {}

        val binding = ActivityMainBinding.inflate(layoutInflater)

        mapView = binding.mapview

        setContentView(binding.root)

        requestLocationPermissions()

        val startServiceIntent = Intent(this, LocationService::class.java)
        this.startForegroundService(startServiceIntent)

        binding.userButton.setOnClickListener {
            val intent = Intent(this, UserActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
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
        val mapView = this.mapView ?: return
        val prevZoom = mapView.map.cameraPosition.zoom
        mapView.map.move(
            CameraPosition(point, max(14.0f, prevZoom), 0.0f, 0.0f),
            Animation(Animation.Type.SMOOTH, 1f),
            null
        )
    }

    fun updateMyLocationPlacemark(point: Point) {
        val mapView = this.mapView ?: return
        if (myLocationPlacemark == null) {
            runOnUiThread {
                val placemark = mapView.map.mapObjects.addPlacemark(point)
                placemark.setText(
                    "Ура, я тут",
                    TextStyle(15f, null, null, TextStyle.Placement.TOP, 10f, false, false)
                )
                myLocationPlacemark = placemark
            }
        } else {
            runOnUiThread {
                myLocationPlacemark?.geometry = point
            }
        }
    }

    fun updateContactLocationPlacemark(contactId: Int) {
        val mapView = this.mapView ?: return
        val contact = Settings.user?.contacts?.find { it.contactId == contactId } ?: return
        val location = contact.lastKnownLocation ?: return
        val placemark = contactsPlacemarks[contactId]
        val point = Point(location.latitude.toDouble() / 1000000,
            location.longitude.toDouble() / 1000000)
        if (placemark == null) {
            runOnUiThread {
                val newPlacemark = mapView.map.mapObjects.addPlacemark(point)
                newPlacemark.setText(
                    contact.name,
                    TextStyle(15f, null, null, TextStyle.Placement.TOP, 10f, false, false)
                )
                contactsPlacemarks[contactId] = newPlacemark
            }
        } else {
            runOnUiThread {
                contactsPlacemarks[contactId]?.geometry = point
            }
        }
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        mapView?.onStart()
        val intentBindService = Intent(this, LocationService::class.java)
        bindService(intentBindService, serviceConnection, 0)
    }

    override fun onStop() {
        super.onStop()
        MapKitFactory.getInstance().onStop()
        mapView?.onStop()
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
        binder?.service?.updateContactsLocations()
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