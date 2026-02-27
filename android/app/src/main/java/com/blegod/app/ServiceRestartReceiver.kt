package com.blegod.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * ServiceRestartReceiver — Safety net to restart the service if it's killed.
 *
 * HOW IT WORKS:
 * When BleScannerService is destroyed (for any reason), it schedules an
 * AlarmManager alarm that broadcasts to this receiver.
 *
 * This receiver then starts the service again.
 *
 * DEFENSE IN DEPTH:
 * This is Layer 6 in our survivability stack:
 *   Layer 1: Foreground service (high priority)
 *   Layer 2: START_STICKY (OS restarts on kill)
 *   Layer 3: Boot receiver (restart on reboot)
 *   Layer 4: Battery optimization exemption (exempt from Doze)
 *   Layer 5: AlarmManager (triggers this receiver)
 *   Layer 6: THIS → receives alarm and restarts service
 *   Layer 7: Watchdog (restarts stalled scanner)
 *   Layer 8: MQTT auto-reconnect
 *
 * This is the same "defense in depth" strategy used in production
 * systems at companies like Google and Amazon.
 */
class ServiceRestartReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BleGod.Restart"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "═══════════════════════════════════════")
        Log.i(TAG, "  Restart broadcast received")
        Log.i(TAG, "  Action: ${intent.action}")
        Log.i(TAG, "═══════════════════════════════════════")

        // Start the scanner service
        val serviceIntent = Intent(context, BleScannerService::class.java).apply {
            action = BleScannerService.ACTION_RESTART
        }
        context.startForegroundService(serviceIntent)

        Log.i(TAG, "Service restart requested via ServiceRestartReceiver")
    }
}
