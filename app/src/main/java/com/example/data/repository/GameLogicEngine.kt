package com.example.data.repository

import com.example.data.model.BoardSymbol
import com.example.data.model.CellCoord
import com.example.data.model.MoveResult
import com.example.data.model.AiDifficulty
import kotlin.random.Random

object GameLogicEngine {

    // Scan directions: Horizontal, Vertical, Diagonal, Antidiagonal
    private val DIRECTIONS = listOf(
        Pair(0, 1),   // Horizontal
        Pair(1, 0),   // Vertical
        Pair(1, 1),   // Diagonal (top-left to bottom-right)
        Pair(1, -1)   // Antidiagonal (top-right to bottom-left)
    )

    /**
     * Checks if placing a symbol at [coord] results in an immediate 5-in-a-row win.
     * Returns a [MoveResult] indicating if there's a winner and lists the coordinates of the winning line.
     */
    fun checkWin(board: Map<CellCoord, BoardSymbol>, coord: CellCoord, symbol: BoardSymbol): MoveResult {
        if (symbol == BoardSymbol.NONE) return MoveResult()

        for ((dr, dc) in DIRECTIONS) {
            val winningSequence = mutableListOf(coord)

            // Scan in positive direction (e.g. right, down, down-right, down-left)
            var step = 1
            while (true) {
                val nextCoord = CellCoord(coord.r + dr * step, coord.c + dc * step)
                if (board[nextCoord] == symbol) {
                    winningSequence.add(nextCoord)
                    step++
                } else {
                    break
                }
            }

            // Scan in negative direction (e.g. left, up, up-left, up-right)
            step = 1
            while (true) {
                val nextCoord = CellCoord(coord.r - dr * step, coord.c - dc * step)
                if (board[nextCoord] == symbol) {
                    winningSequence.add(nextCoord)
                    step++
                } else {
                    break
                }
            }

            // Since we added the coordinate itself, if the size of the sequence is 5 or more, it's a win!
            if (winningSequence.size >= 5) {
                return MoveResult(winningLine = winningSequence.sortedWith(compareBy({ it.r }, { it.c })), hasWon = true)
            }
        }
        return MoveResult(hasWon = false)
    }

    /**
     * Retrieves all valid cells that are within [radius] distance of already placed moves.
     * This avoids scanning the entire infinite plane, focusing only on the active game region.
     */
    fun getCandidateMoves(board: Map<CellCoord, BoardSymbol>, radius: Int = 1): Set<CellCoord> {
        if (board.isEmpty()) {
            return setOf(CellCoord(5, 5)) // First move at the center
        }
        val candidates = mutableSetOf<CellCoord>()
        for (coord in board.keys) {
            for (dr in -radius..radius) {
                for (dc in -radius..radius) {
                    val candidate = CellCoord(coord.r + dr, coord.c + dc)
                    if (board[candidate] == null || board[candidate] == BoardSymbol.NONE) {
                        candidates.add(candidate)
                    }
                }
            }
        }
        return candidates
    }

    /**
     * Powerful AI opponent selecting moves dynamically based on difficulty.
     * Includes a heuristic scoring metric assessing open paths, blocking maneuvers, and wins.
     */
    fun calculateAiMove(
        board: Map<CellCoord, BoardSymbol>,
        aiSymbol: BoardSymbol,
        difficulty: AiDifficulty,
        minRow: Int,
        maxRow: Int,
        minCol: Int,
        maxCol: Int
    ): CellCoord {
        val candidates = getCandidateMoves(board, radius = 1)
        if (candidates.isEmpty()) {
            return CellCoord((minRow + maxRow) / 2, (minCol + maxCol) / 2)
        }

        // Apply randomness factor for easier difficulties
        if (Random.nextFloat() < difficulty.randomnessChance) {
            return candidates.toList().random()
        }

        val playerSymbol = aiSymbol.other()

        // Evaluates each candidate's score (combination of offensive threat + defensive block)
        var bestMove = candidates.first()
        var bestScore = -Long.MAX_VALUE

        for (candidate in candidates) {
            // 1. Check if AI can win immediately (Highest Priority)
            val aiWinCheck = checkWin(board + (candidate to aiSymbol), candidate, aiSymbol)
            if (aiWinCheck.hasWon) {
                return candidate
            }

            // 2. Check if opponent is about to win and block it (Second Highest Priority)
            val playerWinCheck = checkWin(board + (candidate to playerSymbol), candidate, playerSymbol)
            if (playerWinCheck.hasWon) {
                // If it's a block, we assign an extremely high value to this position
                val score = 5000000L
                if (score > bestScore) {
                    bestScore = score
                    bestMove = candidate
                }
                continue
            }

            // 3. Heuristic evaluation for intermediate play
            val score = evaluateCellScore(board, candidate, aiSymbol)
            if (score > bestScore) {
                bestScore = score
                bestMove = candidate
            }
        }

        return bestMove
    }

