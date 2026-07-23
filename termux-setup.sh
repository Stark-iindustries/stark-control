#!/data/data/com.termux/files/usr/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# Stark Control — one-shot ADB permission setup
# Run this ONCE from Termux on your Infinix Smart 6 (Android 11)
#
# Prerequisites:
#   1. Enable Developer Options  →  Settings > About Phone > tap Build Number 7×
#   2. Enable Wireless Debugging →  Settings > Developer Options > Wireless Debugging ON
#   3. Install ADB in Termux    →  pkg install android-tools
# ─────────────────────────────────────────────────────────────────────────────

set -e

PKG="com.starkboard.control"

echo ""
echo "═══════════════════════════════════════════════"
echo "  Stark Control — Permission Setup"
echo "═══════════════════════════════════════════════"
echo ""

# ── Step 1: pair + connect ───────────────────────────────────────────────────
echo "► Open Settings > Developer Options > Wireless Debugging"
echo "  Tap 'Pair device with pairing code' and enter the details below:"
echo ""
read -p "  Pairing port (from the dialog): " PAIR_PORT
read -p "  Pairing code (6 digits):        " PAIR_CODE

echo ""
echo "  Pairing with localhost:$PAIR_PORT ..."
adb pair "localhost:$PAIR_PORT" "$PAIR_CODE"

echo ""
read -p "► Now enter the main Wireless Debugging port shown on screen: " DEBUG_PORT
echo "  Connecting to localhost:$DEBUG_PORT ..."
adb connect "localhost:$DEBUG_PORT"

echo ""
echo "  Verifying connection..."
adb devices
echo ""

# ── Step 2: verify app is installed ─────────────────────────────────────────
if ! adb shell pm list packages | grep -q "$PKG"; then
    echo "✗ Stark Control is not installed. Install the APK first, then re-run."
    exit 1
fi
echo "✓ Stark Control found on device"
echo ""

# ── Step 3: grant permissions ────────────────────────────────────────────────
echo "► Granting permissions..."

# WRITE_SECURE_SETTINGS — fixes Airplane Mode, immersive status bar hide,
# and the Settings.Global mobile_data toggle on Android 11
adb shell pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS
echo "  ✓ WRITE_SECURE_SETTINGS"

# WRITE_SETTINGS via appops — fixes Brightness slider + Rotation lock
adb shell appops set "$PKG" WRITE_SETTINGS allow
echo "  ✓ WRITE_SETTINGS (appops)"

# Activate Notification Listener — fixes Notification Center + Dynamic Island
LISTENER_COMPONENT="$PKG/.StarkNotificationListenerService"
CURRENT=$(adb shell settings get secure enabled_notification_listeners 2>/dev/null || echo "")
if echo "$CURRENT" | grep -q "$LISTENER_COMPONENT"; then
    echo "  ✓ Notification Listener (already enabled)"
else
    if [ -z "$CURRENT" ] || [ "$CURRENT" = "null" ]; then
        NEW_VAL="$LISTENER_COMPONENT"
    else
        NEW_VAL="$CURRENT:$LISTENER_COMPONENT"
    fi
    adb shell settings put secure enabled_notification_listeners "$NEW_VAL"
    echo "  ✓ Notification Listener"
fi

# DnD / Notification Policy access — fixes Do Not Disturb toggle
CURRENT_DND=$(adb shell settings get secure enabled_notification_policy_access_packages 2>/dev/null || echo "")
if echo "$CURRENT_DND" | grep -q "$PKG"; then
    echo "  ✓ DnD Policy Access (already enabled)"
else
    if [ -z "$CURRENT_DND" ] || [ "$CURRENT_DND" = "null" ]; then
        adb shell settings put secure enabled_notification_policy_access_packages "$PKG"
    else
        adb shell settings put secure enabled_notification_policy_access_packages "$CURRENT_DND:$PKG"
    fi
    echo "  ✓ DnD Policy Access"
fi

# Status-bar hide via immersive policy (needs WRITE_SECURE_SETTINGS, set above)
# Stark Control sets this at runtime; this just pre-arms it
adb shell settings put global policy_control "immersive.status=*"
echo "  ✓ Immersive status bar policy armed"

# ── Step 4: rebind notification listener ────────────────────────────────────
echo ""
echo "► Rebinding notification listener service..."
adb shell cmd notification allow_listener "$LISTENER_COMPONENT" 2>/dev/null || true
echo "  ✓ Done"

# ── Step 5: confirm ──────────────────────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════════"
echo "  All permissions granted successfully!"
echo ""
echo "  What each permission enables:"
echo "  • WRITE_SECURE_SETTINGS  → Airplane mode, status bar hiding,"
echo "                             mobile data toggle (on most Android 11)"
echo "  • WRITE_SETTINGS (appop) → Brightness slider, Rotation lock"
echo "  • Notification Listener  → Notification Center, Dynamic Island"
echo "  • DnD Policy Access      → Focus / Do Not Disturb toggle"
echo ""
echo "  Now open Stark Control, tap 'Start Control Center', and enjoy."
echo "═══════════════════════════════════════════════"
echo ""
