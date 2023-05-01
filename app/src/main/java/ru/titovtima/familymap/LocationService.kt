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
import androidx.core.app.NotificationCompat
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
import ru.titovtima.familymap.useractivity.UserActivity
import java.util.Base64
import kotlin.concurrent.thread
import kotlin.math.floor

class LocationService : Service() {
    private var binder: LocationService.MyBinder? = null
    private lateinit var locationClient: FusedLocationProviderClient
    private var maxNotificationId = 1

    override fun onCreate() {
        val startActivityIntent = Intent(this, MainActivity::class.java)
        startActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, startActivityIntent,
            PendingIntent.FLAG_IMMUTABLE)

        val notificationChannelId = "locationNotificationChannelId"
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannel(
                notificationChannelId,
                "notificationChannel",
                NotificationManager.IMPORTANCE_MIN
            )
        )
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannel(
                "contactsAskChannelId",
                "notificationChannel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        val notification = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("Отслеживание местоположения")
            .setContentText("")
            .setSmallIcon(R.drawable.icon)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
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

        getIgnoredContactsAsksFromSharedPrefs()

        locationClient = LocationServices.getFusedLocationProviderClient(this)
        thread {
            while (true) {
                try {
                    val startServiceIntent = Intent(this, LocationService::class.java)
                    this.startForegroundService(startServiceIntent)
                    val authString = Settings.user?.authString
                    if (authString != null) {
                        postLocation(authString)
                        val activity = binder?.activity
                        if (activity != null && activity.isOnForeground)
                            activity.updateAllContactsPlacemarks()
                        checkContactsRequests(authString)
                    }
                } catch (_: Exception) {}
                Thread.sleep(40000)
            }
        }

        Settings.locationService = this
    }

    private suspend fun getUserFromServer(authString: String) {
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
            } else {
                val userActivityIntent = Intent(this, UserActivity::class.java)
                startActivity(userActivityIntent)
            }
        } catch (_: Exception) {
            val userActivityIntent = Intent(this, UserActivity::class.java)
            startActivity(userActivityIntent)
        }
    }

    private fun getIgnoredContactsAsksFromSharedPrefs() {
        val setFromSharedPrefs = Settings.sharedPreferencesObject
            ?.getStringSet(SharedPrefsKeys.KEY_IGNORED_CONTACTS_ASKS.string, mutableSetOf())
            ?: return
        Settings.setIgnoredContactsAsks(setFromSharedPrefs)
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

    fun postLocation(authString: String) {
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
                        postLocationToServer(location, authString)
                    }
                }
        }
    }

    private suspend fun postLocationToServer(location: Location, authString: String) {
        val latitude = floor(location.latitude * 1000000).toInt()
        val longitude = floor(location.longitude * 1000000).toInt()
        val date = location.time
        val stringToPost = "{\"latitude\":$latitude,\"longitude\":$longitude,\"date\":$date}"
        try {
            val response = Settings.httpClient.post("https://familymap.titovtima.ru/location") {
                headers {
                    append("Authorization", "Basic $authString")
                    append("Content-Type", "application/json")
                }
                setBody(stringToPost)
            }
            if (response.status.value !in 200..299) {
                Toast.makeText(this,
                    "Error posting location\n${response.status.value} ${response.status.description}",
                    Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) { }
    }

    suspend fun getContactLocationFromServer(contactId: Int, authString: String): Boolean {
        val contact = Settings.user?.contacts?.find { it.contactId == contactId } ?: return false
        val url = "https://familymap.titovtima.ru/location/last/${contact.login}"
        try {
            val response = Settings.httpClient.get(url) {
                headers {
                    append("Authorization", "Basic $authString")
                }
            }
            return if (response.status.value == 200) {
                val body = response.body<String>()
                val location =
                    MyLocation.readFromByteArray(Base64.getDecoder().decode(body)) ?: return false
                val lastKnownLocation = contact.lastKnownLocation
                if (lastKnownLocation == null || location.date > lastKnownLocation.date)
                    contact.lastKnownLocation = location
                true
            } else {
                false
            }
        } catch (_: Exception) {
            return false
        }
    }

    private fun checkContactsRequests(authString: String) {
        runBlocking {
            val list = getShareLocationAsksFromServer(authString) ?: return@runBlocking
            val user = Settings.user ?: return@runBlocking
            list.forEach { login ->
                if (!user.contacts.any { it.login == login } &&
                    !Settings.ignoredContactsAsks.contains(login) &&
                    !Settings.contactAsksNotifications.containsKey(login)) {
                    showContactAskNotification(login)
                }
            }
        }
    }

    private fun showContactAskNotification(loginAsk: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) {
            maxNotificationId++
            val startActivityIntent = Intent(this, AcceptContactActivity::class.java)
                .apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("contactLogin", loginAsk)
                    putExtra("notificationId", maxNotificationId)
                }

            val pendingIntent = PendingIntent.getActivity(
                this, maxNotificationId, startActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val notificationChannelId = "contactsAskChannelId"
            val notification = NotificationCompat.Builder(this, notificationChannelId)
                .setContentTitle("")
                .setContentText("$loginAsk ${getString(R.string.ask_adding_contact_notification)}")
                .setSmallIcon(R.drawable.icon)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setContentIntent(pendingIntent)
                .build()
            Settings.contactAsksNotifications[loginAsk] = maxNotificationId
            NotificationManagerCompat.from(this).notify(maxNotificationId, notification)
        }
    }

    private suspend fun getShareLocationAsksFromServer(authString: String): List<String>? {
        try {
            val response = Settings.httpClient
                .get("https://familymap.titovtima.ru/shareLocationAsks") {
                    headers {
                        append("Authorization", "Basic $authString")
                    }
                }
            return if (response.status.value == 200) {
                val body = response.body<String>()
                body.split('\n').filter { it.trim().isNotEmpty() }
            } else {
                null
            }
        } catch (_: Exception) {
            return null
        }
    }

    inner class MyBinder: Binder() {
        var activity: MainActivity? = null
        val service = this@LocationService
        var lastKnownLocation: Location? = null
    }
}