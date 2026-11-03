package com.example.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.example.data.repository.GameRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

object PresenceManager : Application.ActivityLifecycleCallbacks {

    private const val TAG = "PresenceManager"
    private var database: FirebaseDatabase? = null
    private var presenceRef: DatabaseReference? = null
    private var myPresenceRef: DatabaseReference? = null
    private var connectedListener: ValueEventListener? = null
    private var counterListener: ValueEventListener? = null
    
    private var currentUid: String? = null
    private var currentUsername: String = "Player"
    private var currentPhotoUrl: String = ""
    
    private var startedActivityCount = 0
    private var isAppInForeground = true

    private var repository: GameRepository? = null

    fun init(application: Application, repo: GameRepository) {
        this.repository = repo
        application.registerActivityLifecycleCallbacks(this)
        
        try {
            database = FirebaseDatabase.getInstance()
            presenceRef = database?.getReference("presence")
            startCountingOnlineUsers()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Realtime Database", e)
        }

        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val user = auth.currentUser
            if (user != null) {
                if (user.isAnonymous) {
                    repository?.let { repo ->
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val localProf = repo.getProfileFlow().first()
                                startPresence(user.uid, localProf.username, "")
                            } catch (e: Exception) {
                                startPresence(user.uid, "Guest_" + user.uid.take(4), "")
                            }
                        }
                    } ?: run {
                        startPresence(user.uid, "Guest_" + user.uid.take(4), "")
                    }
                } else {
                    val username = user.displayName ?: user.email?.substringBefore("@") ?: "Player"
                    val photoUrl = user.photoUrl?.toString() ?: ""
                    startPresence(user.uid, username, photoUrl)
                }
            } else {
                stopPresence()
            }
        }
    }

    @Synchronized
    private fun startPresence(uid: String, username: String, photoUrl: String) {
        if (currentUid == uid) return
        
        stopPresence()
        
        currentUid = uid
        currentUsername = username
        currentPhotoUrl = photoUrl
        
        val db = database ?: return
        val myRef = db.getReference("presence").child(uid)
        myPresenceRef = myRef

        try {
            db.goOnline()
        } catch (e: Exception) {
            Log.e(TAG, "goOnline error", e)
        }

        val connectedRef = db.getReference(".info/connected")
        connectedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected && isAppInForeground) {
                    val presenceMap = mapOf(
                        "uid" to uid,
                        "username" to username,
                        "photoUrl" to photoUrl,
                        "online" to true,
                        "lastSeen" to ServerValue.TIMESTAMP
                    )
                    
                    myRef.setValue(presenceMap)
                    
                    val disconnectMap = mapOf(
                        "online" to false,
                        "lastSeen" to ServerValue.TIMESTAMP
                    )
                    myRef.onDisconnect().updateChildren(disconnectMap)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Connected listener cancelled: ${error.message}")
            }
        }
        connectedRef.addValueEventListener(connectedListener!!)
    }

    @Synchronized
    fun stopPresence() {
        val uid = currentUid ?: return
        val db = database ?: return
        
        connectedListener?.let {
            db.getReference(".info/connected").removeEventListener(it)
        }
        connectedListener = null
        
        val myRef = myPresenceRef
        if (myRef != null) {
            val offlineMap = mapOf(
                "online" to false,
                "lastSeen" to ServerValue.TIMESTAMP
            )
            myRef.updateChildren(offlineMap)
            try {
                myRef.onDisconnect().cancel()
            } catch (e: Exception) {
                Log.e(TAG, "onDisconnect cancel error", e)
            }
        }
        
        currentUid = null
        myPresenceRef = null
    }

    private fun startCountingOnlineUsers() {
        val ref = presenceRef ?: return
        counterListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var count = 0
                for (child in snapshot.children) {
                    val online = child.child("online").getValue(Boolean::class.java) ?: false
                    if (online) {
                        count++
                    }
                }
                
                // Add fluctuating fake bot counters (e.g. 35 to 48 out of the 50 bots are online)
                val botOnlineCount = 35 + (System.currentTimeMillis() % 14).toInt()
                val totalOnline = count + botOnlineCount
                
                // Update our repository live counters safely
                repository?.let { repo ->
                    val currentCounters = repo.onlineCounters.value
                    repo.updateOnlineCounters(
                        players = totalOnline,
                        active = currentCounters.activeMatches,
                        searching = currentCounters.searchingCount
                    )
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Counter listener cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(counterListener!!)
    }

    private fun updateMyPresenceState(online: Boolean) {
        val myRef = myPresenceRef ?: return
        if (online) {
            val presenceMap = mapOf(
                "uid" to (currentUid ?: ""),
                "username" to currentUsername,
                "photoUrl" to currentPhotoUrl,
                "online" to true,
                "lastSeen" to ServerValue.TIMESTAMP
            )
            myRef.setValue(presenceMap)
            
            val disconnectMap = mapOf(
                "online" to false,
                "lastSeen" to ServerValue.TIMESTAMP
            )
            myRef.onDisconnect().updateChildren(disconnectMap)
        } else {
            val offlineMap = mapOf(
                "online" to false,
                "lastSeen" to ServerValue.TIMESTAMP
            )
            myRef.updateChildren(offlineMap)
        }
    }

    // --- ActivityLifecycleCallbacks ---

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount++
        if (!isAppInForeground) {
            isAppInForeground = true
            Log.d(TAG, "App entered foreground, mark user online")
            try {
                database?.goOnline()
            } catch (e: Exception) {
                Log.e(TAG, "goOnline error", e)
            }
            updateMyPresenceState(true)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount--
        if (startedActivityCount == 0) {
            isAppInForeground = false
            Log.d(TAG, "App entered background, update lastSeen")
            updateMyPresenceState(false)
            try {
                database?.goOffline()
            } catch (e: Exception) {
                Log.e(TAG, "goOffline error", e)
            }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
