package com.starkboard.control

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StarkControlTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun StarkControlTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color.Black,
            surface = Color(0xFF1C1C1E),
            primary = Color(0xFF0A84FF),
            onPrimary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White,
        ),
        content = content
    )
}

@Composable
fun MainScreen() {
    val ctx = LocalContext.current
    var step by remember { mutableIntStateOf(0) }

    // Recheck permissions every time the app resumes (LaunchedEffect key = step)
    LaunchedEffect(step) {
        while (true) {
            delay(500)
            // Auto-advance if permission was granted
            when (step) {
                0 -> if (Settings.canDrawOverlays(ctx)) step = 1
                1 -> if (Settings.System.canWrite(ctx)) step = 2
                2 -> {
                    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (nm.isNotificationPolicyAccessGranted) step = 3
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Icon area
            Box(
                Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF0A84FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PhoneAndroid, null,
                    tint = Color.White, modifier = Modifier.size(52.dp))
            }

            Spacer(Modifier.height(28.dp))

            Text(
                "Stark Control",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "iPhone-style Control Center",
                color = Color(0xFF8E8E93),
                fontSize = 16.sp
            )

            Spacer(Modifier.height(40.dp))

            if (step < 3) {
                // Permission steps
                Text(
                    "Grant permissions to continue",
                    color = Color(0xFF8E8E93),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                PermissionStep(
                    number = 1,
                    active = step == 0,
                    done = step > 0,
                    icon = Icons.Default.Layers,
                    title = "Draw Over Other Apps",
                    desc = "Lets the Control Center overlay appear on top of everything.",
                    buttonText = "Grant",
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${ctx.packageName}")
                        )
                        ctx.startActivity(intent)
                    }
                )

                Spacer(Modifier.height(12.dp))

                PermissionStep(
                    number = 2,
                    active = step == 1,
                    done = step > 1,
                    icon = Icons.Default.Settings,
                    title = "Modify System Settings",
                    desc = "Needed for Airplane Mode and screen brightness control.",
                    buttonText = "Grant",
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:${ctx.packageName}")
                        )
                        ctx.startActivity(intent)
                    }
                )

                Spacer(Modifier.height(12.dp))

                PermissionStep(
                    number = 3,
                    active = step == 2,
                    done = step > 2,
                    icon = Icons.Default.DoNotDisturb,
                    title = "Do Not Disturb Access",
                    desc = "Needed to toggle Focus / Do Not Disturb mode.",
                    buttonText = "Grant",
                    onClick = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        ctx.startActivity(intent)
                    }
                )
            } else {
                // All permissions granted — show main control
                AllGrantedScreen()
            }
        }
    }
}

@Composable
fun PermissionStep(
    number: Int,
    active: Boolean,
    done: Boolean,
    icon: ImageVector,
    title: String,
    desc: String,
    buttonText: String,
    onClick: () -> Unit
) {
    val containerColor = when {
        done -> Color(0xFF1C1C1E)
        active -> Color(0xFF1C1C1E)
        else -> Color(0xFF0F0F0F)
    }
    val borderColor = when {
        done -> Color(0xFF30D158)
        active -> Color(0xFF0A84FF)
        else -> Color(0xFF3A3A3C)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Step circle
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (done) Color(0xFF30D158) else if (active) Color(0xFF0A84FF) else Color(0xFF3A3A3C)),
                contentAlignment = Alignment.Center
            ) {
                if (done) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Text("$number", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(desc, color = Color(0xFF8E8E93), fontSize = 13.sp)
            }

            if (active) {
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(buttonText, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun AllGrantedScreen() {
    val ctx = LocalContext.current
    var serviceRunning by remember { mutableStateOf(false) }

    // Check if service is running
    LaunchedEffect(Unit) {
        while (true) {
            // Simple check via shared prefs flag set by service
            val prefs = ctx.getSharedPreferences("stark_control", Context.MODE_PRIVATE)
            serviceRunning = prefs.getBoolean("service_running", false)
            delay(1000)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // All done card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1C1C1E),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF30D158),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "All permissions granted",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Stark Control is ready to use",
                    color = Color(0xFF8E8E93),
                    fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Start / Stop button
        Button(
            onClick = {
                if (serviceRunning) {
                    OverlayService.stop(ctx)
                    val prefs = ctx.getSharedPreferences("stark_control", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("service_running", false).apply()
                    serviceRunning = false
                } else {
                    OverlayService.start(ctx)
                    val prefs = ctx.getSharedPreferences("stark_control", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("service_running", true).apply()
                    serviceRunning = true
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (serviceRunning) Color(0xFFFF453A) else Color(0xFF0A84FF)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                if (serviceRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                null, modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (serviceRunning) "Stop Control Center" else "Start Control Center",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(Modifier.height(16.dp))

        if (serviceRunning) {
            Text(
                "Swipe down from the top of your screen\nto open the Control Center",
                color = Color(0xFF8E8E93),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(28.dp))

        // Feature list
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1C1C1E),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Controls available", color = Color(0xFF8E8E93), fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp))
                FeatureRow(Icons.Default.Wifi, "Wi-Fi toggle")
                FeatureRow(Icons.Default.SignalCellularAlt, "Mobile Data (direct)")
                FeatureRow(Icons.Default.Bluetooth, "Bluetooth toggle")
                FeatureRow(Icons.Default.AirplanemodeActive, "Airplane Mode")
                FeatureRow(Icons.Default.FlashlightOn, "Flashlight")
                FeatureRow(Icons.Default.ScreenRotation, "Rotation lock")
                FeatureRow(Icons.Default.DoNotDisturb, "Focus / Do Not Disturb")
                FeatureRow(Icons.Default.BrightnessHigh, "Brightness slider")
                FeatureRow(Icons.Default.VolumeUp, "Volume slider")
            }
        }
    }
}

@Composable
fun FeatureRow(icon: ImageVector, label: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color(0xFF0A84FF), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, fontSize = 15.sp)
    }
}
