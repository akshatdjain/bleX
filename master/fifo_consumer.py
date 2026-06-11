# fifo_consumer.py
# -------------------------------------------------
# FIFO Consumer → API → DB  (multi-tenant)
# Reads from a single global queue; routes each event
# to the correct tenant via X-Tenant-ID header.
# -------------------------------------------------
print("[FIFO] Script started", flush=True)

import json
import os
import time
import requests
import redis

from config import (
    REDIS_HOST,
    REDIS_PORT,
    REDIS_PASSWORD,
    REDIS_ZONE_QUEUE_KEY,
    API_URL,
    API_TIMEOUT,
    CONSUMER_SLEEP_SEC,
)

BLEX_API_TOKEN = os.getenv("BLEX_API_TOKEN", "")

# -------------------------------------------------
# REDIS SETUP
# -------------------------------------------------
redis_client = redis.Redis(
    host=REDIS_HOST,
    port=REDIS_PORT,
    password=REDIS_PASSWORD,
    decode_responses=True,
)

# -------------------------------------------------
# MAIN LOOP
# -------------------------------------------------
print(f"[CONSUMER] FIFO consumer started — queue: {REDIS_ZONE_QUEUE_KEY}", flush=True)

while True:
    try:
        item = redis_client.blpop(REDIS_ZONE_QUEUE_KEY, timeout=5)

        if not item:
            time.sleep(CONSUMER_SLEEP_SEC)
            continue

        _, raw_event = item
        event = json.loads(raw_event)

        try:
            # Route to the correct tenant via the event's tenant_id field
            tenant_id = event.get("tenant_id") or ""
            headers = {}
            if tenant_id:
                headers["X-Tenant-ID"] = tenant_id
            if BLEX_API_TOKEN:
                headers["Authorization"] = f"Bearer {BLEX_API_TOKEN}"

            resp = requests.post(
                API_URL,
                json=event,
                headers=headers,
                timeout=API_TIMEOUT,
            )

            if resp.status_code == 200:
                print(
                    f"[API OK] {tenant_id}/{event['asset_mac']} "
                    f"{event.get('from_zone_id')} → {event.get('to_zone_id')}",
                    flush=True,
                )
            else:
                print(
                    f"[API ERR {resp.status_code}] {tenant_id}/{event.get('asset_mac')} — "
                    f"pushing back to queue",
                    flush=True,
                )
                redis_client.rpush(REDIS_ZONE_QUEUE_KEY, json.dumps(event))
                time.sleep(2)

        except Exception as e:
            print(f"[API DOWN] {e} → re-queueing", flush=True)
            redis_client.rpush(REDIS_ZONE_QUEUE_KEY, json.dumps(event))
            time.sleep(3)

    except KeyboardInterrupt:
        print("\n[CONSUMER] Stopped by user", flush=True)
        break

    except Exception as e:
        print(f"[CONSUMER ERROR] {e}", flush=True)
        time.sleep(5)
