package ru.titovtima.familymap

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Binder
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.yandex.mapkit.geometry.Point
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import ru.titovtima.familymap.model.*
import java.util.Base64
import kotlin.concurrent.thread
import kotlin.math.floor

class LocationService : Service() {
    private var binder: LocationService.MyBinder? = null
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

        if (Settings.sharedPreferencesObject == null)
            Settings.sharedPreferencesObject = getSharedPreferences("settings", MODE_PRIVATE)

        if (Settings.user == null) {
            val authString = Settings.sharedPreferencesObject
                ?.getString(SharedPrefsKeys.KEY_USER_AUTH_STRING.string, null)
            if (authString != null) {
                runBlocking {
                    getUserFromServer(authString)
                }
            }
        }

        locationClient = LocationServices.getFusedLocationProviderClient(this)
        thread {
            while (true) {
                postLocation()
                updateContactsLocations()
                Thread.sleep(40000)
            }
        }
    }

    private suspend fun getUserFromServer(authString: String) {
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

    fun postLocation() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            locationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location == null) return@addOnSuccessListener
                    val point = Point(location.latitude, location.longitude)
                    binder?.activity?.updateMyLocationPlacemark(point)
                    if (binder?.lastKnownLocation == null)
                        binder?.activity?.moveMapToLocation(point)
                    binder?.lastKnownLocation = location
                    runBlocking {
                        val userAuthString = Settings.user?.authString
                        if (userAuthString != null)
                            postLocationToServer(location, userAuthString)
                    }
                }
        }
    }

    private suspend fun postLocationToServer(location: Location, authString: String) {
        val latitude = floor(location.latitude * 1000000).toInt()
        val longitude = floor(location.longitude * 1000000).toInt()
        val date = location.time
        val stringToPost = "{\"latitude\":$latitude,\"longitude\":$longitude,\"date\":$date}"
        val response = Settings.httpClient.post("https://familymap.titovtima.ru/location") {
            headers {
                append("Authorization", "Basic $authString")
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

    private fun updateContactsLocations() {
        val user = Settings.user ?: return
        val authString = user.authString ?: return
        for (contact in user.contacts) {
            if (!contact.showLocation)
                continue
            runBlocking {
                getContactLocationFromServer(contact, authString)
            }
        }
    }

    private suspend fun getContactLocationFromServer(contact: Contact, authString: String) {
        val url = "https://familymap.titovtima.ru/location/last/${contact.login}"
        val response = Settings.httpClient.get(url) {
            headers {
                append("Authorization", "Basic $authString")
            }
        }
        if (response.status.value == 200) {
            val body = response.body<String>()
            val location = MyLocation.readFromByteArray(Base64.getDecoder().decode(body)) ?: return
            val lastKnownLocation = contact.lastKnownLocation
            if (lastKnownLocation == null || location.date > lastKnownLocation.date)
                contact.lastKnownLocation = location
            binder?.activity?.updateContactLocationPlacemark(contact.contactId)
        }
    }

    inner class MyBinder: Binder() {
        var activity: MainActivity? = null
        val service = this@LocationService
        var lastKnownLocation: Location? = null
    }
}