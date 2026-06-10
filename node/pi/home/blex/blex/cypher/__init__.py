"""
cypher.py — Shared structured logger for all BleX Pi components.
"""
import json
import logging
import os
import sys
from datetime import datetime, timezone
from logging.handlers import TimedRotatingFileHandler

_BASE_DIR = os.path.dirname(os.path.abspath(__file__))
_LOG_DIR  = os.path.join(os.path.dirname(_BASE_DIR), "logs")

_RECORD_BUILTINS = frozenset({
    "args", "created", "exc_info", "exc_text", "filename", "funcName",
    "levelname", "levelno", "lineno", "message", "module", "msecs",
    "msg", "name", "pathname", "process", "processName", "relativeCreated",
    "stack_info", "thread", "threadName", "taskName",
})


class _JsonFormatter(logging.Formatter):
    def __init__(self, comp: str, pi_mac: str, tenant_id: str):
        super().__init__()
        self._fixed = {"comp": comp, "pi_mac": pi_mac, "tenant_id": tenant_id}

    def format(self, record: logging.LogRecord) -> str:
        entry = {
            "ts":  datetime.now(timezone.utc).isoformat(),
            "lvl": record.levelname,
            **self._fixed,
            "msg": record.getMessage(),
        }
        for k, v in record.__dict__.items():
            if k not in _RECORD_BUILTINS and not k.startswith("_"):
                entry[k] = v
        if record.exc_info:
            entry["exc"] = self.formatException(record.exc_info)
        return json.dumps(entry, default=str)


_identity_cache = None

def _get_identity():
    global _identity_cache
    if _identity_cache is not None:
        return _identity_cache
    pi_mac = ""
    try:
        with open("/sys/class/net/wlan0/address") as f:
            pi_mac = f.read().strip().upper()
    except Exception:
        pass
    tenant_id = os.getenv("TENANT_ID", "")
    _identity_cache = (pi_mac, tenant_id)
    return _identity_cache


_loggers: dict = {}


def get_logger(comp: str) -> logging.Logger:
    if comp in _loggers:
        return _loggers[comp]

    pi_mac, tenant_id = _get_identity()
    logger = logging.getLogger(f"blex.{comp}")
    logger.setLevel(logging.DEBUG)
    logger.propagate = False

    fmt = _JsonFormatter(comp, pi_mac, tenant_id)

    sh = logging.StreamHandler(sys.stdout)
    sh.setLevel(logging.DEBUG)
    sh.setFormatter(fmt)
    logger.addHandler(sh)

    os.makedirs(_LOG_DIR, exist_ok=True)
    fh = TimedRotatingFileHandler(
        os.path.join(_LOG_DIR, "blex.log"),
        when="midnight", backupCount=7, encoding="utf-8",
    )
    fh.setLevel(logging.INFO)
    fh.setFormatter(fmt)
    logger.addHandler(fh)

    _loggers[comp] = logger
    return logger
