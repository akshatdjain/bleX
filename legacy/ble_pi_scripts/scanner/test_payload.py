import asyncio
import binascii
from bleak import BleakScanner, BLEDevice, AdvertisementData

# Set this to the MAC addresses of your beacons to filter the noise
# If you leave it empty, it will print EVERYTHING (very spammy)
TARGET_MACS = ["DC:0D:30:26:C4:1C", "F0:03:2A:47:01:1E"]

def detection_callback(device: BLEDevice, ad: AdvertisementData):
    mac = device.address.upper()
    
    if TARGET_MACS and mac not in TARGET_MACS:
        return

    print("\n" + "="*50)
    print(f"BEACON SCANNED: {mac} (RSSI: {ad.rssi})")
    print(f"Name: {ad.local_name}")
    print("-" * 50)

    # 1. Print Manufacturer Specific Data (usually where iBeacon data is)
    if ad.manufacturer_data:
        print("Manufacturer Data:")
        for company_id, data_bytes in ad.manufacturer_data.items():
            hex_str = binascii.hexlify(data_bytes).decode('ascii').upper()
            spaced_hex = ' '.join(hex_str[i:i+2] for i in range(0, len(hex_str), 2))
            print(f"  Company ID: {company_id} (0x{company_id:04X})")
            print(f"  Raw Bytes : {spaced_hex}")
    else:
        print("Manufacturer Data: NONE")

    # 2. Print Service Data (usually where Eddystone or Battery data is)
    if ad.service_data:
        print("Service Data:")
        for svc_uuid, data_bytes in ad.service_data.items():
            hex_str = binascii.hexlify(data_bytes).decode('ascii').upper()
            spaced_hex = ' '.join(hex_str[i:i+2] for i in range(0, len(hex_str), 2))
            print(f"  UUID      : {svc_uuid}")
            print(f"  Raw Bytes : {spaced_hex}")
    else:
        print("Service Data: NONE")

    # 3. Print Service UUIDs (just advertised availability)
    if ad.service_uuids:
        print(f"Service UUIDs Advertised: {', '.join(ad.service_uuids)}")
    else:
        print("Service UUIDs: NONE")

    print("="*50)


async def main():
    print("Starting raw BLE beacon payload scanner...")
    if TARGET_MACS:
        print(f"Filtering for MACs: {', '.join(TARGET_MACS)}")
    else:
        print("WARNING: No MAC filter applied. Prepare for terminal spam!")
        
    scanner = BleakScanner(detection_callback)
    async with scanner:
        # Scan for 60 seconds
        await asyncio.sleep(60)

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nScanner stopped.")
