package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.LocalProfile
import com.example.ui.theme.*
import com.example.ui.viewmodel.GameViewModel
import com.example.util.SoundSystem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: GameViewModel,
    onBack: () -> Unit
) {
    val profile by viewModel.profile.collectAsState()
    val scope = rememberCoroutineScope()

    var activeThemeCard by remember { mutableStateOf(profile.selectedTheme) }
    var slideVolumeValue by remember { mutableFloatStateOf(profile.volume) }
    var soundEffectsEnabled by remember { mutableStateOf(profile.soundEnabled) }

    val themeStr = LocalAppTheme.current

    FrostedGlassBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(com.example.util.Localization.translate("SETTINGS & CONFIGURATION", profile.language), color = textColorForTheme(Color.White), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) },
                    navigationIcon = {
                        IconButton(onClick = {
                            SoundSystem.playMenuClick()
                            onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Return home icon", tint = textColorForTheme(Color.White))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- LANGUAGE SELECTION ---
                Text(com.example.util.Localization.translate("Language Selection", profile.language), fontSize = 11.sp, color = subtitleColorForTheme(Color.White.copy(alpha = 0.5f)), fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf("FA", "EN").forEach { item ->
                        val active = profile.language == item
                        val label = if (item == "FA") "فارسی" else "English"
                        val boxBg = if (themeStr == "SKETCH") {
                            if (active) Color(0xFFDBEAFE) else Color(0x1A000000)
                        } else {
                            if (active) AccentBlue else CarbonSurface
                        }
                        val boxBorderColor = if (themeStr == "SKETCH") {
                            if (active) Color(0xFF1E40AF) else Color(0xFF94A3B8)
                        } else {
                            if (active) PrimaryCyan else NeonBorder
                        }
                        val labelColor = if (themeStr == "SKETCH") {
                            if (active) Color(0xFF1E1B4B) else Color(0xFF475569)
                        } else {
                            if (active) Color.White else Color.White.copy(alpha = 0.6f)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(boxBg, RoundedCornerShape(10.dp))
                                .border(1.dp, boxBorderColor, RoundedCornerShape(10.dp))
                                .maybeSketchBorder(color = boxBorderColor)
                                .clickable {
                                    SoundSystem.playMenuClick()
                                    scope.launch {
                                        viewModel.updateProfile(profile.copy(language = item))
                                    }
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = labelColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // --- GAME THEMES SECTION ---
                Text(com.example.util.Localization.translate("THEME SELECTION", profile.language), fontSize = 11.sp, color = subtitleColorForTheme(Color.White.copy(alpha = 0.5f)), fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("AUTO", "NIGHT", "DAY", "SKETCH").forEach { item ->
                        val active = activeThemeCard == item
                        val boxBg = if (themeStr == "SKETCH") {
                            if (active) Color(0xFFDBEAFE) else Color(0x1A000000)
                        } else {
                            if (active) AccentBlue else CarbonSurface
                        }
                        val boxBorderColor = if (themeStr == "SKETCH") {
                            if (active) Color(0xFF1E40AF) else Color(0xFF94A3B8)
                        } else {
                            if (active) PrimaryCyan else NeonBorder
                        }
                        val labelColor = if (themeStr == "SKETCH") {
                            if (active) Color(0xFF1E1B4B) else Color(0xFF475569)
                        } else {
                            if (active) Color.White else Color.White.copy(alpha = 0.6f)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(boxBg, RoundedCornerShape(10.dp))
                                .border(1.dp, boxBorderColor, RoundedCornerShape(10.dp))
                                .maybeSketchBorder(color = boxBorderColor)
                                .clickable {
                                    SoundSystem.playMenuClick()
                                    activeThemeCard = item
                                    scope.launch {
                                        viewModel.updateProfile(profile.copy(selectedTheme = item))
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (item == "SKETCH") (if (profile.language == "FA") "کاغذی" else "SKETCH") else item,
                                color = labelColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // --- BOARD CELL THEME OPTION ---
                Text(
                    text = if (profile.language == "FA") "تنظیمات پس‌زمینه صفحه بازی" else "BOARD CELL THEME",
                    fontSize = 11.sp,
                    color = subtitleColorForTheme(Color.White.copy(alpha = 0.5f)),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )

                Card(
                    modifier = Modifier.fillMaxWidth().maybeSketchCard(),
                    colors = CardDefaults.cardColors(containerColor = if (themeStr == "SKETCH") Color.Transparent else CarbonSurface),
                    border = if (themeStr == "SKETCH") null else BorderStroke(1.dp, NeonBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (profile.language == "FA") "خانه‌های صفحه بازی دوز" else "Tic-Tac-Toe Cell Color",
                                color = textColorForTheme(Color.White),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (profile.language == "FA") "روشن (سفید) یا تاریک (سیاه)" else "Switch cell background color",
                                color = subtitleColorForTheme(Color.White.copy(alpha = 0.6f)),
                                fontSize = 11.sp
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("WHITE", "BLACK").forEach { option ->
                                val isActive = profile.gridCellTheme == option
                                val label = if (option == "WHITE") {
                                    if (profile.language == "FA") "سفید" else "White"
                                } else {
                                    if (profile.language == "FA") "سیاه" else "Black"
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isActive) accentColorForTheme(PrimaryCyan) else Color.Gray.copy(alpha = 0.15f))
                                        .clickable {
                                            SoundSystem.playMenuClick()
                                            scope.launch {
                                                viewModel.updateProfile(profile.copy(gridCellTheme = option))
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isActive) Color.Black else textColorForTheme(Color.White)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // --- AUDIO SYSTEM PREFERENCES ---
                Text(com.example.util.Localization.translate("AUDIO SYSTEMS CONSOLE", profile.language), fontSize = 11.sp, color = subtitleColorForTheme(Color.White.copy(alpha = 0.5f)), fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)

                Card(
                    modifier = Modifier.fillMaxWidth().maybeSketchCard(),
                    colors = CardDefaults.cardColors(containerColor = if (themeStr == "SKETCH") Color.Transparent else CarbonSurface),
                    border = if (themeStr == "SKETCH") null else BorderStroke(1.dp, NeonBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Info, contentDescription = "Sfx icon", tint = accentColorForTheme(PrimaryCyan))
                                Text(com.example.util.Localization.translate("Play Move / Menu Click Sounds", profile.language), color = textColorForTheme(Color.White), fontSize = 13.sp)
                            }
                            Switch(
                                checked = soundEffectsEnabled,
                                onCheckedChange = { checked ->
                                    SoundSystem.playMenuClick()
                                    soundEffectsEnabled = checked
                                    scope.launch {
                                        viewModel.updateProfile(profile.copy(soundEnabled = checked))
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = if (themeStr == "SKETCH") Color.White else Color.Black,
                                    checkedTrackColor = accentColorForTheme(PrimaryCyan),
                                    uncheckedBorderColor = Color.Gray
                                )
                            )
                        }

                        Divider(color = if (themeStr == "SKETCH") Color(0xFFCBD5E1) else NeonBorder)

                        Text(com.example.util.Localization.translate("Synthesizer Master Volume", profile.language) + ": " + "${(slideVolumeValue * 100).toInt()}%", color = textColorForTheme(Color.White), fontSize = 13.sp)
                        Slider(
                            value = slideVolumeValue,
                            onValueChange = { vol ->
                                slideVolumeValue = vol
                                scope.launch {
                                    viewModel.updateProfile(profile.copy(volume = vol))
                                }
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = accentColorForTheme(PrimaryCyan),
                                activeTrackColor = accentColorForTheme(PrimaryCyan),
                                inactiveTrackColor = if (themeStr == "SKETCH") Color(0xFFCBD5E1) else NeonBorder
                            )
                        )
                    }
                }

                // --- SECURITY & ANTICHEAT SYSTEMS INFORMATION ---
                Text(com.example.util.Localization.translate("ANTI-CHEAT GATEWAY STATUS", profile.language), fontSize = 11.sp, color = subtitleColorForTheme(Color.White.copy(alpha = 0.5f)), fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)

                Card(
                    modifier = Modifier.fillMaxWidth().maybeSketchCard(),
                    colors = CardDefaults.cardColors(containerColor = if (themeStr == "SKETCH") Color.Transparent else CarbonSurface),
                    border = if (themeStr == "SKETCH") null else BorderStroke(1.dp, NeonBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Shield shield anti-cheat logo", tint = if (themeStr == "SKETCH") Color(0xFF15803D) else Color.Green)
                            Text(com.example.util.Localization.translate("SHIELD V3 ACTIVE", profile.language), color = if (themeStr == "SKETCH") Color(0xFF15803D) else Color.Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Text(
                            text = "• " + com.example.util.Localization.translate("Server-side Move coordinate validation active", profile.language) + ".\n" +
                                    "• " + com.example.util.Localization.translate("Turn alternate verification matching client sockets", profile.language) + ".\n" +
                                    "• " + com.example.util.Localization.translate("Dynamic timing delta mapping guarding Elo protection values", profile.language) + ".\n" +
                                    "• " + com.example.util.Localization.translate("Tamper logs synced to admin reports on Firebase consoles", profile.language) + ".",
                            fontSize = 11.sp,
                            color = subtitleColorForTheme(Color.White.copy(alpha = 0.6f)),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
