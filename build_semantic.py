"""
build_semantic.py
Deep semantic extraction from BleX codebase — builds rich knowledge graph
from actual code content read by Claude. Overwrites .graphify_semantic.json
then rebuilds the merged extract and final graph + HTML.

Excludes all dotfolders and tooling noise (.specify, antigravity-skills,
graphify-out, .claude, .git, tasks/blex-multitenant-proposal.pdf, etc.)
so the graph only contains real BleX project files.
"""
import json, os
from pathlib import Path

os.environ['GRAPHIFY_VIZ_NODE_LIMIT'] = '8000'

# ── Filter: exclude any path that contains a dot-folder segment ───────────────
# This catches .specify, .claude, .git, .gradle, .kotlin, .idea, .vscode,
# graphify-out, antigravity-skills (~\), and all other hidden/tool dirs.
# Also strips minified JS blobs and shadcn boilerplate which pollute god-nodes.

import re

# Any path segment that starts with a dot → excluded
_DOT_SEGMENT = re.compile(r'[/\\]\.[^/\\]')

# Additional non-dot paths to exclude
EXCLUDE_PREFIXES = (
    "O:\\blex\\~",                               # antigravity-skills
    "O:\\blex\\graphify-out",                    # graphify artefacts
    "O:\\blex\\backend\\ui_api\\www\\assets",    # minified JS blob
    "O:\\blex\\local-dev\\ui_api\\www\\assets",  # minified JS blob
    "O:\\blex\\ui\\src\\components\\ui",         # shadcn boilerplate
    "O:\\blex\\updates_ui\\src\\components\\ui", # shadcn boilerplate
)

def is_blex(path_str: str) -> bool:
    """Return True only if path belongs to real BleX source — no dot-folders, no noise."""
    if not path_str:
        return False
    p = path_str.replace("/", "\\")
    # Reject any segment that starts with a dot (e.g. \.gradle, \.kotlin, \.git)
    if _DOT_SEGMENT.search(p):
        return False
    # Reject explicit noise prefixes
    for prefix in EXCLUDE_PREFIXES:
        if p.startswith(prefix):
            return False
    return True

