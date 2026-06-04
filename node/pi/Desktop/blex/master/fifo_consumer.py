import json
import time
import os
import sys

_BLEX_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _BLEX_DIR not in sys.path:
    sys.path.insert(0, _BLEX_DIR)
from cypher import get_logger
log = get_logger("fifo")

import requests
import redis

from config import (
    REDIS_HOST, REDIS_PASSWORD, REDIS_PORT,
    REDIS_ZONE_QUEUE_KEY, API_URL, API_TIMEOUT,
    CONSUMER_SLEEP_SEC, TENANT_ID,
)

redis_kwargs = {
    "host": REDIS_HOST,
    "port": REDIS_PORT,
    "decode_responses": True,
}
if REDIS_PASSWORD:
    redis_kwargs["password"] = REDIS_PASSWORD

redis_client = redis.Redis(**redis_kwargs)
log.info("fifo consumer started")

while True:
    try:
        item = redis_client.blpop(REDIS_ZONE_QUEUE_KEY, timeout=5)
        if not item:
            time.sleep(CONSUMER_SLEEP_SEC)
            continue
        _, raw_event = item
        event = json.loads(raw_event)
        tenant_id = event.get("tenant_id") or TENANT_ID or ""
        headers = {"X-Tenant-ID": tenant_id} if tenant_id else {}
        try:
            resp = requests.post(API_URL, json=event, headers=headers, timeout=API_TIMEOUT)
            if resp.status_code == 200:
                log.info("event posted", extra={
                    "asset_mac": event.get("asset_mac"),
                    "from_zone": event.get("from_zone_id"),
                    "to_zone": event.get("to_zone_id"),
                    "state": event.get("state"),
                })
            else:
                log.warning("api error, re-queuing", extra={
                    "status": resp.status_code,
                    "asset_mac": event.get("asset_mac"),
                })
                redis_client.rpush(REDIS_ZONE_QUEUE_KEY, json.dumps(event))
                time.sleep(2)
        except Exception:
            log.error("api down, re-queuing", extra={"asset_mac": event.get("asset_mac")}, exc_info=True)
            redis_client.rpush(REDIS_ZONE_QUEUE_KEY, json.dumps(event))
            time.sleep(3)
    except KeyboardInterrupt:
        log.info("fifo consumer stopped")
        break
    except Exception:
        log.error("consumer error", exc_info=True)
        time.sleep(5)
