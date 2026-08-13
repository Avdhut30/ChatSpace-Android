@file:Suppress("DEPRECATION")

import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val webEnvironment = Properties().apply {
    val file = listOf(rootProject.file(".env.local"), rootProject.file("../.env.local"))
        .firstOrNull { it.exists() }
    file?.readLines()?.forEach { line ->
        val separator = line.indexOf('=')
        if (separator > 0) setProperty(line.substring(0, separator).trim(), line.substring(separator + 1).trim())
    }
}
fun config(name: String, fallback: String, webName: String? = null) =
    (localProperties.getProperty(name) ?: System.getenv(name)
        ?: webName?.let(webEnvironment::getProperty)
        ?: fallback)
        .replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.chatspace.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chatspace.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 14
        versionName = "1.8.1"
        buildConfigField("String", "SUPABASE_URL", "\"${config("SUPABASE_URL", "https://example.supabase.co", "VITE_SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${config("SUPABASE_KEY", "configure-your-publishable-key", "VITE_SUPABASE_PUBLISHABLE_KEY")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${config("GOOGLE_WEB_CLIENT_ID", "", "VITE_GOOGLE_WEB_CLIENT_ID")}\"")
        buildConfigField("String", "FIREBASE_APPLICATION_ID", "\"${config("FIREBASE_APPLICATION_ID", "")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${config("FIREBASE_API_KEY", "")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${config("FIREBASE_PROJECT_ID", "")}\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"${config("FIREBASE_SENDER_ID", "")}\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.navigation:navigation-compose:2.9.1")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.2.0")
    implementation(platform("com.google.firebase:firebase-bom:34.0.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    implementation(platform("io.github.jan-tennert.supabase:bom:3.3.0"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")
    implementation("io.ktor:ktor-client-okhttp:3.3.3")
}
