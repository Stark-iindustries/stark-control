#!/data/data/com.termux/files/usr/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# Stark Control — fully automatic one-shot setup
# Run ONCE from Termux after installing the APK.
#
# Prerequisites (do these before running):
#   1. Settings → About Phone → tap Build Number 7×  (unlocks Developer Options)
#   2. Settings → Developer Options → Wireless Debugging → ON
#   3. Termux: pkg install android-tools
# ─────────────────────────────────────────────────────────────────────────────

set -e

PKG="com.starkboard.control"
LISTENER="$PKG/.StarkNotificationListenerService"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

ok()   { echo -e "  ${GREEN}✓${RESET} $1"; }
info() { echo -e "  ${CYAN}→${RESET} $1"; }
warn() { echo -e "  ${YELLOW}⚠${RESET} $1"; }
fail() { echo -e "  ${RED}✗${RESET} $1"; }

# Read input from /dev/tty so it works whether piped via curl or run directly
ask() {
    local prompt="$1"
    local var="$2"
    printf "      %s" "$prompt" > /dev/tty
    read -r "$var" < /dev/tty
}

echo ""
echo -e "${BOLD}═══════════════════════════════════════════════${RESET}"
echo -e "${BOLD}   Stark Control — Automatic Permission Setup  ${RESET}"
echo -e "${BOLD}═══════════════════════════════════════════════${RESET}"
echo ""

# ── Step 1: Pair ─────────────────────────────────────────────────────────────
echo -e "${BOLD}[1/5] ADB Wireless Pairing${RESET}"
echo ""
info "Open: Settings → Developer Options → Wireless Debugging"
info "Tap 'Pair device with pairing code' — a dialog appears"
echo ""
ask "Pairing port (5-digit number from the dialog): " PAIR_PORT
ask "Pairing code (6-digit number from the dialog): " PAIR_CODE
echo ""
info "Pairing..."
adb pair "localhost:$PAIR_PORT" "$PAIR_CODE"
ok "Paired"

# ── Step 2: Connect ──────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}[2/5] ADB Connect${RESET}"
echo ""
info "Now look at the main Wireless Debugging screen"
info "You'll see your IP address then a colon then a port, e.g. 192.168.x.x:46507"
echo ""
ask "That port number (after the colon on the main screen): " DEBUG_PORT
echo ""
info "Connecting..."
adb connect "localhost:$DEBUG_PORT"
ok "Connected"

echo ""
info "Verifying device..."
adb devices
echo ""

# ── Step 3: Verify app is installed ─────────────────────────────────────────
echo -e "${BOLD}[3/5] Checking Installation${RESET}"
echo ""
if ! adb shell pm list packages | grep -q "$PKG"; then
    fail "Stark Control is not installed. Install the APK first, then re-run."
    exit 1
fi
ok "Stark Control is installed on device"

# ── Step 4: Grant every permission ──────────────────────────────────────────
echo ""
echo -e "${BOLD}[4/5] Granting All Permissions${RESET}"
echo ""

grant_perm() {
    local label="$1"; local cmd="$2"
    if eval "$cmd" 2>/dev/null; then
        ok "$label"
    else
        warn "$label (may already be set or unsupported on this Android version)"
    fi
}

# ── Runtime permissions ──────────────────────────────────────────────────────
grant_perm "POST_NOTIFICATIONS"    "adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS"
grant_perm "READ_PHONE_STATE"      "adb shell pm grant $PKG android.permission.READ_PHONE_STATE"
grant_perm "CAMERA"                "adb shell pm grant $PKG android.permission.CAMERA"
grant_perm "BLUETOOTH_CONNECT"     "adb shell pm grant $PKG android.permission.BLUETOOTH_CONNECT"
grant_perm "BLUETOOTH_SCAN"        "adb shell pm grant $PKG android.permission.BLUETOOTH_SCAN"

# ── Privileged permissions ────────────────────────────────────────────────────
grant_perm "WRITE_SECURE_SETTINGS" "adb shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS"

# ── Appops permissions ────────────────────────────────────────────────────────
grant_perm "Draw over apps (overlay)"        "adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow"
grant_perm "Modify system settings"          "adb shell appops set $PKG WRITE_SETTINGS allow"
grant_perm "Run in background"               "adb shell cmd appops set $PKG RUN_IN_BACKGROUND allow"
grant_perm "Run any in background"           "adb shell cmd appops set $PKG RUN_ANY_IN_BACKGROUND allow"
grant_perm "Start foreground service"        "adb shell cmd appops set $PKG START_FOREGROUND allow"