# ─────────────────────────────────────────────────────────────────────────────
# NODES  (id, label, file_type, source_file)
# ─────────────────────────────────────────────────────────────────────────────
NODES = [
    # ── Android App – Core Classes ──────────────────────────────────────────
    {"id":"BleScannerService","label":"BleScannerService","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt"},
    {"id":"BleScanner","label":"BleScanner","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt"},
    {"id":"MqttManager","label":"MqttManager","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/MqttManager.kt"},
    {"id":"EmbeddedBroker","label":"EmbeddedBroker","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/EmbeddedBroker.kt"},
    {"id":"MqttBridge","label":"MqttBridge","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/MqttBridge.kt"},
    {"id":"PayloadBuilder","label":"PayloadBuilder","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/PayloadBuilder.kt"},
    {"id":"AppConfig","label":"AppConfig","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/AppConfig.kt"},
    {"id":"BeaconData","label":"BeaconData","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/BeaconData.kt"},
    {"id":"ScanBatch","label":"ScanBatch","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/BeaconData.kt"},
    {"id":"BleXApp","label":"BleXApp","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/BleXApp.kt"},
    {"id":"MainActivity","label":"MainActivity","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/MainActivity.kt"},
    {"id":"BootReceiver","label":"BootReceiver","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/BootReceiver.kt"},
    {"id":"ServiceRestartReceiver","label":"ServiceRestartReceiver","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/ServiceRestartReceiver.kt"},
    {"id":"AlertNotificationManager","label":"AlertNotificationManager","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/AlertNotificationManager.kt"},
    {"id":"BatteryMonitor","label":"BatteryMonitor","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/BatteryMonitor.kt"},
    {"id":"ServiceHealth","label":"ServiceHealth","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/ServiceHealth.kt"},

    # ── Android App – Data Layer ─────────────────────────────────────────────
    {"id":"SettingsManager","label":"SettingsManager","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/data/SettingsManager.kt"},
    {"id":"ScanRepository","label":"ScanRepository","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/data/ScanRepository.kt"},
    {"id":"ApiService","label":"ApiService","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/data/ApiService.kt"},
    {"id":"ServiceStatus","label":"ServiceStatus","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/data/ScanRepository.kt"},
    {"id":"LogEntry","label":"LogEntry","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/data/ScanRepository.kt"},
    {"id":"LogLevel","label":"LogLevel","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/data/ScanRepository.kt"},
    {"id":"DiscoveredScanner","label":"DiscoveredScanner","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/data/ScanRepository.kt"},
    {"id":"Zone","label":"Zone","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/data/ScanRepository.kt"},

    # ── Android App – Network ────────────────────────────────────────────────
    {"id":"UdpDiscoveryManager","label":"UdpDiscoveryManager","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/network/UdpDiscoveryManager.kt"},
    {"id":"HostnameInsensitiveSocketFactory","label":"HostnameInsensitiveSocketFactory","file_type":"class","source_file":"android/app/src/main/java/com/blex/app/MqttManager.kt"},

    # ── Android App – UI Screens ─────────────────────────────────────────────
    {"id":"ScannerScreen","label":"ScannerScreen","file_type":"composable","source_file":"android/app/src/main/java/com/blex/app/ui/screens/ScannerScreen.kt"},
    {"id":"SettingsScreen","label":"SettingsScreen","file_type":"composable","source_file":"android/app/src/main/java/com/blex/app/ui/screens/SettingsScreen.kt"},
    {"id":"LogScreen","label":"LogScreen","file_type":"composable","source_file":"android/app/src/main/java/com/blex/app/ui/screens/LogScreen.kt"},
    {"id":"DashboardWebScreen","label":"DashboardWebScreen","file_type":"composable","source_file":"android/app/src/main/java/com/blex/app/ui/screens/DashboardWebScreen.kt"},
    {"id":"LoginScreen","label":"LoginScreen","file_type":"composable","source_file":"android/app/src/main/java/com/blex/app/ui/screens/LoginScreen.kt"},
    {"id":"SetupWizard","label":"SetupWizard","file_type":"composable","source_file":"android/app/src/main/java/com/blex/app/ui/screens/SetupWizard.kt"},
    {"id":"AssetsTab","label":"AssetsTab","file_type":"composable","source_file":"android/app/src/main/java/com/blex/app/ui/screens/configurator/AssetsTab.kt"},
    {"id":"ZonesTab","label":"ZonesTab","file_type":"composable","source_file":"android/app/src/main/java/com/blex/app/ui/screens/configurator/ZonesTab.kt"},
    {"id":"ScannersTab","label":"ScannersTab","file_type":"composable","source_file":"android/app/src/main/java/com/blex/app/ui/screens/configurator/ScannersTab.kt"},
    {"id":"HotspotTab","label":"HotspotTab","file_type":"composable","source_file":"android/app/src/main/java/com/blex/app/ui/screens/configurator/HotspotTab.kt"},
    {"id":"DrawerContent","label":"DrawerContent","file_type":"composable","source_file":"android/app/src/main/java/com/blex/app/ui/DrawerContent.kt"},

    # ── Android App – Key Methods ────────────────────────────────────────────
    {"id":"startScanning","label":"startScanning()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt"},
    {"id":"stopScanning","label":"stopScanning()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt"},
    {"id":"forceRestart","label":"forceRestart()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt"},
    {"id":"parseBeacon","label":"parseBeacon()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt"},
    {"id":"parseEddystone","label":"parseEddystone()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt"},
    {"id":"kalmanFilter","label":"kalmanFilter()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt"},
    {"id":"smoothedRssi","label":"smoothedRssi()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt"},
    {"id":"deliverResults","label":"deliverResults()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt"},
    {"id":"onScanBatchReady","label":"onScanBatchReady callback","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt"},
    {"id":"mqttConnect","label":"MqttManager.connect()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/MqttManager.kt"},
    {"id":"mqttPublish","label":"MqttManager.publish()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/MqttManager.kt"},
    {"id":"mqttReconnectIfNeeded","label":"reconnectIfNeeded()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/MqttManager.kt"},
    {"id":"flushQueue","label":"flushQueue()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/MqttManager.kt"},
    {"id":"buildPayload","label":"PayloadBuilder.buildPayload()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/PayloadBuilder.kt"},
    {"id":"buildBatchPayload","label":"PayloadBuilder.buildBatchPayload()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/PayloadBuilder.kt"},
    {"id":"buildFromTemplate","label":"buildFromTemplate()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/PayloadBuilder.kt"},
    {"id":"startWatchdog","label":"startWatchdog()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt"},
    {"id":"scheduleRestart","label":"scheduleRestart()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt"},
    {"id":"handleSettingsChange","label":"handleSettingsChange()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt"},
    {"id":"getBrokerUrl","label":"SettingsManager.getBrokerUrl()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/data/SettingsManager.kt"},
    {"id":"updateBeacons","label":"ScanRepository.updateBeacons()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/data/ScanRepository.kt"},
    {"id":"connectLocal","label":"MqttBridge.connectLocal()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/MqttBridge.kt"},
    {"id":"connectRemote","label":"MqttBridge.connectRemote()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/MqttBridge.kt"},
    {"id":"buildRemoteUrl","label":"buildRemoteUrl()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/MqttBridge.kt"},
    {"id":"handleLocalMessage","label":"handleLocalMessage()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/MqttBridge.kt"},
    {"id":"parseHeartbeat","label":"parseHeartbeat()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/network/UdpDiscoveryManager.kt"},
    {"id":"pruneStale","label":"pruneStale()","file_type":"method","source_file":"android/app/src/main/java/com/blex/app/network/UdpDiscoveryManager.kt"},

    # ── Pi Master ────────────────────────────────────────────────────────────
    {"id":"master_py","label":"master.py (current)","file_type":"module","source_file":"current/master/master.py"},
    {"id":"master_config","label":"master/config.py","file_type":"module","source_file":"current/master/config.py"},
    {"id":"fifo_consumer","label":"fifo_consumer.py","file_type":"module","source_file":"current/master/fifo_consumer.py"},
    {"id":"master_on_message","label":"on_message()","file_type":"function","source_file":"current/master/master.py"},
    {"id":"process_asset","label":"process_asset()","file_type":"function","source_file":"current/master/master.py"},
    {"id":"process_single_beacon","label":"process_single_beacon()","file_type":"function","source_file":"current/master/master.py"},
    {"id":"compute_zone_scores","label":"compute_zone_scores()","file_type":"function","source_file":"current/master/master.py"},
    {"id":"handle_lost_assets","label":"handle_lost_assets()","file_type":"function","source_file":"current/master/master.py"},
    {"id":"push_fifo","label":"push_fifo()","file_type":"function","source_file":"current/master/master.py"},
    {"id":"load_scanner_zone_map","label":"load_scanner_zone_map()","file_type":"function","source_file":"current/master/master.py"},
    {"id":"scanner_zone_reload_loop","label":"scanner_zone_reload_loop()","file_type":"function","source_file":"current/master/master.py"},
    {"id":"health_push_loop","label":"health_push_loop()","file_type":"function","source_file":"current/master/master.py"},
    {"id":"check_scanner_health","label":"check_scanner_health()","file_type":"function","source_file":"current/master/master.py"},
    {"id":"redis_get_last_zone","label":"redis_get_last_zone()","file_type":"function","source_file":"current/master/master.py"},
    {"id":"redis_set_last_zone","label":"redis_set_last_zone()","file_type":"function","source_file":"current/master/master.py"},
    {"id":"normalize_id","label":"normalize_id()","file_type":"function","source_file":"current/master/master.py"},
    {"id":"now_iso","label":"now_iso()","file_type":"function","source_file":"current/master/master.py"},
    {"id":"ASSET_STATE","label":"ASSET_STATE dict","file_type":"data","source_file":"current/master/master.py"},
    {"id":"SCANNER_ZONE_MAP","label":"SCANNER_ZONE_MAP","file_type":"data","source_file":"current/master/master.py"},

    # ── Pi Scanner ───────────────────────────────────────────────────────────
    {"id":"scanner_py","label":"scanner.py (current)","file_type":"module","source_file":"current/scanner/scanner.py"},
    {"id":"scanner_config","label":"scanner/config.py","file_type":"module","source_file":"current/scanner/config.py"},
    {"id":"KalmanRSSI","label":"KalmanRSSI","file_type":"class","source_file":"current/scanner/kalman.py"},
    {"id":"scanner_boot","label":"scanner_boot.py","file_type":"module","source_file":"current/scanner/scanner_boot.py"},
    {"id":"discovery_broadcast","label":"discovery_broadcast.py","file_type":"module","source_file":"current/scanner/discovery_broadcast.py"},
    {"id":"detection_callback","label":"detection_callback()","file_type":"function","source_file":"current/scanner/scanner.py"},
    {"id":"publish_loop","label":"publish_loop()","file_type":"function","source_file":"current/scanner/scanner.py"},
    {"id":"is_target_beacon","label":"is_target_beacon()","file_type":"function","source_file":"current/scanner/scanner.py"},
    {"id":"parse_ibeacon","label":"parse_ibeacon()","file_type":"function","source_file":"current/scanner/scanner.py"},
    {"id":"parse_eddystone_pi","label":"parse_eddystone()","file_type":"function","source_file":"current/scanner/scanner.py"},
    {"id":"parse_battery","label":"parse_battery()","file_type":"function","source_file":"current/scanner/scanner.py"},
    {"id":"connect_mqtt_forever","label":"connect_mqtt_forever()","file_type":"function","source_file":"current/scanner/scanner.py"},
    {"id":"load_tenant_id","label":"_load_tenant_id()","file_type":"function","source_file":"current/scanner/scanner.py"},
    {"id":"register_scanner_boot","label":"register_scanner()","file_type":"function","source_file":"current/scanner/scanner_boot.py"},
    {"id":"start_all_processes","label":"start_all_processes()","file_type":"function","source_file":"current/scanner/scanner_boot.py"},
    {"id":"stop_all_processes","label":"stop_all_processes()","file_type":"function","source_file":"current/scanner/scanner_boot.py"},
    {"id":"watch_master_ip","label":"watch_master_ip()","file_type":"function","source_file":"current/scanner/scanner_boot.py"},
    {"id":"update_config_scanner","label":"update_config()","file_type":"function","source_file":"current/scanner/scanner_boot.py"},
    {"id":"send_heartbeat","label":"send_heartbeat()","file_type":"function","source_file":"current/scanner/discovery_broadcast.py"},
    {"id":"kalman_update","label":"KalmanRSSI.update()","file_type":"method","source_file":"current/scanner/kalman.py"},

    # ── Backend – FastAPI ────────────────────────────────────────────────────
    {"id":"asset_api_main","label":"asset_api main.py","file_type":"module","source_file":"backend/asset_api/main.py"},
    {"id":"MstZone","label":"MstZone (DB model)","file_type":"class","source_file":"backend/asset_api/models.py"},
    {"id":"MstAsset","label":"MstAsset (DB model)","file_type":"class","source_file":"backend/asset_api/models.py"},
    {"id":"MstScanner","label":"MstScanner (DB model)","file_type":"class","source_file":"backend/asset_api/models.py"},
    {"id":"MstZoneScanner","label":"MstZoneScanner (DB model)","file_type":"class","source_file":"backend/asset_api/models.py"},
    {"id":"MovementLog","label":"MovementLog (DB model)","file_type":"class","source_file":"backend/asset_api/models.py"},
    {"id":"MstMaster","label":"MstMaster (DB model)","file_type":"class","source_file":"backend/asset_api/models.py"},
    {"id":"router_movement","label":"router/movement.py","file_type":"module","source_file":"backend/asset_api/routers/movement.py"},
    {"id":"router_runtime","label":"router/runtime.py","file_type":"module","source_file":"backend/asset_api/routers/runtime.py"},
    {"id":"router_health","label":"router/health.py","file_type":"module","source_file":"backend/asset_api/routers/health.py"},
    {"id":"router_zones","label":"router/zones.py","file_type":"module","source_file":"backend/asset_api/routers/zones.py"},
    {"id":"router_assets","label":"router/assets.py","file_type":"module","source_file":"backend/asset_api/routers/assets.py"},
    {"id":"router_scanners","label":"router/scanners.py","file_type":"module","source_file":"backend/asset_api/routers/scanners.py"},
    {"id":"asset_movement_endpoint","label":"POST /api/asset/movement","file_type":"endpoint","source_file":"backend/asset_api/routers/movement.py"},
    {"id":"get_scanner_zone_map_endpoint","label":"GET /api/runtime/scanner-zone-map","file_type":"endpoint","source_file":"backend/asset_api/routers/runtime.py"},
    {"id":"watch_scanner_zone_map_endpoint","label":"GET /api/runtime/scanner-zone-map/watch","file_type":"endpoint","source_file":"backend/asset_api/routers/runtime.py"},
    {"id":"register_master_endpoint","label":"POST /api/runtime/master","file_type":"endpoint","source_file":"backend/asset_api/routers/runtime.py"},
    {"id":"watch_master_ip_endpoint","label":"GET /api/runtime/master/watch","file_type":"endpoint","source_file":"backend/asset_api/routers/runtime.py"},
    {"id":"register_scanner_endpoint","label":"POST /api/runtime/scanner","file_type":"endpoint","source_file":"backend/asset_api/routers/runtime.py"},
    {"id":"bulk_scanner_health_endpoint","label":"POST /api/health/scanners/bulk","file_type":"endpoint","source_file":"backend/asset_api/routers/health.py"},
    {"id":"bulk_beacon_health_endpoint","label":"POST /api/health/beacons/bulk","file_type":"endpoint","source_file":"backend/asset_api/routers/health.py"},
    {"id":"asset_movement_fn","label":"asset_movement()","file_type":"function","source_file":"backend/asset_api/routers/movement.py"},
    {"id":"get_scanner_zone_map_fn","label":"get_scanner_zone_map()","file_type":"function","source_file":"backend/asset_api/routers/runtime.py"},
    {"id":"watch_scanner_zone_map_fn","label":"watch_scanner_zone_map()","file_type":"function","source_file":"backend/asset_api/routers/runtime.py"},
    {"id":"bulk_update_scanner_health","label":"bulk_update_scanner_health()","file_type":"function","source_file":"backend/asset_api/routers/health.py"},
    {"id":"bulk_update_beacon_health","label":"bulk_update_beacon_health()","file_type":"function","source_file":"backend/asset_api/routers/health.py"},
    {"id":"asset_api_events","label":"events.py (asyncio events)","file_type":"module","source_file":"backend/asset_api/events.py"},
    {"id":"master_ip_event","label":"master_ip_event (asyncio.Event)","file_type":"data","source_file":"backend/asset_api/events.py"},
    {"id":"zone_map_event","label":"zone_map_event (asyncio.Event)","file_type":"data","source_file":"backend/asset_api/events.py"},
    {"id":"asset_api_database","label":"database.py","file_type":"module","source_file":"backend/asset_api/database.py"},

    # ── Concepts / Infrastructure ────────────────────────────────────────────
    {"id":"moquette_broker","label":"Moquette MQTT Broker","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/EmbeddedBroker.kt"},
    {"id":"paho_client","label":"Eclipse Paho MQTT Client","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/MqttManager.kt"},
    {"id":"bleak_lib","label":"bleak BLE library","file_type":"concept","source_file":"current/scanner/scanner.py"},
    {"id":"redis_store","label":"Redis FIFO Queue","file_type":"concept","source_file":"current/master/master.py"},
    {"id":"postgresql","label":"PostgreSQL (asset_tracking DB)","file_type":"concept","source_file":"backend/asset_api/database.py"},
    {"id":"sqlalchemy","label":"SQLAlchemy Async ORM","file_type":"concept","source_file":"backend/asset_api/database.py"},
    {"id":"fastapi","label":"FastAPI","file_type":"concept","source_file":"backend/asset_api/main.py"},
    {"id":"ibeacon_protocol","label":"iBeacon Protocol","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt"},
    {"id":"eddystone_protocol","label":"Eddystone Protocol","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt"},
    {"id":"mqtt_protocol","label":"MQTT Protocol (QoS 1)","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/MqttManager.kt"},
    {"id":"wss_transport","label":"WSS Transport (port 443)","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/MqttBridge.kt"},
    {"id":"tls_transport","label":"TLS/MQTTS Transport (port 8883)","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/MqttManager.kt"},
    {"id":"kalman_filter","label":"Kalman Filter (RSSI smoothing)","file_type":"concept","source_file":"current/scanner/kalman.py"},
    {"id":"ema_filter","label":"EMA Filter (inter-harvest jitter)","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt"},
    {"id":"hysteresis","label":"Hysteresis (zone change guard)","file_type":"concept","source_file":"current/master/master.py"},
    {"id":"dwell_time","label":"Dwell Time Filtering","file_type":"concept","source_file":"current/master/master.py"},
    {"id":"zone_confirm","label":"Zone Confirmation Counter","file_type":"concept","source_file":"current/master/master.py"},
    {"id":"zone_exit","label":"Zone EXIT Detection","file_type":"concept","source_file":"current/master/master.py"},
    {"id":"long_polling","label":"Long-Polling (asyncio)","file_type":"concept","source_file":"backend/asset_api/routers/runtime.py"},
    {"id":"udp_broadcast","label":"UDP Broadcast (port 9000)","file_type":"concept","source_file":"current/scanner/discovery_broadcast.py"},
    {"id":"tenant_id","label":"Tenant ID (multi-tenant)","file_type":"concept","source_file":"backend/asset_api/routers/runtime.py"},
    {"id":"mqtt_topic_tenant","label":"MQTT Topic: ble/{tenant_id}/scanner/{mac}","file_type":"concept","source_file":"current/scanner/scanner.py"},
    {"id":"foreground_service","label":"Android Foreground Service","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt"},
    {"id":"start_sticky","label":"START_STICKY Service","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt"},
    {"id":"wake_lock","label":"Android WakeLock","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt"},
    {"id":"alarm_manager","label":"AlarmManager restart","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt"},
    {"id":"stateflow","label":"StateFlow (Kotlin coroutines)","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/data/ScanRepository.kt"},
    {"id":"shared_preferences","label":"SharedPreferences","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/data/SettingsManager.kt"},
    {"id":"payload_template","label":"MQTT Payload Template Tokens","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/PayloadBuilder.kt"},
    {"id":"battery_svc_uuid","label":"Battery Service UUID 0xFFF0","file_type":"concept","source_file":"current/scanner/scanner.py"},
    {"id":"sigmatic_server","label":"DGX/sigmatic-asc.tech server","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/AppConfig.kt"},
    {"id":"caddy_proxy","label":"Caddy Reverse Proxy","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/AppConfig.kt"},
    {"id":"raspberry_pi","label":"Raspberry Pi Scanner","file_type":"concept","source_file":"current/scanner/scanner_boot.py"},
    {"id":"esp32","label":"ESP32 Scanner","file_type":"concept","source_file":"current/scanner/discovery_broadcast.py"},
    {"id":"android_tablet","label":"Android Tablet Hub","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt"},
    {"id":"provisioner","label":"WiFi Provisioner","file_type":"concept","source_file":"current/scanner/provisioner_service.py"},
    {"id":"local_mode","label":"Local Mode (Pi→Tablet MQTT)","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt"},
    {"id":"cloud_mode","label":"Cloud Mode (Bridge→DGX WSS)","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/MqttBridge.kt"},
    {"id":"offline_queue","label":"Offline Message Queue","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/MqttBridge.kt"},
    {"id":"watchdog","label":"Watchdog (60s health check)","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt"},
    {"id":"beacon_ttl","label":"Beacon TTL (10s memory)","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/data/ScanRepository.kt"},
    {"id":"scan_restart_25min","label":"Scan Restart Every 25 min","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt"},
    {"id":"x_tenant_header","label":"X-Tenant-ID HTTP Header","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/data/ApiService.kt"},
    {"id":"self_signed_tls","label":"Self-signed TLS bypass","file_type":"concept","source_file":"android/app/src/main/java/com/blex/app/MqttManager.kt"},
]

