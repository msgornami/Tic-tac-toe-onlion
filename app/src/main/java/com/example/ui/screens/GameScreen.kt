package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.example.data.model.BoardSymbol
import com.example.data.model.CellCoord
import com.example.data.model.GameMode
import com.example.ui.theme.*
import com.example.ui.viewmodel.GameViewModel
import com.example.util.SoundSystem

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    opponentName: String,
    opponentRank: String,
    opponentElo: Int,
    opponentAvatarId: Int,
    playerSymbol: BoardSymbol,
    gameMode: GameMode,
    aiDifficultyName: String?,
    seriesMax: Int,
    gameId: String = "",
    onExit: () -> Unit
) {
    val board by viewModel.boardState.collectAsState()
    val bounds by viewModel.boardBounds.collectAsState()
    val currentTurn by viewModel.currentTurnSymbol.collectAsState()
    val winningLine by viewModel.winningLine.collectAsState()
    val isComplete by viewModel.gameComplete.collectAsState()
    val isMyTurn by viewModel.isMyTurn.collectAsState()
    val playerSeriesWins by viewModel.playerSeriesWins.collectAsState()
    val oppSeriesWins by viewModel.opponentSeriesWins.collectAsState()
    val activeChat by viewModel.activeGameChat.collectAsState()
    val rematchRequested by viewModel.rematchRequested.collectAsState()
    val opponentRequestedRematch by viewModel.opponentRequestedRematch.collectAsState()
    val rematchAccepted by viewModel.rematchAcceptedByOpponent.collectAsState()

    val localProfile by viewModel.profile.collectAsState()
    val opponentCountryState by viewModel.opponentCountry.collectAsState()

    val activePlayerSymbol by viewModel.playerSymbol.collectAsState()
    val winnerSymbol by viewModel.winnerSymbol.collectAsState()
    val opponentLastMoveCell by viewModel.opponentLastMove.collectAsState()

    val playerTimerSeconds by viewModel.playerTimerSeconds.collectAsState()
    val opponentTimerSeconds by viewModel.opponentTimerSeconds.collectAsState()

    var showExitConfirmationDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        showExitConfirmationDialog = true
    }

    if (showExitConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { 
                SoundSystem.playMenuClick()
                showExitConfirmationDialog = false 
            },
            title = {
                Text(
                    text = if (isComplete) {
                        com.example.util.Localization.translate("Confirm Exit", localProfile.language)
                    } else {
                        com.example.util.Localization.translate("Confirm Abandon", localProfile.language)
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = if (isComplete) {
                        com.example.util.Localization.translate("Are you sure you want to return to the lobby? Your session achievements are saved.", localProfile.language)
                    } else if (gameMode == GameMode.PASS_PLAY) {
                        if (localProfile.language == "FA") {
                            "آیا مایلید این بازی دو نفره محلی را خاتمه داده و خارج شوید؟"
                        } else {
                            "Are you sure you want to end this Pass & Play match?"
                        }
                    } else {
                        com.example.util.Localization.translate("Are you sure you want to surrender and leave the game? This will count as a defeat and your ELO rating will be reduced.", localProfile.language)
                    },
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        SoundSystem.playMenuClick()
                        showExitConfirmationDialog = false
                        viewModel.exitGameSession()
                        onExit()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text(
                        text = if (isComplete) {
                            com.example.util.Localization.translate("Exit", localProfile.language)
                        } else {
                            com.example.util.Localization.translate("Defeat & Exit", localProfile.language)
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        SoundSystem.playMenuClick()
                        showExitConfirmationDialog = false
                    }
                ) {
                    Text(
                        text = com.example.util.Localization.translate("Stay in Battle", localProfile.language),
                        color = PrimaryCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            containerColor = CarbonSurfaceUp,
            tonalElevation = 6.dp
        )
    }

    // Pan & Zoom controls
    var targetPanOffset by remember { mutableStateOf(Offset.Zero) }
    val panOffset by animateOffsetAsState(
        targetValue = targetPanOffset,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    )
    var scale by remember { mutableStateOf(1.0f) }

    val baseCellSize = 64.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val cellSizePx = with(density) { baseCellSize.toPx() }

    var showEmojisFlyout by remember { mutableStateOf(false) }
    var lastMyEmoji by remember { mutableStateOf("") }
    var emojiTriggerCount by remember { mutableStateOf(0) }

    var previousBoardKeys by remember { mutableStateOf(emptySet<CellCoord>()) }
    var latestPlacedCell by remember { mutableStateOf<CellCoord?>(null) }
    val cellDrawProgress = remember { Animatable(0f) }

    val winLineProgressRenderer = remember { Animatable(0f) }
    var showOverlayDelay by remember { mutableStateOf(false) }

    LaunchedEffect(isComplete, winningLine) {
        if (isComplete && !winningLine.isNullOrEmpty()) {
            winLineProgressRenderer.snapTo(0f)
            winLineProgressRenderer.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing)
            )
            kotlinx.coroutines.delay(1200L)
            showOverlayDelay = true
        } else {
            winLineProgressRenderer.snapTo(0f)
            showOverlayDelay = isComplete
        }
    }

    // Start New Game Session on navigation landing
    LaunchedEffect(key1 = opponentName) {
        viewModel.initializeGameSession(
            opponentName = opponentName,
            opponentRank = opponentRank,
            opponentElo = opponentElo,
            opponentAvatarId = opponentAvatarId,
            mySymbol = playerSymbol,
            mode = gameMode,
            difficultyName = aiDifficultyName,
            seriesMax = seriesMax,
            gameId = gameId
        )
        // Center the camera
        targetPanOffset = Offset.Zero
        scale = 1.0f
        previousBoardKeys = emptySet()
        latestPlacedCell = null
    }

    // React to any board moves containing opponent camera tracking and pen scribbling transition animations
    LaunchedEffect(board, activePlayerSymbol) {
        val currentKeys = board.keys
        val newKeys = currentKeys - previousBoardKeys
        if (newKeys.isNotEmpty()) {
            val newlyPlacedCell = newKeys.first()
            latestPlacedCell = newlyPlacedCell

            // Trigger pencil/pen scribbling sweep animation
            cellDrawProgress.snapTo(0f)
            cellDrawProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = LinearEasing)
            )

            // Automate camera centering alignment following opponent symbol plays
            val symbol = board[newlyPlacedCell]
            val mySymbol = activePlayerSymbol
            if (symbol != null && symbol != mySymbol) {
                val currentCellSize = cellSizePx * scale
                targetPanOffset = Offset(
                    x = - (newlyPlacedCell.c + 0.5f) * currentCellSize,
                    y = - (newlyPlacedCell.r + 0.5f) * currentCellSize
                )
            }
        }
        previousBoardKeys = currentKeys
    }

    // React to opponent simulated Emojis
    var lastOpponentChat by remember { mutableStateOf<String?>(null) }
    var showChatPanel by remember { mutableStateOf(false) }
    LaunchedEffect(activeChat.size) {
        if (activeChat.isNotEmpty()) {
            val lastMsg = activeChat.last()
            if (lastMsg.senderName != localProfile.username) {
                lastOpponentChat = lastMsg.content
                SoundSystem.playMoveO() // notify on incoming text alerts
            }
        }
    }

    FrostedGlassBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent
        ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // MAIN ENDLESS GAME GRID (Interacts with gestures for infinite exploration)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isComplete) {
                        detectTransformGestures { _, panDrag, zoomChange, _ ->
                            if (!isComplete) {
                                scale = (scale * zoomChange).coerceIn(0.5f, 2.0f)
                                targetPanOffset = Offset(
                                    targetPanOffset.x + panDrag.x,
                                    targetPanOffset.y + panDrag.y
                                )
                            }
                        }
                    }
                    .pointerInput(isComplete) {
                        detectTapGestures { tapLoc ->
                            if (isComplete) return@detectTapGestures
                            // Calculate centered grid index mappings base on current dynamic scale & screen sizes
                            val currentCellSize = cellSizePx * scale
                            val originX = size.width / 2f + panOffset.x
                            val originY = size.height / 2f + panOffset.y

                            val relativeTouchX = tapLoc.x - originX
                            val relativeTouchY = tapLoc.y - originY

                            // Map offset back to discrete coordinate rows and cols
                            val colCoord = Math.floor((relativeTouchX / currentCellSize).toDouble()).toInt()
                            val rowCoord = Math.floor((relativeTouchY / currentCellSize).toDouble()).toInt()

                            val selectedCoord = CellCoord(rowCoord, colCoord)
                            viewModel.makeMove(selectedCoord)
                        }
                    }
            ) {
                // Background paper infinite effect
                val themeStr = LocalAppTheme.current
                val isLightThemeCell = localProfile.gridCellTheme == "WHITE"
                val cellBgColor = if (isLightThemeCell) Color(0xFFF9F9F9) else Color(0xFF121214)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(cellBgColor)
                )

                // Pure Vector board renderer
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("infinite_board_canvas")
                ) {
                    val currentCellSize = cellSizePx * scale
                    val originX = size.width / 2f + panOffset.x
                    val originY = size.height / 2f + panOffset.y

                    // Define visible coordinate cell limits dynamically to optimize drawing resources
                    // Draw grid covering bounds from -50 to 50 relative to current panning
                    val startRow = (bounds.minRow - 3).coerceAtLeast(-100)
                    val endRow = (bounds.maxRow + 3).coerceAtMost(100)
                    val startCol = (bounds.minCol - 3).coerceAtLeast(-100)
                    val endCol = (bounds.maxCol + 3).coerceAtMost(100)

                    val gridLineColor = if (isLightThemeCell) Color(0xFF1E293B).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.12f)

                    // 1. Draw Grid Lines with black notebook paper lines style (bolded for higher contrast)
                    for (r in startRow..endRow) {
                        val y = originY + r * currentCellSize
                        drawLine(
                            color = gridLineColor,
                            start = Offset(originX + startCol * currentCellSize, y),
                            end = Offset(originX + (endCol + 1) * currentCellSize, y),
                            strokeWidth = 1.6f * scale
                        )
                    }

                    for (c in startCol..endCol) {
                        val x = originX + c * currentCellSize
                        drawLine(
                            color = gridLineColor,
                            start = Offset(x, originY + startRow * currentCellSize),
                            end = Offset(x, originY + (endRow + 1) * currentCellSize),
                            strokeWidth = 1.6f * scale
                        )
                    }

                    // Red school vertical notebook edge ledger line
                    drawLine(
                        color = if (themeStr == "SKETCH") Color(0xFFE11D48).copy(alpha = 0.28f) else Color(0xFFFF3B30).copy(alpha = 0.18f),
                        start = Offset(originX, originY + startRow * currentCellSize),
                        end = Offset(originX, originY + (endRow + 1) * currentCellSize),
                        strokeWidth = 2f * scale
                    )

                    // 2. Draw coordinate indicators for advanced tactical grid aesthetic
                    for (r in bounds.minRow..bounds.maxRow) {
                        val y = originY + r * currentCellSize + currentCellSize / 2f
                    }

                    // Highlight the opponent's last move in a beautiful, glowing, adaptive Material green hue
                    opponentLastMoveCell?.let { oppCoord ->
                        val oppLeft = originX + oppCoord.c * currentCellSize
                        val oppTop = originY + oppCoord.r * currentCellSize
                        drawRect(
                            color = Color(0x3322C55E), // 20% alpha vibrant green fill
                            topLeft = Offset(oppLeft, oppTop),
                            size = Size(currentCellSize, currentCellSize)
                        )
                        drawRect(
                            color = Color(0xFF22C55E), // Solid vibrant green border
                            topLeft = Offset(oppLeft + 1.5f * scale, oppTop + 1.5f * scale),
                            size = Size(currentCellSize - 3f * scale, currentCellSize - 3f * scale),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f * scale)
                        )
                    }

                    // 3. Render dynamic Board Symbol objects (X / O) hand-drawn with ink on paper
                    for ((coord, symbol) in board) {
                        val cellLeft = originX + coord.c * currentCellSize
                        val cellTop = originY + coord.r * currentCellSize

                        val xPadding = currentCellSize * 0.22f
                        val yPadding = currentCellSize * 0.22f

                        // Check if this cell is part of the final winning sequence chain to highlight it
                        val isWinning = winningLine?.contains(coord) == true
                        val baseColorX = if (isWinning) Color(0xFF10B981) else (if (isLightThemeCell) Color(0xFF1D4ED8) else Color(0xFF22D3EE))
                        val baseColorO = if (isWinning) Color(0xFF10B981) else (if (isLightThemeCell) Color(0xFFDC2626) else Color(0xFFF43F5E))

                        val progress = if (coord == latestPlacedCell) cellDrawProgress.value else 1f

                        if (symbol == BoardSymbol.X) {
                            // Hand drawn sketch of X consisting of two sequential strokes
                            if (progress > 0f) {
                                val stroke1Progress = (progress / 0.5f).coerceIn(0f, 1f)
                                drawLine(
                                    color = baseColorX,
                                    start = Offset(cellLeft + xPadding, cellTop + yPadding),
                                    end = Offset(
                                        cellLeft + xPadding + (currentCellSize - xPadding * 2f) * stroke1Progress,
                                        cellTop + yPadding + (currentCellSize - yPadding * 2f) * stroke1Progress
                                    ),
                                    strokeWidth = 4.5f * scale,
                                    cap = StrokeCap.Round
                                )
                            }
                            if (progress > 0.5f) {
                                val stroke2Progress = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
                                drawLine(
                                    color = baseColorX,
                                    start = Offset(cellLeft + currentCellSize - xPadding, cellTop + yPadding),
                                    end = Offset(
                                        (cellLeft + currentCellSize - xPadding) - (currentCellSize - xPadding * 2f) * stroke2Progress,
                                        cellTop + yPadding + (currentCellSize - yPadding * 2f) * stroke2Progress
                                    ),
                                    strokeWidth = 4.5f * scale,
                                    cap = StrokeCap.Round
                                )
                            }
                        } else if (symbol == BoardSymbol.O) {
                            // Hand drawn circular sweep of O (sweep angle = 360 * progress)
                            if (progress > 0f) {
                                drawArc(
                                    color = baseColorO,
                                    startAngle = -90f,
                                    sweepAngle = 360f * progress,
                                    useCenter = false,
                                    topLeft = Offset(cellLeft + xPadding, cellTop + yPadding),
                                    size = Size(currentCellSize - xPadding * 2f, currentCellSize - yPadding * 2f),
                                    style = Stroke(width = 4.5f * scale, cap = StrokeCap.Round)
                                )
                            }
                        }
                    }

                    // 4. Draw Animated Winning Line across consecutive 5 icons
                    val currentWinLine = winningLine
                    if (!currentWinLine.isNullOrEmpty() && winLineProgressRenderer.value > 0f) {
                        val firstCell = currentWinLine.first()
                        val lastCell = currentWinLine.last()

                        val firstCenter = Offset(
                            originX + firstCell.c * currentCellSize + currentCellSize / 2f,
                            originY + firstCell.r * currentCellSize + currentCellSize / 2f
                        )
                        val lastCenter = Offset(
                            originX + lastCell.c * currentCellSize + currentCellSize / 2f,
                            originY + lastCell.r * currentCellSize + currentCellSize / 2f
                        )

                        // Translucent outer highlighter shadow/glow
                        drawLine(
                            color = Color(0x7F10B981), // Emerald glow
                            start = firstCenter,
                            end = firstCenter + (lastCenter - firstCenter) * winLineProgressRenderer.value,
                            strokeWidth = 14f * scale,
                            cap = StrokeCap.Round
                        )

                        // Solid inner laser/felt line
                        drawLine(
                            color = Color(0xFF10B981), // Emerald solid
                            start = firstCenter,
                            end = firstCenter + (lastCenter - firstCenter) * winLineProgressRenderer.value,
                            strokeWidth = 5.5f * scale,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // TOPBAR PANEL: Player state + Series Scoreline + Turns (styled more compactly in transparent blue)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xCD1E3A8A)) // Premium transparent blue
                    .padding(bottom = 1.dp)
            ) {
                Row(
                     modifier = Modifier
                         .fillMaxWidth()
                         .statusBarsPadding()
                         .padding(horizontal = 12.dp, vertical = 1.dp),
                     horizontalArrangement = Arrangement.SpaceBetween,
                     verticalAlignment = Alignment.CenterVertically
                 ) {
                     // Left Side: You
                     Column(
                         horizontalAlignment = Alignment.Start,
                         verticalArrangement = Arrangement.spacedBy(1.dp)
                     ) {
                         Row(verticalAlignment = Alignment.CenterVertically) {
                             val resolvedMyName = if (localProfile.username.isBlank()) {
                                 com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.let { it.displayName ?: it.email?.substringBefore("@") } ?: "Player"
                             } else {
                                 localProfile.username
                             }
                             Text(
                                 text = resolvedMyName + " " + com.example.util.Localization.getCountryFlagAndName(localProfile.country, localProfile.language).first,
                                 fontSize = 11.sp,
                                 fontWeight = FontWeight.Bold,
                                 color = Color.White
                             )
                             Spacer(modifier = Modifier.width(4.dp))
                             Text(
                                 text = "(${if (activePlayerSymbol == BoardSymbol.X) "X" else "O"})",
                                 fontSize = 10.sp,
                                 fontWeight = FontWeight.Bold,
                                 color = if (activePlayerSymbol == BoardSymbol.X) PrimaryCyan else SecondaryOrange
                             )
                         }
                         val formattedLeft = String.format("%02d:%02d", playerTimerSeconds / 60, playerTimerSeconds % 60)
                         val localIsMyTurn = (currentTurn == activePlayerSymbol)
                         Text(
                             text = "⏱️ $formattedLeft",
                             fontSize = 15.sp,
                             fontWeight = FontWeight.Black,
                             color = if (localIsMyTurn) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.65f),
                             modifier = Modifier.padding(start = 1.dp)
                         )
                     }

                     // Center Side: Score
                     Row(
                         verticalAlignment = Alignment.CenterVertically,
                         horizontalArrangement = Arrangement.spacedBy(4.dp)
                     ) {
                         Text("$playerSeriesWins", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                         Text("vs", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                         Text("$oppSeriesWins", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                     }

                     // Right Side: Opponent name and ELO
                     Column(
                         horizontalAlignment = Alignment.End,
                         verticalArrangement = Arrangement.spacedBy(1.dp)
                     ) {
                         Row(verticalAlignment = Alignment.CenterVertically) {
                             Text(
                                 text = "$opponentName " + (if (opponentCountryState.isNotEmpty()) com.example.util.Localization.getCountryFlagAndName(opponentCountryState, localProfile.language).first + " " else "") + "(Elo: $opponentElo)",
                                 fontSize = 10.sp,
                                 fontWeight = FontWeight.Bold,
                                 color = Color.White
                             )
                             Spacer(modifier = Modifier.width(3.dp))
                             Text(
                                 text = "(${if (activePlayerSymbol.other() == BoardSymbol.X) "X" else "O"})",
                                 fontSize = 10.sp,
                                 fontWeight = FontWeight.Bold,
                                 color = if (activePlayerSymbol.other() == BoardSymbol.X) PrimaryCyan else SecondaryOrange
                             )
                         }
                         val formattedRight = String.format("%02d:%02d", opponentTimerSeconds / 60, opponentTimerSeconds % 60)
                         val opponentIsHisTurn = (currentTurn != activePlayerSymbol)
                         Text(
                             text = "⏱️ $formattedRight",
                             fontSize = 15.sp,
                             fontWeight = FontWeight.Black,
                             color = if (opponentIsHisTurn) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.65f),
                             modifier = Modifier.padding(end = 1.dp)
                         )
                     }
                 }

                 // Laser glow slider showing turns
                 AnimatedVisibility(
                     visible = !isComplete,
                     enter = fadeIn(),
                     exit = fadeOut()
                 ) {
                     Box(
                         modifier = Modifier
                             .fillMaxWidth()
                             .height(2.dp)
                             .background(Color.White.copy(alpha = 0.1f))
                     ) {
                         val turnColor = if (currentTurn == BoardSymbol.X) PrimaryCyan else SecondaryOrange
                         Box(
                             modifier = Modifier
                                 .fillMaxWidth(if (isMyTurn) 0.5f else 1.0f)
                                 .align(if (isMyTurn) Alignment.CenterStart else Alignment.CenterEnd)
                                 .fillMaxHeight()
                                 .background(turnColor)
                         )
                     }
                 }

                 // Alert notification ticker bar
                 Row(
                     modifier = Modifier
                         .fillMaxWidth()
                         .padding(horizontal = 10.dp, vertical = 1.dp),
                     horizontalArrangement = Arrangement.SpaceBetween,
                     verticalAlignment = Alignment.CenterVertically
                 ) {
                      Text(
                         text = if (isMyTurn) com.example.util.Localization.translate("👉 YOUR ACTION TURN", localProfile.language) else com.example.util.Localization.translate("⏱️ OPPONENT MEDITATING...", localProfile.language),
                         fontSize = 10.sp,
                         fontWeight = FontWeight.Bold,
                         color = if (isMyTurn) PrimaryCyan else Color.White.copy(alpha = 0.4f)
                     )
                 }
             }

            // FLOATING SIDE ACTIONS (Camera center Autofocus & quick navigation zoom)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = {
                        SoundSystem.playMenuClick()
                        showChatPanel = !showChatPanel
                    },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.85f), CircleShape)
                        .border(1.dp, if (showChatPanel) PrimaryCyan else Color.White.copy(alpha = 0.25f), CircleShape)
                        .size(46.dp)
                        .testTag("chat_panel_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Open Chat",
                        tint = if (showChatPanel) PrimaryCyan else Color.White
                    )
                }
                IconButton(
                    onClick = {
                        SoundSystem.playMenuClick()
                        targetPanOffset = Offset.Zero
                        scale = 1.0f
                    },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                        .border(1.dp, PrimaryCyan.copy(alpha = 0.4f), CircleShape)
                        .size(46.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Center board", tint = PrimaryCyan)
                }

                IconButton(
                    onClick = {
                        SoundSystem.playMenuClick()
                        scale = (scale + 0.25f).coerceAtMost(2.0f)
                    },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        .size(46.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color.White)
                }

                IconButton(
                    onClick = {
                        SoundSystem.playMenuClick()
                        scale = (scale - 0.25f).coerceAtLeast(0.5f)
                    },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        .size(46.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Zoom Out", tint = Color.White)
                }
            }

            // FOOTER SOCIAL PANEL (Emoji sender & Quick Escape Buttons)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        SoundSystem.playMenuClick()
                        showExitConfirmationDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Quit match icon",
                        tint = Color.Red,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = com.example.util.Localization.translate("Abandon Duel", localProfile.language),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // IN-GAME REAL-TIME CHAT SYSTEM (Firestore synchronized)
            AnimatedVisibility(
                visible = showChatPanel,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 50.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .border(1.dp, PrimaryCyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = CarbonSurface.copy(alpha = 0.94f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Title bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "💬 " + if (localProfile.language == "FA") "گفتگوی بازی" else "In-Game Chat",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryCyan
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Color.Green.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (localProfile.language == "FA") "فعال" else "Live",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Green
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    SoundSystem.playMenuClick()
                                    showChatPanel = false
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close chat", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                            }
                        }

                        // Message List & Auto-Scroll logic
                        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                        LaunchedEffect(activeChat.size) {
                            if (activeChat.isNotEmpty()) {
                                listState.animateScrollToItem(activeChat.size - 1)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.15f))
                        ) {
                            if (activeChat.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (localProfile.language == "FA") "هنوز پیامی فرستاده نشده است..." else "No messages yet. Greet your opponent!",
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(activeChat.size) { index ->
                                        val msg = activeChat[index]
                                        val isMe = msg.senderName == localProfile.username
                                        
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                                        ) {
                                            // Sender name
                                            Text(
                                                text = if (isMe) (if (localProfile.language == "FA") "شما" else "You") else msg.senderName,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isMe) PrimaryCyan else SecondaryOrange,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                            
                                            // Message Bubble
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        color = if (isMe) PrimaryCyan.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f),
                                                        shape = RoundedCornerShape(
                                                            topStart = 10.dp,
                                                            topEnd = 10.dp,
                                                            bottomStart = if (isMe) 10.dp else 0.dp,
                                                            bottomEnd = if (isMe) 0.dp else 10.dp
                                                        )
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isMe) PrimaryCyan.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f),
                                                        shape = RoundedCornerShape(
                                                            topStart = 10.dp,
                                                            topEnd = 10.dp,
                                                            bottomStart = if (isMe) 10.dp else 0.dp,
                                                            bottomEnd = if (isMe) 0.dp else 10.dp
                                                        )
                                                    )
                                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                                    .widthIn(max = 220.dp)
                                            ) {
                                                Text(
                                                    text = msg.content,
                                                    fontSize = 11.sp,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Divider line
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.12f))
                        )

                        // Input field and send button Row
                        var chatInputText by remember { mutableStateOf("") }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.3f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = chatInputText,
                                onValueChange = { chatInputText = it },
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(PrimaryCyan),
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                decorationBox = { innerTextField ->
                                    if (chatInputText.isEmpty()) {
                                        Text(
                                            text = if (localProfile.language == "FA") "پیامی بنویسید..." else "Write a message...",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.4f)
                                        )
                                    }
                                    innerTextField()
                                }
                            )

                            IconButton(
                                onClick = {
                                    if (chatInputText.isNotBlank()) {
                                        SoundSystem.playMenuClick()
                                        viewModel.sendGameChatText(chatInputText.trim())
                                        chatInputText = ""
                                    }
                                },
                                modifier = Modifier
                                    .background(PrimaryCyan, RoundedCornerShape(8.dp))
                                    .size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Send",
                                    tint = Color.Black,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // MATCH END SCREEN DIALOG VIEW OVERLAY (Fades in over board)
            if (showOverlayDelay) {
                val playerWon = if (gameMode == GameMode.PASS_PLAY) true else (winnerSymbol == activePlayerSymbol)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .background(CarbonSurface, RoundedCornerShape(16.dp))
                            .border(2.dp, if (playerWon) Color.Green else Color.Red, RoundedCornerShape(16.dp))
                            .padding(24.dp)
                    ) {
                        Icon(
                            imageVector = if (playerWon) Icons.Default.Star else Icons.Default.Warning,
                            contentDescription = "Triumph emoji",
                            tint = if (playerWon) Color.Yellow else Color.Red,
                            modifier = Modifier.size(72.dp)
                        )

                        val titleText = if (gameMode == GameMode.PASS_PLAY) {
                            if (localProfile.language == "FA") "پایان بازی!" else "MATCH COMPLETE!"
                        } else if (playerWon) {
                            if (localProfile.language == "FA") "پیروزی!" else "VICTORY!"
                        } else {
                            if (localProfile.language == "FA") "شکست!" else "DEFEAT!"
                        }

                        Text(
                            text = titleText,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = if (playerWon) Color.Green else Color.Red
                        )

                        val seriesComplete = playerSeriesWins >= (seriesMax / 2) + 1 || oppSeriesWins >= (seriesMax / 2) + 1
                        val subtitleText = if (gameMode == GameMode.PASS_PLAY) {
                            if (localProfile.language == "FA") {
                                "تبریک بازیکن محلی! برنده کل بازی: ${if (winnerSymbol != BoardSymbol.NONE) winnerSymbol.name else "تساوی"}"
                            } else {
                                "Congratulations, winner is: ${if (winnerSymbol != BoardSymbol.NONE) winnerSymbol.name else "DRAW"}"
                            }
                        } else if (seriesComplete) {
                            if (playerWon) {
                                if (localProfile.language == "FA") "شما برنده نهایی سری بازی‌ها شدید!" else "Series secured! You crushed the duel."
                            } else {
                                if (localProfile.language == "FA") "متاسفانه حریف برنده نهایی سری بازی‌ها شد." else "Series lost. Learn and redeploy your tactics!"
                            }
                        } else {
                            if (playerWon) {
                                if (localProfile.language == "FA") "شما برنده این راند شدید! راند بعدی در حال انتظار است." else "Winner of this round! Play dynamic setups!"
                            } else {
                                if (localProfile.language == "FA") "حریف این راند را برد! روی بازی بعدی تمرکز کنید." else "Opponent secured this round! Target the next setup."
                            }
                        }

                        Text(
                            text = subtitleText,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .background(CarbonSurfaceUp, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Your Wins", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                                Text("$playerSeriesWins", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Opp Wins", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                                Text("$oppSeriesWins", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        // Render ELO update tags if RANKED mode complete
                        if (gameMode == GameMode.RANKED && seriesComplete) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (playerWon) "📈 ELO INCREASE" else "📉 ELO DEMOTION",
                                    fontSize = 11.sp,
                                    color = if (playerWon) Color.Green else Color.Red,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (playerWon) "+24 ELO Record point" else "-18 ELO ranking loss",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Match series controls
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (!rematchAccepted) {
                                val buttonText = when {
                                    rematchRequested -> com.example.util.Localization.translate("REQUEST SENT...", localProfile.language)
                                    opponentRequestedRematch -> com.example.util.Localization.translate("ACCEPT REMATCH", localProfile.language)
                                    else -> com.example.util.Localization.translate("REQUEST REMATCH", localProfile.language)
                                }
                                val isButtonEnabled = !rematchRequested || opponentRequestedRematch

                                Button(
                                    onClick = {
                                        SoundSystem.playMenuClick()
                                        viewModel.requestRematch()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                                    enabled = isButtonEnabled
                                ) {
                                    Text(
                                        text = buttonText,
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (opponentRequestedRematch && !rematchAccepted && !rematchRequested) {
                                Text(
                                    text = com.example.util.Localization.translate("Opponent requested a rematch!", localProfile.language),
                                    color = PrimaryCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (rematchRequested && !rematchAccepted) {
                                Text(
                                    text = com.example.util.Localization.translate("Waiting for opponent response...", localProfile.language),
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Button(
                                onClick = {
                                    SoundSystem.playMenuClick()
                                    showExitConfirmationDialog = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = CarbonSurfaceUp),
                                border = BorderStroke(1.dp, NeonBorder)
                            ) {
                                Text("RETURN TO PORT", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
    }
}


