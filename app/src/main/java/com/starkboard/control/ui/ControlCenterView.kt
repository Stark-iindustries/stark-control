package com.starkboard.control.ui

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.*
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.starkboard.control.toggles.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class ControlCenterView(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onClose: () -> Unit
) : FrameLayout(context) {

    private var translateY = -3000f

    init {
        val cv = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            this.setViewTreeLifecycleOwner(lifecycleOwner)
            if (lifecycleOwner is SavedStateRegistryOwner) {
                this.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            }
            if (lifecycleOwner is ViewModelStoreOwner) {
                this.setViewTreeViewModelStoreOwner(lifecycleOwner)
            }
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    ControlCenterContent(context, onClose)
                }
            }
        }
        addView(cv)
        translationY = translateY
    }

    fun animateIn() {
        animate()
            .translationY(0f)
            .setDuration(460)
            .setInterpolator(OvershootInterpolator(0.6f))
            .start()
    }

    fun animateOut(onEnd: () -> Unit) {
        animate()
            .translationY(-height.toFloat().coerceAtLeast(2000f))
            .setDuration(320)
            .setInterpolator(DecelerateInterpolator(2f))
            .withEndAction(onEnd)
            .start()
    }
}

@Composable
private fun ControlCenterContent(context: Context, onClose: () -> Unit) {
    val sbHeight = remember {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (id > 0) (context.resources.getDimensionPixelSize(id) / context.resources.displayMetrics.density).dp
        else 28.dp
    }

    // State for toggles
    var wifiOn by remember { mutableStateOf(WifiToggle.isEnabled(context)) }
    var btOn by remember { mutableStateOf(BluetoothToggle.isEnabled(context)) }
    var airplaneOn by remember { mutableStateOf(AirplaneModeToggle.isEnabled(context)) }
    var dataOn by remember { mutableStateOf(MobileDataToggle.isEnabled(context)) }
    var dndOn by remember { mutableStateOf(DndToggle.isEnabled(context)) }
    var rotationLocked by remember { mutableStateOf(RotationToggle.isLocked(context)) }
    var flashOn by remember { mutableStateOf(false) }

    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var brightness by remember {
        mutableFloatStateOf(
            try { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f }
            catch (_: Exception) { 0.5f }
        )
    }
    var volume by remember {
        mutableFloatStateOf(
            audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
            audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        )
    }

    // Drag-to-dismiss
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { onClose() }
            }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .offset { IntOffset(0, offsetY.roundToInt()) }
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF000000), Color(0xEE111111))
                    ),
                    RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                )
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (offsetY < -80) onClose()
                            else offsetY = 0f
                        }
                    ) { _, dy ->
                        if (dy < 0) offsetY = (offsetY + dy).coerceIn(-300f, 0f)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { /* consume — don't close */ }
                }
        ) {
            Column(
                Modifier.padding(
                    start = 16.dp, end = 16.dp,
                    top = sbHeight + 8.dp, bottom = 20.dp
                )
            ) {
                // ── Row 1: Connectivity + Music ───────────────────────
                Row(Modifier.fillMaxWidth().height(136.dp)) {
                    // Connectivity 2×2 cluster
                    ConnectivityCluster(
                        modifier = Modifier.weight(0.56f).fillMaxHeight(),
                        wifiOn = wifiOn, btOn = btOn,
                        airplaneOn = airplaneOn, dataOn = dataOn,
                        onWifi = { wifiOn = !wifiOn; WifiToggle.toggle(context) },
                        onBt = { btOn = !btOn; BluetoothToggle.toggle(context) },
                        onAirplane = { airplaneOn = !airplaneOn; AirplaneModeToggle.toggle(context) },
                        onData = { dataOn = !dataOn; MobileDataToggle.toggle(context) }
                    )
                    Spacer(Modifier.width(12.dp))
                    // Music player card
                    MusicCard(
                        modifier = Modifier.weight(0.44f).fillMaxHeight(),
                        audio = audio
                    )
                }

                Spacer(Modifier.height(12.dp))

                // ── Row 2: 4 medium tiles ─────────────────────────────
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MediumTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.ScreenRotation,
                        label = if (rotationLocked) "Portrait" else "Rotation",
                        active = rotationLocked,
                        activeColor = Color(0xFFFF9F0A)
                    ) { rotationLocked = !rotationLocked; RotationToggle.toggle(context) }

                    MediumTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.CastConnected,
                        label = "Mirror",
                        active = false
                    ) {}

                    MediumTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.FlashlightOn,
                        label = "Torch",
                        active = flashOn,
                        activeColor = Color.White
                    ) { flashOn = !flashOn; FlashlightToggle.toggle(context) }

                    MediumTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Timer,
                        label = "Timer",
                        active = false
                    ) {}
                }

                Spacer(Modifier.height(12.dp))

                // ── Row 3: Focus pill + Brightness + Volume ───────────
                // Focus / DND
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (dndOn) Color(0xFF2C2C2E) else Color(0xFF1C1C1E))
                        .clickable { dndOn = !dndOn; DndToggle.toggle(context) }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.DarkMode,
                        contentDescription = null,
                        tint = if (dndOn) Color(0xFF5E5CE6) else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Focus",
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Brightness
                IosSlider(
                    value = brightness,
                    icon = Icons.Rounded.BrightnessHigh,
                    onValueChange = { v ->
                        brightness = v
                        try {
                            Settings.System.putInt(
                                context.contentResolver,
                                Settings.System.SCREEN_BRIGHTNESS,
                                (v * 255).toInt()
                            )
                        } catch (_: Exception) {}
                    }
                )

                Spacer(Modifier.height(10.dp))

                // Volume
                IosSlider(
                    value = volume,
                    icon = Icons.Rounded.VolumeUp,
                    onValueChange = { v ->
                        volume = v
                        audio.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            (v * audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).toInt(),
                            0
                        )
                    }
                )

                Spacer(Modifier.height(14.dp))

                // ── Bottom tile grid (phone's actual QS tiles) ────────
                PhoneTiles(context = context, flashOn = flashOn,
                    onFlash = { flashOn = !flashOn; FlashlightToggle.toggle(context) })
            }
        }
    }
}

