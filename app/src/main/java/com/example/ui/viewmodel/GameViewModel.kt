package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.InfiniteApplication
import com.example.data.local.FriendItem
import com.example.data.local.LocalAchievement
import com.example.data.local.LocalProfile
import com.example.data.local.MatchHistoryItem
import com.example.data.model.*
import com.example.data.repository.GameLogicEngine
import com.example.data.repository.GameRepository
import com.example.data.repository.MatchmakingState
import com.example.util.SoundSystem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.random.Random

class GameViewModel(
    application: Application,
    private val repository: GameRepository
) : AndroidViewModel(application) {

    // --- Firebase Auth ---
    private val auth by lazy { FirebaseAuth.getInstance() }
    private var isPerformingAuthSync = false

    private val _firebaseUser = MutableStateFlow<com.google.firebase.auth.FirebaseUser?>(FirebaseAuth.getInstance().currentUser)
    val firebaseUser: StateFlow<com.google.firebase.auth.FirebaseUser?> = _firebaseUser.asStateFlow()
    val isLoggedIn: StateFlow<Boolean> = _firebaseUser.map { it != null && !it.isAnonymous }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun generateRandomGuestName(): String {
        val prefixes = listOf("Gamer", "DozPlayer", "Nova", "Shadow", "Apex", "Grid", "Elite", "Phantom", "Rogue", "Swift", "Pixel", "Nexus", "Vector", "Cosmic", "Vortex")
        val randomWord = prefixes.random()
        val randomNumber = kotlin.random.Random.nextInt(1000, 9999)
        return "${randomWord}_$randomNumber"
    }

    fun signInAnonymously(onComplete: (Boolean, String?) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser != null && currentUser.isAnonymous) {
            _firebaseUser.value = currentUser
            val uniqueGuestName = generateRandomGuestName()
            viewModelScope.launch {
                val currentProfile = repository.getProfileFlow().first()
                val updatedProfile = currentProfile.copy(
                    uid = currentUser.uid,
                    username = uniqueGuestName,
                    avatarId = kotlin.random.Random.nextInt(1, 8),
                    elo = 1000
                )
                repository.updateProfile(updatedProfile)
                onComplete(true, null)
            }
            return
        }
        
        auth.signInAnonymously()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    _firebaseUser.value = user
                    val uniqueGuestName = generateRandomGuestName()
                    viewModelScope.launch {
                        val currentProfile = repository.getProfileFlow().first()
                        val updatedProfile = currentProfile.copy(
                            uid = user?.uid ?: "guest_${kotlin.random.Random.nextInt(100000)}",
                            username = uniqueGuestName,
                            avatarId = kotlin.random.Random.nextInt(1, 8),
                            elo = 1000
                        )
                        repository.updateProfile(updatedProfile)
                        onComplete(true, null)
                    }
                } else {
                    onComplete(false, task.exception?.localizedMessage ?: "Anonymous sign-in failed")
                }
            }
    }

    private fun completeSyncAndLoad(user: com.google.firebase.auth.FirebaseUser, success: Boolean, errorStr: String?, onResult: (Boolean, String?) -> Unit) {
        isPerformingAuthSync = false
        if (success) {
            loadUserDataFromFirestore(user.uid)
        }
        onResult(success, errorStr)
    }

    private fun syncUserAfterAuth(user: com.google.firebase.auth.FirebaseUser, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val currentProfile = repository.getProfileFlow().first()
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            
            val previousAnonUid = currentProfile.uid
            if (previousAnonUid.isNotEmpty() && previousAnonUid != user.uid && !previousAnonUid.startsWith("bot_")) {
                db.collection("users").document(previousAnonUid).delete()
                    .addOnSuccessListener {
                        android.util.Log.d("GameViewModel", "Successfully cleaned up old guest account document: $previousAnonUid")
                    }
            }

            val userRef = db.collection("users").document(user.uid)
            
            val username = user.displayName ?: user.email?.substringBefore("@") ?: "Player"
            val email = user.email ?: ""
            val photoUrl = user.photoUrl?.toString() ?: ""

            // Safely write the initial username and uid to the local DB immediately
            // to prevent empty profile name states in Composable collectAsState
            val initialLocalProfile = currentProfile.copy(
                uid = user.uid,
                username = if (currentProfile.username.isBlank()) username else currentProfile.username
            )
            repository.updateProfile(initialLocalProfile)

            userRef.get().addOnCompleteListener { docTask ->
                if (docTask.isSuccessful) {
                    val doc = docTask.result
                    if (doc != null && doc.exists()) {
                        // If user document already exists, smartly merge statistics (wins, losses, rating, level, xp)
                        // so that any local guest accomplishments are preserved and not lost.
                        val firebaseWins = (doc.getLong("wins") ?: 0L).toInt()
                        val firebaseLosses = (doc.getLong("losses") ?: 0L).toInt()
                        val firebaseRating = (doc.getLong("rating") ?: 1120L).toInt()
                        val firebaseLevel = (doc.getLong("level") ?: 1L).toInt()
                        val firebaseXp = (doc.getLong("xp") ?: 0L).toInt()
                        val firebaseCurrentStreak = (doc.getLong("currentStreak") ?: 0L).toInt()
                        val firebaseMaxStreak = (doc.getLong("maxStreak") ?: 0L).toInt()
                        val firebaseConsecutiveLogins = (doc.getLong("consecutiveLogins") ?: 1L).toInt()
                        val firebaseAvatarId = (doc.getLong("avatarId") ?: 3L).toInt()
                        val firebaseMedals = doc.get("medals") as? List<*>
                        val firebaseHistory = doc.get("history") as? List<*>
                        val rawFirebaseUsername = doc.getString("username") ?: ""
                        val firebaseUsername = if (rawFirebaseUsername.isBlank()) username else rawFirebaseUsername

                        val isLocalFresh = (currentProfile.wins == 0 && currentProfile.losses == 0 && currentProfile.xp == 0)

                        val mergedWins = if (isLocalFresh) firebaseWins else maxOf(currentProfile.wins, firebaseWins)
                        val mergedLosses = if (isLocalFresh) firebaseLosses else maxOf(currentProfile.losses, firebaseLosses)
                        val mergedElo = if (isLocalFresh) firebaseRating else (if (currentProfile.elo > 1000) maxOf(currentProfile.elo, firebaseRating) else firebaseRating)
                        val mergedLevel = if (isLocalFresh) firebaseLevel else maxOf(currentProfile.level, firebaseLevel)
                        val mergedXp = if (isLocalFresh) firebaseXp else maxOf(currentProfile.xp, firebaseXp)
                        val mergedCurrentStreak = if (isLocalFresh) firebaseCurrentStreak else maxOf(currentProfile.currentStreak, firebaseCurrentStreak)
                        val mergedMaxStreak = if (isLocalFresh) firebaseMaxStreak else maxOf(currentProfile.maxStreak, firebaseMaxStreak)
                        val mergedConsecutiveLogins = if (isLocalFresh) firebaseConsecutiveLogins else maxOf(currentProfile.consecutiveLogins, firebaseConsecutiveLogins)
                        val mergedAvatarId = if (isLocalFresh) firebaseAvatarId else currentProfile.avatarId

                        val updates = mapOf(
                            "username" to firebaseUsername,
                            "email" to email,
                            "photoUrl" to photoUrl,
                            "wins" to mergedWins,
                            "losses" to mergedLosses,
                            "rating" to mergedElo,
                            "level" to mergedLevel,
                            "xp" to mergedXp,
                            "currentStreak" to mergedCurrentStreak,
                            "maxStreak" to mergedMaxStreak,
                            "consecutiveLogins" to mergedConsecutiveLogins,
                            "avatarId" to mergedAvatarId,
                            "lastLogin" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )

                        userRef.update(updates).addOnCompleteListener { updateTask ->
                            viewModelScope.launch {
                                repository.saveFetchedMedalsAndHistory(firebaseMedals, firebaseHistory)
                                val updatedProfile = currentProfile.copy(
                                    uid = user.uid,
                                    username = firebaseUsername,
                                    wins = mergedWins,
                                    losses = mergedLosses,
                                    elo = mergedElo,
                                    level = mergedLevel,
                                    xp = mergedXp,
                                    currentStreak = mergedCurrentStreak,
                                    maxStreak = mergedMaxStreak,
                                    consecutiveLogins = mergedConsecutiveLogins,
                                    gamesPlayed = mergedWins + mergedLosses,
                                    avatarId = mergedAvatarId
                                )
                                repository.updateProfile(updatedProfile)
                                repository.syncUserDataToFirestore()
                                completeSyncAndLoad(user, true, null, onResult)
                            }
                        }
                    } else {
                        // If user document does not exist, create document with local guest statistics
                        viewModelScope.launch {
                            val localAchievements = repository.getAchievementsFlow().first()
                            val localHistory = repository.getMatchHistoryFlow().first()

                            val medalsList = localAchievements.map { ach ->
                                mapOf(
                                    "id" to ach.id,
                                    "title" to ach.title,
                                    "description" to ach.description,
                                    "maxProgress" to ach.maxProgress,
                                    "currentProgress" to ach.currentProgress,
                                    "isUnlocked" to ach.isUnlocked,
                                    "unlockedAt" to ach.unlockedAt
                                )
                            }

                            val historyList = localHistory.map { item ->
                                mapOf(
                                    "opponentName" to item.opponentName,
                                    "opponentRank" to item.opponentRank,
                                    "opponentElo" to item.opponentElo,
                                    "opponentAvatarId" to item.opponentAvatarId,
                                    "result" to item.result,
                                    "gameMode" to item.gameMode,
                                    "movesCount" to item.movesCount,
                                    "eloChange" to item.eloChange,
                                    "durationSeconds" to item.durationSeconds,
                                    "timestamp" to item.timestamp
                                )
                            }

                            val newData = mapOf(
                                "uid" to user.uid,
                                "username" to username,
                                "email" to email,
                                "photoUrl" to photoUrl,
                                "wins" to currentProfile.wins,
                                "losses" to currentProfile.losses,
                                "rating" to currentProfile.elo,
                                "level" to currentProfile.level,
                                "xp" to currentProfile.xp,
                                "currentStreak" to currentProfile.currentStreak,
                                "maxStreak" to currentProfile.maxStreak,
                                "consecutiveLogins" to currentProfile.consecutiveLogins,
                                "avatarId" to currentProfile.avatarId,
                                "medals" to medalsList,
                                "history" to historyList,
                                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                "lastLogin" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            )

                            userRef.set(newData).addOnCompleteListener { setTask ->
                                viewModelScope.launch {
                                    val updatedProfile = currentProfile.copy(
                                        uid = user.uid,
                                        username = username,
                                        wins = currentProfile.wins,
                                        losses = currentProfile.losses,
                                        elo = currentProfile.elo,
                                        level = currentProfile.level,
                                        xp = currentProfile.xp,
                                        gamesPlayed = currentProfile.wins + currentProfile.losses,
                                        avatarId = currentProfile.avatarId
                                    )
                                    repository.updateProfile(updatedProfile)
                                    completeSyncAndLoad(user, true, null, onResult)
                                }
                            }
                        }
                    }
                } else {
                    // If Firestore check fails, allow local login to proceed anyway
                    completeSyncAndLoad(user, true, null, onResult)
                }
            }
        }
    }

    fun signInWithGoogle(idToken: String, onResult: (Boolean, String?) -> Unit) {
        isPerformingAuthSync = true
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    _firebaseUser.value = user
                    if (user != null) {
                        syncUserAfterAuth(user, onResult)
                    } else {
                        isPerformingAuthSync = false
                        onResult(true, null)
                    }
                } else {
                    isPerformingAuthSync = false
                    onResult(false, task.exception?.localizedMessage ?: "Sign-in failed")
                }
            }
    }

    fun signInWithEmail(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        isPerformingAuthSync = true
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    _firebaseUser.value = user
                    if (user != null) {
                        syncUserAfterAuth(user, onResult)
                    } else {
                        isPerformingAuthSync = false
                        onResult(true, null)
                    }
                } else {
                    isPerformingAuthSync = false
                    onResult(false, task.exception?.localizedMessage ?: "Sign-in failed")
                }
            }
    }

    fun signUpWithEmail(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        isPerformingAuthSync = true
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    _firebaseUser.value = user
                    if (user != null) {
                        syncUserAfterAuth(user, onResult)
                    } else {
                        isPerformingAuthSync = false
                        onResult(true, null)
                    }
                } else {
                    isPerformingAuthSync = false
                    onResult(false, task.exception?.localizedMessage ?: "Registration failed")
                }
            }
    }

    fun sendPasswordResetEmail(email: String, onResult: (Boolean, String?) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.localizedMessage ?: "Password reset failed")
                }
            }
    }

    fun signOutUser(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.clearAllUserData()
            
            try {
                val context = getApplication<android.app.Application>()
                val client = com.example.util.GoogleSignInHelper.getGoogleSignInClient(context)
                client.signOut()
            } catch (e: Exception) {
                // Ignore if not configured yet
            }
            
            auth.signOut()
            _firebaseUser.value = null
            onComplete()
        }
    }

    // --- Profile & Social ---
    val profile: StateFlow<LocalProfile> = repository.getProfileFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalProfile())

    val matchHistory: StateFlow<List<MatchHistoryItem>> = repository.getMatchHistoryFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val friends: StateFlow<List<FriendItem>> = repository.getFriendsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val achievements: StateFlow<List<LocalAchievement>> = repository.getAchievementsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val onlineCounters: StateFlow<OnlineCounters> = repository.onlineCounters
    private val _matchmakingState = MutableStateFlow<MatchmakingState>(MatchmakingState.Idle)
    val matchmakingState: StateFlow<MatchmakingState> = _matchmakingState.asStateFlow()

    var currentActiveGameId: String? = null
    private var activeGameListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var activeMovesListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var activeGroupChatListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var matchmakingTimerJob: Job? = null
    private var matchmakingFirestoreListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var presenceListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private var currentUserSnapshotListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var lastProcessedStatus: String? = null
    private var onlineMatchRecordedInRoom = false
    val globalChatMessages: StateFlow<List<ChatMessage>> = repository.globalChatMessages
    val spectatorMatches: StateFlow<List<SpectatorMatch>> = repository.spectatorMatches
    val systemAlerts: SharedFlow<String> = repository.systemAlerts

    // --- Active Game Session State ---
    private val _boardState = MutableStateFlow<Map<CellCoord, BoardSymbol>>(emptyMap())
    val boardState: StateFlow<Map<CellCoord, BoardSymbol>> = _boardState.asStateFlow()

    private val _boardBounds = MutableStateFlow(BoardBounds(0, 9, 0, 9))
    val boardBounds: StateFlow<BoardBounds> = _boardBounds.asStateFlow()

    private val _currentTurnSymbol = MutableStateFlow(BoardSymbol.X)
    val currentTurnSymbol: StateFlow<BoardSymbol> = _currentTurnSymbol.asStateFlow()

    // Series/Rematch stats (e.g. Best of 3, 5, 7)
    var seriesMaxGames = 1 // default single match
    private val _playerSeriesWins = MutableStateFlow(0)
    val playerSeriesWins: StateFlow<Int> = _playerSeriesWins.asStateFlow()

    private val _opponentSeriesWins = MutableStateFlow(0)
    val opponentSeriesWins: StateFlow<Int> = _opponentSeriesWins.asStateFlow()

    // Active session profile details
    private val _activeOpponent = MutableStateFlow<OpponentInfo?>(null)
    val activeOpponent: StateFlow<OpponentInfo?> = _activeOpponent.asStateFlow()

    private val _winningLine = MutableStateFlow<List<CellCoord>?>(null)
    val winningLine: StateFlow<List<CellCoord>?> = _winningLine.asStateFlow()

    private val _gameComplete = MutableStateFlow(false)
    val gameComplete: StateFlow<Boolean> = _gameComplete.asStateFlow()

    private val _winnerSymbol = MutableStateFlow<BoardSymbol>(BoardSymbol.NONE)
    val winnerSymbol: StateFlow<BoardSymbol> = _winnerSymbol.asStateFlow()

    private val _opponentLastMove = MutableStateFlow<CellCoord?>(null)
    val opponentLastMove: StateFlow<CellCoord?> = _opponentLastMove.asStateFlow()

    private val _isMyTurn = MutableStateFlow(true)
    val isMyTurn: StateFlow<Boolean> = _isMyTurn.asStateFlow()

    private val _playerSymbol = MutableStateFlow(BoardSymbol.X)
    val playerSymbol: StateFlow<BoardSymbol> = _playerSymbol.asStateFlow()

    private val _activeMode = MutableStateFlow(GameMode.VS_AI)
    val activeMode: StateFlow<GameMode> = _activeMode.asStateFlow()

    private val _opponentCountry = MutableStateFlow("US")
    val opponentCountry: StateFlow<String> = _opponentCountry.asStateFlow()

    // Live Game Chat (Taunts, Emojis)
    private val _activeGameChat = MutableStateFlow<List<ChatMessage>>(emptyList())
    val activeGameChat: StateFlow<List<ChatMessage>> = _activeGameChat.asStateFlow()

    private val _rematchRequested = MutableStateFlow(false)
    val rematchRequested: StateFlow<Boolean> = _rematchRequested.asStateFlow()

    private val _opponentRequestedRematch = MutableStateFlow(false)
    val opponentRequestedRematch: StateFlow<Boolean> = _opponentRequestedRematch.asStateFlow()

    private val _rematchAcceptedByOpponent = MutableStateFlow(false)
    val rematchAcceptedByOpponent: StateFlow<Boolean> = _rematchAcceptedByOpponent.asStateFlow()

    private val _leaderboardState = MutableStateFlow<LeaderboardUiState>(LeaderboardUiState.Loading)
    val leaderboardState: StateFlow<LeaderboardUiState> = _leaderboardState.asStateFlow()

    private var leaderboardListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private var authStateListener: com.google.firebase.auth.FirebaseAuth.AuthStateListener? = null

    private var activeMatchTimeStart = 0L
    private var movesPlayedCount = 0
    private var opponentJob: Job? = null
    private var botJob: Job? = null

    private val _playerTimerSeconds = MutableStateFlow(600)
    val playerTimerSeconds: StateFlow<Int> = _playerTimerSeconds.asStateFlow()

    private val _opponentTimerSeconds = MutableStateFlow(600)
    val opponentTimerSeconds: StateFlow<Int> = _opponentTimerSeconds.asStateFlow()

    private var timerJob: Job? = null

    private fun startChessClock() {
        timerJob?.cancel()
        _playerTimerSeconds.value = 600
        _opponentTimerSeconds.value = 600
        timerJob = viewModelScope.launch(Dispatchers.Default) {
            while (!_gameComplete.value) {
                delay(1000L)
                if (_gameComplete.value) break
                
                if (_currentTurnSymbol.value == _playerSymbol.value) {
                    val currentVal = _playerTimerSeconds.value
                    if (currentVal > 0) {
                        _playerTimerSeconds.value = currentVal - 1
                        if (_playerTimerSeconds.value <= 0) {
                            withContext(Dispatchers.Main) {
                                handleWinner(_playerSymbol.value.other(), null)
                            }
                            break
                        }
                    }
                } else {
                    val currentVal = _opponentTimerSeconds.value
                    if (currentVal > 0) {
                        _opponentTimerSeconds.value = currentVal - 1
                        if (_opponentTimerSeconds.value <= 0) {
                            withContext(Dispatchers.Main) {
                                handleWinner(_playerSymbol.value, null)
                            }
                            break
                        }
                    }
                }
            }
        }
    }

    fun initializeBotsInFirestore() {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        db.collection("users")
            .whereEqualTo("isBot", true)
            .limit(5)
            .get()
            .addOnSuccessListener { snapshots ->
                if (snapshots == null || snapshots.isEmpty) {
                    val fallbackBots = getFallbackBotsLeaderboard()
                    val batch = db.batch()
                    for (bot in fallbackBots.take(20)) {
                        val botUid = "bot_${bot.username}"
                        val botRef = db.collection("users").document(botUid)
                        val botData = mapOf(
                            "uid" to botUid,
                            "username" to bot.username,
                            "rating" to bot.elo,
                            "avatarId" to bot.avatarId,
                            "wins" to bot.wins,
                            "losses" to bot.losses,
                            "isBot" to true,
                            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                        batch.set(botRef, botData, com.google.firebase.firestore.SetOptions.merge())
                    }
                    batch.commit().addOnFailureListener {
                        android.util.Log.e("GameViewModel", "Failed to batch merge fallback bots: ${it.message}")
                    }
                }
            }
    }

    init {
        detectAndSaveCountryFromIp()
        authStateListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _firebaseUser.value = user
            if (user != null) {
                if (!isPerformingAuthSync) {
                    loadUserDataFromFirestore(user.uid)
                }
                initializeBotsInFirestore()
                startLeaderboardListener()
                detectAndSaveCountryFromIp()
            } else {
                currentUserSnapshotListener?.remove()
                currentUserSnapshotListener = null
                startLeaderboardListener()
            }
        }
        auth.addAuthStateListener(authStateListener!!)

        // Sync local matchmaking state
        viewModelScope.launch {
            repository.matchmakingState.collect { state ->
                if (state !is MatchmakingState.Idle) {
                    _matchmakingState.value = state
                }
            }
        }

        // Sync sound preferences to SoundSystem and sync statistics changes to Firestore when profile updates
        viewModelScope.launch {
            var lastSyncedProfile: LocalProfile? = null
            profile.collect { p ->
                SoundSystem.setPreferences(p.soundEnabled, p.volume)
                
                if (isPerformingAuthSync) return@collect
                
                val currentUser = auth.currentUser
                if (currentUser != null && p.uid == currentUser.uid && p.username.isNotBlank()) {
                    // Check if statistically relevant data actually changed to optimize Firestore updates
                    if (lastSyncedProfile == null ||
                        lastSyncedProfile?.username != p.username ||
                        lastSyncedProfile?.wins != p.wins ||
                        lastSyncedProfile?.losses != p.losses ||
                        lastSyncedProfile?.elo != p.elo ||
                        lastSyncedProfile?.level != p.level ||
                        lastSyncedProfile?.xp != p.xp ||
                        lastSyncedProfile?.country != p.country ||
                        lastSyncedProfile?.currentStreak != p.currentStreak ||
                        lastSyncedProfile?.maxStreak != p.maxStreak ||
                        lastSyncedProfile?.consecutiveLogins != p.consecutiveLogins ||
                        lastSyncedProfile?.avatarId != p.avatarId
                    ) {
                        lastSyncedProfile = p
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        val userRef = db.collection("users").document(currentUser.uid)
                        
                        val updates = mapOf(
                            "uid" to currentUser.uid,
                            "username" to p.username,
                            "wins" to p.wins,
                            "losses" to p.losses,
                            "rating" to p.elo,
                            "level" to p.level,
                            "xp" to p.xp,
                            "country" to p.country,
                            "currentStreak" to p.currentStreak,
                            "maxStreak" to p.maxStreak,
                            "consecutiveLogins" to p.consecutiveLogins,
                            "avatarId" to p.avatarId,
                            "lastLogin" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                        // This updates user stats dynamically in Firestore via merge to safely handle new profiles
                        userRef.set(updates, com.google.firebase.firestore.SetOptions.merge())
                        // Perform thorough sync for achievements/history when stats shift
                        repository.syncUserDataToFirestore()
                    }
                }
            }
        }
    }

    fun loadUserDataFromFirestore(uid: String) {
        currentUserSnapshotListener?.remove()
        currentUserSnapshotListener = null
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(uid)
        
        userRef.get().addOnSuccessListener { document ->
            if (document != null && document.exists()) {
                val wins = (document.getLong("wins") ?: 0L).toInt()
                val losses = (document.getLong("losses") ?: 0L).toInt()
                val rating = (document.getLong("rating") ?: 1120L).toInt()
                val level = (document.getLong("level") ?: 1L).toInt()
                val xp = (document.getLong("xp") ?: 0L).toInt()
                val currentStreak = (document.getLong("currentStreak") ?: 0L).toInt()
                val maxStreak = (document.getLong("maxStreak") ?: 0L).toInt()
                val consecutiveLogins = (document.getLong("consecutiveLogins") ?: 1L).toInt()
                val avatarId = (document.getLong("avatarId") ?: 3L).toInt()
                val medalsList = document.get("medals") as? List<*>
                val historyList = document.get("history") as? List<*>
                
                val rawUsername = document.getString("username") ?: ""
                val defaultName = auth.currentUser?.displayName ?: auth.currentUser?.email?.substringBefore("@") ?: "Player"
                val username = if (rawUsername.isBlank()) defaultName else rawUsername
                val country = document.getString("country") ?: "US"
                
                viewModelScope.launch {
                    val currentProfile = repository.getProfileFlow().first()
                    val updatedProfile = currentProfile.copy(
                        uid = uid,
                        username = username,
                        wins = wins,
                        losses = losses,
                        elo = rating,
                        level = level,
                        xp = xp,
                        country = country,
                        gamesPlayed = wins + losses,
                        currentStreak = maxOf(currentProfile.currentStreak, currentStreak),
                        maxStreak = maxOf(currentProfile.maxStreak, maxStreak),
                        consecutiveLogins = maxOf(currentProfile.consecutiveLogins, consecutiveLogins),
                        avatarId = avatarId
                    )
                    if (updatedProfile != currentProfile) {
                        repository.updateProfile(updatedProfile)
                    }
                    repository.saveFetchedMedalsAndHistory(medalsList, historyList)
                    startLeaderboardListener()
                }
            } else {
                // If the user document does not exist in Firestore, update the local profile with the current uid
                // so that it gets synced/created on subsequent profile collects!
                viewModelScope.launch {
                    val currentProfile = repository.getProfileFlow().first()
                    val defaultName = auth.currentUser?.displayName ?: auth.currentUser?.email?.substringBefore("@") ?: "Player"
                    val resolvedUsername = if (currentProfile.username.isBlank()) defaultName else currentProfile.username
                    val updatedProfile = currentProfile.copy(
                        uid = uid,
                        username = resolvedUsername
                    )
                    if (updatedProfile != currentProfile) {
                        repository.updateProfile(updatedProfile)
                    }
                    startLeaderboardListener()
                }
            }
        }.addOnFailureListener { error ->
            android.util.Log.e("GameViewModel", "Error loading user data from Firestore", error)
        }
        
        startPresenceListener()
        setOnlinePresence(true)
        startLeaderboardListener()
    }

    fun setOnlinePresence(online: Boolean) {
        val currentUser = auth.currentUser ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val presenceRef = db.collection("presence").document(currentUser.uid)
        val data = mapOf(
            "uid" to currentUser.uid,
            "username" to (profile.value.username.ifBlank { "Player" }),
            "online" to online,
            "lastSeen" to System.currentTimeMillis(),
            "isBot" to false
        )
        presenceRef.set(data, com.google.firebase.firestore.SetOptions.merge())
    }

    fun startPresenceListener() {
        presenceListenerRegistration?.remove()
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        presenceListenerRegistration = db.collection("presence")
            .whereEqualTo("online", true)
            .whereEqualTo("isBot", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val count = snapshot?.size() ?: 1
                repository.updateOnlineCounters(
                    players = count,
                    active = repository.onlineCounters.value.activeMatches,
                    searching = repository.onlineCounters.value.searchingCount
                )
            }
    }

    fun startOnlineMatchmaking(mode: GameMode) {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val myUid = auth.currentUser?.uid ?: return
        val myName = profile.value.username.ifBlank { "Player" }
        val myRating = profile.value.elo
        val myAvatar = profile.value.avatarId

        _matchmakingState.value = MatchmakingState.Searching(0)

        matchmakingTimerJob?.cancel()
        matchmakingFirestoreListener?.remove()

        viewModelScope.launch {
            // STEP 1: Search for a REAL waiting player (isBot == false)
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    com.google.android.gms.tasks.Tasks.await(
                        db.collection("matchmaking")
                            .whereEqualTo("status", "waiting")
                            .whereEqualTo("isBot", false)
                            .get()
                    )
                }

                // Filter out self
                val opponentDoc = snapshot.documents.firstOrNull { it.id != myUid }
                
                if (opponentDoc != null) {
                    val oppUid = opponentDoc.id
                    val oppName = opponentDoc.getString("username") ?: "Player"
                    val oppRating = (opponentDoc.getLong("rating") ?: 1000L).toInt()
                    val oppAvatar = (opponentDoc.getLong("avatarId") ?: 1L).toInt()
                    val oppRankStr = RankSystem.getRankDetails(oppRating).name

                    // Create Room
                    val gameId = "${oppUid}_${myUid}_${System.currentTimeMillis()}"
                    currentActiveGameId = gameId

                    val gameData = mapOf(
                        "gameId" to gameId,
                        "player1Id" to oppUid,
                        "player1Name" to oppName,
                        "player2Id" to myUid,
                        "player2Name" to myName,
                        "player1Symbol" to "X",
                        "player2Symbol" to "O",
                        "currentTurn" to "X",
                        "status" to "active",
                        "winnerSymbol" to "NONE",
                        "isBotGame" to false,
                        "boardState" to emptyMap<String, String>(),
                        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                    
                    withContext(Dispatchers.IO) {
                        com.google.android.gms.tasks.Tasks.await(db.collection("games").document(gameId).set(gameData))
                    }

                    // Match opponent
                    val matchData = mapOf(
                        "status" to "matched",
                        "gameId" to gameId,
                        "opponentUid" to myUid,
                        "opponentName" to myName,
                        "opponentRating" to myRating,
                        "opponentAvatar" to myAvatar,
                        "playerSymbol" to "X",
                        "oppSymbol" to "O"
                    )
                    db.collection("matchmaking").document(oppUid).update(matchData)

                    _matchmakingState.value = MatchmakingState.Success(
                        opponentName = oppName,
                        opponentRank = oppRankStr,
                        opponentElo = oppRating,
                        opponentAvatarId = oppAvatar,
                        playerSymbol = BoardSymbol.O,
                        gameMode = mode,
                        gameId = gameId
                    )
                    return@launch
                }
            } catch (e: Exception) {
                android.util.Log.e("GameViewModel", "Online matchmaking lookup failed: ${e.message}")
            }

            // Write my wait document
            val myMatchmakingRef = db.collection("matchmaking").document(myUid)
            val waitingData = mapOf(
                "uid" to myUid,
                "username" to myName,
                "rating" to myRating,
                "avatarId" to myAvatar,
                "status" to "waiting",
                "isBot" to false,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            myMatchmakingRef.set(waitingData)

            var seconds = 0
            
            matchmakingFirestoreListener = myMatchmakingRef.addSnapshotListener { dModel, err ->
                if (err != null || dModel == null || !dModel.exists()) return@addSnapshotListener
                
                val status = dModel.getString("status") ?: "waiting"
                if (status == "matched") {
                    val gameId = dModel.getString("gameId") ?: ""
                    currentActiveGameId = gameId
                    val oppName = dModel.getString("opponentName") ?: "Player"
                    val oppRating = (dModel.getLong("opponentRating") ?: 1000L).toInt()
                    val oppRankStr = RankSystem.getRankDetails(oppRating).name
                    val oppAvatar = (dModel.getLong("opponentAvatar") ?: 1L).toInt()
                    
                    matchmakingTimerJob?.cancel()
                    _matchmakingState.value = MatchmakingState.Success(
                        opponentName = oppName,
                        opponentRank = oppRankStr,
                        opponentElo = oppRating,
                        opponentAvatarId = oppAvatar,
                        playerSymbol = BoardSymbol.X,
                        gameMode = mode,
                        gameId = gameId
                    )
                    myMatchmakingRef.delete()
                }
            }

            matchmakingTimerJob = viewModelScope.launch {
                while (seconds < 10) {
                    delay(1000)
                    seconds++
                    _matchmakingState.value = MatchmakingState.Searching(seconds)
                }

                // STEP 2: Pick an existing bot (isBot == true)
                matchmakingFirestoreListener?.remove()
                myMatchmakingRef.delete()

                try {
                    val botDocs = try {
                        withContext(Dispatchers.IO) {
                            com.google.android.gms.tasks.Tasks.await(
                                db.collection("users")
                                    .whereEqualTo("isBot", true)
                                    .get()
                            ).documents
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("GameViewModel", "Failed to load bots from Firestore: ${e.message}")
                        emptyList()
                    }

                    val chosenBotUid: String
                    val chosenBotName: String
                    val chosenBotRating: Int
                    val chosenBotAvatar: Int

                    if (botDocs.isNotEmpty()) {
                        val myRating = profile.value.elo
                        val sortedByEloBotDocs = botDocs.sortedBy { doc ->
                            val botRating = (doc.getLong("rating") ?: 1000L).toInt()
                            Math.abs(botRating - myRating)
                        }
                        val chosenBotDoc = sortedByEloBotDocs.firstOrNull() ?: botDocs.random()

                        chosenBotUid = chosenBotDoc.id
                        chosenBotName = chosenBotDoc.getString("username") ?: "Bot"
                        chosenBotRating = (chosenBotDoc.getLong("rating") ?: 1000L).toInt()
                        chosenBotAvatar = (chosenBotDoc.getLong("avatarId") ?: 1L).toInt()
                    } else {
                        // Dynamic fallback to memory bots to prevent counter going to 0 / Idle state
                        val fallbackBots = getFallbackBotsLeaderboard()
                        val myRating = profile.value.elo
                        val chosenBot = fallbackBots.minByOrNull { Math.abs(it.elo - myRating) } ?: fallbackBots.random()
                        
                        chosenBotUid = "bot_${chosenBot.username}"
                        chosenBotName = chosenBot.username
                        chosenBotRating = chosenBot.elo
                        chosenBotAvatar = chosenBot.avatarId
                    }

                    val botRankStr = RankSystem.getRankDetails(chosenBotRating).name
                    val gameId = "bot_${chosenBotUid}_${myUid}_${System.currentTimeMillis()}"
                    currentActiveGameId = gameId

                    val gameData = mapOf(
                        "gameId" to gameId,
                        "player1Id" to myUid,
                        "player2Id" to chosenBotUid,
                        "player1Name" to myName,
                        "player2Name" to chosenBotName,
                        "player1Symbol" to "X",
                        "player2Symbol" to "O",
                        "currentTurn" to "X",
                        "status" to "active",
                        "winnerSymbol" to "NONE",
                        "isBotGame" to true,
                        "botUid" to chosenBotUid,
                        "boardState" to emptyMap<String, String>(),
                        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                    
                    try {
                        withContext(Dispatchers.IO) {
                            com.google.android.gms.tasks.Tasks.await(db.collection("games").document(gameId).set(gameData))
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("GameViewModel", "Failed to write bot game doc to Firestore: ${e.message}")
                    }

                    _matchmakingState.value = MatchmakingState.Success(
                        opponentName = chosenBotName,
                        opponentRank = botRankStr,
                        opponentElo = chosenBotRating,
                        opponentAvatarId = chosenBotAvatar,
                        playerSymbol = BoardSymbol.X,
                        gameMode = mode,
                        gameId = gameId
                    )
                } catch (e: Exception) {
                    android.util.Log.e("GameViewModel", "Failed to select bot matchmaking: ${e.message}")
                    _matchmakingState.value = MatchmakingState.Idle
                }
            }
        }
    }

    fun startActiveGameSync(gameId: String) {
        activeGameListener?.remove()
        activeMovesListener?.remove()
        activeGroupChatListener?.remove()

        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        lastProcessedStatus = null
        onlineMatchRecordedInRoom = false

        // Listen to room chat messages subcollection in real time
        activeGroupChatListener = db.collection("rooms").document(gameId).collection("messages")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .addSnapshotListener { chatSnapshot, chatErr ->
                if (chatErr != null) {
                    android.util.Log.e("GameViewModel", "Real-time game room chat list fetch failure", chatErr)
                    return@addSnapshotListener
                }
                if (chatSnapshot != null) {
                    val msgs = mutableListOf<ChatMessage>()
                    for (doc in chatSnapshot.documents) {
                        val senderId = doc.getString("senderId") ?: ""
                        val senderName = doc.getString("senderName") ?: "Player"
                        val text = doc.getString("text") ?: ""
                        val timestampVal = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
                        
                        msgs.add(
                            ChatMessage(
                                id = doc.id,
                                senderName = senderName,
                                senderRank = "",
                                senderElo = 0,
                                senderAvatarId = 1,
                                content = text,
                                timestamp = timestampVal
                            )
                        )
                    }
                    _activeGameChat.value = msgs
                }
            }

        activeGameListener = db.collection("games").document(gameId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                
                val myUid = auth.currentUser?.uid ?: ""
                val player1Id = snapshot.getString("player1Id") ?: ""
                val player2Id = snapshot.getString("player2Id") ?: ""
                val oppId = if (myUid == player1Id) player2Id else player1Id
                if (oppId.isNotEmpty() && !oppId.startsWith("bot_")) {
                    db.collection("users").document(oppId).get()
                        .addOnSuccessListener { userDoc ->
                            if (userDoc != null && userDoc.exists()) {
                                val countryOfOpp = userDoc.getString("country") ?: "US"
                                _opponentCountry.value = countryOfOpp
                            }
                        }
                }

                val status = snapshot.getString("status") ?: "active"
                val wasComplete = _gameComplete.value
                if (wasComplete && status == "active") {
                    onOnlineRematchTriggered()
                    return@addSnapshotListener
                }
                
                val currentTurnStr = snapshot.getString("currentTurn") ?: "X"
                val currentTurn = try {
                    BoardSymbol.valueOf(currentTurnStr)
                } catch (e: Exception) {
                    BoardSymbol.NONE
                }
                _currentTurnSymbol.value = currentTurn
                if (status == "active") {
                    _isMyTurn.value = (currentTurn == _playerSymbol.value)
                } else {
                    _isMyTurn.value = false
                }

                if (status == "completed" || status == "surrendered") {
                    _gameComplete.value = true
                    val rematchRequestedBy = snapshot.getString("rematchRequestedBy") ?: ""
                    val myUid = auth.currentUser?.uid ?: ""
                    _opponentRequestedRematch.value = rematchRequestedBy.isNotEmpty() && rematchRequestedBy != myUid

                    val winnerId = snapshot.getString("winnerId") ?: ""
                    val winnerSymbolField = snapshot.getString("winnerSymbol") ?: "NONE"
                    val ws = try {
                        BoardSymbol.valueOf(winnerSymbolField)
                    } catch (e: Exception) {
                        BoardSymbol.NONE
                    }
                    val isMyWin = if (ws != BoardSymbol.NONE) {
                        ws == _playerSymbol.value
                    } else {
                        (winnerId == auth.currentUser?.uid)
                    }
                    _winnerSymbol.value = if (ws != BoardSymbol.NONE) ws else (if (isMyWin) _playerSymbol.value else _playerSymbol.value.other())
                    
                    val wl = mutableListOf<CellCoord>()
                    val wlField = snapshot.get("winningLine") as? List<*>
                    if (wlField != null) {
                        for (item in wlField) {
                            val map = item as? Map<*, *>
                            if (map != null) {
                                val r = (map["row"] as? Long ?: map["r"] as? Long)?.toInt()
                                val c = (map["col"] as? Long ?: map["c"] as? Long)?.toInt()
                                if (r != null && c != null) {
                                    wl.add(CellCoord(r, c))
                                }
                            }
                        }
                    }
                    _winningLine.value = wl
                    
                    if (status != lastProcessedStatus) {
                        lastProcessedStatus = status
                        if (isMyWin) SoundSystem.playVictory() else SoundSystem.playDefeat()
                        if (isMyWin) {
                            _playerSeriesWins.value = 1
                        } else {
                            _opponentSeriesWins.value = 1
                        }
                    }
                    
                    if (!onlineMatchRecordedInRoom) {
                        onlineMatchRecordedInRoom = true
                        viewModelScope.launch {
                            val opp = _activeOpponent.value ?: OpponentInfo("Player", "Silver I", 1000, 1)
                            repository.recordMatch(
                                opponentName = opp.name,
                                opponentRank = opp.rank,
                                opponentElo = opp.elo,
                                opponentAvatarId = opp.avatarId,
                                win = isMyWin,
                                mode = _activeMode.value,
                                movesCount = movesPlayedCount,
                                durationSec = ((System.currentTimeMillis() - activeMatchTimeStart) / 1000).toInt()
                            )
                            // Reward match completion XP
                            val xpReward = if (isMyWin) 100 else 40
                            repository.addXp(xpReward)
                        }
                    }
                } else {
                    _gameComplete.value = false
                    _winnerSymbol.value = BoardSymbol.NONE
                    _winningLine.value = null
                }
            }

        activeMovesListener = db.collection("games").document(gameId).collection("moves")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                
                val oldBoard = _boardState.value
                val newBoardMap = mutableMapOf<CellCoord, BoardSymbol>()
                var movesCount = 0
                for (doc in snapshot.documents) {
                    val row = doc.getLong("row")?.toInt() ?: continue
                    val col = doc.getLong("col")?.toInt() ?: continue
                    val symbolStr = doc.getString("symbol") ?: "NONE"
                    val symbol = try {
                        BoardSymbol.valueOf(symbolStr)
                    } catch (e: Exception) {
                        BoardSymbol.NONE
                    }
                    newBoardMap[CellCoord(row, col)] = symbol
                    movesCount++
                }
                
                // Merge optimistic local moves
                val mergedBoardMap = mutableMapOf<CellCoord, BoardSymbol>()
                mergedBoardMap.putAll(newBoardMap)
                oldBoard.forEach { (coord, symbol) ->
                    if (symbol == _playerSymbol.value && !mergedBoardMap.containsKey(coord)) {
                        mergedBoardMap[coord] = symbol
                    }
                }
                
                _boardState.value = mergedBoardMap
                movesPlayedCount = movesCount

                val addedKeys = newBoardMap.keys - oldBoard.keys

                // Failsafe local win check for online matching
                for (coord in addedKeys) {
                    val sym = newBoardMap[coord]
                    if (sym != null && sym != BoardSymbol.NONE) {
                        val check = GameLogicEngine.checkWin(mergedBoardMap, coord, sym)
                        if (check.hasWon) {
                            handleWinner(sym, check.winningLine)
                        }
                    }
                }
                val opponentSymbol = _playerSymbol.value.other()
                if (oldBoard.isNotEmpty()) {
                    val newlyAddedOpponentCell = addedKeys.firstOrNull { newBoardMap[it] == opponentSymbol }
                    if (newlyAddedOpponentCell != null) {
                        _opponentLastMove.value = newlyAddedOpponentCell
                    }
                } else if (newBoardMap.isNotEmpty()) {
                    val opponentDocs = snapshot.documents.filter { doc ->
                        doc.getString("symbol") == opponentSymbol.name
                    }
                    if (opponentDocs.isNotEmpty()) {
                        val latestDoc = opponentDocs.maxByOrNull { it.getTimestamp("timestamp")?.seconds ?: 0L }
                            ?: opponentDocs.first()
                        val row = latestDoc.getLong("row")?.toInt()
                        val col = latestDoc.getLong("col")?.toInt()
                        if (row != null && col != null) {
                            _opponentLastMove.value = CellCoord(row, col)
                        }
                    }
                }
                
                for (cell in newBoardMap.keys) {
                    expandBoundsIfNeeded(cell)
                }
                
                // Bot moves checked via snapshot document data directly
                db.collection("games").document(gameId).get().addOnSuccessListener { gameDoc ->
                    if (gameDoc != null && gameDoc.exists()) {
                        val currentTurnStr = gameDoc.getString("currentTurn") ?: "X"
                        val currentTurn = try {
                            BoardSymbol.valueOf(currentTurnStr)
                        } catch (e: Exception) {
                            BoardSymbol.NONE
                        }
                        val isBotGame = gameDoc.getBoolean("isBotGame") ?: false
                        if (isBotGame && currentTurn == _playerSymbol.value.other() && !_gameComplete.value && currentTurn != BoardSymbol.NONE) {
                            triggerBotMoveInFirestore(gameId, newBoardMap, currentTurn, gameDoc.getString("botUid") ?: "")
                        }
                    }
                }
            }
    }

    suspend fun submitMoveTransaction(gameId: String, cell: CellCoord, symbol: BoardSymbol, moverUid: String) {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val gameRef = db.collection("games").document(gameId)
        val moveId = "${cell.r}_${cell.c}"
        val moveRef = gameRef.collection("moves").document(moveId)

        withContext(Dispatchers.IO) {
            try {
                val gameDoc = com.google.android.gms.tasks.Tasks.await(gameRef.get())
                if (!gameDoc.exists()) return@withContext

                val status = gameDoc.getString("status") ?: "active"
                val currentTurnStr = gameDoc.getString("currentTurn") ?: "X"
                val currentTurn = try {
                    BoardSymbol.valueOf(currentTurnStr)
                } catch (e: Exception) {
                    BoardSymbol.NONE
                }

                if (status != "active") return@withContext
                if (currentTurn != symbol) return@withContext

                val existingBoard = mutableMapOf<CellCoord, BoardSymbol>()
                val boardField = gameDoc.get("boardState") as? Map<*, *> ?: emptyMap<Any, Any>()
                for ((key, value) in boardField) {
                    val keyStr = key as? String ?: continue
                    val valueStr = value as? String ?: continue
                    val parts = keyStr.split("_")
                    if (parts.size == 2) {
                        val r = parts[0].toIntOrNull() ?: continue
                        val c = parts[1].toIntOrNull() ?: continue
                        existingBoard[CellCoord(r, c)] = try {
                            BoardSymbol.valueOf(valueStr)
                        } catch (e: Exception) {
                            BoardSymbol.NONE
                        }
                    }
                }

                if (existingBoard.containsKey(cell)) return@withContext

                val updatedBoard = existingBoard + (cell to symbol)
                // Reconstruct updated board field safely
                val updatedBoardField = mutableMapOf<String, String>()
                for ((k, v) in boardField) {
                    val kStr = k as? String ?: continue
                    val vStr = v as? String ?: continue
                    updatedBoardField[kStr] = vStr
                }
                updatedBoardField[moveId] = symbol.name

                val checkWin = GameLogicEngine.checkWin(updatedBoard, cell, symbol)
                val isWin = checkWin.hasWon

                val player1Id = gameDoc.getString("player1Id") ?: ""
                val player2Id = gameDoc.getString("player2Id") ?: ""

                val moveData = mapOf(
                    "row" to cell.r,
                    "col" to cell.c,
                    "symbol" to symbol.name,
                    "playerUid" to moverUid,
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )

                // Write the move document directly, triggering local listeners instantly
                com.google.android.gms.tasks.Tasks.await(moveRef.set(moveData))

                if (isWin) {
                    val winnerId = moverUid
                    val loserId = if (winnerId == player1Id) player2Id else player1Id
                    val winningLineList = checkWin.winningLine?.map { mapOf("row" to it.r, "col" to it.c) } ?: emptyList()

                    com.google.android.gms.tasks.Tasks.await(gameRef.update(mapOf(
                        "boardState" to updatedBoardField,
                        "status" to "completed",
                        "winnerId" to moverUid,
                        "winnerSymbol" to symbol.name,
                        "currentTurn" to "NONE",
                        "winningLine" to winningLineList
                    )))

                    // Update user metrics asynchronously
                    val winnerUserRef = db.collection("users").document(winnerId)
                    val loserUserRef = db.collection("users").document(loserId)

                    val winnerDoc = com.google.android.gms.tasks.Tasks.await(winnerUserRef.get())
                    val loserDoc = com.google.android.gms.tasks.Tasks.await(loserUserRef.get())

                    if (winnerDoc.exists()) {
                        val currentWins = (winnerDoc.getLong("wins") ?: 0L).toInt()
                        val currentRating = (winnerDoc.getLong("rating") ?: 1000L).toInt()
                        val currentXp = (winnerDoc.getLong("xp") ?: 0L).toInt()
                        val currentLevel = (winnerDoc.getLong("level") ?: 1L).toInt()

                        val loserRating = if (loserDoc.exists()) (loserDoc.getLong("rating") ?: 1000L).toInt() else 1000
                        val expectedScore = 1.0 / (1.0 + Math.pow(10.0, (loserRating - currentRating).toDouble() / 400.0))
                        var eloChange = (32 * (1.0 - expectedScore)).toInt()
                        if (eloChange <= 0) eloChange = 12

                        val newRating = currentRating + eloChange
                        val newXpRaw = currentXp + 100
                        var newLevel = currentLevel
                        var newXp = newXpRaw
                        var threshold = newLevel * 100 + 150
                        while (newXp >= threshold) {
                            newXp -= threshold
                            newLevel++
                            threshold = newLevel * 100 + 150
                        }

                        winnerUserRef.update(mapOf(
                            "wins" to currentWins + 1,
                            "rating" to newRating,
                            "xp" to newXp,
                            "level" to newLevel
                        ))
                    }

                    if (loserDoc.exists()) {
                        val currentLosses = (loserDoc.getLong("losses") ?: 0L).toInt()
                        val currentRating = (loserDoc.getLong("rating") ?: 1000L).toInt()
                        val winnerRating = if (winnerDoc.exists()) (winnerDoc.getLong("rating") ?: 1000L).toInt() else 1000

                        val expectedScore = 1.0 / (1.0 + Math.pow(10.0, (currentRating - winnerRating).toDouble() / 400.0))
                        var eloChange = (32 * (0.0 - expectedScore)).toInt()
                        if (eloChange >= 0) eloChange = -10

                        val newRating = (currentRating + eloChange).coerceAtLeast(600)

                        loserUserRef.update(mapOf(
                            "losses" to currentLosses + 1,
                            "rating" to newRating
                        ))
                    }
                } else {
                    com.google.android.gms.tasks.Tasks.await(gameRef.update(mapOf(
                        "boardState" to updatedBoardField,
                        "currentTurn" to symbol.other().name
                    )))
                }
            } catch (e: Exception) {
                android.util.Log.e("GameViewModel", "Error submitting move to firestore: ${e.message}")
            }
        }
    }

    fun triggerBotMoveInFirestore(gameId: String, currentBoard: Map<CellCoord, BoardSymbol>, botSymbol: BoardSymbol, botUid: String) {
        botJob?.cancel()
        botJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            delay(1000)
            val bounds = _boardBounds.value
            val decision = GameLogicEngine.calculateAiMove(
                board = currentBoard,
                aiSymbol = botSymbol,
                difficulty = AiDifficulty.NORMAL,
                minRow = bounds.minRow,
                maxRow = bounds.maxRow,
                minCol = bounds.minCol,
                maxCol = bounds.maxCol
            )
            submitMoveTransaction(gameId, decision, botSymbol, botUid)
        }
    }

    fun surrenderGame() {
        val gameId = currentActiveGameId ?: return
        val myUid = auth.currentUser?.uid ?: return

        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val gameRef = db.collection("games").document(gameId)

        viewModelScope.launch {
            db.runTransaction { transaction ->
                val gameDoc = transaction.get(gameRef)
                val status = gameDoc.getString("status") ?: "active"
                if (status == "active") {
                    val player1Id = gameDoc.getString("player1Id") ?: ""
                    val player2Id = gameDoc.getString("player2Id") ?: ""

                    val winnerId = if (myUid == player1Id) player2Id else player1Id

                    val winnerUserRef = db.collection("users").document(winnerId)
                    val loserUserRef = db.collection("users").document(myUid)
                    val winnerDoc = transaction.get(winnerUserRef)
                    val loserDoc = transaction.get(loserUserRef)

                    transaction.update(gameRef, mapOf(
                        "status" to "surrendered",
                        "winnerId" to winnerId,
                        "winnerSymbol" to (if (winnerId == player1Id) "X" else "O")
                    ))

                    if (winnerDoc.exists()) {
                        val wins = (winnerDoc.getLong("wins") ?: 0L).toInt()
                        transaction.update(winnerUserRef, "wins", wins + 1)
                    }
                    if (loserDoc.exists()) {
                        val losses = (loserDoc.getLong("losses") ?: 0L).toInt()
                        transaction.update(loserUserRef, "losses", losses + 1)
                    }
                }
                null
            }
        }
    }

    fun startSearching(mode: GameMode, diff: AiDifficulty? = null) {
        if (mode == GameMode.RANKED || mode == GameMode.CASUAL) {
            startOnlineMatchmaking(mode)
        } else {
            repository.startMatchmaking(mode, diff)
        }
    }

    fun cancelSearching() {
        matchmakingTimerJob?.cancel()
        matchmakingFirestoreListener?.remove()

        val myUid = auth.currentUser?.uid
        if (myUid != null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("matchmaking").document(myUid).delete()
        }

        repository.cancelMatchmaking()
        _matchmakingState.value = MatchmakingState.Idle
    }

    fun claimDailyOffer() {
        viewModelScope.launch {
            repository.claimDailyReward()
        }
    }

    fun addCustomFriend(username: String, country: String) {
        viewModelScope.launch {
            repository.addFriend(username, country)
        }
    }

    fun removeFriendByUid(uid: String) {
        viewModelScope.launch {
            repository.removeFriend(uid)
        }
    }

    fun inviteFriendToJoin(name: String) {
        viewModelScope.launch {
            repository.sendGameInvite(name)
        }
    }

    fun sendLobbyChat(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.sendGlobalChatMessage(text)
        }
    }

    fun updateProfile(newProfile: LocalProfile) {
        viewModelScope.launch {
            repository.updateProfile(newProfile)
        }
    }

    suspend fun fetchCountryFromIp(): String {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            // Try api.country.is first
            val result1 = tryFetchUrl("https://api.country.is") { response ->
                val regex = "\"country\"\\s*:\\s*\"([A-Za-z]{2})\"".toRegex()
                regex.find(response)?.groupValues?.get(1)?.uppercase()
            }
            if (result1 != null) return@withContext result1

            // Try ip-api.com next
            val result2 = tryFetchUrl("http://ip-api.com/json/") { response ->
                val regex = "\"countryCode\"\\s*:\\s*\"([A-Za-z]{2})\"".toRegex()
                regex.find(response)?.groupValues?.get(1)?.uppercase()
            }
            if (result2 != null) return@withContext result2

            // Try ipapi.co next
            val result3 = tryFetchUrl("https://ipapi.co/json/") { response ->
                val regex = "\"country_code\"\\s*:\\s*\"([A-Za-z]{2})\"".toRegex()
                regex.find(response)?.groupValues?.get(1)?.uppercase()
            }
            
            result3 ?: "US"
        }
    }

    private fun tryFetchUrl(urlString: String, parser: (String) -> String?): String? {
        var conn: java.net.HttpURLConnection? = null
        try {
            val url = java.net.URL(urlString)
            conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                return parser(response)
            }
        } catch (e: Exception) {
            android.util.Log.e("GameViewModel", "Failed to fetch from url: $urlString", e)
        } finally {
            conn?.disconnect()
        }
        return null
    }

    fun detectAndSaveCountryFromIp() {
        viewModelScope.launch {
            try {
                val currentP = repository.getProfileFlow().first()
                val detected = fetchCountryFromIp()
                if (detected.isNotBlank() && detected != currentP.country) {
                    val updated = currentP.copy(country = detected)
                    repository.updateProfile(updated)
                }
            } catch (e: Exception) {
                android.util.Log.e("GameViewModel", "detectAndSaveCountryFromIp failed", e)
            }
        }
    }

    // --- Active Match Controllers ---

    fun initializeGameSession(
        opponentName: String,
        opponentRank: String,
        opponentElo: Int,
        opponentAvatarId: Int,
        mySymbol: BoardSymbol,
        mode: GameMode,
        difficultyName: String? = null,
        seriesMax: Int = 1,
        gameId: String = ""
    ) {
        opponentJob?.cancel()
        botJob?.cancel()
        _matchmakingState.value = MatchmakingState.Idle
        _boardState.value = emptyMap()
        _boardBounds.value = BoardBounds(0, 9, 0, 9)
        _currentTurnSymbol.value = BoardSymbol.X
        _winningLine.value = null
        _gameComplete.value = false
        _winnerSymbol.value = BoardSymbol.NONE
        _opponentLastMove.value = null
        _playerSymbol.value = mySymbol
        _activeMode.value = mode
        _activeGameChat.value = emptyList()
        _rematchRequested.value = false
        _opponentRequestedRematch.value = false
        _rematchAcceptedByOpponent.value = false

        if (mode == GameMode.VS_AI) {
            val randomBotCountries = listOf("US", "DE", "FR", "JP", "GB", "CA", "IN", "TR", "IR")
            _opponentCountry.value = randomBotCountries[Math.abs(opponentName.hashCode()) % randomBotCountries.size]
        } else {
            _opponentCountry.value = ""
        }

        seriesMaxGames = seriesMax
        _playerSeriesWins.value = 0
        _opponentSeriesWins.value = 0

        _activeOpponent.value = OpponentInfo(
            name = opponentName,
            rank = opponentRank,
            elo = opponentElo,
            avatarId = opponentAvatarId,
            difficulty = difficultyName?.let { AiDifficulty.valueOf(it) }
        )

        activeMatchTimeStart = System.currentTimeMillis()
        movesPlayedCount = 0
        _isMyTurn.value = mySymbol == BoardSymbol.X

        currentActiveGameId = gameId

        if (gameId.isNotEmpty() && (mode == GameMode.RANKED || mode == GameMode.CASUAL)) {
            startActiveGameSync(gameId)
        } else {
            activeGameListener?.remove()
            activeMovesListener?.remove()
            // If I am O and opponent is X (or AI), trigger opponent move
            if (mySymbol == BoardSymbol.O) {
                triggerOpponentMove()
            }
        }
        startChessClock()
    }

    fun makeMove(cell: CellCoord) {
        if (_gameComplete.value) return

        val activeGameId = currentActiveGameId
        if (!activeGameId.isNullOrEmpty() && (_activeMode.value == GameMode.RANKED || _activeMode.value == GameMode.CASUAL)) {
            if (!_isMyTurn.value) return
            if (boardState.value[cell] != null && boardState.value[cell] != BoardSymbol.NONE) return
            
            // Play Move feedback Sound programmatically
            if (_playerSymbol.value == BoardSymbol.X) SoundSystem.playMoveX() else SoundSystem.playMoveO()
            
            // Optimistic update
            _boardState.value = _boardState.value + (cell to _playerSymbol.value)
            movesPlayedCount++
            expandBoundsIfNeeded(cell)
            _isMyTurn.value = false
            
            viewModelScope.launch {
                submitMoveTransaction(activeGameId, cell, _playerSymbol.value, auth.currentUser?.uid ?: "")
            }
            return
        }

        if (boardState.value[cell] != null && boardState.value[cell] != BoardSymbol.NONE) return

        // Verify Turn Order for Multiplayer / AI
        if (_activeMode.value != GameMode.PASS_PLAY && !_isMyTurn.value) return

        val originalTurnSymbol = _currentTurnSymbol.value
        val actualSymbol = if (_activeMode.value == GameMode.PASS_PLAY) originalTurnSymbol else _playerSymbol.value

        // Execute local placement on the infinite board
        val updatedBoard = boardState.value + (cell to actualSymbol)
        _boardState.value = updatedBoard
        movesPlayedCount++

        // Play Move feedback Sound programmatically
        if (actualSymbol == BoardSymbol.X) SoundSystem.playMoveX() else SoundSystem.playMoveO()

        // Handle board bounds expansion dynamically if move touches limits
        expandBoundsIfNeeded(cell)

        // Evaluate victory conditions
        val check = GameLogicEngine.checkWin(updatedBoard, cell, actualSymbol)
        if (check.hasWon) {
            handleWinner(actualSymbol, check.winningLine)
            return
        }

        // Shift turns
        val nextSymbol = originalTurnSymbol.other()
        _currentTurnSymbol.value = nextSymbol
        _isMyTurn.value = !_isMyTurn.value

        // Trigger AI or online simulation if it's they turn
        if (_activeMode.value != GameMode.PASS_PLAY && !_isMyTurn.value) {
            triggerOpponentMove()
        }
    }

    private fun expandBoundsIfNeeded(cell: CellCoord) {
        val bounds = _boardBounds.value
        var minR = bounds.minRow
        var maxR = bounds.maxRow
        var minC = bounds.minCol
        var maxC = bounds.maxCol

        // Add 2 padding layers when player approaches within 1 cell of actual edge
        if (cell.r - minR <= 1) minR -= 2
        if (maxR - cell.r <= 1) maxR += 2
        if (cell.c - minC <= 1) minC -= 2
        if (maxC - cell.c <= 1) maxC += 2

        _boardBounds.value = BoardBounds(minR, maxR, minC, maxC)
    }

    private fun handleWinner(symbol: BoardSymbol, line: List<CellCoord>?) {
        _winningLine.value = line
        _gameComplete.value = true
        _winnerSymbol.value = symbol

        val playerWon = if (_activeMode.value == GameMode.PASS_PLAY) {
            true // standard win celebration for local
        } else {
            symbol == _playerSymbol.value
        }

        if (playerWon) {
            _playerSeriesWins.value = _playerSeriesWins.value + 1
            SoundSystem.playVictory()
        } else {
            _opponentSeriesWins.value = _opponentSeriesWins.value + 1
            SoundSystem.playDefeat()
        }

        // Finalize state if Series is complete (only for local/AI games, since online game persistence is managed by activeGameListener)
        val isOnlineMode = (_activeMode.value == GameMode.RANKED || _activeMode.value == GameMode.CASUAL)
        if (!isOnlineMode) {
            val victoryThreshold = (seriesMaxGames / 2) + 1
            if (_playerSeriesWins.value >= victoryThreshold || _opponentSeriesWins.value >= victoryThreshold) {
                saveHistoryRecord(playerWon)
            }
        }
    }

    private fun saveHistoryRecord(playerWon: Boolean) {
        val opp = _activeOpponent.value ?: return
        val duration = ((System.currentTimeMillis() - activeMatchTimeStart) / 1000).toInt()

        viewModelScope.launch {
            // Save results to Room database
            repository.recordMatch(
                opponentName = opp.name,
                opponentRank = opp.rank,
                opponentElo = opp.elo,
                opponentAvatarId = opp.avatarId,
                win = playerWon,
                mode = _activeMode.value,
                movesCount = movesPlayedCount,
                durationSec = duration
            )
            // Reward match completion XP
            val xpReward = if (playerWon) 100 else 40
            repository.addXp(xpReward)
        }
    }

    // --- Opponent Turn Simulation (AI intellect and fake peer players) ---

    private fun triggerOpponentMove() {
        if (_activeMode.value == GameMode.PASS_PLAY) return
        opponentJob?.cancel()
        opponentJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            // Realistic dynamic delay (1.2s to 2.8s wait time)
            val delayDuration = if (_activeMode.value == GameMode.VS_AI) {
                val speed = _activeOpponent.value?.difficulty?.ordinal ?: 1
                (800L + (speed * 150) + Random.nextLong(600))
            } else {
                (1200L + Random.nextLong(1500))
            }
            delay(delayDuration)

            val currentBoard = _boardState.value
            val oppSymbol = _playerSymbol.value.other()
            val bounds = _boardBounds.value

            val decision: CellCoord = if (_activeMode.value == GameMode.VS_AI) {
                val difficulty = _activeOpponent.value?.difficulty ?: AiDifficulty.NORMAL
                GameLogicEngine.calculateAiMove(currentBoard, oppSymbol, difficulty, bounds.minRow, bounds.maxRow, bounds.minCol, bounds.maxCol)
            } else {
                // Highly optimized simulator playing for Online match
                val diffTier = if (oppSymbol == BoardSymbol.O) AiDifficulty.MASTER else AiDifficulty.EXPERT
                GameLogicEngine.calculateAiMove(currentBoard, oppSymbol, diffTier, bounds.minRow, bounds.maxRow, bounds.minCol, bounds.maxCol)
            }

            // Execute opponent move on Main thread safely
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val updatedBoard = currentBoard + (decision to oppSymbol)
                _boardState.value = updatedBoard
                _opponentLastMove.value = decision
                movesPlayedCount++

                if (oppSymbol == BoardSymbol.X) SoundSystem.playMoveX() else SoundSystem.playMoveO()
                expandBoundsIfNeeded(decision)

                // Random Chat Taunt Emoji simulator in online mode!
                if (_activeMode.value == GameMode.RANKED || _activeMode.value == GameMode.CASUAL) {
                    suggestOpponentEmojiChance(updatedBoard, decision)
                }

                val check = GameLogicEngine.checkWin(updatedBoard, decision, oppSymbol)
                if (check.hasWon) {
                    handleWinner(oppSymbol, check.winningLine)
                } else {
                    _currentTurnSymbol.value = _playerSymbol.value
                    _isMyTurn.value = true
                }
            }
        }
    }

    private fun suggestOpponentEmojiChance(board: Map<CellCoord, BoardSymbol>, lastMove: CellCoord) {
        val chance = Random.nextFloat()
        if (chance > 0.35f) return // 35% chance to send comments or emotes on specific events

        val oppSymbol = _playerSymbol.value.other()
        val checkThree = GameLogicEngine.checkWin(board, lastMove, oppSymbol) // heuristic triggers

        val dialogues = when {
            checkThree.hasWon -> listOf("Got ya! 😎", "Unblocked line set", "Watch out!")
            movesPlayedCount > 30 -> listOf("Whoa, infinite board is expanding fast!", "Good defenses!", "Tense session!")
            else -> listOf("Nice move!", "Good game so far", "Whoops! haha", "👍", "🔥", "🤔")
        }

        val chat = _activeGameChat.value.toMutableList()
        val opp = _activeOpponent.value ?: return
        chat.add(
            ChatMessage(
                id = "chat_${System.currentTimeMillis()}",
                senderName = opp.name,
                senderRank = opp.rank,
                senderElo = opp.elo,
                senderAvatarId = opp.avatarId,
                content = dialogues.random()
            )
        )
        _activeGameChat.value = chat
    }

    // --- Game Chat actions ---

    fun sendGameChatText(content: String) {
        if (content.isBlank()) return
        val profileVal = profile.value
        val gameId = currentActiveGameId
        val mode = _activeMode.value
        
        val isOnlineGame = !gameId.isNullOrEmpty() && (mode == GameMode.RANKED || mode == GameMode.CASUAL || mode == GameMode.PRIVATE_ROOM)
        
        if (isOnlineGame) {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val senderId = auth.currentUser?.uid ?: "local_unknown"
            
            val messageData = mapOf(
                "senderId" to senderId,
                "senderName" to profileVal.username,
                "text" to content,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            
            db.collection("rooms").document(gameId!!).collection("messages").add(messageData)
                .addOnFailureListener { e ->
                    android.util.Log.e("GameViewModel", "Failed to upload chat message to room Firestore", e)
                }
        } else {
            // Local fallback logic (e.g. VS AI or Pass & Play)
            val list = _activeGameChat.value.toMutableList()
            list.add(
                ChatMessage(
                    id = "my_g_${System.currentTimeMillis()}",
                    senderName = profileVal.username,
                    senderRank = profileVal.rankName,
                    senderElo = profileVal.elo,
                    senderAvatarId = profileVal.avatarId,
                    content = content
                )
            )
            _activeGameChat.value = list

            // Opponent bot responses to your custom comments
            if (mode == GameMode.VS_AI) {
                viewModelScope.launch {
                    delay(1500L + Random.nextLong(1500))
                    val replies = listOf("Haha yeah!", "Wow, sharp!", "Phew", "Indeed!", "😱", "GG")
                    val opp = _activeOpponent.value ?: return@launch
                    val upList = _activeGameChat.value.toMutableList()
                    upList.add(
                        ChatMessage(
                            id = "opp_ans_${System.currentTimeMillis()}",
                            senderName = opp.name,
                            senderRank = opp.rank,
                            senderElo = opp.elo,
                            senderAvatarId = opp.avatarId,
                            content = replies.random()
                        )
                    )
                    _activeGameChat.value = upList
                }
            }
        }
    }

    // --- Rematch Handlers ---

    fun requestRematch() {
        val gameId = currentActiveGameId
        val mode = _activeMode.value
        val isOnlineGame = !gameId.isNullOrEmpty() && (mode == GameMode.RANKED || mode == GameMode.CASUAL) && !gameId.startsWith("bot_")

        if (isOnlineGame) {
            _rematchRequested.value = true
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val myUid = auth.currentUser?.uid ?: ""

            if (_opponentRequestedRematch.value) {
                acceptRematchOnline()
            } else {
                viewModelScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            db.collection("games").document(gameId!!).update("rematchRequestedBy", myUid)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("GameViewModel", "Failed to send rematch request: ${e.message}")
                    }
                }
            }
        } else {
            _rematchRequested.value = true

            // Fast simulations of opponent accepting rematch
            viewModelScope.launch {
                delay(1500L + Random.nextLong(1500))
                if (_activeMode.value == GameMode.VS_AI || Random.nextFloat() > 0.15f) { // 85% opponent accept chance
                    _rematchAcceptedByOpponent.value = true
                    delay(800)
                    resetSeriesGame()
                } else {
                    sendLobbyChat("Opponent left match, returned to matchmaking.")
                }
            }
        }
    }

    fun acceptRematchOnline() {
        val gameId = currentActiveGameId ?: return
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val gameRef = db.collection("games").document(gameId)
                    
                    // Clear previous moves collection
                    val movesSnapshot = com.google.android.gms.tasks.Tasks.await(gameRef.collection("moves").get())
                    val batch = db.batch()
                    for (doc in movesSnapshot.documents) {
                        batch.delete(doc.reference)
                    }
                    com.google.android.gms.tasks.Tasks.await(batch.commit())

                    // Reset game doc
                    com.google.android.gms.tasks.Tasks.await(
                        gameRef.update(mapOf(
                            "boardState" to emptyMap<String, String>(),
                            "status" to "active",
                            "winnerId" to null,
                            "winnerSymbol" to "NONE",
                            "currentTurn" to "X",
                            "winningLine" to null,
                            "rematchRequestedBy" to null,
                            "rematchAccepted" to true
                        ))
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("GameViewModel", "Failed to accept online rematch: ${e.message}")
            }
        }
    }

    private fun onOnlineRematchTriggered() {
        // Rotate symbols locally
        val nextSymbol = _playerSymbol.value.other()
        _playerSymbol.value = nextSymbol

        _boardState.value = emptyMap()
        _boardBounds.value = BoardBounds(0, 9, 0, 9)
        _currentTurnSymbol.value = BoardSymbol.X
        _winningLine.value = null
        _gameComplete.value = false
        _winnerSymbol.value = BoardSymbol.NONE
        _opponentLastMove.value = null

        _rematchRequested.value = false
        _opponentRequestedRematch.value = false
        _rematchAcceptedByOpponent.value = false

        onlineMatchRecordedInRoom = false
        activeMatchTimeStart = System.currentTimeMillis()
        movesPlayedCount = 0
        _isMyTurn.value = nextSymbol == BoardSymbol.X
        startChessClock()
    }

    private fun resetSeriesGame() {
        val opp = _activeOpponent.value ?: return
        opponentJob?.cancel()

        // Rotate symbols for fair rematches
        val nextSymbol = _playerSymbol.value.other()

        _boardState.value = emptyMap()
        _boardBounds.value = BoardBounds(0, 9, 0, 9)
        _currentTurnSymbol.value = BoardSymbol.X
        _winningLine.value = null
        _gameComplete.value = false
        _winnerSymbol.value = BoardSymbol.NONE
        _opponentLastMove.value = null
        _playerSymbol.value = nextSymbol
        _rematchRequested.value = false
        _opponentRequestedRematch.value = false
        _rematchAcceptedByOpponent.value = false

        activeMatchTimeStart = System.currentTimeMillis()
        movesPlayedCount = 0
        _isMyTurn.value = nextSymbol == BoardSymbol.X
        startChessClock()

        val oldGameId = currentActiveGameId
        if (!oldGameId.isNullOrEmpty() && (_activeMode.value == GameMode.RANKED || _activeMode.value == GameMode.CASUAL)) {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val myUid = auth.currentUser?.uid ?: ""
            val newGameId = "rematch_${System.currentTimeMillis()}_$myUid"
            currentActiveGameId = newGameId

            val isBotGame = oldGameId.startsWith("bot_") || (opp.difficulty != null)
            val gameData = mapOf(
                "gameId" to newGameId,
                "player1Id" to myUid,
                "player2Id" to (if (isBotGame) "bot_${opp.name}" else "opponent"),
                "player1Name" to (profile.value.username),
                "player2Name" to opp.name,
                "player1Symbol" to if (nextSymbol == BoardSymbol.X) "X" else "O",
                "player2Symbol" to if (nextSymbol == BoardSymbol.X) "O" else "X",
                "currentTurn" to "X",
                "status" to "active",
                "winnerSymbol" to "NONE",
                "isBotGame" to isBotGame,
                "boardState" to emptyMap<String, String>(),
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        com.google.android.gms.tasks.Tasks.await(db.collection("games").document(newGameId).set(gameData))
                    }
                    startActiveGameSync(newGameId)
                } catch (e: Exception) {
                    android.util.Log.e("GameViewModel", "Failed to recreate rematch game document: ${e.message}")
                }
            }
        }

        if (nextSymbol == BoardSymbol.O && _activeMode.value != GameMode.PASS_PLAY && _activeMode.value != GameMode.RANKED && _activeMode.value != GameMode.CASUAL) {
            triggerOpponentMove()
        }
    }

    fun exitGameSession() {
        opponentJob?.cancel()
        botJob?.cancel()
        timerJob?.cancel()
        
        // If the game is active, not complete, and not Couch Pass & Play, penalize surrender with ELO loss!
        if (!_gameComplete.value && _activeMode.value != GameMode.PASS_PLAY) {
            val opp = _activeOpponent.value
            if (opp != null) {
                _gameComplete.value = true
                _opponentSeriesWins.value = _opponentSeriesWins.value + 1
                viewModelScope.launch {
                    repository.recordMatch(
                        opponentName = opp.name,
                        opponentRank = opp.rank,
                        opponentElo = opp.elo,
                        opponentAvatarId = opp.avatarId,
                        win = false,
                        mode = _activeMode.value,
                        movesCount = movesPlayedCount,
                        durationSec = ((System.currentTimeMillis() - activeMatchTimeStart) / 1000).toInt()
                    )
                }
            }
        }
        
        _boardState.value = emptyMap()
        _gameComplete.value = false
        _winningLine.value = null

        activeGameListener?.remove()
        activeMovesListener?.remove()
        activeGroupChatListener?.remove()
        activeGameListener = null
        activeMovesListener = null
        activeGroupChatListener = null
    }

    private fun getFallbackBotsLeaderboard(): List<LeaderboardEntry> {
        val botNames = listOf(
            "ShadowCaster", "Phoenix_Rx", "VoltSniper", "PixelLover", "VortexDread", 
            "AlphaOmega", "GigaByte_99", "NeonRider", "CyberSamurai", "FrostBite", 
            "RogueWarlock", "GridMaster", "InfiniteTox", "ZeroChill", "MatrixCode", 
            "NexusWarden", "ApexPredator", "StormBringer", "BlazeRacer", "ChaosScythe", 
            "ChronoGlitch", "CosmicSage", "DynastyKing", "EmberSlinger", "GhostStride", 
            "IronClad_X", "LunaEclipse", "MysticGrid", "NovaPulse", "OmegaCore", 
            "QuantumX", "RuneWeaver", "SolarFlare", "TitanFist", "VectorFlow", 
            "WarpGate", "XenonGlow", "Yuki_TT", "ZephyrWind", "AcroTic", 
            "TacToem", "GomokuGuru", "GridLock", "BoardBoss", "LineCutter", 
            "ThreeAndFour", "FiveInARow", "SlayerPro", "ChessKing", "StarDuster"
        )
        
        val entries = mutableListOf<LeaderboardEntry>()
        for ((index, name) in botNames.withIndex()) {
            val elo = 900 + (index * 26)
            val wins = 15 + index * 3
            val losses = 10 + index * 2
            val games = wins + losses
            val winRate = if (games == 0) 0f else (wins * 100f / games)
            entries.add(
                LeaderboardEntry(
                    position = 51 - index,
                    username = name,
                    country = "🌏",
                    avatarId = 1 + java.lang.Math.abs(("bot_$name").hashCode() % 8),
                    wins = wins,
                    losses = losses,
                    elo = elo,
                    winRate = winRate,
                    isPlayer = false,
                    photoUrl = ""
                )
            )
        }
        
        return entries.sortedWith(
            compareByDescending<LeaderboardEntry> { it.elo }
                .thenByDescending { it.wins }
                .thenBy { it.username }
        ).mapIndexed { idx, entry ->
            entry.copy(position = idx + 1)
        }
    }

    fun startLeaderboardListener() {
        _leaderboardState.value = LeaderboardUiState.Loading
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        leaderboardListenerRegistration?.remove()
        
        // Query using Firestore rating index to fetch sorted data efficiently and correctly
        leaderboardListenerRegistration = db.collection("users")
            .orderBy("rating", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    val fallbackEntries = getFallbackBotsLeaderboard()
                    _leaderboardState.value = LeaderboardUiState.Success(fallbackEntries)
                    return@addSnapshotListener
                }
                
                if (snapshots != null) {
                    val liveEntries = mutableListOf<LeaderboardEntry>()
                    
                    for (doc in snapshots.documents) {
                        val username = doc.getString("username") ?: "Player"
                        val docUid = doc.getString("uid") ?: doc.id
                        val wins = (doc.getLong("wins") ?: 0L).toInt()
                        val losses = (doc.getLong("losses") ?: 0L).toInt()
                        val rating = (doc.getLong("rating") ?: 1000L).toInt()
                        val photoUrl = doc.getString("photoUrl") ?: ""
                        
                        val loggedInUid = auth.currentUser?.uid
                        val isSelf = docUid == loggedInUid
                        val isBot = doc.getBoolean("isBot") ?: docUid.startsWith("bot_")
                        
                        val games = wins + losses
                        val winRate = if (games == 0) 0f else (wins * 100f / games)
                        
                        liveEntries.add(
                            LeaderboardEntry(
                                position = 1,
                                username = username,
                                country = if (isSelf) "US" else if (isBot) "🌏" else "IR",
                                avatarId = if (isSelf) (profile.value.avatarId) else (1 + java.lang.Math.abs(docUid.hashCode() % 8)),
                                wins = wins,
                                losses = losses,
                                elo = rating,
                                winRate = winRate,
                                isPlayer = isSelf,
                                photoUrl = photoUrl
                            )
                        )
                    }
                    
                    // Merge liveEntries with fallback bots list so that we always have a full 50+ list of high-quality entries
                    val fallbackBots = getFallbackBotsLeaderboard()
                    val mergedEntries = mutableListOf<LeaderboardEntry>()
                    
                    // Add all live entries first
                    mergedEntries.addAll(liveEntries)
                    
                    // For each fallback bot, if their username is not already present in liveEntries, add them
                    for (bot in fallbackBots) {
                        if (mergedEntries.none { it.username.equals(bot.username, ignoreCase = true) }) {
                            mergedEntries.add(bot)
                        }
                    }
                    
                    // Insert local player if they are still not in the merged list
                    val currentProfile = profile.value
                    val hasSelf = mergedEntries.any { it.isPlayer }
                    if (!hasSelf && currentProfile.username.isNotBlank()) {
                        val games = currentProfile.wins + currentProfile.losses
                        val winRate = if (games == 0) 0f else (currentProfile.wins * 100f / games)
                        mergedEntries.add(
                            LeaderboardEntry(
                                position = 1,
                                username = currentProfile.username,
                                country = "US",
                                avatarId = currentProfile.avatarId,
                                wins = currentProfile.wins,
                                losses = currentProfile.losses,
                                elo = currentProfile.elo,
                                winRate = winRate,
                                isPlayer = true,
                                photoUrl = ""
                            )
                        )
                    }
                    
                    // Sort in memory securely by ELO and wins desc
                    val sortedEntries = mergedEntries.sortedWith(
                        compareByDescending<LeaderboardEntry> { it.elo }
                            .thenByDescending { it.wins }
                            .thenBy { it.username }
                    ).mapIndexed { idx, entry ->
                        entry.copy(position = idx + 1)
                    }
                    _leaderboardState.value = LeaderboardUiState.Success(sortedEntries)
                } else {
                    val fallbackEntries = getFallbackBotsLeaderboard()
                    _leaderboardState.value = LeaderboardUiState.Success(fallbackEntries)
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        leaderboardListenerRegistration?.remove()
        authStateListener?.let { auth.removeAuthStateListener(it) }
    }
}

sealed class LeaderboardUiState {
    object Loading : LeaderboardUiState()
    data class Success(val entries: List<LeaderboardEntry>) : LeaderboardUiState()
    data class Error(val message: String) : LeaderboardUiState()
}

// Simple Holder representing Opponent info in live game sessions
data class OpponentInfo(
    val name: String,
    val rank: String,
    val elo: Int,
    val avatarId: Int,
    val difficulty: AiDifficulty? = null
)

// ViewModel Factories declarations for clean non-Hilt injection
class GameViewModelFactory(
    private val application: Application,
    private val repository: GameRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            return GameViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class representation")
    }
}
