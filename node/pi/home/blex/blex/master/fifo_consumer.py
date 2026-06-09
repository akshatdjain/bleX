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

# Config from environment — single source of truth: /etc/blex/blex.env
REDIS_HOST     = os.getenv("REDIS_HOST", "127.0.0.1")
REDIS_PORT     = int(os.getenv("REDIS_PORT", "6379"))
REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", "")
TENANT_ID      = os.getenv("TENANT_ID", "")
REDIS_ZONE_QUEUE_KEY = f"zone:movement:queue:{TENANT_ID}" if TENANT_ID else "zone:movement:queue"
API_URL          = os.getenv("API_URL", "https://sigmatic-asc.tech/asset/api/asset/movement")
API_TIMEOUT      = int(os.getenv("API_TIMEOUT", "5"))
CONSUMER_SLEEP_SEC = int(os.getenv("CONSUMER_SLEEP_SEC", "1"))

redis_kwargs = {
    "host": REDIS_HOST,
    "port": REDIS_PORT,
    "decode_responses": True,
}
if REDIS_PASSWORD:
    redis_kwargs["password"] = REDIS_PASSWORD

redis_client = redis.Redis(**redis_kwargs)
log.info("fifo consumer started")


def _api_headers(tenant_id: str = "") -> dict:
    h = {}
    if tenant_id:
        h["X-Tenant-ID"] = tenant_id
    token = os.getenv("BLEX_API_TOKEN", "")
    if token:
        h["Authorization"] = f"Bearer {token}"
    return h


while True:
    try:
        item = redis_client.blpop(REDIS_ZONE_QUEUE_KEY, timeout=5)
        if not item:
            time.sleep(CONSUMER_SLEEP_SEC)
            continue
        _, raw_event = item
        event = json.loads(raw_event)
        tenant_id = event.get("tenant_id") or TENANT_ID or ""
        headers = _api_headers(tenant_id)
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
