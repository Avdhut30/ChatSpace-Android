# Secure production releases

ChatSpace has two production distributions:

- `directRelease` produces `ChatSpace.apk`, with the GitHub self-updater.
- `playRelease` produces `ChatSpace-Play.aab`, without the install-packages
  permission or GitHub updater. Upload this bundle to Google Play.

Both artifacts must always use the same permanent release/upload key. Never
commit the keystore or its passwords. Losing the key prevents direct-download
users from receiving future updates.

## 1. Create the permanent key once

Use Android Studio's **Build > Generate Signed Bundle / APK > Create new** or
`keytool`. Use a unique strong store password and key password, an expiry of at
least 25 years, and keep two encrypted offline backups in separate locations.

Users of the old debug-signed APK must uninstall it once before installing the
first production-signed release. Supabase messages and account data remain on
the server, but local drafts are removed. All later production releases update
normally without uninstalling.

## 2. Configure the protected GitHub environment

In `Avdhut30/ChatSpace-Android`, create an environment named `production` and
restrict deployment to protected tags/branches. Add these Actions secrets:

- `ANDROID_KEYSTORE_BASE64`: base64 of the complete `.jks` file
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `SUPABASE_URL`
- `SUPABASE_KEY`: the public/publishable key, never a service-role key
- `GOOGLE_WEB_CLIENT_ID`
- `FIREBASE_APPLICATION_ID`, `FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID`, and
  `FIREBASE_SENDER_ID` when push notifications are enabled

On PowerShell, create the single-line keystore value without printing it:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('chatspace-release.jks')) |
  gh secret set ANDROID_KEYSTORE_BASE64 --repo Avdhut30/ChatSpace-Android --env production
```

Set each remaining value with `gh secret set SECRET_NAME --repo
Avdhut30/ChatSpace-Android --env production` and enter it at the hidden prompt.

## 3. Publish

1. Increase both `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Commit and push the source to `main`.
3. Create and push the matching tag, for example `v2.0.0`.
4. The release workflow builds, signs, verifies, hashes, and publishes the APK
   and Play bundle.
5. Test the APK on at least one phone and one tablet before advertising it.

For Google Play, enroll in Play App Signing and treat this key as the upload
key. Complete the Play Console privacy policy, Data safety, content rating,
developer verification, screenshots, and testing requirements before rollout.
