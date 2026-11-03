package com.example

import android.app.Application
import com.example.data.local.GameDatabase
import com.example.data.repository.GameRepository

class InfiniteApplication : Application() {

    // Database and Repository singletons initialized lazily when needed
    val database: GameDatabase by lazy { GameDatabase.getInstance(this) }
    val repository: GameRepository by lazy { GameRepository(database) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Initialize real-time presence tracking
        com.example.util.PresenceManager.init(this, repository)
    }

    companion object {
        lateinit var instance: InfiniteApplication
            private set
    }
}
