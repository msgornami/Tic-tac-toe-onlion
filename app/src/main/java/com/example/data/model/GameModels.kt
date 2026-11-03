package com.example.data.model

// Coordinate layout for infinite expanding board
data class CellCoord(val r: Int, val c: Int)

enum class BoardSymbol {
    NONE, X, O;

    fun other(): BoardSymbol = when (this) {
        X -> O
        O -> X
        NONE -> NONE
    }
}

data class MoveResult(
    val winningLine: List<CellCoord>? = null,
    val hasWon: Boolean = false,
    val isBoardUpdated: Boolean = false
)

enum class GameMode {
    RANKED, CASUAL, PRIVATE_ROOM, VS_AI, PASS_PLAY
}

enum class AiDifficulty(val label: String, val searchDepth: Int, val randomnessChance: Float) {
    EASY("Easy", 1, 0.4f),
    NORMAL("Normal", 1, 0.15f),
    HARD("Hard", 2, 0.05f),
    EXPERT("Expert", 3, 0.0f),
    MASTER("Master", 4, 0.0f),
    IMPOSSIBLE("Impossible", 4, 0.0f)
}

data class OnlineCounters(
    val playersOnline: Int = 1,
    val activeMatches: Int = 412,
    val searchingCount: Int = 31,
    val serverStatus: String = "OPERATIONAL"
)

data class LeaderboardEntry(
    val position: Int,
    val username: String,
    val country: String,
    val avatarId: Int,
    val wins: Int,
    val losses: Int,
    val elo: Int,
    val winRate: Float,
    val isPlayer: Boolean = false,
    val photoUrl: String = ""
)

data class RawLeaderboardUser(
    val uid: String,
    val username: String,
    val wins: Int,
    val losses: Int,
    val rating: Int,
    val photoUrl: String,
    val score: Int
)

data class ChatMessage(
    val id: String,
    val senderName: String,
    val senderRank: String,
    val senderElo: Int,
    val senderAvatarId: Int,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSystemMessage: Boolean = false,
    val isEmojiOnly: Boolean = false
)

// Helper structure for board bounding range
data class BoardBounds(
    val minRow: Int,
    val maxRow: Int,
    val minCol: Int,
    val maxCol: Int
)

class RankSystem {
    companion object {
        fun getRankDetails(elo: Int): RankTier {
            return when {
                elo < 800 -> RankTier("Bronze III", "BRONZE", elo, 600, 800)
                elo < 900 -> RankTier("Bronze II", "BRONZE", elo, 800, 900)
                elo < 1000 -> RankTier("Bronze I", "BRONZE", elo, 900, 1000)
                elo < 1100 -> RankTier("Silver III", "SILVER", elo, 1000, 1100)
                elo < 1200 -> RankTier("Silver II", "SILVER", elo, 1100, 1200)
                elo < 1300 -> RankTier("Silver I", "SILVER", elo, 1200, 1300)
                elo < 1400 -> RankTier("Gold III", "GOLD", elo, 1300, 1400)
                elo < 1500 -> RankTier("Gold II", "GOLD", elo, 1400, 1500)
                elo < 1600 -> RankTier("Gold I", "GOLD", elo, 1500, 1600)
                elo < 1700 -> RankTier("Platinum III", "PLATINUM", elo, 1600, 1700)
                elo < 1800 -> RankTier("Platinum II", "PLATINUM", elo, 1700, 1800)
                elo < 1900 -> RankTier("Platinum I", "PLATINUM", elo, 1800, 1900)
                elo < 2000 -> RankTier("Diamond III", "DIAMOND", elo, 1900, 2000)
                elo < 2100 -> RankTier("Diamond II", "DIAMOND", elo, 2000, 2100)
                elo < 2200 -> RankTier("Diamond I", "DIAMOND", elo, 2100, 2200)
                elo < 2400 -> RankTier("Master", "MASTER", elo, 2200, 2400)
                elo < 2600 -> RankTier("Grandmaster", "GRANDMASTER", elo, 2400, 2600)
                else -> RankTier("Legend", "LEGEND", elo, 2600, 9999)
            }
        }
    }
}

data class RankTier(
    val name: String,
    val group: String, // "BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND", "MASTER", "GRANDMASTER", "LEGEND"
    val currentElo: Int,
    val minElo: Int,
    val maxElo: Int
) {
    val progressFraction: Float
        get() = if (maxElo == 9999) 1.0f else ((currentElo - minElo).toFloat() / (maxElo - minElo).toFloat()).coerceIn(0f, 1f)
}

data class SpectatorMatch(
    val id: String,
    val player1Name: String,
    val player2Name: String,
    val player1Rank: String,
    val player2Rank: String,
    val player1Elo: Int,
    val player2Elo: Int,
    val player1AvatarId: Int,
    val player2AvatarId: Int,
    val round: Int,
    val movesCount: Int,
    val seriesScore: String,
    val simulatedBoard: Map<CellCoord, BoardSymbol>,
    val isActive: Boolean,
    val durationSeconds: Int = 120 // Fallback default
)


