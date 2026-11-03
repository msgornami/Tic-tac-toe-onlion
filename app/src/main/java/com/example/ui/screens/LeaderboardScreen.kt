package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import coil.compose.AsyncImage
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.LocalProfile
import com.example.data.model.LeaderboardEntry
import com.example.ui.theme.*
import com.example.ui.viewmodel.GameViewModel
import com.example.util.SoundSystem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    viewModel: GameViewModel,
    onBack: () -> Unit
) {
    val activeProfile by viewModel.profile.collectAsState()
    val leaderboardUiState by viewModel.leaderboardState.collectAsState()
    val friendsList by viewModel.friends.collectAsState()

    FrostedGlassBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("LEADERBOARDS", color = textColorForTheme(Color.White), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) },
                    navigationIcon = {
                        IconButton(onClick = {
                            SoundSystem.playMenuClick()
                            onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Return home", tint = textColorForTheme(Color.White))
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
                when (val state = leaderboardUiState) {
                    is com.example.ui.viewmodel.LeaderboardUiState.Loading -> {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = PrimaryCyan)
                        }
                    }
                    is com.example.ui.viewmodel.LeaderboardUiState.Error -> {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error icon",
                                tint = Color.Red.copy(alpha = 0.8f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = state.message,
                                color = Color.White.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.startLeaderboardListener() },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                            ) {
                                Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    is com.example.ui.viewmodel.LeaderboardUiState.Success -> {
                        val displayedList = state.entries

                        if (displayedList.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Empty",
                                        tint = Color.White.copy(alpha = 0.2f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "No players yet",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            // Podiums block for top 3 entities
                            if (displayedList.size >= 3) {
                                PodiumSection(topThree = displayedList.take(3))
                            }

                            // Rest list of standings
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(displayedList) { item ->
                                    LeadercardRow(item = item)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PodiumSection(topThree: List<LeaderboardEntry>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // Second position (Left)
        if (topThree.size >= 2) {
            val s = topThree[1]
            PodiumCard(entry = s, order = 2, scaleCoeff = 0.85f, modifier = Modifier.weight(1f))
        }

        // First position (Center)
        if (topThree.isNotEmpty()) {
            val f = topThree[0]
            PodiumCard(entry = f, order = 1, scaleCoeff = 1.0f, modifier = Modifier.weight(1.1f))
        }

        // Third position (Right)
        if (topThree.size >= 3) {
            val t = topThree[2]
            PodiumCard(entry = t, order = 3, scaleCoeff = 0.8f, modifier = Modifier.weight(0.95f))
        }
    }
}

@Composable
fun PodiumCard(entry: LeaderboardEntry, order: Int, scaleCoeff: Float, modifier: Modifier) {
    val highlightColor = when (order) {
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFFC0C0C0) // Silver
        else -> Color(0xFFCD7F32) // Bronze
    }
    val themeStr = LocalAppTheme.current

    Box(
        modifier = modifier
            .fillMaxHeight(scaleCoeff)
            .maybeSketchCard(backgroundColor = if (entry.isPlayer) Color(0xFFDBEAFE) else Color(0xFFFFFDF9))
            .then(
                if (themeStr != "SKETCH") {
                    Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    CarbonSurfaceUp,
                                    CarbonSurface
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(1.dp, if (entry.isPlayer) PrimaryCyan else highlightColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                } else Modifier
            )
            .padding(8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            // Crown/Order Icon
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Leader trophy",
                tint = highlightColor,
                modifier = Modifier.size(18.dp)
            )

            // Name
            Text(
                entry.username,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = textColorForTheme(Color.White),
                maxLines = 1,
                textAlign = TextAlign.Center
            )

            // Elo stats
            Text(
                "🏆 ${entry.elo}",
                color = if (themeStr == "SKETCH") Color(0xFFCA8A04) else Color.Yellow,
                fontWeight = FontWeight.Black,
                fontSize = 10.sp
            )

            Text(
                "W: ${entry.wins} L: ${entry.losses}",
                color = subtitleColorForTheme(Color.White.copy(alpha = 0.6f)),
                fontWeight = FontWeight.Medium,
                fontSize = 8.sp
            )

            // Ribbon
            Box(
                modifier = Modifier
                    .background(highlightColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    "#$order",
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
fun LeadercardRow(item: LeaderboardEntry) {
    val themeStr = LocalAppTheme.current
    val highlightColor = when (item.position) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> if (themeStr == "SKETCH") Color(0xFF475569) else Color.White.copy(alpha = 0.5f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .maybeSketchCard(backgroundColor = if (item.isPlayer) Color(0xFFDBEAFE) else Color(0xFFFFFDF9))
            .then(
                if (themeStr != "SKETCH") {
                    Modifier
                        .background(
                            color = if (item.isPlayer) CarbonSurfaceUp else CarbonSurface,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .border(
                            1.dp,
                            if (item.isPlayer) PrimaryCyan else NeonBorder,
                            RoundedCornerShape(10.dp)
                        )
                } else Modifier
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Standings position indicator
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(if (themeStr == "SKETCH") Color(0x0C000000) else Color.White.copy(alpha = 0.04f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${item.position}",
                    color = highlightColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }

            // Avatar frame
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(if (themeStr == "SKETCH") Color(0x0C000000) else Color.White.copy(alpha = 0.04f), CircleShape)
                    .border(1.dp, if (item.isPlayer) (if (themeStr == "SKETCH") Color(0xFF1D4ED8) else PrimaryCyan) else (if (themeStr == "SKETCH") Color(0xFFCBD5E1) else Color.White.copy(alpha = 0.2f)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!item.photoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = item.photoUrl,
                        contentDescription = "Avatar profile index",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Avatar profile index",
                        tint = if (item.isPlayer) (if (themeStr == "SKETCH") Color(0xFF1D4ED8) else PrimaryCyan) else subtitleColorForTheme(Color.White.copy(alpha = 0.7f)),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Name + dynamic country
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        item.username,
                        color = if (item.isPlayer) (if (themeStr == "SKETCH") Color(0xFF1D4ED8) else PrimaryCyan) else textColorForTheme(Color.White),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = if (item.country == "US") "🇺🇸" else if (item.country == "JP") "🇯🇵" else "🌏",
                        fontSize = 10.sp
                    )
                }

                Text(
                    text = "Wins: ${item.wins} | Losses: ${item.losses} | WR: ${item.winRate.toInt()}%",
                    fontSize = 9.sp,
                    color = subtitleColorForTheme(Color.White.copy(alpha = 0.5f))
                )
            }
        }

        // Elo ranking stand
        Text(
            text = "🏆 ${item.elo}",
            color = if (themeStr == "SKETCH") Color(0xFFCA8A04) else Color.Yellow,
            fontWeight = FontWeight.Black,
            fontSize = 13.sp
        )
    }
}
