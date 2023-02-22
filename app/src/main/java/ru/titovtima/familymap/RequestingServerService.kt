package ru.titovtima.familymap

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.yandex.mapkit.geometry.Point
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import ru.titovtima.familymap.model.Settings
import kotlin.concurrent.thread
import kotlin.math.floor

class RequestingServerService : Service() {
    private var binder: RequestingServerService.MyBinder? = null
    private lateinit var locationClient: FusedLocationProviderClient

    override fun onCreate() {
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(
                    this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            }

        val notificationChannelId = "myChannelId"
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannel(
                notificationChannelId,
                "notificationChannel",
                NotificationManager.IMPORTANCE_MIN
            )
        )
        val notification: Notification =
            Notification.Builder(this, notificationChannelId)
                .setContentTitle("Отслеживание местоположения")
                .setContentText("")
                .setSmallIcon(androidx.core.R.drawable.notification_bg)
                .setContentIntent(pendingIntent)
                .setTicker("")
                .build()

        startForeground(1, notification)


        locationClient = LocationServices.getFusedLocationProviderClient(this)
        thread {
            while (true) {
                getLocation()
                Thread.sleep(40000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        val newBinder = MyBinder()
        binder = newBinder
        return newBinder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        binder?.activity = null
        return true
    }

    fun getLocation() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            Log.d("myLogs", "getLocation function")
            locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location == null) return@addOnSuccessListener
                    Log.d("myLogs", "success getting location")
                    val point = Point(location.latitude, location.longitude)
                    binder?.activity?.updateLocationPlacemark(point)
                    if (binder?.lastKnownLocation == null)
                        binder?.activity?.moveMapToLocation(point)
                    binder?.lastKnownLocation = location
                    runBlocking {
                        postLocationToServer(location)
                    }
                }
        }
    }

    suspend fun postLocationToServer(location: Location) {
        val latitude = floor(location.latitude * 1000000).toInt()
        val longitude = floor(location.longitude * 1000000).toInt()
        val date = location.time
        val stringToPost = "{\"latitude\":$latitude,\"longitude\":$longitude,\"date\":$date}"
        val response = Settings.httpClient.post("https://familymap.titovtima.ru/location") {
            headers {
                append("Authorization", "Basic dGVzdC50aXRvdnRpbWE6dGl0b3Z0aW1h")
                append("Content-Type", "application/json")
            }
            setBody(stringToPost)
        }
        if (response.status.value in 200..299) {
//            Toast.makeText(this, "Location posted", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this,
                "Error posting location\n${response.status.value} ${response.status.description}",
                Toast.LENGTH_LONG).show()
        }
    }

    inner class MyBinder: Binder() {
        var activity: MainActivity? = null
        val service = this@RequestingServerService
        var lastKnownLocation: Location? = null
    }
}