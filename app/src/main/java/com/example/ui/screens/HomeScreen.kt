package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.LocalProfile
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.GameViewModel
import com.example.util.SoundSystem
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: GameViewModel,
    onNavigateToGame: (mode: GameMode, diff: AiDifficulty?, seriesMax: Int) -> Unit,
    onNavigateToLeaderboard: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPrivateRoom: () -> Unit,
    onNavigateToSpectate: () -> Unit,
    onLogout: () -> Unit
) {
    val profile by viewModel.profile.collectAsState()
    val counters by viewModel.onlineCounters.collectAsState()
    val chatMessages by viewModel.globalChatMessages.collectAsState()
    val systemAlerts = viewModel.systemAlerts

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    var showAiDiffDialog by remember { mutableStateOf(false) }
    var selectedAiDiff by remember { mutableStateOf(AiDifficulty.NORMAL) }
    var matchSeriesRounds by remember { mutableStateOf(1) } // 1, 3, 5, 7 rounds

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val offset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    // Collect systemic alerts (achievements, levels, rewards)
    LaunchedEffect(key1 = true) {
        systemAlerts.collect { alert ->
            snackbarHostState.showSnackbar(alert)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Color.Transparent
    ) { innerPadding ->
        FrostedGlassBackground(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header: Branding logo
                val themeStr = LocalAppTheme.current
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = com.example.util.Localization.translate("INFINITE", profile.language),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = if (themeStr == "SKETCH") Color(0xFF1E3A8A) else AccentIndigoLight,
                            letterSpacing = 4.sp
                        )
                        if (themeStr == "SKETCH") {
                            Text(
                                text = com.example.util.Localization.translate("TIC TAC TOE", profile.language),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                style = LocalTextStyle.current.copy(
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.15f),
                                        offset = Offset(2f, 2f),
                                        blurRadius = 1f
                                    )
                                )
                            )
                        } else {
                            Text(
                                text = com.example.util.Localization.translate("TIC TAC TOE", profile.language),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                style = LocalTextStyle.current.copy(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            AccentIndigoLight,
                                            AccentFuchsiaLight,
                                            Color(0xFFE2E8F0),
                                            AccentFuchsiaLight,
                                            AccentIndigoLight
                                        ),
                                        start = Offset(offset, 0f),
                                        end = Offset(offset + 300f, 150f)
                                    )
                                )
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                SoundSystem.playMenuClick()
                                onNavigateToSettings()
                            },
                            modifier = Modifier
                                .background(if (themeStr == "SKETCH") Color(0x13000000) else CarbonSurfaceUp, CircleShape)
                                .border(1.dp, if (themeStr == "SKETCH") Color(0xFF94A3B8) else Color.White.copy(alpha = 0.15f), CircleShape)
                                .size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = com.example.util.Localization.translate("SETTINGS", profile.language),
                                tint = if (themeStr == "SKETCH") Color(0xFF334155) else Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                SoundSystem.playMenuClick()
                                viewModel.signOutUser {
                                    onLogout()
                                }
                            },
                            modifier = Modifier
                                .background(if (themeStr == "SKETCH") Color(0x13000000) else CarbonSurfaceUp, CircleShape)
                                .border(1.dp, if (themeStr == "SKETCH") Color(0xFFE11D48).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.15f), CircleShape)
                                .size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = com.example.util.Localization.translate("SIGN OUT", profile.language),
                                tint = Color.Red
                            )
                        }
                    }
                }

            // Glassmorphism Profile Header Card
            ProfileHeaderCard(profile = profile, onProfileClick = {
                SoundSystem.playMenuClick()
                onNavigateToProfile()
            })

            // Live Servers online counter banner
            ServerCounterRow(counters = counters, language = profile.language)

            // Mini Live Demo Autoplay Arena
            MiniLiveDemoBoard(themeStr = themeStr, language = profile.language)

            // Game modes heading
            Text(
                text = com.example.util.Localization.translate("SELECT BATTLEGROUND", profile.language),
                fontSize = 13.sp,
                color = subtitleColorForTheme(Color.White.copy(alpha = 0.6f)),
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Game Mode Grid Layout
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically()
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    maxItemsInEachRow = 2
                ) {
                    GameModeCard(
                        title = com.example.util.Localization.translate("Ranked Duel", profile.language),
                        caption = com.example.util.Localization.translate("Compete for Elo tier & Climb Leaderboards", profile.language),
                        icon = Icons.Default.Star,
                        accentColor = PrimaryCyan,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ranked_lobby_button"),
                        onClick = {
                            SoundSystem.playMenuClick()
                            onNavigateToGame(GameMode.RANKED, null, 1)
                        }
                    )

                    GameModeCard(
                        title = com.example.util.Localization.translate("Casual Arena", profile.language),
                        caption = com.example.util.Localization.translate("Friendly quick multiplayer clash", profile.language),
                        icon = Icons.Default.PlayArrow,
                        accentColor = AccentBlue,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("casual_lobby_button"),
                        onClick = {
                            SoundSystem.playMenuClick()
                            onNavigateToGame(GameMode.CASUAL, null, 1)
                        }
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 2
            ) {
                GameModeCard(
                    title = com.example.util.Localization.translate("Offline vs AI", profile.language),
                    caption = com.example.util.Localization.translate("Train with 6 custom adaptive difficulty bots", profile.language),
                    icon = Icons.Default.Face,
                    accentColor = SecondaryOrange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("match_ai_button"),
                    onClick = {
                        SoundSystem.playMenuClick()
                        showAiDiffDialog = true
                    }
                )

                GameModeCard(
                    title = com.example.util.Localization.translate("Pass & Play", profile.language),
                    caption = com.example.util.Localization.translate("Two player battle on one single device", profile.language),
                    icon = Icons.Default.Person,
                    accentColor = Color(0xFFA855F7),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pass_play_button"),
                    onClick = {
                        SoundSystem.playMenuClick()
                        onNavigateToGame(GameMode.PASS_PLAY, null, 1) // default single match local
                    }
                )
            }

            // Navigation links row (Achievements, Leaderboard, Daily Reward)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        SoundSystem.playMenuClick()
                        viewModel.claimDailyOffer()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (themeStr == "SKETCH") Color(0xFFDCFCE7) else Color(0xFF152A20)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .maybeSketchBorder(color = if (themeStr == "SKETCH") Color(0xFF16A34A) else Color(0xFF22C55E).copy(alpha = 0.3f)),
                    border = if (themeStr == "SKETCH") null else BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Daily login reward", tint = if (themeStr == "SKETCH") Color(0xFF16A34A) else Color(0xFF22C55E))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(com.example.util.Localization.translate("Daily Gift", profile.language), color = if (themeStr == "SKETCH") Color(0xFF15803D) else Color.White, fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        SoundSystem.playMenuClick()
                        onNavigateToLeaderboard()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (themeStr == "SKETCH") Color(0xFFFEF08A) else CarbonSurface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .maybeSketchBorder(color = if (themeStr == "SKETCH") Color(0xFFCA8A04) else NeonBorder),
                    border = if (themeStr == "SKETCH") null else BorderStroke(1.dp, NeonBorder),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = "Leaderboard", tint = if (themeStr == "SKETCH") Color(0xFFCA8A04) else Color.Yellow)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(com.example.util.Localization.translate("Leaderboards", profile.language), color = if (themeStr == "SKETCH") Color(0xFF854D0E) else Color.White, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Premium Creator and Version Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .maybeSketchCard()
                    .then(
                        if (themeStr != "SKETCH") {
                            Modifier
                                .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(12.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        } else Modifier
                    )
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Alien Boy",
                    color = textColorForTheme(Color.White.copy(alpha = 0.9f)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Telegram: @Xlove",
                    color = accentColorForTheme(PrimaryCyan),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Version 2.0.0",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
        }
    }

    // AI Difficulty setup Dialog
    if (showAiDiffDialog) {
        AlertDialog(
            onDismissRequest = { showAiDiffDialog = false },
            containerColor = CarbonSurface,
            title = {
                Text(
                    "TRAINING ENTRANCE",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select AI Difficulty Intellect:", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)

                    // Difficulty selector list
                    val diffs = AiDifficulty.values()
                    var openDiffDrop by remember { mutableStateOf(false) }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { openDiffDrop = !openDiffDrop },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = CarbonSurfaceUp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, NeonBorder)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(selectedAiDiff.label, color = SecondaryOrange, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown icon")
                            }
                        }

                        DropdownMenu(
                            expanded = openDiffDrop,
                            onDismissRequest = { openDiffDrop = false },
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .background(CarbonSurfaceUp)
                        ) {
                            diffs.forEach { diff ->
                                DropdownMenuItem(
                                    text = { Text(diff.label, color = Color.White, fontWeight = FontWeight.SemiBold) },
                                    onClick = {
                                        selectedAiDiff = diff
                                        openDiffDrop = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text("Match Series Count (Rematches):", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 3, 5).forEach { rounds ->
                            val active = matchSeriesRounds == rounds
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = if (active) AccentBlue else CarbonSurfaceUp,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        SoundSystem.playMenuClick()
                                        matchSeriesRounds = rounds
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (rounds == 1) "Single Match" else "Best of $rounds",
                                    color = if (active) Color.White else Color.White.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAiDiffDialog = false
                        SoundSystem.playMenuClick()
                        onNavigateToGame(GameMode.VS_AI, selectedAiDiff, matchSeriesRounds)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SecondaryOrange)
                ) {
                    Text("START DUEL", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiDiffDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.5f))
                }
            }
        )
    }
}

@Composable
fun ProfileHeaderCard(profile: LocalProfile, onProfileClick: () -> Unit) {
    val rankDetails = RankSystem.getRankDetails(profile.elo)
    val themeStr = LocalAppTheme.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .maybeSketchCard()
            .then(
                if (themeStr != "SKETCH") {
                    Modifier
                        .background(
                            Brush.verticalGradient(listOf(CarbonSurface, CarbonSurfaceUp)),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(1.dp, NeonBorder, RoundedCornerShape(16.dp))
                } else Modifier
            )
            .clickable(onClick = onProfileClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // High fidelity styled Avatar
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(if (themeStr == "SKETCH") Color(0xFFDBEAFE) else AccentBlue.copy(alpha = 0.2f), CircleShape)
                    .border(2.dp, if (themeStr == "SKETCH") Color(0xFF1D4ED8) else PrimaryCyan, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = com.example.util.AvatarHelper.getSticker(profile.avatarId),
                    fontSize = 32.sp
                )
                // Small country code flag decoration
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .background(Color.White, CircleShape)
                        .border(1.dp, Color.Black, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val (flag, _) = com.example.util.Localization.getCountryFlagAndName(profile.country, profile.language)
                    Text(
                        text = flag,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // User stats and level progress
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val (flag, _) = com.example.util.Localization.getCountryFlagAndName(profile.country, profile.language)
                    val resolvedUsername = if (profile.username.isBlank()) {
                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.let { it.displayName ?: it.email?.substringBefore("@") } ?: "Player"
                    } else {
                        profile.username
                    }
                    Text(
                        text = "$resolvedUsername $flag",
                        fontSize = 18.sp,
                        color = textColorForTheme(Color.White),
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .background(if (themeStr == "SKETCH") Color(0xFFDBEAFE) else PrimaryCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Lvl ${profile.level}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (themeStr == "SKETCH") Color(0xFF1D4ED8) else PrimaryCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // XP progress bar
                val req = profile.level * 100 + 150
                val progress = (profile.xp.toFloat() / req).coerceIn(0f, 1f)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape),
                        color = if (themeStr == "SKETCH") Color(0xFF2563EB) else PrimaryCyan,
                        trackColor = if (themeStr == "SKETCH") Color(0xFFE2E8F0) else Color.White.copy(alpha = 0.1f)
                    )
                    Text(
                        text = "${profile.xp}/${req} XP",
                        fontSize = 9.sp,
                        color = subtitleColorForTheme(Color.White.copy(alpha = 0.6f))
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🏆 " + com.example.util.Localization.translate("Elo Rating", profile.language) + ": ${profile.elo}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (themeStr == "SKETCH") Color(0xFFCA8A04) else Color.Yellow
                    )
                    Text(
                        text = "⚔️ " + com.example.util.Localization.translate("Win Rate", profile.language) + ": ${if (profile.gamesPlayed == 0) "0%" else "${(profile.wins * 100) / profile.gamesPlayed}%"}",
                        fontSize = 12.sp,
                        color = subtitleColorForTheme(Color.White.copy(alpha = 0.7f))
                    )
                }
            }

            // Rank tier indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .then(
                        if (themeStr == "SKETCH") {
                            Modifier
                                .background(Color(0x06000000), RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(10.dp))
                        } else {
                            Modifier
                                .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                        }
                    )
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Rank Tier Badge",
                    tint = when (rankDetails.group) {
                        "BRONZE" -> Color(0xFFCD7F32)
                        "SILVER" -> Color(0xFFC0C0C0)
                        "GOLD" -> Color(0xFFFFD700)
                        else -> if (themeStr == "SKETCH") Color(0xFF1D4ED8) else PrimaryCyan
                    },
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = rankDetails.name,
                    color = textColorForTheme(Color.White),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ServerCounterRow(counters: OnlineCounters, language: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsating pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val themeStr = LocalAppTheme.current

    Card(
        modifier = Modifier.fillMaxWidth().maybeSketchCard(),
        colors = CardDefaults.cardColors(containerColor = if (themeStr == "SKETCH") Color.Transparent else CarbonSurface),
        shape = RoundedCornerShape(12.dp),
        border = if (themeStr == "SKETCH") null else BorderStroke(1.dp, NeonBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Pulsating green dot indicating online count is fresh
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color(0xFF22C55E).copy(alpha = dotAlpha), CircleShape)
                    .border(1.dp, Color(0xFF22C55E), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${com.example.util.Localization.translate("Online Players: ", language)}${counters.playersOnline}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = textColorForTheme(Color.White),
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun GameModeCard(
    title: String,
    caption: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val themeStr = LocalAppTheme.current
    Box(
        modifier = modifier
            .height(130.dp)
            .maybeSketchCard()
            .then(
                if (themeStr != "SKETCH") {
                    Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    CarbonSurface,
                                    CarbonSurface.copy(alpha = 0.8f)
                                )
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .border(1.dp, NeonBorder, RoundedCornerShape(14.dp))
                } else Modifier
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(accentColorForTheme(accentColor).copy(alpha = if (themeStr == "SKETCH") 0.1f else 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "Game mode icon banner",
                        tint = accentColorForTheme(accentColor),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Arrow launcher",
                    tint = textColorForTheme(Color.White).copy(alpha = 0.25f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = textColorForTheme(Color.White)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = caption,
                    fontSize = 10.sp,
                    color = subtitleColorForTheme(Color.White.copy(alpha = 0.6f)),
                    lineHeight = 12.sp,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun MiniLiveDemoBoard(themeStr: String, language: String) {
    var boardState by remember { mutableStateOf(emptyMap<CellCoord, BoardSymbol>()) }
    var activePlayerState by remember { mutableStateOf(BoardSymbol.X) }
    var winningLineState by remember { mutableStateOf<List<CellCoord>?>(null) }
    var isDrawState by remember { mutableStateOf(false) }
    var latestPlacedCellState by remember { mutableStateOf<CellCoord?>(null) }
    var messageTicker by remember { mutableStateOf("Autoplay matches starting...") }

    val cellDrawProgress = remember { Animatable(0f) }

    // Live red pulsating status dot
    val liveDotTransition = rememberInfiniteTransition(label = "live_dot")
    val liveDotAlpha by liveDotTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    // Pulsing neon green glow Alpha for winning cells
    val winningPulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by winningPulseTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Endless simulation match loop in side-effect: local, low CPU footprint
    LaunchedEffect(key1 = true) {
        while (true) {
            if (winningLineState != null || isDrawState) {
                // Pause for 2 seconds upon match climax
                kotlinx.coroutines.delay(2000)
                boardState = emptyMap()
                winningLineState = null
                isDrawState = false
                activePlayerState = BoardSymbol.X
                latestPlacedCellState = null
                messageTicker = "Loading next training match..."
                cellDrawProgress.snapTo(0f)
            } else {
                // Natural simulated delay between moves
                val delayMs = kotlin.random.Random.nextLong(1000, 1800)
                kotlinx.coroutines.delay(delayMs)

                val chosenCell = getMiniAiMove(boardState, activePlayerState)
                val opponentSpec = activePlayerState.other()

                // Inspect tactical impact of move before placing
                val testWin = checkMiniWin(boardState + (chosenCell to activePlayerState)) != null
                val testBlock = checkMiniWin(boardState + (chosenCell to opponentSpec)) != null
                val claimsCenter = chosenCell.r == 2 && chosenCell.c == 2

                latestPlacedCellState = chosenCell
                cellDrawProgress.snapTo(0f)
                
                // Commit move
                boardState = boardState + (chosenCell to activePlayerState)

                // Animate hand-drawn sketch of placed symbol
                cellDrawProgress.animateTo(1f, tween(250, easing = FastOutSlowInEasing))

                // Frame educational ticker updates explaining moves
                messageTicker = when {
                    testWin -> "🏆 AI ${activePlayerState} lands a winning line of 4!"
                    testBlock -> "🛡️ AI ${activePlayerState} blocks AI ${opponentSpec}'s winning threat!"
                    claimsCenter -> "🎯 AI ${activePlayerState} seizes the center coordinate!"
                    boardState.size <= 2 -> "🚀 AI ${activePlayerState} initiates tactical opening..."
                    else -> {
                        val phrases = listOf(
                            "⚡ AI ${activePlayerState} builds up strategic pressure...",
                            "♟️ AI ${activePlayerState} positions for line creation...",
                            "🎯 AI ${activePlayerState} pushes offensive vectors...",
                            "🔍 AI ${activePlayerState} scales defensive cover..."
                        )
                        phrases[kotlin.random.Random.nextInt(phrases.size)]
                    }
                }

                // Match conditions check
                val winLines = checkMiniWin(boardState)
                if (winLines != null) {
                    winningLineState = winLines
                } else if (boardState.size >= 25) {
                    isDrawState = true
                    messageTicker = "🤝 DRAW! Stalemate of perfect defenses."
                } else {
                    activePlayerState = opponentSpec
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .maybeSketchCard(),
        colors = CardDefaults.cardColors(
            containerColor = if (themeStr == "SKETCH") Color(0xFFFFFDF9) else CarbonSurface.copy(alpha = 0.5f)
        ),
        border = if (themeStr == "SKETCH") null else BorderStroke(1.dp, NeonBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title Header with Pulse Broadcast Dot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFFEF4444).copy(alpha = liveDotAlpha), CircleShape)
                        .border(1.dp, Color(0xFFEF4444), CircleShape)
                )
                Text(
                    text = "LIVE TRAINING SIMULATOR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = textColorForTheme(Color.White.copy(alpha = 0.9f)),
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .background(if (themeStr == "SKETCH") Color(0x13000000) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "5x5 GRID",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColorForTheme(PrimaryCyan)
                    )
                }
            }

            // Ticker Message
            Text(
                text = messageTicker,
                fontSize = 12.sp,
                color = if (themeStr == "SKETCH") Color(0xFF1E3A8A) else PrimaryCyan,
                fontWeight = FontWeight.Medium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (themeStr == "SKETCH") Color(0x06000000) else Color.Black.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                    .padding(vertical = 5.dp, horizontal = 10.dp)
            )

            // Centered Canvas Board
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .background(if (themeStr == "SKETCH") Color(0xFFFBF9F3) else Color.Black.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .border(1.dp, if (themeStr == "SKETCH") Color(0xFFCBD5E1) else NeonBorder.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    val currentCellSize = size.width / 5f

                    // 1. Highlight current winning cells with pulsing neon glow
                    if (winningLineState != null) {
                        for (coord in winningLineState!!) {
                            val cellLeft = coord.c * currentCellSize
                            val cellTop = coord.r * currentCellSize
                            drawRect(
                                color = Color(0x2210B981).copy(alpha = pulseAlpha * 0.35f + 0.1f),
                                topLeft = Offset(cellLeft, cellTop),
                                size = androidx.compose.ui.geometry.Size(currentCellSize, currentCellSize)
                            )
                            drawRect(
                                color = Color(0xFF10B981).copy(alpha = pulseAlpha),
                                topLeft = Offset(cellLeft + 1f, cellTop + 1f),
                                size = androidx.compose.ui.geometry.Size(currentCellSize - 2f, currentCellSize - 2f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                            )
                        }
                    }

                    // 2. Draw Grid coordinate lines
                    val gridLineColor = if (themeStr == "SKETCH") Color(0xFF475569).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.12f)
                    for (r in 1..4) {
                        val y = r * currentCellSize
                        drawLine(
                            color = gridLineColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.5f
                        )
                    }
                    for (c in 1..4) {
                        val x = c * currentCellSize
                        drawLine(
                            color = gridLineColor,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1.5f
                        )
                    }

                    // 3. Notebook pink ledger margin line
                    drawLine(
                        color = if (themeStr == "SKETCH") Color(0xFFE11D48).copy(alpha = 0.28f) else Color(0xFFFF3B30).copy(alpha = 0.18f),
                        start = Offset(currentCellSize * 0.45f, 0f),
                        end = Offset(currentCellSize * 0.45f, size.height),
                        strokeWidth = 2f
                    )

                    // 4. Render active symbols
                    for ((coord, symbol) in boardState) {
                        val cellLeft = coord.c * currentCellSize
                        val cellTop = coord.r * currentCellSize

                        val xPadding = currentCellSize * 0.24f
                        val yPadding = currentCellSize * 0.24f

                        val isWinning = winningLineState?.contains(coord) == true
                        val baseColorX = if (isWinning) Color(0xFF10B981) else (if (themeStr == "SKETCH") Color(0xFF1D4ED8) else Color(0xFF22D3EE))
                        val baseColorO = if (isWinning) Color(0xFF10B981) else (if (themeStr == "SKETCH") Color(0xFFDC2626) else Color(0xFFF43F5E))

                        val progress = if (coord == latestPlacedCellState) cellDrawProgress.value else 1f

                        if (symbol == BoardSymbol.X) {
                            // Stroke 1 of X
                            if (progress > 0f) {
                                val stroke1Progress = (progress / 0.5f).coerceIn(0f, 1f)
                                drawLine(
                                    color = baseColorX,
                                    start = Offset(cellLeft + xPadding, cellTop + yPadding),
                                    end = Offset(
                                        cellLeft + xPadding + (currentCellSize - xPadding * 2f) * stroke1Progress,
                                        cellTop + yPadding + (currentCellSize - yPadding * 2f) * stroke1Progress
                                    ),
                                    strokeWidth = 3f,
                                    cap = StrokeCap.Round
                                )
                            }
                            // Stroke 2 of X
                            if (progress > 0.5f) {
                                val stroke2Progress = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
                                drawLine(
                                    color = baseColorX,
                                    start = Offset(cellLeft + currentCellSize - xPadding, cellTop + yPadding),
                                    end = Offset(
                                        (cellLeft + currentCellSize - xPadding) - (currentCellSize - xPadding * 2f) * stroke2Progress,
                                        cellTop + yPadding + (currentCellSize - yPadding * 2f) * stroke2Progress
                                    ),
                                    strokeWidth = 3f,
                                    cap = StrokeCap.Round
                                )
                            }
                        } else if (symbol == BoardSymbol.O) {
                            if (progress > 0f) {
                                drawArc(
                                    color = baseColorO,
                                    startAngle = -90f,
                                    sweepAngle = 360f * progress,
                                    useCenter = false,
                                    topLeft = Offset(cellLeft + xPadding, cellTop + yPadding),
                                    size = androidx.compose.ui.geometry.Size(currentCellSize - xPadding * 2f, currentCellSize - yPadding * 2f),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f, cap = StrokeCap.Round)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Educational Local win condition checking for 4 consecutive symbols on compact 5x5 board
fun checkMiniWin(board: Map<CellCoord, BoardSymbol>): List<CellCoord>? {
    val directions = listOf(
        Pair(0, 1),   // Horizontal
        Pair(1, 0),   // Vertical
        Pair(1, 1),   // Diagonal \
        Pair(1, -1)   // Diagonal /
    )
    for (r in 0..4) {
        for (c in 0..4) {
            val symbol = board[CellCoord(r, c)] ?: continue
            if (symbol == BoardSymbol.NONE) continue

            for ((dr, dc) in directions) {
                val coords = (0..3).map { step -> CellCoord(r + dr * step, c + dc * step) }
                if (coords.all { it.r in 0..4 && it.c in 0..4 && board[it] == symbol }) {
                    return coords
                }
            }
        }
    }
    return null
}

// Smart, fast, fully-local gameplay scorer for autonomous AI vs AI replay
fun getMiniAiMove(board: Map<CellCoord, BoardSymbol>, aiSymbol: BoardSymbol): CellCoord {
    val emptyCells = mutableListOf<CellCoord>()
    for (r in 0..4) {
        for (c in 0..4) {
            val cell = CellCoord(r, c)
            if (board[cell] == null || board[cell] == BoardSymbol.NONE) {
                emptyCells.add(cell)
            }
        }
    }
    if (emptyCells.isEmpty()) return CellCoord(2, 2)
    
    // First move opens in center
    if (board.isEmpty()) return CellCoord(2, 2)

    val opponentSymbol = if (aiSymbol == BoardSymbol.X) BoardSymbol.O else BoardSymbol.X

    // 1. Can AI win immediately?
    for (cell in emptyCells) {
        val testBoard = board + (cell to aiSymbol)
        if (checkMiniWin(testBoard) != null) {
            return cell
        }
    }

    // 2. Can opponent win? Block immediately!
    for (cell in emptyCells) {
        val testBoard = board + (cell to opponentSymbol)
        if (checkMiniWin(testBoard) != null) {
            return cell
        }
    }

    // 3. Fallback to scoring candidate moves dynamically
    var bestCell = emptyCells.first()
    var bestScore = -1000f

    for (cell in emptyCells) {
        var score = 0f

        // Prefer coordinates near the center
        val distToCenter = kotlin.math.abs(cell.r - 2) + kotlin.math.abs(cell.c - 2)
        score += (4 - distToCenter) * 1.5f

        // Scan scan directions for tactical combinations
        val directions = listOf(Pair(0, 1), Pair(1, 0), Pair(1, 1), Pair(1, -1))
        for ((dr, dc) in directions) {
            for (offset in -3..0) {
                var aiPoints = 0
                var oppPoints = 0
                var emptyPoints = 0
                var outOfBounds = false

                for (step in 0..3) {
                    val nr = cell.r + dr * (offset + step)
                    val nc = cell.c + dc * (offset + step)
                    if (nr in 0..4 && nc in 0..4) {
                        val sym = if (nr == cell.r && nc == cell.c) aiSymbol else board[CellCoord(nr, nc)]
                        if (sym == aiSymbol) {
                            aiPoints++
                        } else if (sym == opponentSymbol) {
                            oppPoints++
                        } else if (sym == null || sym == BoardSymbol.NONE) {
                            emptyPoints++
                        }
                    } else {
                        outOfBounds = true
                        break
                    }
                }

                if (!outOfBounds) {
                    if (aiPoints == 3 && emptyPoints == 1) {
                        score += 50f
                    } else if (aiPoints == 2 && emptyPoints == 2) {
                        score += 10f
                    }
                    if (oppPoints == 3 && emptyPoints == 1) {
                        score += 35f
                    }
                }
            }
        }

        // Add minimal noise so games play organically
        score += kotlin.random.Random.nextFloat() * 2f

        if (score > bestScore) {
            bestScore = score
            bestCell = cell
        }
    }

    return bestCell
}
