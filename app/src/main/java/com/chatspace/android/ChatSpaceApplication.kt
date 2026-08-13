package com.chatspace.android

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.functions.Functions
import kotlinx.coroutines.flow.MutableStateFlow

class ChatSpaceApplication : Application() {
    lateinit var supabase: SupabaseClient
        private set
    val pendingNotificationRoom = MutableStateFlow<String?>(null)

    override fun onCreate() {
        super.onCreate()
        supabase = createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY) {
            install(Auth) {
                scheme = "chatspace"
                host = "auth"
                flowType = FlowType.PKCE
            }
            install(Postgrest) {
                requireValidSession = true
            }
            install(Realtime)
            install(Storage) {
                requireValidSession = true
            }
            install(Functions) {
                requireValidSession = true
            }
        }
        PushNotifications.createChannel(this)
        if (PushNotifications.isConfigured && FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(
                this,
                FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                    .build(),
            )
        }
    }

    fun openNotificationRoom(roomId: String?) {
        if (!roomId.isNullOrBlank()) pendingNotificationRoom.value = roomId
    }

    fun clearNotificationRoom() { pendingNotificationRoom.value = null }
}
