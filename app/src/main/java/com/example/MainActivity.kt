package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.model.AiDifficulty
import com.example.data.model.BoardSymbol
import com.example.data.model.GameMode
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.GameViewModel
import com.example.ui.viewmodel.GameViewModelFactory
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    private lateinit var viewModelTarget: GameViewModel

    override fun onStart() {
        super.onStart()
        if (::viewModelTarget.isInitialized) {
            viewModelTarget.setOnlinePresence(true)
        }
    }

    override fun onStop() {
        super.onStop()
        if (::viewModelTarget.isInitialized) {
            viewModelTarget.setOnlinePresence(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // First initialize centralized ViewModel with our Application singleton container
            val app = application as InfiniteApplication
            val factory = GameViewModelFactory(app, app.repository)
            val viewModel: GameViewModel = viewModel(factory = factory)
            viewModelTarget = viewModel
            
            val profile by viewModel.profile.collectAsState()
            val isDark = when (profile.selectedTheme) {
                "NIGHT" -> true
                "DAY" -> false
                "SKETCH" -> false // Sketch theme is light notebook paper
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(theme = profile.selectedTheme, darkTheme = isDark) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    InfiniteTicTacToeApp(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun InfiniteTicTacToeApp(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "login",
        modifier = modifier
    ) {
        // --- 0. AUTH LOGIN SCREEN ---
        composable("login") {
            LoginScreen(
                viewModel = viewModel,
                onNavigateToHome = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        // --- 1. LOBBY HOME SCREEN ---
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToGame = { mode, diff, seriesMax ->
                    val randomSym = if (kotlin.random.Random.nextBoolean()) "X" else "O"
                    if (mode == GameMode.VS_AI) {
                        navController.navigate("game/VS_AI/${diff?.name ?: "NORMAL"}/$seriesMax?playSym=$randomSym")
                    } else if (mode == GameMode.PASS_PLAY) {
                        navController.navigate("game/PASS_PLAY/NONE/$seriesMax?playSym=$randomSym")
                    } else {
                        // Ranked or Casual online dules triggers Matchmaking lobby first
                        navController.navigate("lobby/${mode.name}")
                    }
                },
                onNavigateToLeaderboard = { navController.navigate("leaderboard") },
                onNavigateToProfile = { navController.navigate("profile") },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToPrivateRoom = { navController.navigate("private_lobby") },
                onNavigateToSpectate = { navController.navigate("spectate_list") },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }

        // --- 2. MATCHMAKING WAIT LOBBY ---
        composable(
            route = "lobby/{mode}",
            arguments = listOf(navArgument("mode") { type = NavType.StringType })
        ) { backStackEntry ->
            val modeStr = backStackEntry.arguments?.getString("mode") ?: "CASUAL"
            val parsedMode = GameMode.valueOf(modeStr)

            LobbyScreen(
                viewModel = viewModel,
                mode = parsedMode,
                onBack = { navController.popBackStack() },
                onStartGame = { oppName, oppRank, oppElo, oppAvatar, playSym, seriesCount, gameId ->
                    // Route directly from search success to core gameplay layout
                    navController.navigate("game/${parsedMode.name}/NONE/$seriesCount?oppName=$oppName&oppRank=$oppRank&oppElo=$oppElo&oppAvatar=$oppAvatar&playSym=${playSym.name}&gameId=$gameId") {
                        // Pop matching lobby to clear back-stack history
                        popUpTo("home")
                    }
                }
            )
        }

        // --- 3. PRIVATE ROOM FRIEND LOBBY ---
        composable("private_lobby") {
            LobbyScreen(
                viewModel = viewModel,
                mode = GameMode.PRIVATE_ROOM,
                onBack = { navController.popBackStack() },
                onStartGame = { oppName, oppRank, oppElo, oppAvatar, playSym, seriesCount, gameId ->
                    navController.navigate("game/PRIVATE_ROOM/NONE/$seriesCount?oppName=$oppName&oppRank=$oppRank&oppElo=$oppElo&oppAvatar=$oppAvatar&playSym=${playSym.name}&gameId=$gameId") {
                        popUpTo("home")
                    }
                }
            )
        }

        // --- 4. THE GAMEPLAY BOARD SCREEN ---
        composable(
            route = "game/{mode}/{aiDiff}/{seriesMax}?oppName={oppName}&oppRank={oppRank}&oppElo={oppElo}&oppAvatar={oppAvatar}&playSym={playSym}&gameId={gameId}",
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("aiDiff") { type = NavType.StringType },
                navArgument("seriesMax") { type = NavType.IntType },
                navArgument("oppName") { type = NavType.StringType; defaultValue = "" },
                navArgument("oppRank") { type = NavType.StringType; defaultValue = "" },
                navArgument("oppElo") { type = NavType.IntType; defaultValue = 1000 },
                navArgument("oppAvatar") { type = NavType.IntType; defaultValue = 1 },
                navArgument("playSym") { type = NavType.StringType; defaultValue = "X" },
                navArgument("gameId") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val modeStr = backStackEntry.arguments?.getString("mode") ?: "VS_AI"
            val dStr = backStackEntry.arguments?.getString("aiDiff") ?: "NONE"
            val rounds = backStackEntry.arguments?.getInt("seriesMax") ?: 1

            val optOppName = backStackEntry.arguments?.getString("oppName") ?: ""
            val optOppRank = backStackEntry.arguments?.getString("oppRank") ?: ""
            val optOppElo = backStackEntry.arguments?.getInt("oppElo") ?: 1000
            val optOppAvatar = backStackEntry.arguments?.getInt("oppAvatar") ?: 1
            val optPlaySym = backStackEntry.arguments?.getString("playSym") ?: "X"
            val gameId = backStackEntry.arguments?.getString("gameId") ?: ""

            val mode = GameMode.valueOf(modeStr)
            val aiDifficulty = if (dStr == "NONE") null else AiDifficulty.valueOf(dStr)

            // Setup appropriate opponent metrics dynamically
            val opponentName = if (optOppName.isNotEmpty()) optOppName else when (mode) {
                GameMode.VS_AI -> "${aiDifficulty?.label ?: "Normal"} Bot AI"
                GameMode.PASS_PLAY -> "Local Player 2"
                GameMode.PRIVATE_ROOM -> "Private Friend"
                else -> "Gamer #${Random.nextInt(100, 999)}"
            }
            val opponentRank = if (optOppRank.isNotEmpty()) optOppRank else when (mode) {
                GameMode.VS_AI -> aiDifficulty?.label ?: "Normal"
                GameMode.PASS_PLAY -> "Local"
                else -> "Silver I"
            }
            val opponentElo = if (optOppName.isNotEmpty()) optOppElo else when (mode) {
                GameMode.VS_AI -> 1200 + (aiDifficulty?.ordinal ?: 1) * 150
                GameMode.PASS_PLAY -> 0
                else -> 1180
            }
            val opponentAvatarId = if (optOppName.isNotEmpty()) optOppAvatar else Random.nextInt(1, 8)
            val playerSymbol = BoardSymbol.valueOf(optPlaySym)

            GameScreen(
                viewModel = viewModel,
                opponentName = opponentName,
                opponentRank = opponentRank,
                opponentElo = opponentElo,
                opponentAvatarId = opponentAvatarId,
                playerSymbol = playerSymbol,
                gameMode = mode,
                aiDifficultyName = aiDifficulty?.name,
                seriesMax = rounds,
                gameId = gameId,
                onExit = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }

        // --- 5. THE LEADERBOARDS TAB ---
        composable("leaderboard") {
            LeaderboardScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // --- 6. PLAYER METRICS AND ACHIEVEMENTS ---
        composable("profile") {
            ProfileScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // --- 7. GAME CONFIGURATION AND FIREBASE COPIES ---
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // --- 8. PRO WATCH ARENA FEEDS LIST ---
        composable("spectate_list") {
            SpectateScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
