package com.example.data.repository

import android.content.Context
import com.example.data.local.*
import com.example.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random

class GameRepository(private val db: GameDatabase) {

    private val dao = db.gameDao()
    private val repositoryScope = CoroutineScope(DispatchScopeProvider.ioDispatcher + SupervisorJob())

    // Online Lobby Counters State
    private val _onlineCounters = MutableStateFlow(OnlineCounters())
    val onlineCounters: StateFlow<OnlineCounters> = _onlineCounters.asStateFlow()

    fun updateOnlineCounters(players: Int, active: Int, searching: Int) {
        _onlineCounters.value = OnlineCounters(
            playersOnline = players,
            activeMatches = active,
            searchingCount = searching,
            serverStatus = "OPERATIONAL"
        )
    }

    // Matchmaking State
    private val _matchmakingState = MutableStateFlow<MatchmakingState>(MatchmakingState.Idle)
    val matchmakingState: StateFlow<MatchmakingState> = _matchmakingState.asStateFlow()

    // Global Chat Stream
    private val _globalChatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val globalChatMessages: StateFlow<List<ChatMessage>> = _globalChatMessages.asStateFlow()

    // Live Matches for Spectator Mode
    private val _spectatorMatches = MutableStateFlow<List<SpectatorMatch>>(emptyList())
    val spectatorMatches: StateFlow<List<SpectatorMatch>> = _spectatorMatches.asStateFlow()

    // UI Toast Messages (like Daily Reward reminders, Friends online alerts, or Achievements unlocked!)
    private val _systemAlerts = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val systemAlerts: SharedFlow<String> = _systemAlerts.asSharedFlow()

    init {
        // Pre-populate Database defaults if empty
        repositoryScope.launch {
            val existingProfile = dao.getProfileDirect()
            if (existingProfile == null) {
                // First-time profile configuration
                dao.insertOrUpdateProfile(LocalProfile())
                setupDefaultAchievements()
                setupDefaultFriends()
            }
            startFluctuatingCounters()
            startGlobalChatSimulation()
            startSpectatorMatchesSimulation()
            initializeFakePlayersInFirestore()
            startBotLeaderboardActivitySimulation()
        }
    }

