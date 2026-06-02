import asyncio

# Events for triggering instant reloads

master_ip_event = asyncio.Event()
zone_map_event = asyncio.Event()
ZONE_MAP_VERSION = 1

async def notify_master_ip_changed():
    master_ip_event.set()
    await asyncio.sleep(0.1)
    master_ip_event.clear()

async def notify_zone_map_changed():
    global ZONE_MAP_VERSION
    ZONE_MAP_VERSION += 1
    zone_map_event.set()
    await asyncio.sleep(0.1)
    zone_map_event.clear()
