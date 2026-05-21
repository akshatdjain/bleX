import json
import os
import threading
import time
from datetime import datetime
from queue import Queue, Empty


class TelemetryLogger:
    """
    JSON Lines telemetry logger with DAILY file rotation.
    """

    def __init__(
        self,
        base_dir: str = "logs",
        prefix: str = "telemetry_master",
        flush_interval_sec: float = 1.0,
        max_queue_size: int = 10000,
        use_utc: bool = True
    ):
        self.base_dir = base_dir
        self.prefix = prefix
        self.flush_interval_sec = flush_interval_sec
        self.queue = Queue(maxsize=max_queue_size)
        self.use_utc = use_utc

        self._stop = False
        self._seq = 0
        self._current_date = None
        self._file = None

        os.makedirs(self.base_dir, exist_ok=True)

        self._thread = threading.Thread(
            target=self._writer_loop,
            daemon=True
        )
        self._thread.start()

    # -------------------------------------------------
    # PUBLIC API
    # -------------------------------------------------
    def log(self, row: dict):
        try:
            self._seq += 1
            row["sequence_id"] = self._seq
            self.queue.put_nowait(row)
        except Exception:
            # drop silently if overloaded
            pass

    def close(self):
        self._stop = True
        self._thread.join(timeout=2)
        self._close_file()

    # -------------------------------------------------
    # INTERNAL
    # -------------------------------------------------
    def _now(self):
        return datetime.utcnow() if self.use_utc else datetime.now()

    def _get_date_str(self):
        return self._now().strftime("%Y-%m-%d")

    def _open_file_if_needed(self):
        date_str = self._get_date_str()

        if date_str != self._current_date:
            self._close_file()
            self._current_date = date_str
            self._seq = 0  # reset sequence daily

            path = os.path.join(
                self.base_dir,
                f"{self.prefix}_{date_str}.jsonl"
            )
            self._file = open(path, "a", buffering=1)

    def _close_file(self):
        if self._file:
            try:
                self._file.close()
            except Exception:
                pass
            self._file = None

    def _writer_loop(self):
        buffer = []
        last_flush = time.time()

        while not self._stop or not self.queue.empty():
            try:
                item = self.queue.get(timeout=0.1)
                buffer.append(item)
            except Empty:
                pass

            now = time.time()
            if buffer and (now - last_flush >= self.flush_interval_sec):
                self._flush(buffer)
                buffer.clear()
                last_flush = now

        if buffer:
            self._flush(buffer)

    def _flush(self, rows):
        try:
            self._open_file_if_needed()
            for row in rows:
                self._file.write(json.dumps(row) + "\n")
        except Exception:
            # must never crash master
            pass
