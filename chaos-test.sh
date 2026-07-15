#!/bin/bash
TARGETS=("node-2" "node-3")
DURATION=60
END=$((SECONDS + DURATION))

echo "=== Chaos test starting ==="
echo "Duration: ${DURATION}s"
echo "Targets: ${TARGETS[*]} (node-1 kept alive as entry point)"

KILL_COUNT=0
while [ $SECONDS -lt $END ]; do
    # Alternate between node-2 and node-3, with randomness
    INDEX=$(( (KILL_COUNT + RANDOM) % 2 ))
    NODE=${TARGETS[$INDEX]}
    KILL_COUNT=$((KILL_COUNT + 1))

    echo "[$(date +%H:%M:%S)] Killing $NODE"
    docker compose stop "$NODE"

    # Wait for gossip to detect failure (5-8s)
    sleep $(( RANDOM % 4 + 5 ))

    echo "[$(date +%H:%M:%S)] Restarting $NODE"
    docker compose start "$NODE"

    # Wait before next kill (5-8s)
    sleep $(( RANDOM % 4 + 5 ))
done

echo "=== Chaos test complete ($KILL_COUNT kills) ==="