# ─────────────────────────────────────────────────────────────────────────────
# EDGES
# ─────────────────────────────────────────────────────────────────────────────
EDGES = [
    # ── BleScannerService orchestrates everything ────────────────────────────
    {"source":"BleScannerService","target":"BleScanner","relation":"instantiates","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt","weight":2.0},
    {"source":"BleScannerService","target":"MqttManager","relation":"instantiates","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt","weight":2.0},
    {"source":"BleScannerService","target":"EmbeddedBroker","relation":"instantiates","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt","weight":2.0},
    {"source":"BleScannerService","target":"MqttBridge","relation":"instantiates","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt","weight":2.0},
    {"source":"BleScannerService","target":"UdpDiscoveryManager","relation":"instantiates","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt","weight":1.5},
    {"source":"BleScannerService","target":"AlertNotificationManager","relation":"instantiates","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt","weight":1.0},
    {"source":"BleScannerService","target":"SettingsManager","relation":"uses","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt","weight":1.5},
    {"source":"BleScannerService","target":"ScanRepository","relation":"writes_to","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt","weight":1.5},
    {"source":"BleScannerService","target":"foreground_service","relation":"implements","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt","weight":1.5},
    {"source":"BleScannerService","target":"start_sticky","relation":"uses","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt","weight":1.0},
    {"source":"BleScannerService","target":"wake_lock","relation":"acquires","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt","weight":1.0},
    {"source":"BleScannerService","target":"alarm_manager","relation":"uses","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt","weight":1.0},
    {"source":"BleScannerService","target":"watchdog","relation":"runs","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt","weight":1.0},
    {"source":"BleScannerService","target":"startWatchdog","relation":"calls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt","weight":1.0},
    {"source":"BleScannerService","target":"scheduleRestart","relation":"calls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt","weight":1.0},
    {"source":"BleScannerService","target":"handleSettingsChange","relation":"calls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt","weight":1.0},
    {"source":"BleScannerService","target":"PayloadBuilder","relation":"calls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt","weight":1.5},
    {"source":"BleScannerService","target":"local_mode","relation":"implements","confidence":"EXTRACTED","confidence_score":0.9,"source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt","weight":1.0},
    {"source":"BleScannerService","target":"cloud_mode","relation":"implements","confidence":"EXTRACTED","confidence_score":0.9,"source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt","weight":1.0},

    # ── BleScanner ───────────────────────────────────────────────────────────
    {"source":"BleScanner","target":"BeaconData","relation":"produces","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt","weight":2.0},
    {"source":"BleScanner","target":"ibeacon_protocol","relation":"parses","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt","weight":1.5},
    {"source":"BleScanner","target":"eddystone_protocol","relation":"parses","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt","weight":1.5},
    {"source":"BleScanner","target":"kalman_filter","relation":"applies","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt","weight":1.5},
    {"source":"BleScanner","target":"ema_filter","relation":"applies","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt","weight":1.5},
    {"source":"BleScanner","target":"scan_restart_25min","relation":"implements","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt","weight":1.0},
    {"source":"BleScanner","target":"SettingsManager","relation":"reads","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt","weight":1.0},
    {"source":"BleScanner","target":"ScanRepository","relation":"writes_to","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt","weight":1.0},
    {"source":"BleScanner","target":"startScanning","relation":"exposes","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt","weight":1.0},
    {"source":"BleScanner","target":"parseBeacon","relation":"calls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt","weight":1.0},
    {"source":"BleScanner","target":"onScanBatchReady","relation":"fires","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt","weight":1.0},
    {"source":"onScanBatchReady","target":"buildBatchPayload","relation":"triggers","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt","weight":1.5},
    {"source":"onScanBatchReady","target":"mqttPublish","relation":"triggers","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt","weight":1.5},
    {"source":"onScanBatchReady","target":"updateBeacons","relation":"triggers","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt","weight":1.0},
    {"source":"kalmanFilter","target":"kalman_filter","relation":"implements","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt","weight":1.0},
    {"source":"smoothedRssi","target":"kalmanFilter","relation":"calls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt","weight":1.0},
    {"source":"smoothedRssi","target":"ema_filter","relation":"applies","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScanner.kt","weight":1.0},

    # ── MqttManager ─────────────────────────────────────────────────────────
    {"source":"MqttManager","target":"paho_client","relation":"uses","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/MqttManager.kt","weight":2.0},
    {"source":"MqttManager","target":"SettingsManager","relation":"reads","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/MqttManager.kt","weight":1.0},
    {"source":"MqttManager","target":"mqtt_protocol","relation":"uses","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/MqttManager.kt","weight":1.5},
    {"source":"MqttManager","target":"tls_transport","relation":"supports","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/MqttManager.kt","weight":1.0},
    {"source":"MqttManager","target":"self_signed_tls","relation":"implements","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/MqttManager.kt","weight":1.0},
    {"source":"MqttManager","target":"HostnameInsensitiveSocketFactory","relation":"uses","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/MqttManager.kt","weight":1.0},
    {"source":"MqttManager","target":"offline_queue","relation":"maintains","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/MqttManager.kt","weight":1.0},
    {"source":"MqttManager","target":"mqtt_topic_tenant","relation":"publishes_to","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/MqttManager.kt","weight":1.5},
    {"source":"mqttConnect","target":"EmbeddedBroker","relation":"connects_to","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/MqttManager.kt","weight":1.5},
    {"source":"getBrokerUrl","target":"EmbeddedBroker","relation":"routes_through","confidence":"EXTRACTED","confidence_score":0.9,"source_file":"android/app/src/main/java/com/blex/app/data/SettingsManager.kt","weight":1.0},

    # ── EmbeddedBroker ───────────────────────────────────────────────────────
    {"source":"EmbeddedBroker","target":"moquette_broker","relation":"wraps","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/EmbeddedBroker.kt","weight":2.0},
    {"source":"EmbeddedBroker","target":"SettingsManager","relation":"reads","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/EmbeddedBroker.kt","weight":1.0},
    {"source":"raspberry_pi","target":"EmbeddedBroker","relation":"connects_to","confidence":"EXTRACTED","confidence_score":0.9,"source_file":"android/app/src/main/java/com/blex/app/EmbeddedBroker.kt","weight":1.5},
    {"source":"esp32","target":"EmbeddedBroker","relation":"connects_to","confidence":"EXTRACTED","confidence_score":0.9,"source_file":"android/app/src/main/java/com/blex/app/EmbeddedBroker.kt","weight":1.5},

    # ── MqttBridge ───────────────────────────────────────────────────────────
    {"source":"MqttBridge","target":"EmbeddedBroker","relation":"subscribes_to","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/MqttBridge.kt","weight":2.0},
    {"source":"MqttBridge","target":"sigmatic_server","relation":"forwards_to","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/MqttBridge.kt","weight":2.0},
    {"source":"MqttBridge","target":"wss_transport","relation":"uses","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/MqttBridge.kt","weight":1.5},
    {"source":"MqttBridge","target":"offline_queue","relation":"maintains","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/MqttBridge.kt","weight":1.0},
    {"source":"MqttBridge","target":"self_signed_tls","relation":"implements","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/MqttBridge.kt","weight":1.0},
    {"source":"connectLocal","target":"EmbeddedBroker","relation":"connects_to","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/MqttBridge.kt","weight":1.0},
    {"source":"connectRemote","target":"sigmatic_server","relation":"connects_to","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/MqttBridge.kt","weight":1.0},
    {"source":"buildRemoteUrl","target":"wss_transport","relation":"produces","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/MqttBridge.kt","weight":1.0},
    {"source":"handleLocalMessage","target":"connectRemote","relation":"calls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/MqttBridge.kt","weight":1.0},

    # ── PayloadBuilder ───────────────────────────────────────────────────────
    {"source":"PayloadBuilder","target":"BeaconData","relation":"reads","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/PayloadBuilder.kt","weight":1.5},
    {"source":"PayloadBuilder","target":"SettingsManager","relation":"reads","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/PayloadBuilder.kt","weight":1.0},
    {"source":"PayloadBuilder","target":"payload_template","relation":"applies","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/PayloadBuilder.kt","weight":1.5},
    {"source":"PayloadBuilder","target":"tenant_id","relation":"injects","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/PayloadBuilder.kt","weight":1.0},
    {"source":"buildBatchPayload","target":"buildPayload","relation":"calls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/PayloadBuilder.kt","weight":1.0},
    {"source":"buildPayload","target":"buildFromTemplate","relation":"calls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/PayloadBuilder.kt","weight":1.0},

    # ── Data layer ───────────────────────────────────────────────────────────
    {"source":"ScanRepository","target":"stateflow","relation":"uses","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/data/ScanRepository.kt","weight":1.5},
    {"source":"ScanRepository","target":"beacon_ttl","relation":"enforces","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/data/ScanRepository.kt","weight":1.0},
    {"source":"ScanRepository","target":"ApiService","relation":"uses","confidence":"EXTRACTED","confidence_score":0.8,"source_file":"android/app/src/main/java/com/blex/app/data/ScanRepository.kt","weight":1.0},
    {"source":"SettingsManager","target":"shared_preferences","relation":"uses","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/data/SettingsManager.kt","weight":1.5},
    {"source":"SettingsManager","target":"tenant_id","relation":"stores","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/data/SettingsManager.kt","weight":1.0},
    {"source":"ApiService","target":"asset_api_main","relation":"calls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/data/ApiService.kt","weight":1.5},
    {"source":"ApiService","target":"x_tenant_header","relation":"sends","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/data/ApiService.kt","weight":1.0},

    # ── Survivability stack ──────────────────────────────────────────────────
    {"source":"BootReceiver","target":"BleScannerService","relation":"restarts","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BootReceiver.kt","weight":1.5},
    {"source":"ServiceRestartReceiver","target":"BleScannerService","relation":"restarts","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/ServiceRestartReceiver.kt","weight":1.5},
    {"source":"scheduleRestart","target":"alarm_manager","relation":"uses","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt","weight":1.0},
    {"source":"scheduleRestart","target":"ServiceRestartReceiver","relation":"fires","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt","weight":1.0},

    # ── UDP Discovery ────────────────────────────────────────────────────────
    {"source":"UdpDiscoveryManager","target":"udp_broadcast","relation":"listens_for","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/network/UdpDiscoveryManager.kt","weight":1.5},
    {"source":"UdpDiscoveryManager","target":"ScanRepository","relation":"writes_to","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/network/UdpDiscoveryManager.kt","weight":1.0},
    {"source":"parseHeartbeat","target":"DiscoveredScanner","relation":"produces","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/network/UdpDiscoveryManager.kt","weight":1.0},
    {"source":"discovery_broadcast","target":"udp_broadcast","relation":"broadcasts_on","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/scanner/discovery_broadcast.py","weight":1.5},
    {"source":"send_heartbeat","target":"UdpDiscoveryManager","relation":"feeds","confidence":"EXTRACTED","confidence_score":0.9,"source_file":"current/scanner/discovery_broadcast.py","weight":1.0},

    # ── UI Screens ───────────────────────────────────────────────────────────
    {"source":"ScannerScreen","target":"ScanRepository","relation":"observes","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/ui/screens/ScannerScreen.kt","weight":1.5},
    {"source":"SettingsScreen","target":"SettingsManager","relation":"reads_writes","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/ui/screens/SettingsScreen.kt","weight":1.5},
    {"source":"LogScreen","target":"ScanRepository","relation":"observes","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/ui/screens/LogScreen.kt","weight":1.0},
    {"source":"AssetsTab","target":"ApiService","relation":"calls","confidence":"EXTRACTED","confidence_score":0.9,"source_file":"android/app/src/main/java/com/blex/app/ui/screens/configurator/AssetsTab.kt","weight":1.0},
    {"source":"ZonesTab","target":"ApiService","relation":"calls","confidence":"EXTRACTED","confidence_score":0.9,"source_file":"android/app/src/main/java/com/blex/app/ui/screens/configurator/ZonesTab.kt","weight":1.0},
    {"source":"ScannersTab","target":"UdpDiscoveryManager","relation":"displays","confidence":"EXTRACTED","confidence_score":0.9,"source_file":"android/app/src/main/java/com/blex/app/ui/screens/configurator/ScannersTab.kt","weight":1.0},
    {"source":"ScannersTab","target":"ScanRepository","relation":"observes","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/ui/screens/configurator/ScannersTab.kt","weight":1.0},
    {"source":"SetupWizard","target":"ApiService","relation":"calls","confidence":"EXTRACTED","confidence_score":0.9,"source_file":"android/app/src/main/java/com/blex/app/ui/screens/SetupWizard.kt","weight":1.0},
    {"source":"LoginScreen","target":"ApiService","relation":"calls","confidence":"EXTRACTED","confidence_score":0.9,"source_file":"android/app/src/main/java/com/blex/app/ui/screens/LoginScreen.kt","weight":1.0},
    {"source":"DashboardWebScreen","target":"sigmatic_server","relation":"loads","confidence":"EXTRACTED","confidence_score":0.9,"source_file":"android/app/src/main/java/com/blex/app/ui/screens/DashboardWebScreen.kt","weight":1.0},
    {"source":"MainActivity","target":"BleScannerService","relation":"starts","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/MainActivity.kt","weight":1.5},
    {"source":"MainActivity","target":"ScannerScreen","relation":"hosts","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/MainActivity.kt","weight":1.0},

    # ── Pi Scanner flow ──────────────────────────────────────────────────────
    {"source":"scanner_py","target":"bleak_lib","relation":"uses","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/scanner/scanner.py","weight":2.0},
    {"source":"scanner_py","target":"KalmanRSSI","relation":"uses","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/scanner/scanner.py","weight":2.0},
    {"source":"scanner_py","target":"ibeacon_protocol","relation":"parses","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/scanner/scanner.py","weight":1.5},
    {"source":"scanner_py","target":"eddystone_protocol","relation":"parses","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/scanner/scanner.py","weight":1.5},
    {"source":"scanner_py","target":"battery_svc_uuid","relation":"parses","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/scanner/scanner.py","weight":1.0},
    {"source":"scanner_py","target":"mqtt_topic_tenant","relation":"publishes_to","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/scanner/scanner.py","weight":1.5},
    {"source":"scanner_py","target":"EmbeddedBroker","relation":"publishes_to","confidence":"EXTRACTED","confidence_score":0.9,"source_file":"current/scanner/scanner.py","weight":1.5},
    {"source":"detection_callback","target":"KalmanRSSI","relation":"calls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/scanner/scanner.py","weight":1.0},
    {"source":"kalman_update","target":"kalman_filter","relation":"implements","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/scanner/kalman.py","weight":1.5},
    {"source":"publish_loop","target":"detection_callback","relation":"reads_state_from","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/scanner/scanner.py","weight":1.0},
    {"source":"load_tenant_id","target":"tenant_id","relation":"loads","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/scanner/scanner.py","weight":1.0},
    {"source":"scanner_boot","target":"scanner_py","relation":"starts","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/scanner/scanner_boot.py","weight":2.0},
    {"source":"scanner_boot","target":"discovery_broadcast","relation":"starts","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/scanner/scanner_boot.py","weight":1.5},
    {"source":"scanner_boot","target":"provisioner","relation":"starts","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/scanner/scanner_boot.py","weight":1.0},
    {"source":"register_scanner_boot","target":"register_scanner_endpoint","relation":"calls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/scanner/scanner_boot.py","weight":1.5},
    {"source":"watch_master_ip","target":"watch_master_ip_endpoint","relation":"long_polls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/scanner/scanner_boot.py","weight":1.5},
    {"source":"update_config_scanner","target":"scanner_config","relation":"writes","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/scanner/scanner_boot.py","weight":1.0},

    # ── Pi Master flow ───────────────────────────────────────────────────────
    {"source":"master_py","target":"redis_store","relation":"uses","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/master/master.py","weight":2.0},
    {"source":"master_py","target":"mqtt_protocol","relation":"subscribes_to","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/master/master.py","weight":2.0},
    {"source":"master_py","target":"ASSET_STATE","relation":"maintains","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/master/master.py","weight":1.5},
    {"source":"master_py","target":"SCANNER_ZONE_MAP","relation":"maintains","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/master/master.py","weight":1.5},
    {"source":"master_on_message","target":"process_single_beacon","relation":"calls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/master/master.py","weight":1.5},
    {"source":"process_single_beacon","target":"process_asset","relation":"calls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/master/master.py","weight":1.5},
    {"source":"process_asset","target":"compute_zone_scores","relation":"calls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/master/master.py","weight":1.5},
    {"source":"process_asset","target":"hysteresis","relation":"applies","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/master/master.py","weight":1.5},
    {"source":"process_asset","target":"dwell_time","relation":"applies","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/master/master.py","weight":1.5},
    {"source":"process_asset","target":"zone_confirm","relation":"applies","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/master/master.py","weight":1.5},
    {"source":"process_asset","target":"push_fifo","relation":"calls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/master/master.py","weight":1.5},
    {"source":"process_asset","target":"redis_get_last_zone","relation":"calls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/master/master.py","weight":1.0},
    {"source":"process_asset","target":"redis_set_last_zone","relation":"calls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/master/master.py","weight":1.0},
    {"source":"handle_lost_assets","target":"zone_exit","relation":"implements","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/master/master.py","weight":1.5},
    {"source":"handle_lost_assets","target":"push_fifo","relation":"calls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/master/master.py","weight":1.0},
    {"source":"push_fifo","target":"redis_store","relation":"writes_to","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/master/master.py","weight":1.5},
    {"source":"load_scanner_zone_map","target":"watch_scanner_zone_map_endpoint","relation":"long_polls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/master/master.py","weight":1.5},
    {"source":"scanner_zone_reload_loop","target":"load_scanner_zone_map","relation":"calls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/master/master.py","weight":1.0},
    {"source":"health_push_loop","target":"bulk_scanner_health_endpoint","relation":"calls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/master/master.py","weight":1.5},
    {"source":"health_push_loop","target":"bulk_beacon_health_endpoint","relation":"calls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/master/master.py","weight":1.5},
    {"source":"health_push_loop","target":"check_scanner_health","relation":"calls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/master/master.py","weight":1.0},
    {"source":"fifo_consumer","target":"redis_store","relation":"reads_from","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/master/fifo_consumer.py","weight":2.0},
    {"source":"fifo_consumer","target":"asset_movement_endpoint","relation":"calls","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/master/fifo_consumer.py","weight":2.0},

    # ── Backend API ──────────────────────────────────────────────────────────
    {"source":"asset_api_main","target":"fastapi","relation":"uses","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"backend/asset_api/main.py","weight":2.0},
    {"source":"asset_api_main","target":"router_movement","relation":"includes","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"backend/asset_api/main.py","weight":1.0},
    {"source":"asset_api_main","target":"router_runtime","relation":"includes","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"backend/asset_api/main.py","weight":1.0},
    {"source":"asset_api_main","target":"router_health","relation":"includes","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"backend/asset_api/main.py","weight":1.0},
    {"source":"asset_api_main","target":"router_zones","relation":"includes","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"backend/asset_api/main.py","weight":1.0},
    {"source":"asset_api_main","target":"router_assets","relation":"includes","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"backend/asset_api/main.py","weight":1.0},
    {"source":"asset_api_main","target":"router_scanners","relation":"includes","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"backend/asset_api/main.py","weight":1.0},
    {"source":"asset_api_database","target":"sqlalchemy","relation":"uses","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"backend/asset_api/database.py","weight":1.5},
    {"source":"asset_api_database","target":"postgresql","relation":"connects_to","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"backend/asset_api/database.py","weight":2.0},
    {"source":"MstZone","target":"postgresql","relation":"mapped_to","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"backend/asset_api/models.py","weight":1.0},
    {"source":"MstAsset","target":"postgresql","relation":"mapped_to","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"backend/asset_api/models.py","weight":1.0},
    {"source":"MstScanner","target":"postgresql","relation":"mapped_to","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"backend/asset_api/models.py","weight":1.0},
    {"source":"MovementLog","target":"postgresql","relation":"mapped_to","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"backend/asset_api/models.py","weight":1.0},
    {"source":"MstZoneScanner","target":"MstZone","relation":"joins","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"backend/asset_api/models.py","weight":1.0},
    {"source":"MstZoneScanner","target":"MstScanner","relation":"joins","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"backend/asset_api/models.py","weight":1.0},
    {"source":"asset_movement_fn","target":"MovementLog","relation":"writes","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"backend/asset_api/routers/movement.py","weight":1.5},
    {"source":"asset_movement_fn","target":"MstAsset","relation":"updates","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"backend/asset_api/routers/movement.py","weight":1.5},
    {"source":"get_scanner_zone_map_fn","target":"MstZoneScanner","relation":"queries","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"backend/asset_api/routers/runtime.py","weight":1.0},
    {"source":"watch_scanner_zone_map_fn","target":"long_polling","relation":"implements","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"backend/asset_api/routers/runtime.py","weight":1.5},
    {"source":"watch_scanner_zone_map_fn","target":"zone_map_event","relation":"waits_on","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"backend/asset_api/routers/runtime.py","weight":1.0},
    {"source":"bulk_update_scanner_health","target":"MstScanner","relation":"updates","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"backend/asset_api/routers/health.py","weight":1.0},
    {"source":"bulk_update_beacon_health","target":"MstAsset","relation":"updates","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"backend/asset_api/routers/health.py","weight":1.0},
    {"source":"asset_api_events","target":"master_ip_event","relation":"defines","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"backend/asset_api/events.py","weight":1.0},
    {"source":"asset_api_events","target":"zone_map_event","relation":"defines","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"backend/asset_api/events.py","weight":1.0},

    # ── Infrastructure ───────────────────────────────────────────────────────
    {"source":"sigmatic_server","target":"caddy_proxy","relation":"uses","confidence":"EXTRACTED","confidence_score":0.9,"source_file":"android/app/src/main/java/com/blex/app/AppConfig.kt","weight":1.0},
    {"source":"caddy_proxy","target":"wss_transport","relation":"terminates","confidence":"EXTRACTED","confidence_score":0.9,"source_file":"android/app/src/main/java/com/blex/app/AppConfig.kt","weight":1.0},
    {"source":"caddy_proxy","target":"asset_api_main","relation":"reverse_proxies","confidence":"EXTRACTED","confidence_score":0.9,"source_file":"android/app/src/main/java/com/blex/app/AppConfig.kt","weight":1.0},
    {"source":"raspberry_pi","target":"scanner_py","relation":"runs","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/scanner/scanner.py","weight":1.5},
    {"source":"android_tablet","target":"BleScannerService","relation":"runs","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/BleScannerService.kt","weight":1.5},
    {"source":"android_tablet","target":"EmbeddedBroker","relation":"runs","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/EmbeddedBroker.kt","weight":1.5},
    {"source":"tenant_id","target":"mqtt_topic_tenant","relation":"scopes","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/scanner/scanner.py","weight":1.0},
    {"source":"tenant_id","target":"redis_store","relation":"namespaces","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"current/master/config.py","weight":1.0},
    {"source":"tenant_id","target":"x_tenant_header","relation":"sent_as","confidence":"EXTRACTED","confidence_score":1.0,"source_file":"android/app/src/main/java/com/blex/app/data/ApiService.kt","weight":1.0},
]

# ─────────────────────────────────────────────────────────────────────────────
# Normalise nodes — add missing fields
# ─────────────────────────────────────────────────────────────────────────────
for n in NODES:
    n.setdefault("source_location", None)
    n.setdefault("source_url", None)
    n.setdefault("captured_at", None)
    n.setdefault("author", None)
    n.setdefault("contributor", None)

for e in EDGES:
    e.setdefault("source_location", None)

semantic = {"nodes": NODES, "edges": EDGES, "hyperedges": [], "input_tokens": 0, "output_tokens": 0}
Path("O:/blex/graphify-out/.graphify_semantic.json").write_text(
    json.dumps(semantic, indent=2, ensure_ascii=False), encoding="utf-8")
print(f"Semantic: {len(NODES)} nodes, {len(EDGES)} edges")

# ── Step 3: merge AST + semantic (with noise filtering) ───────────────────────
ast = json.loads(Path("O:/blex/graphify-out/.graphify_ast.json").read_text(encoding="utf-8"))

# Filter AST nodes — keep only real BleX files
ast_nodes_clean = [n for n in ast["nodes"] if is_blex(n.get("source_file", ""))]
ast_node_ids    = {n["id"] for n in ast_nodes_clean}

# Filter AST edges — both endpoints must survive
ast_edges_clean = [
    e for e in ast["edges"]
    if e["source"] in ast_node_ids and e["target"] in ast_node_ids
]

print(f"AST after filter: {len(ast_nodes_clean)} nodes (was {len(ast['nodes'])}), "
      f"{len(ast_edges_clean)} edges (was {len(ast['edges'])})")

seen = set(ast_node_ids)
merged_nodes = list(ast_nodes_clean)
for n in NODES:
    if n["id"] not in seen:
        merged_nodes.append(n)
        seen.add(n["id"])

merged = {
    "nodes": merged_nodes,
    "edges": ast_edges_clean + EDGES,
    "hyperedges": [],
    "input_tokens": 0,
    "output_tokens": 0,
}
Path("O:/blex/graphify-out/.graphify_extract.json").write_text(
    json.dumps(merged, indent=2, ensure_ascii=False), encoding="utf-8")
print(f"Merged extract: {len(merged_nodes)} nodes, {len(merged['edges'])} edges")

# ── Step 4: build graph + cluster + analyze + HTML ───────────────────────────
from graphify.build   import build_from_json
from graphify.cluster import cluster, score_all
from graphify.analyze import god_nodes, surprising_connections, suggest_questions
from graphify.report  import generate
from graphify.export  import to_json, to_html

detection = json.loads(Path("O:/blex/graphify-out/.graphify_detect.json").read_text(encoding="utf-8-sig"))

G           = build_from_json(merged)
communities = cluster(G)
cohesion    = score_all(G, communities)
tokens      = {"input": 0, "output": 0}
gods        = god_nodes(G)
surprises   = surprising_connections(G, communities)
labels      = {cid: f"Community {cid}" for cid in communities}
questions   = suggest_questions(G, communities, labels)

report = generate(G, communities, cohesion, labels, gods, surprises, detection, tokens,
                  "O:/blex", suggested_questions=questions)
Path("O:/blex/graphify-out/GRAPH_REPORT.md").write_text(report, encoding="utf-8")
print("GRAPH_REPORT.md written")

to_json(G, communities, "O:/blex/graphify-out/graph.json")
print("graph.json written")

analysis = {
    "communities": {str(k): v for k, v in communities.items()},
    "cohesion":    {str(k): v for k, v in cohesion.items()},
    "gods":        gods,
    "surprises":   surprises,
    "questions":   questions,
}
Path("O:/blex/graphify-out/.graphify_analysis.json").write_text(
    json.dumps(analysis, indent=2, ensure_ascii=False), encoding="utf-8")

to_html(G, communities, "O:/blex/graphify-out/graph.html", community_labels=labels)
print("graph.html written")

print(f"\nDone: {G.number_of_nodes()} nodes, {G.number_of_edges()} edges, {len(communities)} communities")