@Composable
private fun ConnectivityCluster(
    modifier: Modifier,
    wifiOn: Boolean, btOn: Boolean, airplaneOn: Boolean, dataOn: Boolean,
    onWifi: () -> Unit, onBt: () -> Unit, onAirplane: () -> Unit, onData: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF1C1C1E),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ConnTile(Modifier.weight(1f), Icons.Rounded.AirplanemodeActive, "Airplane", airplaneOn, onAirplane)
                ConnTile(Modifier.weight(1f), Icons.Rounded.Wifi, "WiFi", wifiOn, onWifi)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ConnTile(Modifier.weight(1f), Icons.Rounded.Bluetooth, "BT", btOn, onBt)
                ConnTile(Modifier.weight(1f), Icons.Rounded.SignalCellularAlt, "Data", dataOn, onData)
            }
        }
    }
}

@Composable
private fun ConnTile(modifier: Modifier, icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) Color(0xFF0A84FF) else Color(0xFF2C2C2E)
    Box(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun MediumTile(
    modifier: Modifier, icon: ImageVector, label: String,
    active: Boolean, activeColor: Color = Color(0xFF0A84FF), onClick: () -> Unit
) {
    val bg = if (active) Color(0xFF2C2C2E) else Color(0xFF1C1C1E)
    val iconTint = if (active) activeColor else Color.White
    Column(
        modifier
            .height(72.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun IosSlider(value: Float, icon: ImageVector, onValueChange: (Float) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1C1C1E))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).height(28.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color(0xFF3A3A3C)
            )
        )
    }
}

