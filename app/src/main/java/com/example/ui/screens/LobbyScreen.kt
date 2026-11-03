package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.FriendItem
import com.example.data.model.BoardSymbol
import com.example.data.model.GameMode
import com.example.data.repository.MatchmakingState
import com.example.ui.theme.*
import com.example.ui.viewmodel.GameViewModel
import com.example.util.SoundSystem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(
    viewModel: GameViewModel,
    mode: GameMode,
    onBack: () -> Unit,
    onStartGame: (opponentName: String, opponentRank: String, opponentElo: Int, opponentAvatarId: Int, playerSymbol: BoardSymbol, seriesMax: Int, gameId: String) -> Unit
) {
    val matchmakingState by viewModel.matchmakingState.collectAsState()
    val friendsList by viewModel.friends.collectAsState()

    val scope = rememberCoroutineScope()
    var displayPrivateLobbyState by remember { mutableStateOf(mode == GameMode.PRIVATE_ROOM) }

    // Private Room Variables
    var roomCodeState by remember { mutableStateOf("X7B-9P2") }
    var mockFriendJoined by remember { mutableStateOf(false) }
    var roundsChosen by remember { mutableStateOf(1) } // 1, 3, 5, 7

    // Start Searching on entry if not private room
    LaunchedEffect(key1 = mode) {
        if (mode != GameMode.PRIVATE_ROOM) {
            viewModel.startSearching(mode)
        }
    }

    // Direct game launcher on success matchmaking
    LaunchedEffect(matchmakingState) {
        val state = matchmakingState
        if (state is MatchmakingState.Success) {
            onStartGame(
                state.opponentName,
                state.opponentRank,
                state.opponentElo,
                state.opponentAvatarId,
                state.playerSymbol,
                1, // standard single match
                state.gameId
            )
        }
    }

    val themeStr = LocalAppTheme.current

    FrostedGlassBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (displayPrivateLobbyState) "PRIVATE ROOM LOBBY" else "MATCHMAKING", color = textColorForTheme(Color.White), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) },
                    navigationIcon = {
                        IconButton(onClick = {
                            SoundSystem.playMenuClick()
                            viewModel.cancelSearching()
                            onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back back icon", tint = textColorForTheme(Color.White))
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (displayPrivateLobbyState) {
                    // --- PRIVATE LOBBY LAYOUT ---
                    PrivateLobbyWidget(
                        code = roomCodeState,
                        rounds = roundsChosen,
                        friendJoined = mockFriendJoined,
                        friends = friendsList,
                        onRoundsChange = { roundsChosen = it },
                        onInviteFriend = { friend ->
                            SoundSystem.playMenuClick()
                            viewModel.inviteFriendToJoin(friend.username)
                            scope.launch {
                                delay(3000)
                                mockFriendJoined = true // simulate opponent joining private slot!
                            }
                        },
                        onRefreshCode = {
                            SoundSystem.playMenuClick()
                            val codeChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
                            roomCodeState = "${(1..3).map { codeChars.random() }.joinToString("")}-${(1..3).map { codeChars.random() }.joinToString("")}"
                        },
                        onLaunchMatch = {
                            if (mockFriendJoined) {
                                onStartGame(
                                    "Private Friend",
                                    "Silver III",
                                    1080,
                                    5,
                                    BoardSymbol.X,
                                    roundsChosen,
                                    ""
                                )
                            }
                        }
                    )
                } else {
                    // --- ONLINE MATCHMAKING ACTIVE SEARCH SPIN ---
                    val searchSeconds = if (matchmakingState is MatchmakingState.Searching) (matchmakingState as MatchmakingState.Searching).elapsedTimeSeconds else 0

                    OnlineSearchWidget(
                        elapsedSeconds = searchSeconds,
                        modeName = mode.name,
                        onCancel = {
                            SoundSystem.playMenuClick()
                            viewModel.cancelSearching()
                            onBack()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun OnlineSearchWidget(
    elapsedSeconds: Int,
    modeName: String,
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse finder")
    val rotAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "rotation"
    )
    val themeStr = LocalAppTheme.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(if (themeStr == "SKETCH") Color(0xFFE2E8F0) else AccentBlue.copy(alpha = 0.1f), CircleShape)
                .border(1.dp, if (themeStr == "SKETCH") Color(0xFF475569) else PrimaryCyan.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Rotating search radar icon
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search Radar icon",
                tint = if (themeStr == "SKETCH") Color(0xFF2563EB) else PrimaryCyan,
                modifier = Modifier
                    .size(54.dp)
                    .rotate(rotAngle)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "MATCHMAKING SEARCH IN PROGRESS",
                color = textColorForTheme(Color.White),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Text(
                "Searching comparable opponents for $modeName Duel...",
                color = subtitleColorForTheme(Color.White.copy(alpha = 0.5f)),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }

        Text(
            text = "⏳ MATCH SEARCH TIMER: ${elapsedSeconds}s",
            color = if (themeStr == "SKETCH") Color(0xFFCA8A04) else Color.Yellow,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold
        )

        // Simulated connectivity logs
        Card(
            modifier = Modifier.fillMaxWidth(0.9f).maybeSketchCard(),
            colors = CardDefaults.cardColors(containerColor = if (themeStr == "SKETCH") Color.Transparent else CarbonSurface),
            border = if (themeStr == "SKETCH") null else BorderStroke(1.dp, NeonBorder)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "📡 MATCHING PIPELINE LOGS:",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (themeStr == "SKETCH") Color(0xFF2563EB) else PrimaryCyan
                )
                val logText = when {
                    elapsedSeconds < 2 -> "• Contacting Global TicTac master broker...\n• Syncing ELO ranges..."
                    elapsedSeconds < 4 -> "• Broadening ELO bracket tolerance (±60)...\n• 14 active matches scanned..."
                    else -> "• Optimizing ping paths...\n• Connecting peer session sockets..."
                }
                Text(
                    text = logText,
                    fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = subtitleColorForTheme(Color.White.copy(alpha = 0.7f)),
                    lineHeight = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val cancelBorder = if (themeStr == "SKETCH") Modifier.sketchBorder(Color(0xFFDC2626), strokeWidth = 2f) else Modifier
        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(containerColor = if (themeStr == "SKETCH") Color(0xFFFEE2E2) else Color.Red.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth(0.7f).then(cancelBorder),
            border = if (themeStr == "SKETCH") null else BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
        ) {
            Text("CANCEL MATCH SEARCH", color = if (themeStr == "SKETCH") Color(0xFFDC2626) else Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PrivateLobbyWidget(
    code: String,
    rounds: Int,
    friendJoined: Boolean,
    friends: List<FriendItem>,
    onRoundsChange: (Int) -> Unit,
    onInviteFriend: (FriendItem) -> Unit,
    onRefreshCode: () -> Unit,
    onLaunchMatch: () -> Unit
) {
    val themeStr = LocalAppTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "INVITATION ROOM CODE",
            fontSize = 11.sp,
            color = subtitleColorForTheme(Color.White.copy(alpha = 0.5f)),
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .maybeSketchCard()
                .then(
                    if (themeStr != "SKETCH") {
                        Modifier
                            .background(CarbonSurface, RoundedCornerShape(12.dp))
                            .border(1.dp, NeonBorder, RoundedCornerShape(12.dp))
                    } else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                code,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = accentColorForTheme(PrimaryCyan),
                letterSpacing = 2.sp
            )

            IconButton(onClick = onRefreshCode) {
                Icon(Icons.Default.Refresh, contentDescription = "Roll Code", tint = textColorForTheme(Color.White))
            }
        }

        Text(
            "Share room code above with friends. Once they enter, they join your room!",
            fontSize = 11.sp,
            color = subtitleColorForTheme(Color.White.copy(alpha = 0.5f)),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Divider(color = if (themeStr == "SKETCH") Color(0xFFCBD5E1) else NeonBorder)

        // Slot players status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Player slot (Me)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .maybeSketchCard()
                    .then(
                        if (themeStr != "SKETCH") {
                            Modifier
                                .background(CarbonSurface, RoundedCornerShape(10.dp))
                                .border(2.dp, PrimaryCyan, RoundedCornerShape(10.dp))
                        } else Modifier
                    )
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Person, contentDescription = "Player 1", tint = accentColorForTheme(PrimaryCyan), modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Me (Spectre)", color = textColorForTheme(Color.White), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("Host", color = subtitleColorForTheme(Color.White.copy(alpha = 0.4f)), fontSize = 10.sp)
            }

            // Friend Slot
            Column(
                modifier = Modifier
                    .weight(1f)
                    .maybeSketchCard()
                    .then(
                        if (themeStr != "SKETCH") {
                            Modifier
                                .background(CarbonSurface, RoundedCornerShape(10.dp))
                                .border(1.dp, if (friendJoined) Color.Green else NeonBorder, RoundedCornerShape(10.dp))
                        } else Modifier
                    )
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (friendJoined) Icons.Default.Person else Icons.Default.Add,
                    contentDescription = "Player 2 Slot",
                    tint = if (friendJoined) (if (themeStr == "SKETCH") Color(0xFF16A34A) else Color.Green) else subtitleColorForTheme(Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (friendJoined) "Friend Joined" else "Slot Empty",
                    color = if (friendJoined) (if (themeStr == "SKETCH") Color(0xFF16A34A) else Color.Green) else subtitleColorForTheme(Color.White.copy(alpha = 0.4f)),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    text = if (friendJoined) "Ready to Deploy" else "Awaiting Joiner...",
                    color = subtitleColorForTheme(Color.White.copy(alpha = 0.4f)),
                    fontSize = 10.sp
                )
            }
        }

        Divider(color = if (themeStr == "SKETCH") Color(0xFFCBD5E1) else NeonBorder)

        // Rounds Configuration selector
        Text("SET SERIES ROUND COUNT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = subtitleColorForTheme(Color.White.copy(alpha = 0.5f)))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(1, 3, 5, 7).forEach { num ->
                val active = rounds == num
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .maybeSketchCard(backgroundColor = if (active) Color(0xFFDBEAFE) else Color(0xFFFFFDF9))
                        .then(
                            if (themeStr != "SKETCH") {
                                Modifier.background(if (active) AccentBlue else CarbonSurface, RoundedCornerShape(8.dp))
                            } else Modifier
                        )
                        .clickable { onRoundsChange(num) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (num == 1) "Single" else "Bo${num}",
                        color = if (active) (if (themeStr == "SKETCH") Color(0xFF1D4ED8) else Color.White) else subtitleColorForTheme(Color.White.copy(alpha = 0.6f)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Divider(color = if (themeStr == "SKETCH") Color(0xFFCBD5E1) else NeonBorder)

        // Friend Inviter list
        Text("QUICK INVITE FRIENDS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = subtitleColorForTheme(Color.White.copy(alpha = 0.5f)))
        val activeFriends = friends.filter { it.status == "ONLINE" }

        if (activeFriends.isEmpty()) {
            Text("No friends online right now.", color = subtitleColorForTheme(Color.White.copy(alpha = 0.4f)), fontSize = 11.sp)
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                activeFriends.forEach { friend ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .maybeSketchCard()
                            .then(
                                if (themeStr != "SKETCH") {
                                    Modifier.background(CarbonSurface, RoundedCornerShape(8.dp))
                                } else Modifier
                            )
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(friend.username, color = textColorForTheme(Color.White), fontWeight = FontWeight.Bold, fontSize = 12.sp)

                        Button(
                            onClick = { onInviteFriend(friend) },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColorForTheme(PrimaryCyan)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Invite", color = if (themeStr == "SKETCH") Color.White else Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val buttonBorder = if (themeStr == "SKETCH") {
            Modifier.sketchBorder(Color(0xFF15803D), strokeWidth = 2f)
        } else Modifier
        Button(
            onClick = onLaunchMatch,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (friendJoined) (if (themeStr == "SKETCH") Color(0xFFDCFCE7) else Color.Green) else (if (themeStr == "SKETCH") Color(0xFFF1F5F9) else Color.White.copy(alpha = 0.1f))
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .then(buttonBorder)
                .testTag("launch_match_button"),
            enabled = friendJoined,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "LAUNCH MATCH",
                color = if (friendJoined) (if (themeStr == "SKETCH") Color(0xFF15803D) else Color.Black) else subtitleColorForTheme(Color.White.copy(alpha = 0.5f)),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
