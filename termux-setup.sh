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

echo ""
echo -e "${BOLD}═══════════════════════════════════════════════${RESET}"
echo -e "${BOLD}   Stark Control — Automatic Permission Setup  ${RESET}"
echo -e "${BOLD}═══════════════════════════════════════════════${RESET}"
echo ""

# ── Step 1: Pair ─────────────────────────────────────────────────────────────
echo -e "${BOLD}[1/5] ADB Wireless Pairing${RESET}"
echo ""
info "Open: Settings → Developer Options → Wireless Debugging"
info "Tap: 'Pair device with pairing code' — a dialog appears"
echo ""
read -p "      Pairing port (from the dialog): " PAIR_PORT
read -p "      Pairing code (6 digits):         " PAIR_CODE
echo ""
info "Pairing..."
adb pair "localhost:$PAIR_PORT" "$PAIR_CODE"
ok "Paired"

# ── Step 2: Connect ──────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}[2/5] ADB Connect${RESET}"
echo ""
info "Back on the Wireless Debugging main screen, note the port next to the IP address"
read -p "      Wireless Debugging port:          " DEBUG_PORT
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
grant_perm "POST_NOTIFICATIONS"   "adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS"
grant_perm "READ_PHONE_STATE"     "adb shell pm grant $PKG android.permission.READ_PHONE_STATE"
grant_perm "CAMERA"               "adb shell pm grant $PKG android.permission.CAMERA"
grant_perm "BLUETOOTH_CONNECT"   "adb shell pm grant $PKG android.permission.BLUETOOTH_CONNECT"
grant_perm "BLUETOOTH_SCAN"      "adb shell pm grant $PKG android.permission.BLUETOOTH_SCAN"

# ── Privileged permissions (require WRITE_SECURE_SETTINGS first) ─────────────
grant_perm "WRITE_SECURE_SETTINGS" "adb shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS"

# ── Appops permissions ───────────────────────────────────────────────────────
grant_perm "Draw over apps (SYSTEM_ALERT_WINDOW)"  "adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow"
grant_perm "Modify system settings (WRITE_SETTINGS)" "adb shell appops set $PKG WRITE_SETTINGS allow"
grant_perm "Run in background"                     "adb shell cmd appops set $PKG RUN_IN_BACKGROUND allow"
grant_perm "Run any in background"                 "adb shell cmd appops set $PKG RUN_ANY_IN_BACKGROUND allow"
grant_perm "Start in background (BOOT)"            "adb shell cmd appops set $PKG START_FOREGROUND allow"

# ── Notification Listener ────────────────────────────────────────────────────
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

# ── Do Not Disturb policy ────────────────────────────────────────────────────
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

# ── Immersive status bar (hide real status bar) ──────────────────────────────
grant_perm "Immersive status bar policy" \
    "adb shell settings put global policy_control 'immersive.status=*'"

# ── Battery & sleep — never kill, never sleep ────────────────────────────────
grant_perm "Battery optimization exempt"     "adb shell cmd deviceidle whitelist +$PKG"
grant_perm "Disable phantom process killer"  "adb shell settings put global settings_enable_monitor_phantom_procs false"
grant_perm "Background restrictions removed" "adb shell cmd appops set $PKG OP_RUN_ANY_IN_BACKGROUND allow"
grant_perm "Stay on while plugged in"        "adb shell settings put global stay_on_while_plugged_in 3"
grant_perm "Max screen timeout (no auto-sleep)" \
    "adb shell settings put system screen_off_timeout 2147483647"
grant_perm "Disable adaptive battery for app" \
    "adb shell dumpsys deviceidle whitelist +$PKG"

# Disable battery saver auto-restrictions for the app
adb shell cmd power set-mode 0 2>/dev/null || true  # clear any forced battery saver
ok "Battery saver cleared"

# ── Step 5: Start Stark Control ──────────────────────────────────────────────
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
echo "  • Draw over apps          → Control Center visible on top of everything"
echo "  • Notification Listener   → Dynamic Island + Notification Center live"
echo "  • DnD Policy Access       → Do Not Disturb toggle works"
echo "  • WRITE_SETTINGS          → Brightness slider + Rotation lock work"
echo "  • WRITE_SECURE_SETTINGS   → Airplane mode + Mobile data toggle work"
echo "  • Battery exempt          → Android will never kill the service"
echo "  • Phantom process killer  → Disabled (service survives in background)"
echo "  • Screen timeout          → Max (won't sleep while you use it)"
echo "  • Stay on while charging  → Screen stays on when plugged in"
echo "  • All Bluetooth/Camera    → Toggles and QS tiles work"
echo ""
echo -e "  Auto-starts on every boot. No further setup needed."
echo -e "${BOLD}═══════════════════════════════════════════════${RESET}"
echo ""
