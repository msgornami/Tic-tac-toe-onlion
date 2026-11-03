package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BoardSymbol
import com.example.data.model.CellCoord
import com.example.data.model.SpectatorMatch
import com.example.ui.theme.*
import com.example.ui.viewmodel.GameViewModel
import com.example.util.SoundSystem
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpectateScreen(
    viewModel: GameViewModel,
    onBack: () -> Unit
) {
    val matches by viewModel.spectatorMatches.collectAsState()
    var activeMatchToWatch by remember { mutableStateOf<SpectatorMatch?>(null) }

    FrostedGlassBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (activeMatchToWatch == null) "SPECTATOR ARENA" else "LIVE BROADCAST", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) },
                    navigationIcon = {
                        IconButton(onClick = {
                            SoundSystem.playMenuClick()
                            if (activeMatchToWatch != null) {
                                activeMatchToWatch = null
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit broadcast screen", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (activeMatchToWatch == null) {
                    // --- SELECTION ARENA SCREEN ---
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "WATCH CURRENT ONGOING PRO MATCHES",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )

                        if (matches.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No live matches available. Check back soon!", color = Color.White.copy(alpha = 0.5f))
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(matches) { match ->
                                    ProMatchCard(match = match, onClick = {
                                        SoundSystem.playMenuClick()
                                        activeMatchToWatch = match
                                    })
                                }
                            }
                        }
                    }
                } else {
                    // --- ACTIVE MATCH BROADCAST WITH COMMUNICATING CHAT FEEDS ---
                    LiveBroadcastGridScreen(
                        match = activeMatchToWatch!!,
                        onExit = {
                            SoundSystem.playMenuClick()
                            activeMatchToWatch = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ProMatchCard(match: SpectatorMatch, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse finder")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CarbonSurface),
        border = BorderStroke(1.dp, NeonBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color.Red.copy(alpha = dotAlpha), CircleShape)
                    )
                    Text(
                        text = "LIVE STREAMING",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Active eye counts", tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                    Text(
                        text = "${120 + (match.movesCount * 5)} spectating",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            // Competitor duel banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(match.player1Name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("🛡️ ELO ${match.player1Elo} | ${match.player1Rank}", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                }

                Text(
                    text = "VS",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = PrimaryCyan
                )

                Column(horizontalAlignment = Alignment.End) {
                    Text(match.player2Name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("🛡️ ELO ${match.player2Elo} | ${match.player2Rank}", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                }
            }

            Divider(color = NeonBorder)

            // Bottom metadata summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Moves: ${match.movesCount} placed",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )

                Text(
                    text = "Duration: ${match.durationSeconds}s",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )

                Box(
                    modifier = Modifier
                        .background(AccentBlue.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Spectate",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryCyan
                    )
                }
            }
        }
    }
}

@Composable
fun LiveBroadcastGridScreen(
    match: SpectatorMatch,
    onExit: () -> Unit
) {
    // Keep local simulated board for this viewer
    val localBoardMap = remember { mutableStateMapOf<CellCoord, BoardSymbol>() }
    var boardMovesCount by remember { mutableStateOf(match.movesCount) }

    // Floating scroll comments log
    val streamComments = remember { mutableStateListOf<String>() }

    // Live automatic board changes simulation
    LaunchedEffect(key1 = true) {
        // Pre-fill board layout slightly
        for (i in 0..12) {
            val r = Random.nextInt(0, 10)
            val c = Random.nextInt(0, 10)
            val sym = if (i % 2 == 0) BoardSymbol.X else BoardSymbol.O
            localBoardMap[CellCoord(r, c)] = sym
        }

        val fanSayings = listOf(
            "Incredible diagonal sequence by ${match.player1Name}",
            "OH! What a block by ${match.player2Name}",
            "This board is getting huge, 11x11 boundary trigger!",
            "GG!",
            "Insane setups",
            "I learn so much diagonal placements watching ${match.player1Name}",
            "Wait! Is that double unblocked three threat?!",
            "This moves series is crazy fast",
            "Perfect block orange!"
        )

        while (this.isActive) {
            delay(1500L + Random.nextLong(1500))
            val randR = Random.nextInt(-3, 13)
            val randC = Random.nextInt(-3, 13)
            val nextSymbol = if (boardMovesCount % 2 == 0) BoardSymbol.X else BoardSymbol.O

            localBoardMap[CellCoord(randR, randC)] = nextSymbol
            boardMovesCount++

            // Sound feedback for stream feeds
            if (nextSymbol == BoardSymbol.X) SoundSystem.playMoveX() else SoundSystem.playMoveO()

            // Dynamic stream chats arriving
            streamComments.add(fanSayings.random())
            if (streamComments.size > 5) {
                streamComments.removeAt(0)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Core stream profile headers card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = CarbonSurfaceUp),
            border = BorderStroke(1.dp, NeonBorder)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("${match.player1Name} vs ${match.player2Name}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Total moves placed: $boardMovesCount | Stream viewing: ${300 + boardMovesCount * 4}", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                }

                Button(
                    onClick = onExit,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f))
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = "Exit channel", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Leave Feed", fontSize = 11.sp)
                }
            }
        }

        // Live Grid Board view box (Constant sizes for broadcast display focus)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(Color.Black)
                .border(1.dp, NeonBorder),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gridSquarePx = 30.dp.toPx()
                val originX = size.width / 2f
                val originY = size.height / 2f

                // Draw central spectator grids
                for (r in -6..6) {
                    val y = originY + r * gridSquarePx
                    drawLine(
                        color = NeonBorder.copy(alpha = 0.4f),
                        start = Offset(originX - 6 * gridSquarePx, y),
                        end = Offset(originX + 6 * gridSquarePx, y),
                        strokeWidth = 1f
                    )
                }

                for (c in -6..6) {
                    val x = originX + c * gridSquarePx
                    drawLine(
                        color = NeonBorder.copy(alpha = 0.4f),
                        start = Offset(x, originY - 6 * gridSquarePx),
                        end = Offset(x, originY + 6 * gridSquarePx),
                        strokeWidth = 1f
                    )
                }

                // Draw dynamic matching symbols coordinates map
                for ((coord, sym) in localBoardMap) {
                    // Filter rendering coordinates strictly within visible spectator limits of (-6..6)
                    if (coord.r in -6..6 && coord.c in -6..6) {
                        val cellLeft = originX + coord.c * gridSquarePx
                        val cellTop = originY + coord.r * gridSquarePx
                        val pad = gridSquarePx * 0.22f

                        if (sym == BoardSymbol.X) {
                            drawLine(
                                color = PrimaryCyan.copy(alpha = 0.8f),
                                start = Offset(cellLeft + pad, cellTop + pad),
                                end = Offset(cellLeft + gridSquarePx - pad, cellTop + gridSquarePx - pad),
                                strokeWidth = 3f
                            )
                            drawLine(
                                color = PrimaryCyan.copy(alpha = 0.8f),
                                start = Offset(cellLeft + gridSquarePx - pad, cellTop + pad),
                                end = Offset(cellLeft + pad, cellTop + gridSquarePx - pad),
                                strokeWidth = 3f
                            )
                        } else {
                            drawCircle(
                                color = SecondaryOrange.copy(alpha = 0.8f),
                                radius = (gridSquarePx - pad * 2f) / 2f,
                                center = Offset(cellLeft + gridSquarePx / 2f, cellTop + gridSquarePx / 2f),
                                style = Stroke(width = 3f)
                            )
                        }
                    }
                }
            }
        }

        // Live Chat Feed panel (scrolling comments)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "💬 STREAMING SPECTATOR FEED",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryCyan,
                letterSpacing = 1.2.sp
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                border = BorderStroke(1.dp, NeonBorder)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(streamComments) { comment ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Viewer icon", tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(12.dp))
                            Text(
                                text = "Anonymous spec: ",
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                            Text(
                                text = comment,
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
