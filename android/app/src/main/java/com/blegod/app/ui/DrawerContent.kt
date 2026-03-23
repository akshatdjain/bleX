package com.blegod.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Left‑side navigation drawer content — Google Drive style.
 *
 * @param currentRoute   The currently active navigation route
 * @param logsVisible    Whether the "Logs" item should be shown (toggle in Settings)
 * @param onNavigate     Called when the user taps a drawer item
 */
@Composable
fun DrawerContent(
    currentRoute: String,
    logsVisible: Boolean,
    onNavigate: (String) -> Unit
) {
    ModalDrawerSheet(
        drawerShape = RoundedCornerShape(0.dp, 16.dp, 16.dp, 0.dp),
        modifier = Modifier.width(280.dp)
    ) {
        // ── Header ──────────────────────────────
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.BluetoothSearching,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "BleX",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp)
        ) {
            // ── Scanner (Home) ──────────────────
            DrawerItem(
                icon = Icons.Default.BluetoothSearching,
                label = "Dashboard",
                selected = currentRoute == "scanner",
                onClick = { onNavigate("scanner") }
            )

            Spacer(Modifier.height(4.dp))

            // ── Settings ────────────────────────
            DrawerItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                selected = currentRoute == "settings" || currentRoute.startsWith("settings/"),
                onClick = { onNavigate("settings") }
            )

            Spacer(Modifier.height(4.dp))

            // ── Configurator ────────────────────
            DrawerItem(
                icon = Icons.Default.Build,
                label = "Configurator",
                selected = currentRoute.startsWith("config/"),
                onClick = { onNavigate("config/hotspot") }
            )

            Spacer(Modifier.height(4.dp))

            // ── Web Dashboard ────────────────────
            DrawerItem(
                icon = Icons.Default.Language,
                label = "Web Dashboard",
                selected = currentRoute == "webdashboard",
                onClick = { onNavigate("webdashboard") }
            )

            // ── Logs (conditional) ──────────────
            if (logsVisible) {
                Spacer(Modifier.height(4.dp))
                DrawerItem(
                    icon = Icons.Default.Terminal,
                    label = "Logs",
                    selected = currentRoute == "logs",
                    onClick = { onNavigate("logs") }
                )
            }

            Spacer(Modifier.weight(1f))

            // ── Footer ─────────────────────────
            Text(
                "v3.0.5",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            )
        }
    }
}

// ── Reusable drawer items ────────────────────────────────────────

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.height(52.dp)
    )
}

@Composable
private fun DrawerSubItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        icon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        },
        label = {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.height(46.dp)
    )
}
