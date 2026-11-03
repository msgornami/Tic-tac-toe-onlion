package com.example.data.model

data class User(
    val username: String = "",
    val wins: Int = 0,
    val losses: Int = 0,
    val rating: Int = 1000,
    val level: Int = 1,
    val xp: Int = 0,
    val photoUrl: String = ""
)
