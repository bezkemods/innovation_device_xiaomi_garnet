#!/system/bin/sh
# setup_zram.sh
# Helper script for zRAM setup
# Place this in system/bin/setup_zram.sh

ZRAM_DEVICE="/dev/block/zram0"
ZRAM_SIZE_PATH="/sys/block/zram0/disksize"
ZRAM_COMP_PATH="/sys/block/zram0/comp_algorithm"
SWAPPINESS_PATH="/proc/sys/vm/swappiness"

# Check if zRAM device exists
if [ ! -b "$ZRAM_DEVICE" ]; then
    echo "zRAM device not found"
    exit 1
fi

# Function to setup zRAM
setup_zram() {
    local size_mb=$1
    local swappiness=$2
    
    # Disable existing swap
    swapoff "$ZRAM_DEVICE" 2>/dev/null
    
    # Reset zRAM
    echo 1 > "$ZRAM_SIZE_PATH"
    sleep 0.5
    
    # Set compression algorithm
    echo "lz4" > "$ZRAM_COMP_PATH" 2>/dev/null || echo "lzo" > "$ZRAM_COMP_PATH" 2>/dev/null
    
    # Set size
    local size_bytes=$((size_mb * 1024 * 1024))
    echo "$size_bytes" > "$ZRAM_SIZE_PATH"
    sleep 0.5
    
    # Format and enable
    mkswap "$ZRAM_DEVICE" >/dev/null 2>&1
    swapon "$ZRAM_DEVICE" >/dev/null 2>&1
    
    # Set swappiness
    echo "$swappiness" > "$SWAPPINESS_PATH"
    
    echo "zRAM setup complete: ${size_mb}MB, swappiness: $swappiness"
}

# Function to disable zRAM
disable_zram() {
    swapoff "$ZRAM_DEVICE" 2>/dev/null
    echo 1 > "$ZRAM_SIZE_PATH"
    echo "zRAM disabled"
}

# Main
case "$1" in
    enable)
        SIZE_MB=${2:-1024}
        SWAPPINESS=${3:-60}
        setup_zram "$SIZE_MB" "$SWAPPINESS"
        ;;
    disable)
        disable_zram
        ;;
    status)
        if [ -f "$ZRAM_SIZE_PATH" ]; then
            SIZE=$(cat "$ZRAM_SIZE_PATH")
            if [ "$SIZE" -gt 1 ]; then
                SIZE_MB=$((SIZE / 1024 / 1024))
                SWAP=$(cat "$SWAPPINESS_PATH")
                echo "zRAM enabled: ${SIZE_MB}MB, swappiness: $SWAP"
            else
                echo "zRAM disabled"
            fi
        else
            echo "zRAM not available"
        fi
        ;;
    *)
        echo "Usage: $0 {enable [size_mb] [swappiness]|disable|status}"
        exit 1
        ;;
esac

exit 0
