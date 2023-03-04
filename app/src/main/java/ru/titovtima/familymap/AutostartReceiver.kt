package ru.titovtima.familymap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AutostartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null && Intent.ACTION_BOOT_COMPLETED == intent?.action) {
            val startServiceIntent = Intent(context, LocationService::class.java)
            context.startForegroundService(startServiceIntent)
        }
    }
}