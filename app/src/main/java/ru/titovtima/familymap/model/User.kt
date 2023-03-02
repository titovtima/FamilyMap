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
                   val login: String?,
                   var name: String,
                   var showLocation: Boolean = true,
                   var shareLocation: Boolean = true,
                   var lastKnownLocation: MyLocation? = null
)

@Serializable
data class MyLocation(val latitude: Int, val longitude: Int, val date: Long) {
    constructor(location: Location): this(
        floor(location.latitude * 1000000).toInt(),
        floor(location.longitude * 1000000).toInt(),
        location.time
    )

    companion object {
        fun readFromByteArray(array: ByteArray, offset: Int = 0): MyLocation? {
            if (array.size < offset + 16) return null
            val latitude = (array[offset].toInt() and 0xff shl 0) or
                    (array[offset + 1].toInt() and 0xff shl 8) or
                            (array[offset + 2].toInt() and 0xff shl 16) or
                            (array[offset + 3].toInt() and 0xff shl 24)
            val longitude = (array[offset + 4].toInt() and 0xff shl 0) or
                    (array[offset + 5].toInt() and 0xff shl 8) or
                            (array[offset + 6].toInt() and 0xff shl 16) or
                            (array[offset + 7].toInt() and 0xff shl 24)
            val date = (array[offset + 8].toLong() and 0xff shl 0) or
                    (array[offset + 9].toLong() and 0xff shl 8) or
                            (array[offset + 10].toLong() and 0xff shl 16) or
                            (array[offset + 11].toLong() and 0xff shl 24)or
                            (array[offset + 12].toLong() and 0xff shl 32) or
                            (array[offset + 13].toLong() and 0xff shl 40) or
                            (array[offset + 14].toLong() and 0xff shl 48)or
                            (array[offset + 15].toLong() and 0xff shl 56)
            return MyLocation(latitude, longitude, date)
        }
    }
}
