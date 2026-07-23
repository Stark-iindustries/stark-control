package com.starkboard.control.ui

import android.content.Context
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistryOwner
import com.starkboard.control.model.NotificationItem
import com.starkboard.control.toggles.FlashlightToggle
import kotlin.math.roundToInt

class NotificationCenterView(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    notifications: List<NotificationItem>,
    private val onClose: () -> Unit
) : FrameLayout(context) {

    private val notifState = mutableStateListOf<NotificationItem>().also { it.addAll(notifications) }

    fun updateNotifications(items: List<NotificationItem>) {
        notifState.clear()
        notifState.addAll(items)
    }

    init {
        val cv = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            this.setViewTreeLifecycleOwner(lifecycleOwner)
            if (lifecycleOwner is SavedStateRegistryOwner) {
                this.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            }
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    NotificationCenterContent(
                        context = context,
                        notifications = notifState,
                        onClose = onClose
                    )
                }
            }
        }
        addView(cv)
        translationY = -3000f
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
private fun NotificationCenterContent(
    context: Context,
    notifications: List<NotificationItem>,
    onClose: () -> Unit
) {
    val sbHeight = remember {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (id > 0) (context.resources.getDimensionPixelSize(id) / context.resources.displayMetrics.density).dp
        else 28.dp
    }

    var offsetY by remember { mutableFloatStateOf(0f) }
    var flashOn by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onClose() } }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .offset { IntOffset(0, offsetY.roundToInt()) }
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xEE0A0A0A))),
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
                .pointerInput(Unit) { detectTapGestures { /* consume */ } }
        ) {
            Column(Modifier.fillMaxSize()) {
                // Spacer for status bar
                Spacer(Modifier.height(sbHeight + 8.dp))

                // Header
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Notification Center",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3A3A3C))
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }

                // Notification list
                if (notifications.isEmpty()) {
                    Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.NotificationsNone, null,
                                tint = Color(0xFF3A3A3C),
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("No Notifications", color = Color(0xFF636366), fontSize = 15.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(notifications, key = { it.key }) { item ->
                            NotificationCard(item)
                        }
                    }
                }

                // Bottom dock: Flashlight + Camera (like iOS)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DockButton(
                        icon = Icons.Rounded.FlashlightOn,
                        active = flashOn,
                        tint = if (flashOn) Color.White else Color(0xFFAEAEB2)
                    ) {
                        flashOn = !flashOn
                        FlashlightToggle.toggle(context)
                    }
                    DockButton(
                        icon = Icons.Rounded.CameraAlt,
                        active = false,
                        tint = Color(0xFFAEAEB2)
                    ) {}
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(item: NotificationItem) {
    Surface(
        Modifier.fillMaxWidth(),
        color = Color(0xFF1C1C1E),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            // App icon placeholder (coloured circle)
            val seed = item.packageName.hashCode()
            val hue = ((seed and 0xFFFFFF) % 360).toFloat()
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.hsv(hue, 0.6f, 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    item.appName.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        item.appName,
                        color = Color(0xFFAEAEB2),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        item.timeLabel(),
                        color = Color(0xFF636366),
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    item.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.text.isNotBlank()) {
                    Text(
                        item.text,
                        color = Color(0xFFAEAEB2),
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DockButton(icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color(0xFF1C1C1E))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
    }
}
