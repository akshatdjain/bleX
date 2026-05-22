package com.blex.app

import android.content.Context
import com.blex.app.data.SettingsManager
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * PayloadBuilder — Builds MQTT JSON payloads from a configurable template.
 *
 * The user defines a JSON template in Settings with placeholder tokens like
 * ${SCANNER_MAC}, ${BEACON_MAC}, etc. This builder replaces those tokens
 * with actual values from BeaconData.
 *
 * Alternatively, uses the field toggles for a structured build.
 */
object PayloadBuilder {

    private val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Build a JSON payload for a single beacon using the configured template.
     */
    fun buildPayload(context: Context, beacon: BeaconData): String {
        val settings = SettingsManager.getInstance(context)
        val scannerMac = settings.scannerMacLabel.ifEmpty { AppConfig.getTabletMac(context) }

        return try {
            buildFromTemplate(settings.mqttPayloadTemplate, scannerMac, beacon, settings)
        } catch (e: Exception) {
            // Fallback to structured build if template is invalid
            buildStructured(settings, scannerMac, beacon)
        }
    }

    /**
     * Build payload by replacing template tokens.
     */
    private fun buildFromTemplate(template: String, scannerMac: String, beacon: BeaconData, settings: SettingsManager? = null): String {
        var result = template
            .replace("\${SCANNER_MAC}", scannerMac)
            .replace("\${BEACON_MAC}", beacon.mac)
            .replace("\${RSSI}", beacon.rssi.toString())
            .replace("\${TX_POWER}", (beacon.txPower ?: 0).toString())
            .replace("\${TIMESTAMP_UTC}", utcFormat.format(Date(beacon.timestamp)))
            .replace("\${BEACON_TYPE}", beacon.beaconType ?: "unknown")
            .replace("\${IBEACON_UUID}", beacon.ibeaconUuid ?: "")
            .replace("\${IBEACON_MAJOR}", (beacon.ibeaconMajor ?: 0).toString())
            .replace("\${IBEACON_MINOR}", (beacon.ibeaconMinor ?: 0).toString())
            .replace("\${EDDYSTONE_NAMESPACE}", beacon.eddystoneNamespace ?: "")
            .replace("\${EDDYSTONE_INSTANCE}", beacon.eddystoneInstance ?: "")
            .replace("\${NAME}", beacon.name ?: "")
            .replace("\${DEVICE_ID}", beacon.deviceId)

        if (settings != null) {
            result = result.replace("\${TENANT_ID}", settings.tenantId)
        }

        return result
    }

    /**
     * Structured fallback build using field toggles.
     */
    private fun buildStructured(settings: SettingsManager, scannerMac: String, beacon: BeaconData): String {
        val json = JSONObject()
        json.put("tenant_id", settings.tenantId)
        json.put("scanner_mac", scannerMac)
        json.put("beacon_mac", beacon.mac)
        json.put("rssi", beacon.rssi)

        if (settings.payloadIncludeTxPower && beacon.txPower != null) {
            json.put("tx_power", beacon.txPower)
        }

        json.put("timestamp_utc", utcFormat.format(Date(beacon.timestamp)))

        if (settings.payloadIncludeBeaconType && beacon.beaconType != null) {
            json.put("beacon_type", beacon.beaconType)
        }

        if (settings.payloadIncludeUuid && beacon.ibeaconUuid != null) {
            json.put("ibeacon_uuid", beacon.ibeaconUuid)
        }

        if (settings.payloadIncludeMajorMinor) {
            beacon.ibeaconMajor?.let { json.put("ibeacon_major", it) }
            beacon.ibeaconMinor?.let { json.put("ibeacon_minor", it) }
        }

        if (settings.payloadIncludeName && beacon.name != null) {
            json.put("name", beacon.name)
        }

        return json.toString()
    }

    /**
     * Build a batch payload (array of beacon payloads).
     * Uses master-compatible format so cloud master can parse mac + rssi.kalman correctly.
     */
    fun buildBatchPayload(context: Context, beacons: List<BeaconData>): String {
        val settings = SettingsManager.getInstance(context)
        val scannerMac = settings.scannerMacLabel.ifEmpty { AppConfig.getTabletMac(context) }
        val ts = utcFormat.format(Date())

        val json = JSONObject()
        json.put("scanner_mac", scannerMac)
        json.put("scanner_id", scannerMac)
        json.put("tenant_id", settings.tenantId)
        json.put("timestamp_utc", ts)
        json.put("beacon_count", beacons.size)

        val beaconArray = org.json.JSONArray()
        for (beacon in beacons) {
            try {
                val b = JSONObject()
                b.put("mac", beacon.mac)
                b.put("scanner_mac", scannerMac)
                b.put("scanner_id", scannerMac)
                b.put("tenant_id", settings.tenantId)
                b.put("timestamp", ts)
                b.put("rssi", JSONObject().apply {
                    put("raw", beacon.rssi)
                    put("kalman", beacon.rssi)  // tablet doesn't apply Kalman — raw is best we have
                })
                beacon.txPower?.let { b.put("tx_power", it) }
                beaconArray.put(b)
            } catch (_: Exception) {}
        }
        json.put("beacons", beaconArray)

        return json.toString()
    }

    /** Available template tokens for the settings UI */
    val AVAILABLE_TOKENS = listOf(
        "\${TENANT_ID}" to "Tenant ID (organization)",
        "\${SCANNER_MAC}" to "Scanner device identifier",
        "\${BEACON_MAC}" to "Beacon MAC address",
        "\${RSSI}" to "Signal strength (dBm)",
        "\${TX_POWER}" to "Transmit power (dBm)",
        "\${TIMESTAMP_UTC}" to "UTC timestamp (ISO 8601)",
        "\${BEACON_TYPE}" to "iBeacon or Eddystone",
        "\${IBEACON_UUID}" to "iBeacon proximity UUID",
        "\${IBEACON_MAJOR}" to "iBeacon major value",
        "\${IBEACON_MINOR}" to "iBeacon minor value",
        "\${EDDYSTONE_NAMESPACE}" to "Eddystone namespace ID",
        "\${EDDYSTONE_INSTANCE}" to "Eddystone instance ID",
        "\${NAME}" to "Beacon advertised name",
        "\${DEVICE_ID}" to "Android device ID"
    )
}
