#!/system/bin/sh
# storage_fix.sh — Android 16 QPR2 Android/data + Android/obb folder fix
# Installed into the device tree at: system/bin/storage_fix.sh
# Based on Universal-Storage-Fix by @Rubyneee (Garnet Geeks), adapted for native builds

BASE="/data/media/0/Android"
DATA="$BASE/data"
OBB="$BASE/obb"

LOG="/data/media/0/storage_fix.log"
PKGLIST="/data/system/packages.list"

log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $*" >> "$LOG" 2>/dev/null; }

# Wait until FUSE / internal storage is actually mounted
_wait=0
while [ ! -d "/data/media/0" ]; do
    sleep 1
    _wait=$((_wait + 1))
    [ "$_wait" -ge 60 ] && { log "TIMEOUT: /data/media/0 not available"; exit 1; }
done
log "FUSE ready (waited ${_wait}s)"

# Wait until PackageManager has written packages.list
_pw=0
while [ ! -s "$PKGLIST" ]; do
    sleep 1
    _pw=$((_pw + 1))
    [ "$_pw" -ge 30 ] && { log "TIMEOUT: $PKGLIST not available"; exit 1; }
done
log "packages.list ready (waited ${_pw}s)"

# Ensure base directories exist with correct ownership
mkdir -p "$DATA" "$OBB" 2>/dev/null
chown media_rw:media_rw "$DATA" "$OBB" 2>/dev/null
chmod 771 "$DATA" "$OBB" 2>/dev/null
chcon u:object_r:media_rw_data_file:s0 "$DATA" "$OBB" 2>/dev/null

# Apply correct permissions and SELinux context to a directory
set_dir_meta() {
    chmod 777 "$1" 2>/dev/null
    chown media_rw:media_rw "$1" 2>/dev/null
    chcon u:object_r:media_rw_data_file:s0 "$1" 2>/dev/null
}

# Create / repair Android/data and Android/obb for every installed package.
# packages.list format: <package> <uid> <debugFlag> <dataPath> <seinfo> ...
# We only need the first field (package name).
TOTAL=0
CREATED=0

while IFS=' ' read -r pkg _rest; do
    [ -z "$pkg" ] && continue
    TOTAL=$((TOTAL + 1))
    _d="$DATA/$pkg"
    _o="$OBB/$pkg"
    _made=0
    [ ! -d "$_d" ] && mkdir -p "$_d" 2>/dev/null && _made=1
    [ ! -d "$_o" ] && mkdir -p "$_o" 2>/dev/null && _made=1
    [ -d "$_d" ] && set_dir_meta "$_d"
    [ -d "$_o" ] && set_dir_meta "$_o"
    [ "$_made" -eq 1 ] && { log "CREATED: $pkg"; CREATED=$((CREATED + 1)); }
done < "$PKGLIST"

log "DONE: scanned ${TOTAL} packages, created ${CREATED} directories"
exit 0
