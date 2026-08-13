# Preserve models decoded by kotlinx.serialization and Supabase at runtime.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod
-keep,includedescriptorclasses class com.chatspace.android.data.**$$serializer { *; }
-keepclassmembers class com.chatspace.android.data.** {
    *** Companion;
}

# Keep Firebase Messaging service discovery intact.
-keep class com.chatspace.android.ChatSpaceMessagingService { *; }
