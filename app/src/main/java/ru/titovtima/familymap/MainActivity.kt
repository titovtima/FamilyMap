package ru.titovtima.familymap

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.map.TextStyle
import com.yandex.mapkit.mapview.MapView
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import ru.titovtima.familymap.databinding.ActivityMainBinding
import java.util.Date
import kotlin.math.floor

class MainActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var locationClient: FusedLocationProviderClient
    private val client = HttpClient()
    private lateinit var myLocationPlacemark: PlacemarkMapObject
    private var myLocationPlacemarkWasSet = false

    override fun onCreate(savedInstanceState: Bundle?) {
        MapKitFactory.setApiKey("API-key was here")

        MapKitFactory.initialize(this)

        val binding = ActivityMainBinding.inflate(layoutInflater)

        mapView = binding.mapview

        setContentView(binding.root)
        super.onCreate(savedInstanceState)

        locationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    fun getLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val locationPermissionRequest = registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                when {
                    permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                        getLocation()
                    }
                    permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                        getLocation()
                    }
                    else -> {
                    }
                }
            }
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    val point = Point(location.latitude, location.longitude)
                    mapView.map.move(
                        CameraPosition(point, 14.0f, 0.0f, 0.0f),
                        Animation(Animation.Type.SMOOTH, 1f),
                        null
                    )
                    if (!myLocationPlacemarkWasSet) {
                        myLocationPlacemarkWasSet = true
                        myLocationPlacemark = mapView.map.mapObjects.addPlacemark(point)
                        myLocationPlacemark.setText(
                            "Ура, я тут",
                            TextStyle(15f, null, null, TextStyle.Placement.TOP, 10f, false, false)
                        )
                    } else {
                        myLocationPlacemark.geometry = point
                    }
                    runBlocking {
                        postLocationToServer(point)
                    }
                }
        }
    }

    suspend fun postLocationToServer(point: Point) {
        val latitude = floor(point.latitude * 1000000).toInt()
        val longitude = floor(point.longitude * 1000000).toInt()
        val date = Date().time
        val stringToPost = "{\"latitude\":$latitude,\"longitude\":$longitude,\"date\":$date}"
        val response = client.post("https://familymap.titovtima.ru/location") {
            headers {
                append("Authorization", "Basic dGVzdC50aXRvdnRpbWE6dGl0b3Z0aW1h")
                append("Content-Type", "application/json")
            }
            setBody(stringToPost)
        }
        if (response.status.value in 200..299) {
            Toast.makeText(this, "Location posted", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this,
                "Error posting location\n${response.status.value} ${response.status.description}",
                Toast.LENGTH_LONG).show()
        }
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        mapView.onStart()
        getLocation()
    }

    override fun onStop() {
        super.onStop()
        MapKitFactory.getInstance().onStop()
        mapView.onStop()
    }
}