    private fun evaluateCellScore(board: Map<CellCoord, BoardSymbol>, cell: CellCoord, aiSymbol: BoardSymbol): Long {
        val playerSymbol = aiSymbol.other()
        var totalScore = 0L

        for ((dr, dc) in DIRECTIONS) {
            // We evaluate lines of length 5 that cover the target cell
            for (offset in -4..0) {
                var targetCount = 0
                var opponentCount = 0
                var blockerTargetCount = 0
                var blockerOpponentCount = 0

                val opponentOfAi = playerSymbol
                val opponentOfPlayer = aiSymbol

                for (i in 0..4) {
                    val r = cell.r + dr * (offset + i)
                    val c = cell.c + dc * (offset + i)
                    val currentCell = CellCoord(r, c)
                    
                    val symbol = if (currentCell == cell) aiSymbol else board[currentCell] ?: BoardSymbol.NONE
                    if (symbol == aiSymbol) {
                        targetCount++
                    } else if (symbol == opponentOfAi) {
                        opponentCount++
                    }

                    val bSymbol = if (currentCell == cell) playerSymbol else board[currentCell] ?: BoardSymbol.NONE
                    if (bSymbol == playerSymbol) {
                        blockerTargetCount++
                    } else if (bSymbol == opponentOfPlayer) {
                        blockerOpponentCount++
                    }
                }

                // Score for AI
                val aiScore = if (opponentCount > 0) 0L else when (targetCount) {
                    5 -> 1000000L
                    4 -> 10000L
                    3 -> 800L
                    2 -> 50L
                    1 -> 2L
                    else -> 0L
                }
                totalScore += aiScore

                // Score for blocker
                val blockerScore = if (blockerOpponentCount > 0) 0L else when (blockerTargetCount) {
                    5 -> 1000000L
                    4 -> 10000L
                    3 -> 800L
                    2 -> 50L
                    1 -> 2L
                    else -> 0L
                }
                totalScore += (blockerScore * 0.85).toLong()
            }
        }

        // Add small center bias to favor centralized shapes rather than isolated moves
        val distToCenter = Math.pow((cell.r - 5).toDouble(), 2.0) + Math.pow((cell.c - 5).toDouble(), 2.0)
        totalScore -= (distToCenter * 0.1).toLong()

        return totalScore
    }

    /**
     * Evaluates a single 5-cell window to determine its weight.
     * Higher weight for unblocked pairs, triples, and quadruples.
     */
    private fun scoreFiveWindow(window: List<BoardSymbol>, targetSymbol: BoardSymbol): Long {
        val opponent = targetSymbol.other()
        val targetCount = window.count { it == targetSymbol }
        val opponentCount = window.count { it == opponent }

        if (opponentCount > 0) {
            // Window is blocked by opponent, worthless for establishing a sequence
            return 0L
        }

        return when (targetCount) {
            5 -> 1000000L  // Five in a row (Win)
            4 -> 10000L    // Four in a row (Major Threat)
            3 -> 800L      // Three in a row (Open Three)
            2 -> 50L       // Two in a row (Builder)
            1 -> 2L        // Single cell
            else -> 0L
        }
    }
}
