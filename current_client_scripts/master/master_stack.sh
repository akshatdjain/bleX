#!/bin/bash
# =================================================
# MASTER STACK BOOT SCRIPT
# - waits for network
# - registers master
# - runs master + fifo in parallel
# =================================================

BASE_DIR="/home/master/master"
LOG_DIR="$BASE_DIR/logs"

mkdir -p "$LOG_DIR"

echo "[BOOT] Master stack starting at $(date -u)" | tee -a "$LOG_DIR/boot.log"

cd "$BASE_DIR" || exit 1

# -------------------------------------------------
# 1. WAIT FOR NETWORK
# -------------------------------------------------
echo "[BOOT] Waiting for network..." | tee -a "$LOG_DIR/boot.log"

for i in {1..30}; do
    if ip route | grep -q default; then
        echo "[BOOT] Network OK" | tee -a "$LOG_DIR/boot.log"
        break
    fi
    sleep 1
done

# -------------------------------------------------
# 2. REGISTER MASTER (NON-BLOCKING, NON-FATAL)
# -------------------------------------------------
echo "[BOOT] Registering master..." | tee -a "$LOG_DIR/boot.log"

python3 -u master_register.py \
    >> "$LOG_DIR/register.out" \
    2>> "$LOG_DIR/register.err" || true

# -------------------------------------------------
# 3. START MASTER (BACKGROUND)
# -------------------------------------------------
echo "[BOOT] Starting master.py" | tee -a "$LOG_DIR/boot.log"

python3 -u master.py \
    >> "$LOG_DIR/master.out" \
    2>> "$LOG_DIR/master.err" &

MASTER_PID=$!
echo $MASTER_PID > "$LOG_DIR/master.pid"

# -------------------------------------------------
# 4. START FIFO CONSUMER (BACKGROUND)
# -------------------------------------------------
echo "[BOOT] Starting fifo_consumer.py" | tee -a "$LOG_DIR/boot.log"

python3 -u fifo_consumer.py \
    >> "$LOG_DIR/fifo.out" \
    2>> "$LOG_DIR/fifo.err" &

FIFO_PID=$!
echo $FIFO_PID > "$LOG_DIR/fifo.pid"

# -------------------------------------------------
# 5. STATUS
# -------------------------------------------------
echo "[BOOT] Master PID: $MASTER_PID" | tee -a "$LOG_DIR/boot.log"
echo "[BOOT] FIFO   PID: $FIFO_PID"   | tee -a "$LOG_DIR/boot.log"
echo "[BOOT] Master stack running"     | tee -a "$LOG_DIR/boot.log"

# -------------------------------------------------
# 6. KEEP SCRIPT ALIVE
# -------------------------------------------------
wait
