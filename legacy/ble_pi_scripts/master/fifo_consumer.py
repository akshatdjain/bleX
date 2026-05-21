# fifo_consumer.py
# -------------------------------------------------
# FIFO Consumer → API → DB
# -------------------------------------------------
print("[FIFO] Script started", flush=True)

import json
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

# -------------------------------------------------
# REDIS SETUP
# -------------------------------------------------
redis_kwargs = {
    "host": REDIS_HOST,
    "port": REDIS_PORT,
    "decode_responses": True,
}
if REDIS_PASSWORD:
    redis_kwargs["password"] = REDIS_PASSWORD

redis_client = redis.Redis(**redis_kwargs)

# -------------------------------------------------
# MAIN LOOP
# -------------------------------------------------
print("[CONSUMER] FIFO consumer started")

while True:
    try:
        # -----------------------------------------
        # Blocking pop (FIFO)
        # -----------------------------------------
        item = redis_client.blpop(REDIS_ZONE_QUEUE_KEY, timeout=5)

        if not item:
            time.sleep(CONSUMER_SLEEP_SEC)
            continue

        _, raw_event = item
        event = json.loads(raw_event)

        # -----------------------------------------
        # Call API
        # -----------------------------------------
        try:
            resp = requests.post(
                API_URL,
                json=event,
                timeout=API_TIMEOUT,
            )

            if resp.status_code == 200:
                print(
                    f"[API OK] {event['asset_mac']} "
                    f"{event.get('from_zone_id')} → {event.get('to_zone_id')}"
                )
            else:
                print(
                    f"[API ERR {resp.status_code}] pushing back to queue"
                )
                redis_client.rpush(
                    REDIS_ZONE_QUEUE_KEY,
                    json.dumps(event)
                )
                time.sleep(2)

        except Exception as e:
            print(f"[API DOWN] {e} → re-queueing")
            redis_client.rpush(
                REDIS_ZONE_QUEUE_KEY,
                json.dumps(event)
            )
            time.sleep(3)

    except KeyboardInterrupt:
        print("\n[CONSUMER] Stopped by user")
        break

    except Exception as e:
        print(f"[CONSUMER ERROR] {e}")
        time.sleep(5)
