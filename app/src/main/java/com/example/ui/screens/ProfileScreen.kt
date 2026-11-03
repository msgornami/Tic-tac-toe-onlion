package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.LocalAchievement
import com.example.data.local.LocalProfile
import com.example.data.local.MatchHistoryItem
import com.example.ui.theme.*
import com.example.ui.viewmodel.GameViewModel
import com.example.util.SoundSystem
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.example.util.GoogleSignInHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: GameViewModel,
    onBack: () -> Unit
) {
    val profile by viewModel.profile.collectAsState()
    val achievements by viewModel.achievements.collectAsState()
    val matchHistory by viewModel.matchHistory.collectAsState()

    var activeSubTab by remember { mutableStateOf(0) } // 0 = Career Metrics, 1 = Accomplishments, 2 = Match Log

    var showEditUsernameDialog by remember { mutableStateOf(false) }
    var newUsernameInput by remember(profile.username) { mutableStateOf(profile.username) }

    val context = LocalContext.current
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    var isLinkingGoogle by remember { mutableStateOf(false) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isLinkingGoogle = true
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                viewModel.signInWithGoogle(idToken) { success, errorMsg ->
                    isLinkingGoogle = false
                    if (success) {
                        Toast.makeText(context, "Successfully upgraded and synced Google profile! Welcome ${account.displayName}!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to link Google profile: $errorMsg", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                isLinkingGoogle = false
                Toast.makeText(context, "Failed to retrieve Google Identity credentials.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            isLinkingGoogle = false
            Toast.makeText(context, "Google authentication failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    FrostedGlassBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("PLAYER PROFILE", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) },
                    navigationIcon = {
                        IconButton(onClick = {
                            SoundSystem.playMenuClick()
                            onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back icon", tint = Color.White)
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
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Profile core details summary card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                    border = BorderStroke(1.dp, NeonBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Profile Icon
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .background(AccentBlue.copy(alpha = 0.2f), CircleShape)
                                .border(2.dp, PrimaryCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = com.example.util.AvatarHelper.getSticker(profile.avatarId),
                                fontSize = 38.sp
                            )
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(profile.username, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                IconButton(
                                    onClick = {
                                        SoundSystem.playMenuClick()
                                        showEditUsernameDialog = true
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Username",
                                        tint = PrimaryCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Level ${profile.level}", color = PrimaryCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("•", color = Color.Gray, fontSize = 12.sp)
                                Text("${profile.rankName} (${profile.elo} ELO)", color = Color.Yellow, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Avatar Sticker Selector Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                    border = BorderStroke(1.dp, NeonBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (profile.language == "FA") "تنظیمات عکس پروفایل (انتخاب از بین ۱۰ استیکر)" else "SELECT PROFILE STICKER (10 DESIGNS)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryCyan,
                            letterSpacing = 1.sp
                        )
                        
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(com.example.util.AvatarHelper.STICKERS.size) { index ->
                                val sticker = com.example.util.AvatarHelper.STICKERS[index]
                                val stickerId = index + 1
                                val isSelected = profile.avatarId == stickerId
                                
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .background(if (isSelected) PrimaryCyan.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                                        .border(2.dp, if (isSelected) PrimaryCyan else Color.White.copy(alpha = 0.2f), CircleShape)
                                        .clickable {
                                            SoundSystem.playMenuClick()
                                            viewModel.updateProfile(profile.copy(avatarId = stickerId))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = sticker,
                                        fontSize = 26.sp
                                    )
                                }
                            }
                        }
                    }
                }

                if (!isLoggedIn) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CarbonSurfaceUp),
                        border = BorderStroke(1.dp, NeonBorder.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "GUEST PROFILE (UNSYNCED)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentFuchsiaLight,
                                letterSpacing = 2.sp
                            )
                            Text(
                                "You are currently playing as a guest. Your rating (" + profile.elo + " ELO) and accomplishments are saved locally, but they are NOT visible on the public leaderboard. Sign in with Gmail to save and list your statistics publicly!",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                lineHeight = 15.sp
                            )
                            if (isLinkingGoogle) {
                                CircularProgressIndicator(
                                    color = PrimaryCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Button(
                                    onClick = {
                                        SoundSystem.playMenuClick()
                                        val client = GoogleSignInHelper.getGoogleSignInClient(context)
                                        googleSignInLauncher.launch(client.signInIntent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "G",
                                            color = Color(0xFFEA4335),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            text = "Sign in with Google",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Tabs navigator row
                TabRow(
                    selectedTabIndex = activeSubTab,
                    containerColor = CarbonSurface,
                    contentColor = PrimaryCyan,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(selected = activeSubTab == 0, onClick = { SoundSystem.playMenuClick(); activeSubTab = 0 }) {
                        Text("METRICS", modifier = Modifier.padding(12.dp), fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                    Tab(selected = activeSubTab == 1, onClick = { SoundSystem.playMenuClick(); activeSubTab = 1 }) {
                        Text("MEDALS", modifier = Modifier.padding(12.dp), fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                    Tab(selected = activeSubTab == 2, onClick = { SoundSystem.playMenuClick(); activeSubTab = 2 }) {
                        Text("HISTORY", modifier = Modifier.padding(12.dp), fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }

                // Dynamic view based on selected state
                Box(modifier = Modifier.weight(1f)) {
                    when (activeSubTab) {
                        0 -> CareerMetricsView(profile = profile)
                        1 -> AchievementsListView(achievements = achievements)
                        else -> MatchHistoryView(history = matchHistory)
                    }
                }
            }
        }
    }

    if (showEditUsernameDialog) {
        AlertDialog(
            onDismissRequest = { showEditUsernameDialog = false },
            containerColor = CarbonSurface,
            title = {
                Text(
                    "EDIT USERNAME",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Choose your duel nickname:",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = newUsernameInput,
                        onValueChange = { newUsernameInput = it },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = PrimaryCyan,
                            unfocusedBorderColor = NeonBorder,
                            focusedContainerColor = CarbonSurfaceUp,
                            unfocusedContainerColor = CarbonSurfaceUp
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newUsernameInput.isNotBlank()) {
                            SoundSystem.playMenuClick()
                            viewModel.updateProfile(profile.copy(username = newUsernameInput.trim()))
                            showEditUsernameDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                ) {
                    Text("SAVE", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditUsernameDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.5f))
                }
            }
        )
    }
}

@Composable
fun CareerMetricsView(profile: LocalProfile) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Grid cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(label = "Matches Played", value = "${profile.gamesPlayed}", modifier = Modifier.weight(1f))
            MetricCard(label = "Total Victories", value = "${profile.wins}", color = Color.Green, modifier = Modifier.weight(1f))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(label = "Total Defeats", value = "${profile.losses}", color = Color.Red, modifier = Modifier.weight(1f))
            // The competitive infinite boards NEVER end in draws
            MetricCard(label = "Draw Results", value = "0", modifier = Modifier.weight(1f))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(label = "Active Streak", value = "${profile.currentStreak} Wins", color = PrimaryCyan, modifier = Modifier.weight(1f))
            MetricCard(label = "Career Max Streak", value = "${profile.maxStreak} Wins", color = Color.Yellow, modifier = Modifier.weight(1f))
        }

        // Daily claims metrics details
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CarbonSurfaceUp),
            border = BorderStroke(1.dp, NeonBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "DAILY COHERENCE STATUS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 1.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Daily streak consistency: ", color = Color.White, fontSize = 13.sp)
                    Text("${profile.consecutiveLogins} Days Streak", color = Color.Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun MetricCard(label: String, value: String, color: Color = Color.White, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CarbonSurface),
        border = BorderStroke(1.dp, NeonBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, textAlign = TextAlign.Center)
            Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun AchievementsListView(achievements: List<LocalAchievement>) {
    if (achievements.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No medals loaded.", color = Color.White.copy(alpha = 0.5f))
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(achievements) { ach ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (ach.isUnlocked) CarbonSurface else CarbonSurface.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, if (ach.isUnlocked) PrimaryCyan.copy(alpha = 0.4f) else NeonBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(if (ach.isUnlocked) Color.Yellow.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (ach.isUnlocked) Icons.Default.Star else Icons.Default.Lock,
                                contentDescription = "Accomplishment lock status",
                                tint = if (ach.isUnlocked) Color.Yellow else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                ach.title,
                                color = if (ach.isUnlocked) Color.White else Color.White.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                ach.description,
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                lineHeight = 12.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${ach.currentProgress}/${ach.maxProgress}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (ach.isUnlocked) Color.Green else Color.White.copy(alpha = 0.4f)
                            )
                            if (ach.isUnlocked) {
                                Text("UNLOCKED", fontSize = 8.sp, color = Color.Green, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MatchHistoryView(history: List<MatchHistoryItem>) {
    if (history.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "History empty icon", tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(56.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text("No battle histories found yet.", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(history) { match ->
                val win = match.result == "VICTORY"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                    border = BorderStroke(1.dp, if (win) Color.Green.copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(if (win) Color.Green else Color.Red, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(match.result, fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.Black)
                                }
                                Text(
                                    match.opponentName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Mode: ${match.gameMode} | Opponent ELO: ${match.opponentElo} (${match.opponentRank})",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            if (match.eloChange != 0) {
                                Text(
                                    text = if (match.eloChange > 0) "+${match.eloChange} ELO" else "${match.eloChange} ELO",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    color = if (match.eloChange > 0) Color.Green else Color.Red
                                )
                            }
                            Text(
                                "${match.movesCount} moves | ${match.durationSeconds}s",
                                fontSize = 9.sp,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}