    private suspend fun initializeFakePlayersInFirestore() {
        withContext(Dispatchers.IO) {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            var user = auth.currentUser
            var retries = 0
            while (user == null && retries < 20) {
                delay(1000)
                user = auth.currentUser
                retries++
            }
            if (user == null) {
                android.util.Log.e("GameRepository", "Cannot initialize bots in Firestore: Not authenticated.")
                return@withContext
            }
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val collectionRef = db.collection("users")
                
                val snapshot = com.google.android.gms.tasks.Tasks.await(
                    collectionRef.whereEqualTo("isBot", true).limit(1).get()
                )
                
                if (snapshot == null || snapshot.isEmpty) {
                    android.util.Log.d("GameRepository", "No bots found in Firestore. Creating 50 fake players...")
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
                    
                    val batch = db.batch()
                    for ((index, name) in botNames.withIndex()) {
                        val botUid = "bot_$name"
                        val botRef = collectionRef.document(botUid)
                        
                        val elo = 900 + (index * 26) + kotlin.random.Random.nextInt(-10, 10)
                        val wins = 15 + index * 3 + kotlin.random.Random.nextInt(-3, 3)
                        val losses = 10 + index * 2 + kotlin.random.Random.nextInt(-2, 2)
                        
                        val botData = mapOf(
                            "uid" to botUid,
                            "username" to name,
                            "rating" to elo,
                            "wins" to wins,
                            "losses" to losses,
                            "isBot" to true,
                            "photoUrl" to "",
                            "level" to (elo / 100).coerceIn(1, 50),
                            "xp" to 0
                        )
                        batch.set(botRef, botData)
                    }
                    com.google.android.gms.tasks.Tasks.await(batch.commit())
                    android.util.Log.d("GameRepository", "Successfully uploaded 50 bots to Firestore!")
                } else {
                    android.util.Log.d("GameRepository", "Fake players are already initialized in Firestore.")
                }
            } catch (e: Exception) {
                android.util.Log.e("GameRepository", "Error setting up bot database: ${e.message}", e)
            }
        }
    }

    private fun startBotLeaderboardActivitySimulation() {
        repositoryScope.launch {
            delay(15000)
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
            
            while (isActive) {
                delay(30000L + kotlin.random.Random.nextLong(15000))
                try {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val bot1 = botNames.random()
                    var bot2 = botNames.random()
                    while (bot2 == bot1) {
                        bot2 = botNames.random()
                    }
                    
                    val bot1Uid = "bot_$bot1"
                    val bot2Uid = "bot_$bot2"
                    
                    val bot1Ref = db.collection("users").document(bot1Uid)
                    val bot2Ref = db.collection("users").document(bot2Uid)
                    
                    bot1Ref.get().addOnSuccessListener { doc1 ->
                        if (doc1 != null && doc1.exists()) {
                            val elo1 = (doc1.getLong("rating") ?: 1000L).toInt()
                            val wins1 = (doc1.getLong("wins") ?: 0L).toInt()
                            val losses1 = (doc1.getLong("losses") ?: 0L).toInt()
                            
                            bot2Ref.get().addOnSuccessListener { doc2 ->
                                if (doc2 != null && doc2.exists()) {
                                    val elo2 = (doc2.getLong("rating") ?: 1000L).toInt()
                                    val wins2 = (doc2.getLong("wins") ?: 0L).toInt()
                                    val losses2 = (doc2.getLong("losses") ?: 0L).toInt()
                                    
                                    val prob1 = 1.0 / (1.0 + Math.pow(10.0, (elo2 - elo1).toDouble() / 400.0))
                                    val bot1Won = kotlin.random.Random.nextDouble() < prob1
                                    
                                    val kFactor = 32
                                    val expectedS1 = prob1
                                    val actualS1 = if (bot1Won) 1.0 else 0.0
                                    var eloChange = (kFactor * (actualS1 - expectedS1)).toInt()
                                    if (eloChange == 0) eloChange = if (bot1Won) 12 else -10
                                    
                                    val newElo1 = (elo1 + eloChange).coerceAtLeast(600)
                                    val newElo2 = (elo2 - eloChange).coerceAtLeast(600)
                                    
                                    val newWins1 = if (bot1Won) wins1 + 1 else wins1
                                    val newLosses1 = if (!bot1Won) losses1 + 1 else losses1
                                    
                                    val newWins2 = if (!bot1Won) wins2 + 1 else wins2
                                    val newLosses2 = if (bot1Won) losses2 + 1 else losses2
                                    
                                    db.runTransaction { transaction ->
                                        transaction.update(bot1Ref, mapOf(
                                            "rating" to newElo1,
                                            "wins" to newWins1,
                                            "losses" to newLosses1,
                                            "level" to (newElo1 / 100).coerceIn(1, 50)
                                        ))
                                        transaction.update(bot2Ref, mapOf(
                                            "rating" to newElo2,
                                            "wins" to newWins2,
                                            "losses" to newLosses2,
                                            "level" to (newElo2 / 100).coerceIn(1, 50)
                                        ))
                                        null
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Fail silently in background
                }
            }
        }
    }

    suspend fun clearAllUserData() {
        dao.clearProfile()
        dao.clearMatchHistory()
        dao.clearFriends()
        dao.clearAchievements()
        dao.insertOrUpdateProfile(LocalProfile())
        setupDefaultAchievements()
        setupDefaultFriends()
    }

    // --- Profile & Stats Management ---

    fun getProfileFlow(): Flow<LocalProfile> {
        return dao.getProfileFlow()
            .filterNotNull()
            .onStart {
                // Ensure profile is constructed
                if (dao.getProfileDirect() == null) {
                    dao.insertOrUpdateProfile(LocalProfile())
                }
            }
    }

    suspend fun updateProfile(profile: LocalProfile) {
        dao.insertOrUpdateProfile(profile)
    }

    suspend fun addXp(amount: Int) {
        val current = dao.getProfileDirect() ?: LocalProfile()
        var newXp = current.xp + amount
        var newLevel = current.level
        var xpRequired = getXpForNextLevel(newLevel)

        val leveledUpList = mutableListOf<Int>()
        while (newXp >= xpRequired) {
            newXp -= xpRequired
            newLevel++
            xpRequired = getXpForNextLevel(newLevel)
            leveledUpList.add(newLevel)
        }

        val updated = current.copy(
            level = newLevel,
            xp = newXp
        )
        dao.insertOrUpdateProfile(updated)

        for (lvl in leveledUpList) {
            _systemAlerts.emit("🎉 Level Up! You reached Level $lvl!")
            unlockAchievement("level_$lvl")
        }
    }

    fun getXpForNextLevel(level: Int): Int {
        return level * 100 + 150
    }

    // --- Match History ---

    fun getMatchHistoryFlow(): Flow<List<MatchHistoryItem>> = dao.getMatchHistoryFlow()

    suspend fun recordMatch(
        opponentName: String,
        opponentRank: String,
        opponentElo: Int,
        opponentAvatarId: Int,
        win: Boolean,
        mode: GameMode,
        movesCount: Int,
        durationSec: Int
    ) {
        val current = dao.getProfileDirect() ?: LocalProfile()
        val resultString = if (win) "VICTORY" else "DEFEAT"

        // ELO Calculation (Standard multiplayer Elo system)
        var eloChange = 0
        var newElo = current.elo

        if (mode == GameMode.RANKED) {
            val expectedScore = 1.0 / (1.0 + Math.pow(10.0, (opponentElo - current.elo).toDouble() / 400.0))
            val actualScore = if (win) 1.0 else 0.0
            val kFactor = 32
            eloChange = (kFactor * (actualScore - expectedScore)).toInt()
            if (eloChange == 0) eloChange = if (win) 12 else -10 // Assure a minimum movement
            newElo = (current.elo + eloChange).coerceAtLeast(600)
            
            // Update opponent bot's rating in Firestore
            try {
                val botUid = "bot_$opponentName"
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val botRef = db.collection("users").document(botUid)
                
                // Bot's expected score and update
                val botExpectedScore = 1.0 / (1.0 + Math.pow(10.0, (current.elo - opponentElo).toDouble() / 400.0))
                val botActualScore = if (!win) 1.0 else 0.0
                val botEloChange = (kFactor * (botActualScore - botExpectedScore)).toInt()
                val finalizedBotEloChange = if (botEloChange == 0) (if (!win) 12 else -10) else botEloChange
                val botNewElo = (opponentElo + finalizedBotEloChange).coerceAtLeast(600)
                
                botRef.get().addOnSuccessListener { doc ->
                    val curBotWins = if (doc != null && doc.exists()) (doc.getLong("wins") ?: 0L).toInt() else 15
                    val curBotLosses = if (doc != null && doc.exists()) (doc.getLong("losses") ?: 0L).toInt() else 10
                    
                    val updatedBotWins = if (!win) curBotWins + 1 else curBotWins
                    val updatedBotLosses = if (win) curBotLosses + 1 else curBotLosses
                    
                    val botData = mapOf(
                        "uid" to botUid,
                        "username" to opponentName,
                        "rating" to botNewElo,
                        "wins" to updatedBotWins,
                        "losses" to updatedBotLosses,
                        "isBot" to true,
                        "level" to (botNewElo / 100).coerceIn(1, 50)
                    )
                    botRef.set(botData, com.google.firebase.firestore.SetOptions.merge())
                }
            } catch (e: Exception) {
                // Ignore if offline
            }
        }

        val newWins = if (win) current.wins + 1 else current.wins
        val newLosses = if (!win) current.losses + 1 else current.losses
        val newStreak = if (win) current.currentStreak + 1 else 0
        val maxStreak = if (newStreak > current.maxStreak) newStreak else current.maxStreak

        val newRankStr = RankSystem.getRankDetails(newElo).name

        val updated = current.copy(
            elo = newElo,
            rankName = newRankStr,
            wins = newWins,
            losses = newLosses,
            gamesPlayed = current.gamesPlayed + 1,
            currentStreak = newStreak,
            maxStreak = maxStreak
        )

        // Always save newly updated profile stats to the local Room database to ensure ELO and wins/losses match
        dao.insertOrUpdateProfile(updated)

        dao.insertMatchHistory(
            MatchHistoryItem(
                opponentName = opponentName,
                opponentRank = opponentRank,
                opponentElo = opponentElo,
                opponentAvatarId = opponentAvatarId,
                result = resultString,
                gameMode = mode.name,
                movesCount = movesCount,
                eloChange = eloChange,
                durationSeconds = durationSec
            )
        )

        // Evaluate achievements based on new stats
        evaluateAchievements(updated)

        if (mode == GameMode.RANKED) {
            val oldDetails = RankSystem.getRankDetails(current.elo)
            val newDetails = RankSystem.getRankDetails(newElo)
            if (newDetails.name != oldDetails.name) {
                if (newElo > current.elo) {
                    _systemAlerts.emit("🏆 RANK PROMOTION: Reached ${newDetails.name}!")
                } else {
                    _systemAlerts.emit("⚠️ RANK DEMOTION: Moved to ${newDetails.name}")
                }
            }
        }
        
        try {
            syncUserDataToFirestore()
        } catch (e: Exception) {
            android.util.Log.e("GameRepository", "Auto sync target failed", e)
        }
    }

    // --- Friends ---

    fun getFriendsFlow(): Flow<List<FriendItem>> = dao.getFriendsFlow()

    suspend fun addFriend(username: String, country: String = "US") {
        val randId = "p_${Random.nextInt(1000, 9999)}"
        val friendElo = Random.nextInt(1000, 1800)
        val friendRank = RankSystem.getRankDetails(friendElo).name
        val newFriend = FriendItem(
            uid = randId,
            username = username,
            avatarId = Random.nextInt(1, 8),
            country = country,
            rankName = friendRank,
            elo = friendElo,
            status = "ONLINE"
        )
        dao.insertFriend(newFriend)
        _systemAlerts.emit("🤝 Friends request accepted. You are now friends with $username!")
    }

    suspend fun removeFriend(uid: String) {
        dao.removeFriend(uid)
    }

    suspend fun sendGameInvite(friendName: String) {
        _systemAlerts.emit("📨 Match invitation dispatched to $friendName!")
        // Simulate response after short delay
        repositoryScope.launch {
            delay(4000)
            _systemAlerts.emit("🎮 $friendName is busy in another Ranked lobby.")
        }
    }

    // --- Achievements ---

    fun getAchievementsFlow(): Flow<List<LocalAchievement>> = dao.getAchievementsFlow()

    suspend fun unlockAchievement(id: String) {
        val list = dao.getAchievementsFlow().firstOrNull() ?: emptyList()
        val match = list.find { it.id == id } ?: return
        if (!match.isUnlocked) {
            val updated = match.copy(isUnlocked = true, currentProgress = match.maxProgress, unlockedAt = System.currentTimeMillis())
            dao.updateAchievement(updated)
            _systemAlerts.emit("🎖️ Achievement Unlocked: ${match.title}!")
            addXp(120) // Award XP
        }
    }

    private suspend fun setupDefaultAchievements() {
        val list = listOf(
            LocalAchievement("first_win", "First Win", "Vanquish your first opponent in any game mode", 1, 0),
            LocalAchievement("wins_10", "Ascending Legend", "Clinch 10 victories", 10, 0),
            LocalAchievement("streak_5", "Unstoppable Force", "Reach a 5-win streak", 5, 0),
            LocalAchievement("streak_10", "Immortal Gridmaster", "Reach a 10-win streak", 10, 0),
            LocalAchievement("rank_gold", "Golden Horizon", "Ascend to Gold III Rank or higher", 1, 0),
            LocalAchievement("level_5", "Skilled Tactician", "Reach Player Level 5", 5, 1),
            LocalAchievement("perfectionist", "Flawless Setup", "Secure a victory in under 12 moves", 12, 0)
        )
        dao.insertAchievements(list)
    }

    private suspend fun evaluateAchievements(profile: LocalProfile) {
        val achievements = dao.getAchievementsFlow().firstOrNull() ?: return
        for (ach in achievements) {
            if (ach.isUnlocked) continue
            var progress = ach.currentProgress
            var shouldUnlock = false

            when (ach.id) {
                "first_win" -> {
                    if (profile.wins > 0) {
                        progress = 1
                        shouldUnlock = true
                    }
                }
                "wins_10" -> {
                    progress = profile.wins.coerceAtMost(10)
                    shouldUnlock = profile.wins >= 10
                }
                "streak_5" -> {
                    progress = profile.maxStreak.coerceAtMost(5)
                    shouldUnlock = profile.maxStreak >= 5
                }
                "streak_10" -> {
                    progress = profile.maxStreak.coerceAtMost(10)
                    shouldUnlock = profile.maxStreak >= 10
                }
                "rank_gold" -> {
                    val tier = RankSystem.getRankDetails(profile.elo)
                    if (profile.elo >= 1300) {
                        progress = 1
                        shouldUnlock = true
                    }
                }
            }

            if (progress != ach.currentProgress || shouldUnlock) {
                dao.updateAchievement(ach.copy(currentProgress = progress, isUnlocked = shouldUnlock, unlockedAt = if (shouldUnlock) System.currentTimeMillis() else 0L))
                if (shouldUnlock) {
                    _systemAlerts.emit("🎖️ Achievement Unlocked: ${ach.title}!")
                    addXp(120)
                }
            }
        }
    }

    private suspend fun setupDefaultFriends() {
        val list = listOf(
            FriendItem("friend_1", "SupaStarrX", 1, "US", "Gold I", 1380, "ONLINE", true),
            FriendItem("friend_2", "TacticalO", 5, "CA", "Silver I", 1220, "ACTIVE_MATCH"),
            FriendItem("friend_3", "InfiniteFoil", 7, "JP", "Platinum III", 1640, "OFFLINE"),
            FriendItem("friend_4", "GomokuMaster", 6, "KR", "Legend", 2610, "ONLINE")
        )
        for (friend in list) {
            dao.insertFriend(friend)
        }
    }

    // --- Weekly Daily Login Reward System ---

    suspend fun claimDailyReward(): Boolean {
        val profile = dao.getProfileDirect() ?: return false
        val today = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L

        if (today - profile.lastRewardTimestamp > oneDayMs) {
            val consecutive = if (today - profile.lastRewardTimestamp < 2 * oneDayMs) profile.consecutiveLogins + 1 else 1
            // Rewards: level rewards or XP
            val xpGain = 100 + (consecutive * 25).coerceAtMost(150)

            val updated = profile.copy(
                lastRewardTimestamp = today,
                consecutiveLogins = consecutive
            )
            dao.insertOrUpdateProfile(updated)
            addXp(xpGain)
            try {
                syncUserDataToFirestore()
            } catch (e: Exception) {
                android.util.Log.e("GameRepository", "Daily reward sync failed", e)
            }
            _systemAlerts.emit("🎁 Daily Reward claimed! +$xpGain XP (Day $consecutive Streak!)")
            return true
        }
        _systemAlerts.emit("⏱️ Reward already claimed today! Check back in tomorrow.")
        return false
    }

    // --- Real-time Lobby Simulation (Players count, global chats, matches) ---

    private fun startFluctuatingCounters() {
        repositoryScope.launch {
            while (isActive) {
                delay(3000L + Random.nextLong(2000))
                val base = _onlineCounters.value
                val diffMatches = Random.nextInt(-3, 4)
                val diffSearching = Random.nextInt(-2, 3)

                _onlineCounters.value = OnlineCounters(
                    playersOnline = base.playersOnline,
                    activeMatches = (base.activeMatches + diffMatches).coerceIn(200, 600),
                    searchingCount = (base.searchingCount + diffSearching).coerceIn(10, 80),
                    serverStatus = "OPERATIONAL"
                )
            }
        }
    }

    private fun startGlobalChatSimulation() {
        // Initial chat messages
        val seedMessages = mutableListOf(
            ChatMessage("c1", "RookPawn", "Silver I", 1210, 4, "Anyone up for a Ranked duel?", timestamp = System.currentTimeMillis() - 40000),
            ChatMessage("c2", "AlphaStar", "Grandmaster", 2490, 7, "Infinite boards really test your vertical Gomoku setup.", timestamp = System.currentTimeMillis() - 35000),
            ChatMessage("c3", "ZeroNoob", "Bronze III", 720, 2, "Can matches end in a draw? Still haven't seen one.", timestamp = System.currentTimeMillis() - 30000),
            ChatMessage("c4", "GomokuMaster", "Legend", 2610, 6, "Nope, the board keeps growing! Draws are mathematically impossible.", timestamp = System.currentTimeMillis() - 25000)
        )
        _globalChatMessages.value = seedMessages

        val dynamicQuotes = listOf(
            "That last win streak pushed me into Gold II! 🔥",
            "Impossible AI is actually INSANE. Blocked my unblocked three instantly.",
            "Ranked ELO points are hard to keep today.",
            "Private match room code 49132! Who wants to play first?",
            "Just scored a 15-move win diagonal. Awesome!",
            "GG to user SupaStarrX, close match mate.",
            "Wait, so I can expand the board forever?! 😮",
            "Bronze tier is crowded today, matching under 3 seconds.",
            "Legend rank here I come! 🏆",
            "Need teammate for Private Lobby practicing, ELO range 1100."
        )

        val names = listOf("HyperSonic", "GigaBrain", "TicoMaster", "VoidWalker", "Ninja_X", "GlitchKing", "Nexus9", "ChronoGamer")
        val ranks = listOf("Silver III", "Gold II", "Bronze II", "Platinum I", "Diamond III", "Master", "Silver I", "Bronze I")

        repositoryScope.launch {
            while (isActive) {
                delay(6000L + Random.nextLong(6000))
                val randomName = names.random()
                val randomRank = ranks.random()
                val elo = Random.nextInt(800, 2100)
                val msg = dynamicQuotes.random()

                val chats = _globalChatMessages.value.toMutableList()
                chats.add(
                    ChatMessage(
                        id = "msg_${System.currentTimeMillis()}",
                        senderName = randomName,
                        senderRank = randomRank,
                        senderElo = elo,
                        senderAvatarId = Random.nextInt(1, 8),
                        content = msg
                    )
                )
                if (chats.size > 25) {
                    chats.removeAt(0)
                }
                _globalChatMessages.value = chats
            }
        }
    }

    suspend fun sendGlobalChatMessage(content: String) {
        val profile = dao.getProfileDirect() ?: LocalProfile()
        val chats = _globalChatMessages.value.toMutableList()
        val myRankDetails = RankSystem.getRankDetails(profile.elo)

        chats.add(
            ChatMessage(
                id = "my_msg_${System.currentTimeMillis()}",
                senderName = profile.username,
                senderRank = myRankDetails.name,
                senderElo = profile.elo,
                senderAvatarId = profile.avatarId,
                content = content
            )
        )
        _globalChatMessages.value = chats

        // Trigger reactive responses from simulated players after minor delay
        repositoryScope.launch {
            delay(1500 + Random.nextLong(2000))
            val replies = listOf(
                "Nice, welcome to the chat ${profile.username}!",
                "Elo gains today are dynamic, good luck out there.",
                "Grid checkers assemble! 👋",
                "Infinite Tic Tac Toe > Standard TTT, 100% facts.",
                "Anyone up for Online Ranked?"
            )
            val responderName = listOf("Nova_Strike", "CipherX", "GomokuHero").random()
            val responderElo = Random.nextInt(1000, 1600)
            val responderRank = RankSystem.getRankDetails(responderElo).name

            val refreshedChats = _globalChatMessages.value.toMutableList()
            refreshedChats.add(
                ChatMessage(
                    id = "msg_rep_${System.currentTimeMillis()}",
                    senderName = responderName,
                    senderRank = responderRank,
                    senderElo = responderElo,
                    senderAvatarId = Random.nextInt(1, 8),
                    content = replies.random()
                )
            )
            if (refreshedChats.size > 25) refreshedChats.removeAt(0)
            _globalChatMessages.value = refreshedChats
        }
    }

    // --- Spectator Mode Live Simulation ---

    private fun startSpectatorMatchesSimulation() {
        val matches = listOf(
            SpectatorMatch("m_1", "ProSlayerX", "AlphaZero", "Diamond II", "Diamond III", 1680, 1720, 2, 4, 1, 15, "0-1", emptyMap(), false),
            SpectatorMatch("m_2", "MasterMind", "GigaChess", "Master I", "Platinum I", 2150, 1590, 5, 2, 3, 24, "2-1", emptyMap(), false),
            SpectatorMatch("m_3", "JuniorGrid", "NewbieSquare", "Bronze II", "Bronze I", 840, 930, 4, 1, 0, 9, "0-0", emptyMap(), false)
        )
        _spectatorMatches.value = matches

        repositoryScope.launch {
            while (isActive) {
                delay(3000)
                val curr = _spectatorMatches.value.toMutableList()
                for (i in curr.indices) {
                    val match = curr[i]
                    // Simulate progressive board changes and cell updates in background
                    val newMovesCount = match.movesCount + 1
                    val newTime = match.durationSeconds + 3
                    val newBoard = match.simulatedBoard.toMutableMap()
                    val boardSize = 10 + (newMovesCount / 8) // Expanding board size logic

                    // Random placement
                    val emptyRow = Random.nextInt(0, boardSize)
                    val emptyCol = Random.nextInt(0, boardSize)
                    val symbolStr = if (newMovesCount % 2 == 0) BoardSymbol.X else BoardSymbol.O
                    newBoard[CellCoord(emptyRow, emptyCol)] = symbolStr

                    curr[i] = match.copy(
                        movesCount = newMovesCount,
                        durationSeconds = newTime,
                        simulatedBoard = newBoard
                    )
                }
                _spectatorMatches.value = curr
            }
        }
    }

    // --- REAL-TIME MULTIPLAYER MATCHMAKING SIMULATOR ---

    private var matchmakingJob: Job? = null

    fun startMatchmaking(mode: GameMode, selectedAiDifficulty: AiDifficulty? = null) {
        matchmakingJob?.cancel()
        if (mode == GameMode.VS_AI) {
            _matchmakingState.value = MatchmakingState.Success(
                opponentName = "${selectedAiDifficulty?.label ?: "Normal"} AI",
                opponentRank = selectedAiDifficulty?.label ?: "Normal",
                opponentElo = 1000 + (selectedAiDifficulty?.ordinal ?: 0) * 150,
                opponentAvatarId = 8,
                playerSymbol = BoardSymbol.X, // Player starts as X
                gameMode = GameMode.VS_AI,
                aiDifficultyName = selectedAiDifficulty?.name
            )
            return
        }
        if (mode == GameMode.PASS_PLAY) {
            _matchmakingState.value = MatchmakingState.Success(
                opponentName = "Local Player",
                opponentRank = "Local",
                opponentElo = 0,
                opponentAvatarId = 0,
                playerSymbol = BoardSymbol.X,
                gameMode = GameMode.PASS_PLAY
            )
            return
        }

        _matchmakingState.value = MatchmakingState.Searching(0)

        matchmakingJob = repositoryScope.launch {
            var timePassed = 0
            val limit = Random.nextInt(3, 8) // Search time

            while (timePassed < limit) {
                delay(1000)
                timePassed++
                _matchmakingState.value = MatchmakingState.Searching(timePassed)
            }

            // Match with one of the 50 bots whose ELO is closest to the player's ELO!
            val currentProfile = dao.getProfileDirect() ?: LocalProfile()
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
            
            val matchingIndex = ((currentProfile.elo - 900) / 26).coerceIn(0, 49)
            val spreadIndex = (matchingIndex + Random.nextInt(-3, 4)).coerceIn(0, 49)
            val selectedOpponent = botNames[spreadIndex]
            
            var opponentElo = 900 + (spreadIndex * 26) + Random.nextInt(-10, 10)
            val botUid = "bot_$selectedOpponent"
            
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val doc = com.google.android.gms.tasks.Tasks.await(db.collection("users").document(botUid).get())
                if (doc != null && doc.exists()) {
                    opponentElo = (doc.getLong("rating") ?: opponentElo.toLong()).toInt()
                }
            } catch (e: Exception) {
                // fallback
            }
            
            val opponentRank = RankSystem.getRankDetails(opponentElo).name

            _matchmakingState.value = MatchmakingState.Success(
                opponentName = selectedOpponent,
                opponentRank = opponentRank,
                opponentElo = opponentElo,
                opponentAvatarId = 1 + java.lang.Math.abs(botUid.hashCode() % 8),
                playerSymbol = if (Random.nextBoolean()) BoardSymbol.X else BoardSymbol.O,
                gameMode = mode
            )
        }
    }

    suspend fun syncUserDataToFirestore() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val currentUser = auth.currentUser ?: return@withContext
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val userRef = db.collection("users").document(currentUser.uid)

            val profile = dao.getProfileDirect() ?: return@withContext
            val achievements = dao.getAchievementsFlow().firstOrNull() ?: emptyList()
            val history = dao.getMatchHistoryFlow().firstOrNull() ?: emptyList()

            val medalsList = achievements.map { ach ->
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

            val historyList = history.map { item ->
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

            val updates = mapOf(
                "wins" to profile.wins,
                "losses" to profile.losses,
                "rating" to profile.elo,
                "level" to profile.level,
                "xp" to profile.xp,
                "currentStreak" to profile.currentStreak,
                "maxStreak" to profile.maxStreak,
                "consecutiveLogins" to profile.consecutiveLogins,
                "medals" to medalsList,
                "history" to historyList
            )

            try {
                com.google.android.gms.tasks.Tasks.await(userRef.set(updates, com.google.firebase.firestore.SetOptions.merge()))
                android.util.Log.d("GameRepository", "Successfully synced all metrics to Firestore!")
            } catch (e: Exception) {
                android.util.Log.e("GameRepository", "Error syncing all metrics to Firestore", e)
            }
        }
    }

    suspend fun saveFetchedMedalsAndHistory(medalsList: List<*>?, historyList: List<*>?) {
        if (medalsList == null && historyList == null) return
        
        // Fetch or update local Achievements
        val localAchievements = dao.getAchievementsFlow().firstOrNull() ?: emptyList()
        if (medalsList != null) {
            for (item in medalsList) {
                val med = item as? Map<*, *> ?: continue
                val id = med["id"] as? String ?: continue
                val currentProgress = med["currentProgress"]?.toString()?.toDoubleOrNull()?.toInt() ?: 0
                val isUnlocked = med["isUnlocked"] as? Boolean ?: false
                val unlockedAt = med["unlockedAt"]?.toString()?.toDoubleOrNull()?.toLong() ?: 0L
                
                val localMatch = localAchievements.find { it.id == id }
                if (localMatch != null) {
                    if (isUnlocked || currentProgress > localMatch.currentProgress) {
                        val updated = localMatch.copy(
                            currentProgress = maxOf(localMatch.currentProgress, currentProgress),
                            isUnlocked = localMatch.isUnlocked || isUnlocked,
                            unlockedAt = if (isUnlocked) (if (localMatch.unlockedAt > 0) localMatch.unlockedAt else unlockedAt) else 0L
                        )
                        dao.updateAchievement(updated)
                    }
                }
            }
        }

        // Fetch or update Match History
        val localHistory = dao.getMatchHistoryFlow().firstOrNull() ?: emptyList()
        if (historyList != null) {
            for (item in historyList) {
                val hist = item as? Map<*, *> ?: continue
                val opponentName = hist["opponentName"] as? String ?: ""
                val opponentRank = hist["opponentRank"] as? String ?: ""
                val opponentElo = hist["opponentElo"]?.toString()?.toDoubleOrNull()?.toInt() ?: 1000
                val opponentAvatarId = hist["opponentAvatarId"]?.toString()?.toDoubleOrNull()?.toInt() ?: 1
                val result = hist["result"] as? String ?: "DEFEAT"
                val gameMode = hist["gameMode"] as? String ?: "CASUAL"
                val movesCount = hist["movesCount"]?.toString()?.toDoubleOrNull()?.toInt() ?: 0
                val eloChange = hist["eloChange"]?.toString()?.toDoubleOrNull()?.toInt() ?: 0
                val durationSeconds = hist["durationSeconds"]?.toString()?.toDoubleOrNull()?.toInt() ?: 0
                
                val rawTimestamp = hist["timestamp"]
                val timestamp = when (rawTimestamp) {
                    is Number -> rawTimestamp.toLong()
                    is String -> rawTimestamp.toDoubleOrNull()?.toLong() ?: 0L
                    else -> 0L
                }

                val alreadyExists = localHistory.any { 
                    (it.timestamp == timestamp && it.opponentName == opponentName) ||
                    (java.lang.Math.abs(it.timestamp - timestamp) < 8000 && it.opponentName == opponentName) ||
                    (it.opponentName == opponentName && it.movesCount == movesCount && it.result == result && it.gameMode == gameMode && it.eloChange == eloChange)
                }
                if (!alreadyExists) {
                    dao.insertMatchHistory(
                        com.example.data.local.MatchHistoryItem(
                            opponentName = opponentName,
                            opponentRank = opponentRank,
                            opponentElo = opponentElo,
                            opponentAvatarId = opponentAvatarId,
                            result = result,
                            gameMode = gameMode,
                            movesCount = movesCount,
                            eloChange = eloChange,
                            durationSeconds = durationSeconds,
                            timestamp = if (timestamp > 0) timestamp else System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    fun cancelMatchmaking() {
        matchmakingJob?.cancel()
        _matchmakingState.value = MatchmakingState.Idle
    }
}

// Support Dispatcher injection pattern (MVVM Unit Testing compatibility)
object DispatchScopeProvider {
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
}

// --- Multiplayer Simulation state classes ---

sealed interface MatchmakingState {
    object Idle : MatchmakingState
    data class Searching(val elapsedTimeSeconds: Int) : MatchmakingState
    data class Success(
        val opponentName: String,
        val opponentRank: String,
        val opponentElo: Int,
        val opponentAvatarId: Int,
        val playerSymbol: BoardSymbol,
        val gameMode: GameMode,
        val aiDifficultyName: String? = null,
        val gameId: String = ""
    ) : MatchmakingState
}


