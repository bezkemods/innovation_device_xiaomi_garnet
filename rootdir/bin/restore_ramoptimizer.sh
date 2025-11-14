#!/system/bin/sh
# RAM Optimizer boot restore script
# This script integrates with setup_zram.sh for cleaner management

LOG_TAG="RamOptimizer"
SETTINGS_FILE="/data/data/org.lineageos.settings/shared_prefs/org.lineageos.settings_preferences.xml"
ZRAM_SETUP="/system/bin/setup_zram.sh"

log_info() {
    log -t "$LOG_TAG" -p i "$1"
    echo "[INFO] $1"
}

log_error() {
    log -t "$LOG_TAG" -p e "$1"
    echo "[ERROR] $1" >&2
}

# Wait for system to be ready
wait_for_system() {
    local timeout=30
    local count=0
    
    while [ $count -lt $timeout ]; do
        if [ -f "$SETTINGS_FILE" ]; then
            return 0
        fi
        sleep 1
        count=$((count + 1))
    done
    
    return 1
}

# Function to get preference value from XML
get_pref_value() {
    local key="$1"
    local default="$2"
    
    if [ -f "$SETTINGS_FILE" ]; then
        # Try to extract value, handle both boolean and integer types
        value=$(grep "name=\"$key\"" "$SETTINGS_FILE" | sed -n 's/.*value="\([^"]*\)".*/\1/p')
        if [ -z "$value" ]; then
            # Try boolean format
            value=$(grep "name=\"$key\"" "$SETTINGS_FILE" | sed -n 's/.*<boolean[^>]*value="\([^"]*\)".*/\1/p')
        fi
        
        if [ -n "$value" ]; then
            echo "$value"
        else
            echo "$default"
        fi
    else
        echo "$default"
    fi
}

# Main restore logic
main() {
    log_info "Starting RAM Optimizer restore..."
    
    # Check if setup script exists
    if [ ! -f "$ZRAM_SETUP" ]; then
        log_error "setup_zram.sh not found at $ZRAM_SETUP"
        # Fall back to direct implementation
        USE_DIRECT=1
    else
        USE_DIRECT=0
    fi
    
    # Wait for settings file
    if ! wait_for_system; then
        log_info "Settings file not found after timeout, using defaults"
        ZRAM_ENABLED="false"
        ZRAM_SIZE="1024"
        ZRAM_SWAPPINESS="60"
    else
        # Get saved settings
        ZRAM_ENABLED=$(get_pref_value "zram_enable" "false")
        ZRAM_SIZE=$(get_pref_value "zram_size" "1024")
        ZRAM_SWAPPINESS=$(get_pref_value "zram_swappiness" "60")
    fi
    
    log_info "Configuration:"
    log_info "  ZRAM Enabled: $ZRAM_ENABLED"
    log_info "  ZRAM Size: $ZRAM_SIZE MB"
    log_info "  Swappiness: $ZRAM_SWAPPINESS"
    
    # Restore ZRAM using setup script or direct method
    if [ "$ZRAM_ENABLED" = "true" ]; then
        log_info "Restoring ZRAM configuration..."
        
        if [ $USE_DIRECT -eq 0 ]; then
            # Use setup_zram.sh
            "$ZRAM_SETUP" enable "$ZRAM_SIZE" "$ZRAM_SWAPPINESS"
            if [ $? -eq 0 ]; then
                log_info "ZRAM restored successfully via setup script"
            else
                log_error "Failed to restore ZRAM via setup script"
            fi
        else
            # Direct implementation fallback
            log_info "Using direct ZRAM setup..."
            
            # Check device exists
            if [ ! -b /dev/block/zram0 ]; then
                log_error "ZRAM device not found"
                exit 1
            fi
            
            # Disable existing
            swapoff /dev/block/zram0 2>/dev/null
            echo 1 > /sys/block/zram0/reset 2>/dev/null
            sleep 0.5
            
            # Set algorithm
            if grep -q "lz4" /sys/block/zram0/comp_algorithm 2>/dev/null; then
                echo "lz4" > /sys/block/zram0/comp_algorithm 2>/dev/null
            fi
            
            # Set size
            ZRAM_SIZE_BYTES=$((ZRAM_SIZE * 1024 * 1024))
            echo "$ZRAM_SIZE_BYTES" > /sys/block/zram0/disksize
            
            # Enable
            mkswap /dev/block/zram0 2>/dev/null
            swapon /dev/block/zram0 -p 32767 2>/dev/null
            
            if [ $? -eq 0 ]; then
                log_info "ZRAM enabled directly"
                setprop vendor.zram.enabled 1
            fi
        fi
        
        # Set swappiness
        echo "$ZRAM_SWAPPINESS" > /proc/sys/vm/swappiness 2>/dev/null
        
    else
        log_info "ZRAM disabled in settings, ensuring it's off"
        
        if [ $USE_DIRECT -eq 0 ]; then
            "$ZRAM_SETUP" disable
        else
            swapoff /dev/block/zram0 2>/dev/null
            echo 0 > /sys/block/zram0/disksize 2>/dev/null
        fi
        
        setprop vendor.zram.enabled 0
    fi
    
    # Set optimal VM parameters
    echo 100 > /proc/sys/vm/vfs_cache_pressure 2>/dev/null
    
    # Set properties for status tracking
    setprop vendor.ramoptimizer.boot_restored 1
    
    log_info "RAM Optimizer restore completed"
}

# Execute main function
main
exit $?