@Composable
private fun MusicCard(modifier: Modifier, audio: AudioManager) {
    // Try to show currently playing info via MediaMetadata
    Surface(modifier, color = Color(0xFF1C1C1E), shape = RoundedCornerShape(20.dp)) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Music", color = Color(0xFF8E8E93), fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Text("Now Playing", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1)
            Spacer(Modifier.weight(1f))
            // Controls
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { audio.dispatchMediaKeyEvent(
                    android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)) },
                    modifier = Modifier.size(36.dp)
                ) { Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(22.dp)) }

                IconButton(onClick = { audio.dispatchMediaKeyEvent(
                    android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) },
                    modifier = Modifier.size(40.dp)
                ) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp)) }

                IconButton(onClick = { audio.dispatchMediaKeyEvent(
                    android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)) },
                    modifier = Modifier.size(36.dp)
                ) { Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
            }
        }
    }
}

@Composable
private fun PhoneTiles(context: Context, flashOn: Boolean, onFlash: () -> Unit) {
    // Read actual QS tiles from the phone and show known ones
    val tiles = remember {
        try {
            Settings.Secure.getString(context.contentResolver, "sysui_qs_tiles")
                ?.split(",")?.map { it.trim() } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    val shown = mutableListOf<Pair<String, ImageVector>>()
    fun hasOrDefault(id: String, icon: ImageVector, label: String): Triple<String, String, ImageVector> =
        Triple(label, id, icon)

    // Map phone tiles to icons
    tiles.forEach { t ->
        when {
            t.contains("flashlight", true) -> shown.add("Torch" to Icons.Rounded.FlashlightOn)
            t.contains("calculator", true) -> shown.add("Calc" to Icons.Rounded.Calculate)
            t.contains("camera", true)     -> shown.add("Camera" to Icons.Rounded.CameraAlt)
            t.contains("record", true)     -> shown.add("Record" to Icons.Rounded.FiberSmartRecord)
            t.contains("hotspot", true)    -> shown.add("Hotspot" to Icons.Rounded.WifiTethering)
            t.contains("location", true)   -> shown.add("Location" to Icons.Rounded.LocationOn)
            t.contains("battery", true)    -> shown.add("Battery" to Icons.Rounded.BatterySaver)
            t.contains("nfc", true)        -> shown.add("NFC" to Icons.Rounded.Nfc)
            t.contains("dark", true)       -> shown.add("Dark" to Icons.Rounded.DarkMode)
            t.contains("alarm", true)      -> shown.add("Alarm" to Icons.Rounded.Alarm)
        }
    }
    // Always show at least these if list is short
    if (shown.size < 4) {
        if (shown.none { it.first == "Torch" })   shown.add("Torch" to Icons.Rounded.FlashlightOn)
        if (shown.none { it.first == "Camera" })  shown.add("Camera" to Icons.Rounded.CameraAlt)
        if (shown.none { it.first == "Hotspot" }) shown.add("Hotspot" to Icons.Rounded.WifiTethering)
        if (shown.none { it.first == "Alarm" })   shown.add("Alarm" to Icons.Rounded.Alarm)
    }
    val displayTiles = shown.take(8)

    val cols = 4
    val rows = (displayTiles.size + cols - 1) / cols
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(cols) { col ->
                    val idx = row * cols + col
                    if (idx < displayTiles.size) {
                        val (label, icon) = displayTiles[idx]
                        val isFlash = label == "Torch"
                        SmallTile(
                            modifier = Modifier.weight(1f),
                            icon = icon,
                            label = label,
                            active = if (isFlash) flashOn else false,
                            onClick = if (isFlash) onFlash else {{}}
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallTile(modifier: Modifier, icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        modifier
            .height(68.dp)
            .clip(CircleShape)
            .background(if (active) Color(0xFF2C2C2E) else Color(0xFF1C1C1E))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon, null,
            tint = if (active) Color.White else Color(0xFFAEAEB2),
            modifier = Modifier.size(26.dp)
        )
        Text(label, color = Color(0xFFAEAEB2), fontSize = 10.sp)
    }
}
