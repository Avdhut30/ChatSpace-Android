# ChatSpace for Android

Native Android client for the ChatSpace Supabase backend. It uses Kotlin,
Jetpack Compose, Material 3, Supabase Auth/PostgREST/Storage, and the backend
schema maintained in the main [ChatSpace repository](https://github.com/Avdhut30/ChatSpace/tree/main/supabase).

Supported on Android 8.0 (API 26) and newer.

## Run

1. Open this repository's root directory in Android Studio.
2. Let Gradle sync and choose an Android 8.0+ emulator or device.
3. Run the `app` configuration.

Copy `local.properties.example` to `local.properties`, retain the Android SDK
path created by Android Studio, and add these private values:

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_KEY=your-publishable-key
GOOGLE_WEB_CLIENT_ID=your-web-oauth-client-id.apps.googleusercontent.com
```

Never use a Supabase service-role or secret key in the Android app.

Google login uses Android Credential Manager and stays inside the app. Create
both Android and Web OAuth client IDs in Google Cloud, register them with the
Supabase Google provider, and put the **Web client ID** in
`GOOGLE_WEB_CLIENT_ID`. The Android client must use package name
`com.chatspace.android` and the SHA-1 fingerprint for the signing certificate.

For email-confirmation links, add `chatspace://auth` to the Supabase
Authentication redirect allow list. The manifest and client are configured to
receive that deep link using PKCE.

## Implemented features

- Persistent email/password accounts, registration, native Google sign-in, sign out
- Profile display name, unique username, and international mobile number
- Optional phone-contact discovery that returns only registered ChatSpace profiles
- Personal spaces, private direct messages, invite-only group creation
- Supabase Realtime room/message/story updates, presence, and typing indicators
- Message search, replies, edits, deletes, likes, and author identity
- Signed inline image previews plus video, audio, PDF, and document sharing
- Up to 100 MB per chat file or story upload and 15 MB normalized profile photos
- Native voice-message recording and playback through the device media viewer
- Text, photo, and video stories with views and owner deletion
- Profile-photo upload and private identity editing
- Group metadata, member, administrator, and deletion controls
- Personal-room password create/change/remove, lock, and unlock controls
- Per-room drafts, unread markers, search, filters, and message timestamps
- Per-user pinned, archived, and muted conversations with synced preferences
- Telegram-inspired inbox and conversation styling, attachment previews, date separators, swipe-to-reply, message sharing, and double-tap likes
- Multiple simultaneous 24-hour stories per user with broader phone media compatibility
- Device-local message dates and times, clickable profiles, Auth avatar fallback, and resilient inline image previews
- WhatsApp-style one-time QR login for linking the web app from a signed-in phone
- GitHub release update checks with secure in-app APK download and Android installer handoff
- Responsive Material 3 UI with loading, empty, and error states

The Android client and web app share the same Supabase Auth users, RLS
policies, rooms, messages, storage buckets, and RPC functions.

## Push notifications

ChatSpace 1.6 supports high-priority Firebase Cloud Messaging notifications,
including background delivery and opening the correct conversation when an
alert is tapped. Complete the one-time setup in the main repository's
[notification guide](https://github.com/Avdhut30/ChatSpace/blob/main/supabase/functions/notify-message/README.md). Without Firebase values, the
app safely falls back to local realtime notifications while its process is
running; killed-app delivery requires FCM.

## Link the web app with a QR code

ChatSpace 1.7 adds **Menu > Link web device**. On the web sign-in page, choose
**QR code**, scan it with the signed-in Android app, and confirm. Every QR code
contains fresh random secrets, expires after 75 seconds, and can be consumed
only once. Complete the backend setup in the main repository's
[device-link guide](https://github.com/Avdhut30/ChatSpace/blob/main/supabase/functions/device-link/README.md)
before using this feature.

## Share the Android app

Choose **Menu > Share app QR** to show a scannable download QR or share the
permanent latest-release link with another Android device.

## App updates

ChatSpace checks the public GitHub release feed when it starts. When a newer
`ChatSpace.apk` is available, the app offers an **Update now** action, downloads
the APK into app-owned storage, and opens Android's package installer. Android
requires the user to grant install permission once and confirm every update.
Updates are never installed silently. You can also choose
**Menu > Check for updates** at any time.

## Production distribution

Production builds are signed, non-debuggable, R8-optimized, HTTPS-only, and
exclude authentication/session data from Android backup and device transfer.
The `directRelease` APK supports GitHub updates. The `playRelease` App Bundle
removes the install-packages permission for Google Play policy compatibility.
See [RELEASING.md](RELEASING.md) for the permanent signing-key and protected
GitHub Actions setup required before the first public production release.

## Contact privacy

ChatSpace asks for Contacts permission only after the user selects **Find from
phone contacts**. Phone numbers are normalized locally and sent over HTTPS only
for an exact registered-user match. The backend does not store submitted
address-book numbers and returns only public profile fields. Requests are
limited to 500 unique numbers and rate-limited per account.
