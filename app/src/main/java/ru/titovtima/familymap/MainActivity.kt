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
import kotlin.math.max

class MainActivity : AppCompatActivity() {
    var mapView: MapView? = null
    var myLocationPlacemark: PlacemarkMapObject? = null
    val serviceConnection = MyServiceConnection()
    private var mapLoadedFirstTime = true
    private var binder: RequestingServerService.MyBinder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            MapKitFactory.setApiKey("API-key was here")

            MapKitFactory.initialize(this)
        } catch (_: Error) {}

        val binding = ActivityMainBinding.inflate(layoutInflater)

        mapView = binding.mapview

        setContentView(binding.root)
        super.onCreate(savedInstanceState)

        requestLocationPermissions()

        val startServiceIntent = Intent(this, RequestingServerService::class.java)
        this.startForegroundService(startServiceIntent)
    }

    fun requestLocationPermissions() {
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
                    binder?.service?.getLocation()
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

    fun requestBackgroundLocationPermission() {
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

    fun updateLocationPlacemark(point: Point) {
        val mapView = this.mapView ?: return
        if (myLocationPlacemark == null) {
            val placemark = mapView.map.mapObjects.addPlacemark(point)
            placemark.setText(
                "Ура, я тут",
                TextStyle(15f, null, null, TextStyle.Placement.TOP, 10f, false, false)
            )
            myLocationPlacemark = placemark
        } else {
            myLocationPlacemark?.geometry = point
        }
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        mapView?.onStart()
        val intentBindService = Intent(this, RequestingServerService::class.java)
        bindService(intentBindService, serviceConnection, 0)
    }

    override fun onStop() {
        super.onStop()
        MapKitFactory.getInstance().onStop()
        mapView?.onStop()
        unbindService(serviceConnection)
    }

    inner class MyServiceConnection: ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, binder: IBinder?) {
            if (binder == null) return
            val myBinder = binder as RequestingServerService.MyBinder
            myBinder.activity = this@MainActivity
            this@MainActivity.binder = myBinder
            if (mapLoadedFirstTime) {
                mapLoadedFirstTime = false
                myBinder.lastKnownLocation = null
            }
            myBinder.service.getLocation()
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
        }
    }
}