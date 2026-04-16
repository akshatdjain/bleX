package com.blegod.app

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * BeaconData — Represents one BLE beacon scan result.
 *
 * Now includes iBeacon UUID/Major/Minor and Eddystone namespace/instance
 * fields parsed from raw advertisement data.
 */
data class BeaconData(
    @SerializedName("mac")
    val mac: String,

    @SerializedName("rssi")
    val rssi: Int,

    @SerializedName("timestamp")
    val timestamp: Long,

    @SerializedName("device_id")
    val deviceId: String,

    @SerializedName("tx_power")
    val txPower: Int? = null,

    @SerializedName("name")
    val name: String? = null,

    // ── Beacon type identification ───────────────────────────────

    @SerializedName("beacon_type")
    val beaconType: String? = null,  // "iBeacon" or "Eddystone"

    // ── iBeacon-specific fields ──────────────────────────────────

    @SerializedName("ibeacon_uuid")
    val ibeaconUuid: String? = null,

    @SerializedName("ibeacon_major")
    val ibeaconMajor: Int? = null,

    @SerializedName("ibeacon_minor")
    val ibeaconMinor: Int? = null,

    // ── Eddystone-specific fields ────────────────────────────────

    @SerializedName("eddystone_namespace")
    val eddystoneNamespace: String? = null,

    @SerializedName("eddystone_instance")
    val eddystoneInstance: String? = null
) {
    companion object {
        private val gson = Gson()
    }

    fun toJson(): String = gson.toJson(this)
}

/**
 * ScanBatch — A batch of beacon scan results.
 */
data class ScanBatch(
    @SerializedName("device_id")
    val deviceId: String,

    @SerializedName("timestamp")
    val timestamp: Long,

    @SerializedName("beacon_count")
    val beaconCount: Int,

    @SerializedName("beacons")
    val beacons: List<BeaconData>
) {
    companion object {
        private val gson = Gson()
    }

    fun toJson(): String = gson.toJson(this)
}
