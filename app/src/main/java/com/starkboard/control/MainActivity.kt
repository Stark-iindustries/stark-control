package com.starkboard.control

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color.Black,
                    surface = Color(0xFF1C1C1E),
                    primary = Color(0xFF0A84FF),
                    onPrimary = Color.White,
                    onBackground = Color.White,
                    onSurface = Color.White,
                )
            ) {
                SetupScreen()
            }
        }
    }
}

@Composable
fun SetupScreen() {
    val ctx = LocalContext.current
    var overlayOk by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var writeSettingsOk by remember { mutableStateOf(Settings.System.canWrite(ctx)) }
    var dndOk by remember { mutableStateOf((ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted) }
    var notifListenerOk by remember { mutableStateOf(isNotifListenerEnabled(ctx)) }
    var serviceRunning by remember { mutableStateOf(false) }

    val allGranted = overlayOk && writeSettingsOk && dndOk && notifListenerOk

    LaunchedEffect(Unit) {
        while (true) {
            overlayOk = Settings.canDrawOverlays(ctx)
            writeSettingsOk = Settings.System.canWrite(ctx)
            dndOk = (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted
            notifListenerOk = isNotifListenerEnabled(ctx)
            delay(800)
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))

            // App icon / title
            Box(
                Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF0A84FF)),
                contentAlignment = Alignment.Center
            ) {
                Text("S", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            Text("Stark Control", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("iPhone 16 UI for Android", color = Color(0xFF8E8E93), fontSize = 14.sp)

            Spacer(Modifier.height(32.dp))

            // Permission cards
            PermCard("Draw over apps", overlayOk, Icons.Rounded.Layers) {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${ctx.packageName}")))
            }
            Spacer(Modifier.height(10.dp))
            PermCard("Modify system settings", writeSettingsOk, Icons.Rounded.Tune) {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:${ctx.packageName}")))
            }
            Spacer(Modifier.height(10.dp))
            PermCard("Do Not Disturb access", dndOk, Icons.Rounded.DoNotDisturb) {
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
            Spacer(Modifier.height(10.dp))
            PermCard("Notification access", notifListenerOk, Icons.Rounded.Notifications) {
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }

            Spacer(Modifier.height(24.dp))

            // Termux setup card
            AdbSetupCard(ctx)

            Spacer(Modifier.height(24.dp))

            // Start / Stop
            AnimatedVisibility(allGranted) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = {
                            if (serviceRunning) OverlayService.stop(ctx)
                            else OverlayService.start(ctx)
                            serviceRunning = !serviceRunning
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (serviceRunning) Color(0xFFFF453A) else Color(0xFF0A84FF)
                        )
                    ) {
                        Text(
                            if (serviceRunning) "Stop Stark Control" else "Start Stark Control",
                            fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (serviceRunning) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Swipe top-left → Notifications\nSwipe top-right → Controls",
                            color = Color(0xFF8E8E93), fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun PermCard(label: String, granted: Boolean, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = if (granted) ({}) else onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1C1C1E),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = if (granted) Color(0xFF30D158) else Color(0xFF0A84FF),
                modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (granted) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF30D158), modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Rounded.ChevronRight, null, tint = Color(0xFF636366), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun AdbSetupCard(ctx: Context) {
    val pkg = ctx.packageName
    val commands = listOf(
        "pkg install android-tools -y",
        "adb connect localhost:5555",
        "adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS",
        "adb shell pm grant $pkg android.permission.MODIFY_PHONE_STATE",
        "adb shell pm grant $pkg android.permission.WRITE_SETTINGS",
        "adb shell cmd notification allow_listener $pkg/.StarkNotificationListenerService",
        "adb shell settings put global policy_control immersive.status=*"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1C1C1E),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Terminal, null, tint = Color(0xFF0A84FF), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("One-time Termux Setup", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Text("Run once in Termux to unlock all features:", color = Color(0xFF8E8E93), fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))

            commands.forEach { cmd ->
                Surface(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    color = Color(0xFF2C2C2E),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        cmd,
                        color = Color(0xFF30D158),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "⚠ Enable Developer Options + Wireless Debugging first",
                color = Color(0xFFFF9F0A), fontSize = 12.sp
            )
        }
    }
}

private fun isNotifListenerEnabled(ctx: Context): Boolean {
    val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
    return flat.contains(ctx.packageName)
}
