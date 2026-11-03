package com.example.util

object AvatarHelper {
    val STICKERS = listOf(
        "🐱", // Cat
        "🦁", // Lion
        "🦊", // Fox
        "🐼", // Panda
        "🐨", // Koala
        "🐸", // Frog
        "🤖", // Robot
        "🦄", // Unicorn
        "🐉", // Dragon
        "👽"  // Alien
    )

    fun getSticker(avatarId: Int): String {
        val index = (avatarId - 1).coerceIn(0, STICKERS.size - 1)
        return STICKERS[index]
    }
}
