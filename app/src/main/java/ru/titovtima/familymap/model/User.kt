package ru.titovtima.familymap.model

import android.location.Location
import kotlinx.serialization.Serializable
import kotlin.math.floor

@Serializable
data class User(val login: String,
                var name: String,
                val contacts: MutableList<Contact> = mutableListOf()) {
    var authString: String? = null
}

@Serializable
data class Contact(val contactId: Int,
                   val login: String,
                   var name: String,
                   var showLocation: Boolean = true,
                   var lastKnownLocation: MyLocation? = null
)

@Serializable
data class MyLocation(val latitude: Int, val longitude: Int, val date: Long) {
    constructor(location: Location): this(
        floor(location.latitude * 1000000).toInt(),
        floor(location.longitude * 1000000).toInt(),
        location.time
    )
}