# ── Notification Listener ─────────────────────────────────────────────────────
CURRENT_NL=$(adb shell settings get secure enabled_notification_listeners 2>/dev/null || echo "")
if echo "$CURRENT_NL" | grep -q "$LISTENER"; then
    ok "Notification Listener (already enabled)"
else
    if [ -z "$CURRENT_NL" ] || [ "$CURRENT_NL" = "null" ]; then
        adb shell settings put secure enabled_notification_listeners "$LISTENER"
    else
        adb shell settings put secure enabled_notification_listeners "$CURRENT_NL:$LISTENER"
    fi
    adb shell cmd notification allow_listener "$LISTENER" 2>/dev/null || true
    ok "Notification Listener"
fi

# ── Do Not Disturb policy ─────────────────────────────────────────────────────
CURRENT_DND=$(adb shell settings get secure enabled_notification_policy_access_packages 2>/dev/null || echo "")
if echo "$CURRENT_DND" | grep -q "$PKG"; then
    ok "DnD Policy Access (already enabled)"
else
    if [ -z "$CURRENT_DND" ] || [ "$CURRENT_DND" = "null" ]; then
        adb shell settings put secure enabled_notification_policy_access_packages "$PKG"
    else
        adb shell settings put secure enabled_notification_policy_access_packages "$CURRENT_DND:$PKG"
    fi
    ok "DnD Policy Access"
fi

# ── Immersive mode — hide the real status bar ─────────────────────────────────
grant_perm "Hide real status bar"   "adb shell settings put global policy_control 'immersive.status=*'"

# ── Battery & sleep — never kill, never sleep ─────────────────────────────────
grant_perm "Battery optimization exempt"      "adb shell cmd deviceidle whitelist +$PKG"
grant_perm "Doze whitelist"                   "adb shell dumpsys deviceidle whitelist +$PKG"
grant_perm "Phantom process killer disabled"  "adb shell settings put global settings_enable_monitor_phantom_procs false"
grant_perm "Background restrictions removed"  "adb shell cmd appops set $PKG OP_RUN_ANY_IN_BACKGROUND allow"
grant_perm "Stay on while plugged in"         "adb shell settings put global stay_on_while_plugged_in 3"
grant_perm "Max screen timeout"               "adb shell settings put system screen_off_timeout 2147483647"

# Clear any forced battery saver mode
adb shell cmd power set-mode 0 2>/dev/null || true
ok "Battery saver cleared"

# ── Step 5: Launch Stark Control ─────────────────────────────────────────────
echo ""
echo -e "${BOLD}[5/5] Launching Stark Control${RESET}"
echo ""
info "Starting app..."
adb shell am start -n "$PKG/.MainActivity" 2>/dev/null || true
sleep 1
info "Starting overlay service..."
adb shell am startforegroundservice -n "$PKG/.OverlayService" 2>/dev/null \
    || adb shell am startservice -n "$PKG/.OverlayService" 2>/dev/null || true
ok "Stark Control launched"

# ── Done ─────────────────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}═══════════════════════════════════════════════${RESET}"
echo -e "${GREEN}${BOLD}  All done! Stark Control is fully set up.${RESET}"
echo ""
echo -e "  ${BOLD}What was granted:${RESET}"
echo "  • Draw over apps          → overlay visible on top of everything"
echo "  • Notification Listener   → Dynamic Island + Notification Center"
echo "  • DnD Policy Access       → Do Not Disturb toggle works"
echo "  • WRITE_SETTINGS          → Brightness slider + Rotation lock"
echo "  • WRITE_SECURE_SETTINGS   → Airplane mode + Mobile data + hides real status bar"
echo "  • Battery exempt          → Android will never kill the service"
echo "  • Phantom process killer  → Disabled (survives in background)"
echo "  • Screen timeout          → Max (won't auto-sleep)"
echo "  • Stay on while charging  → Screen stays on when plugged in"
echo "  • All Bluetooth/Camera    → Every toggle works"
echo ""
echo -e "  Auto-starts on every boot. No further setup needed."
echo -e "${BOLD}═══════════════════════════════════════════════${RESET}"
echo ""
