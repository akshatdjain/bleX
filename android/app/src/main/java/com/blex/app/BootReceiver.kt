package com.blegod.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BootReceiver — Automatically starts BleGod when the tablet boots up.
 *
 * HOW IT WORKS:
 * 1. Android finishes booting
 * 2. Android broadcasts ACTION_BOOT_COMPLETED to all registered receivers
 * 3. Our BootReceiver catches this broadcast
 * 4. We start BleScannerService as a foreground service
 * 5. BLE scanning resumes automatically — no user interaction needed!
 *
 * This is registered in AndroidManifest.xml with the appropriate
 * intent filter for BOOT_COMPLETED and QUICKBOOT_POWERON.
 *
 * QUICKBOOT_POWERON is used by some OEMs (like HTC, some Samsung devices)
 * that have a "quick boot" feature instead of a full boot.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BleGod.Boot"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_REBOOT
        ) {
            Log.i(TAG, "═══════════════════════════════════════")
            Log.i(TAG, "  Device booted — starting BleGod")
            Log.i(TAG, "  Action: $action")
            Log.i(TAG, "═══════════════════════════════════════")

            // Start the scanner service as a foreground service
            val serviceIntent = Intent(context, BleScannerService::class.java)
            context.startForegroundService(serviceIntent)

            Log.i(TAG, "BleScannerService start requested")
        }
    }
}
