package com.chatspace.android

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.chatspace.android.data.Message
import com.chatspace.android.data.ConversationPreference
import com.chatspace.android.data.Profile
import com.chatspace.android.data.Room
import com.chatspace.android.data.Story
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        (application as ChatSpaceApplication).openNotificationRoom(intent.getStringExtra(PushNotifications.EXTRA_ROOM_ID))
        (application as ChatSpaceApplication).supabase.handleDeeplinks(intent)
        setContent { ChatSpaceTheme { ChatSpaceApp() } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        (application as ChatSpaceApplication).openNotificationRoom(intent.getStringExtra(PushNotifications.EXTRA_ROOM_ID))
        (application as ChatSpaceApplication).supabase.handleDeeplinks(intent)
    }
}

private val Blue = Color(0xFF229ED9)
private val BlueDark = Color(0xFF168AC1)
private val Cyan = Color(0xFF40B7E9)
private val Ink = Color(0xFF17212B)
private val Muted = Color(0xFF707C87)
private val Background = Color(0xFFF4F5F6)
private val SoftBlue = Color(0xFFE6F4FA)
private val ChatBackground = Color(0xFFDCE6E9)
private const val APP_DOWNLOAD_URL = "https://github.com/Avdhut30/ChatSpace-Android/releases/latest/download/ChatSpace.apk"

@Composable private fun ChatSpaceTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(
            primary = Color(0xFF52BDEB), onPrimary = Color(0xFF002F43),
            primaryContainer = Color(0xFF124E68), onPrimaryContainer = Color(0xFFD1F0FF),
            secondary = Cyan, background = Color(0xFF101820), onBackground = Color(0xFFE7EDF2),
            surface = Color(0xFF18242D), onSurface = Color(0xFFE7EDF2),
            surfaceVariant = Color(0xFF22313C), onSurfaceVariant = Color(0xFFBEC9D1),
            outline = Color(0xFF50616D),
        ) else lightColorScheme(
            primary = Blue, onPrimary = Color.White, primaryContainer = SoftBlue,
            onPrimaryContainer = Color(0xFF0B5D7E), secondary = Cyan,
            background = Background, onBackground = Ink, surface = Color.White,
            onSurface = Ink, surfaceVariant = Color(0xFFF1F5F9), onSurfaceVariant = Muted,
            outline = Color(0xFFCBD5E1),
        ),
        content = content,
    )
}

@Composable private fun ChatSpaceApp(vm: ChatViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { if (BuildConfig.SELF_UPDATE_ENABLED) vm.checkForUpdates() }
    LaunchedEffect(state.signedIn) {
        if (state.signedIn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(state.error, state.notice) { if (state.error != null || state.notice != null) { kotlinx.coroutines.delay(3500); vm.clearMessage() } }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            state.booting -> LaunchScreen()
            !state.signedIn -> AuthScreen(state.busy, vm::signIn, vm::signUp, vm::google, vm::reportError)
            state.profile == null && state.error != null -> LoadFailure(state.error!!, vm::refresh, vm::signOut)
            state.selectedRoom != null -> ChatScreen(state, vm)
            else -> HomeScreen(state, vm)
        }
        state.error?.takeUnless { it == "ROOM_LOCKED" || (state.signedIn && state.profile == null) }?.let { MessageBanner(it, true) }
        state.notice?.let { MessageBanner(it, false) }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
    }
    state.appUpdate?.let { update ->
        AppUpdateDialog(update, vm::dismissAppUpdate, vm::reportError)
    }
}

@Composable private fun LaunchScreen() {
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primaryContainer))),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandMark(76.dp)
        Spacer(Modifier.height(18.dp))
        Text("ChatSpace", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Text("Your people. Your conversations.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        CircularProgressIndicator(strokeWidth = 3.dp)
    }
}

@Composable private fun LoadFailure(message: String, retry: () -> Unit, signOut: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = Color(0xFFFFE4E6)) {
            Icon(Icons.Default.CloudOff, null, tint = Color(0xFFBE123C), modifier = Modifier.padding(22.dp).size(36.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text("We couldn't load your chats", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        Button(retry, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Try again")
        }
        TextButton(signOut) { Text("Sign out") }
    }
}

@Composable private fun BrandMark(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * .3f))
            .background(Brush.linearGradient(listOf(Blue, Cyan))),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Default.Forum, null, tint = Color.White, modifier = Modifier.size(size * .52f)) }
}

@Composable private fun MessageBanner(text: String, error: Boolean) {
    Surface(
        color = if (error) Color(0xFFBE123C) else Color(0xFF047857),
        contentColor = Color.White,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (error) Icons.Default.ErrorOutline else Icons.Default.CheckCircle, null)
            Spacer(Modifier.width(10.dp))
            Text(text, Modifier.weight(1f), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable private fun AuthScreen(busy: Boolean, login: (String,String)->Unit, register: (String,String,String,String,String)->Unit, google: (String,String)->Unit, reportError: (String)->Unit) {
    var isRegister by remember { mutableStateOf(false) }; var name by remember { mutableStateOf("") }; var username by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.background)))) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
                .padding(horizontal = 22.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BrandMark(66.dp)
            Spacer(Modifier.height(16.dp))
            Text("ChatSpace", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("Conversations that feel closer", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(22.dp)) {
                    Text(if (isRegister) "Join ChatSpace" else "Welcome back", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(if (isRegister) "Create your profile in a minute" else "Sign in to continue", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                    if (isRegister) { Field(name, {name=it}, "Display name"); Field(username, {username=it}, "Username"); Field(phone, {phone=it}, "Mobile (+country code)") }
                    Field(email, {email=it}, "Email address"); Field(password, {password=it}, "Password", true)
                    Button(
                        onClick = { if (isRegister) register(name,username,phone,email,password) else login(email,password) },
                        enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                    ) { Text(if (isRegister) "Create account" else "Log in", fontWeight = FontWeight.Bold) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f)); Text("  or  ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp); HorizontalDivider(Modifier.weight(1f))
                    }
                    NativeGoogleSignInButton(busy, google, reportError)
                    TextButton(onClick = { isRegister=!isRegister }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(if (isRegister) "Already have an account? Log in" else "New here? Create an account")
                    }
                }
            }
        }
    }
}

@Composable private fun NativeGoogleSignInButton(
    busy: Boolean,
    signIn: (idToken: String, nonce: String) -> Unit,
    reportError: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    OutlinedButton(
        onClick = {
            if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
                reportError("Google Sign-In is unavailable in this build. Update ChatSpace and try again.")
                return@OutlinedButton
            }
            scope.launch {
                val rawNonce = UUID.randomUUID().toString()
                val hashedNonce = MessageDigest.getInstance("SHA-256")
                    .digest(rawNonce.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                val googleOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                    .setNonce(hashedNonce)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleOption)
                    .build()
                try {
                    val result = CredentialManager.create(context).getCredential(context, request)
                    val credential = result.credential
                    require(
                        credential is CustomCredential &&
                            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                    ) { "Google returned an unsupported credential" }
                    val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    signIn(googleCredential.idToken, rawNonce)
                } catch (_: GetCredentialCancellationException) {
                    // The user closed the native account chooser; remain on the sign-in screen.
                } catch (error: GetCredentialException) {
                    reportError(error.message ?: "Google Sign-In is unavailable on this device")
                } catch (error: Exception) {
                    reportError(error.message ?: "Google Sign-In failed")
                }
            }
        },
        enabled = !busy,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Icon(Icons.Default.AccountCircle, null); Spacer(Modifier.width(10.dp)); Text("Continue with Google", fontWeight = FontWeight.SemiBold) }
}

@Composable private fun Field(value: String, change: (String)->Unit, label: String, password: Boolean = false) {
    OutlinedTextField(
        value, change, label = { Text(label) }, singleLine = true,
        shape = RoundedCornerShape(14.dp),
        visualTransformation = if(password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth().padding(bottom=10.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun HomeScreen(state: ChatUiState, vm: ChatViewModel) {
    var search by remember { mutableStateOf("") }; var filter by remember { mutableStateOf("all") }; var menu by remember { mutableStateOf(false) }; var create by remember { mutableStateOf(false) }; var profile by remember { mutableStateOf(false) }; var story by remember { mutableStateOf(false) }; var showAppQr by remember { mutableStateOf(false) }; var viewerStoryId by remember { mutableStateOf<String?>(null) }; var webLinkQr by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("chatspace", android.content.Context.MODE_PRIVATE) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Blue, titleContentColor = Color.White, actionIconContentColor = Color.White),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(state.profile, 38.dp, onClick={profile=true})
                        Spacer(Modifier.width(11.dp))
                        Column {
                            Text("ChatSpace", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text("${state.rooms.size} conversation${if(state.rooms.size == 1) "" else "s"}", color = Color.White.copy(alpha=.76f), fontSize = 11.sp)
                        }
                    }
                },
                actions = {
                    IconButton(onClick=vm::refresh) { Icon(Icons.Default.Refresh,"Refresh") }
                    IconButton(onClick={story=true}) { Icon(Icons.Default.AddCircleOutline,"Add story") }
                    IconButton(onClick={menu=true}) { Icon(Icons.Default.MoreVert,"Menu") }
                    DropdownMenu(menu,{menu=false}) {
                        DropdownMenuItem({Text("New conversation")},{menu=false; create=true},leadingIcon={Icon(Icons.Default.AddComment,null)})
                        DropdownMenuItem({Text("Edit profile")},{menu=false; profile=true},leadingIcon={Icon(Icons.Default.Person,null)})
                        DropdownMenuItem(
                            { Text("Share app QR") },
                            { menu = false; showAppQr = true },
                            leadingIcon = { Icon(Icons.Default.QrCode2, null) },
                        )
                        if (BuildConfig.SELF_UPDATE_ENABLED) {
                            DropdownMenuItem(
                                { Text(if (state.checkingForUpdate) "Checking for updates…" else "Check for updates") },
                                { menu = false; vm.checkForUpdates(manual = true) },
                                enabled = !state.checkingForUpdate,
                                leadingIcon = { Icon(Icons.Default.SystemUpdate, null) },
                            )
                        }
                        DropdownMenuItem(
                            { Text("Link web device") },
                            {
                                menu = false
                                val options = GmsBarcodeScannerOptions.Builder()
                                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                    .enableAutoZoom()
                                    .build()
                                GmsBarcodeScanning.getClient(context, options).startScan()
                                    .addOnSuccessListener { barcode ->
                                        barcode.rawValue?.let { webLinkQr = it }
                                            ?: vm.reportError("The QR code could not be read")
                                    }
                                    .addOnFailureListener { error ->
                                        vm.reportError(error.message ?: "QR scanner is unavailable")
                                    }
                            },
                            leadingIcon = { Icon(Icons.Default.QrCodeScanner, null) },
                        )
                        DropdownMenuItem({Text("Sign out")},{menu=false; vm.signOut()},leadingIcon={Icon(Icons.AutoMirrored.Filled.Logout,null)})
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { create = true },
                icon = { Icon(Icons.Default.Edit, null) },
                text = { Text("New chat", fontWeight = FontWeight.Bold) },
                containerColor = Blue,
                contentColor = Color.White,
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            StoriesRow(state.stories, state.profile, { story=true }) { selected -> viewerStoryId=selected.id; vm.viewStory(selected) }
            OutlinedTextField(
                search, {search=it}, placeholder={Text("Search chats and messages")},
                leadingIcon={Icon(Icons.Default.Search,null)},
                trailingIcon = { if(search.isNotBlank()) IconButton({search=""}) { Icon(Icons.Default.Close,"Clear") } },
                singleLine=true, shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface),
                modifier=Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(listOf("all" to "All", "direct" to "People", "group" to "Groups", "archived" to "Archived")) { (value,label) ->
                    FilterChip(selected=filter==value,onClick={filter=value},label={Text(label)},leadingIcon={if(filter==value)Icon(Icons.Default.Check,null,Modifier.size(16.dp))})
                }
            }
            val shown=state.rooms.filter { room ->
                val preference = state.conversationPreferences[room.row.id]
                val matchesSection = if (filter == "archived") {
                    preference?.isArchived == true
                } else {
                    preference?.isArchived != true && (filter == "all" || room.type == filter)
                }
                matchesSection &&
                    (search.isBlank() || room.displayName.contains(search,true) || roomPreview(room).contains(search,true))
            }.sortedByDescending { state.conversationPreferences[it.row.id]?.isPinned == true }
            if(shown.isEmpty()) EmptyRooms(search.isNotBlank()) {create=true}
            else LazyColumn(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
                contentPadding = PaddingValues(bottom=96.dp),
            ) { items(shown,key={it.row.id}) { room ->
                val unread = room.row.lastMessageCreatedAt?.let { it > (prefs.getString("seen:${room.row.id}", "") ?: "") } == true
                val preference = state.conversationPreferences[room.row.id]
                    ?: ConversationPreference(state.profile?.id.orEmpty(), room.row.id)
                RoomItem(
                    room = room,
                    unread = unread,
                    currentUserId = state.profile?.id,
                    preference = preference,
                    open = { selected ->
                        prefs.edit().putString("seen:${selected.row.id}", selected.row.lastMessageCreatedAt ?: java.time.Instant.now().toString()).apply()
                        vm.openRoom(selected)
                    },
                    change = { pinned, archived, muted ->
                        vm.updateConversationPreference(room.row.id, pinned, archived, muted)
                    },
                )
            } }
        }
    }
    webLinkQr?.let { qrValue ->
        AlertDialog(
            onDismissRequest = { webLinkQr = null },
            icon = { Icon(Icons.Default.Computer, null) },
            title = { Text("Link this browser?") },
            text = { Text("Only continue if this QR code is displayed on a browser you control. The code can be used once and expires automatically.") },
            confirmButton = {
                Button(onClick = { webLinkQr = null; vm.approveWebLink(qrValue) }) { Text("Link browser") }
            },
            dismissButton = { TextButton(onClick = { webLinkQr = null }) { Text("Cancel") } },
        )
    }
    state.lockedRoom?.let { lockedRoom -> UnlockDialog(lockedRoom,vm::dismissUnlock,{password -> vm.unlock(lockedRoom,password)}) }
    if(create) NewConversationDialog(state, vm, {create=false})
    if(profile) ProfileEditorDialog(state.profile, vm, {profile=false})
    if(story) StoryCreatorDialog(vm,{story=false})
    if(showAppQr) AppDownloadQrDialog { showAppQr = false }
    viewerStoryId?.let { selectedId ->
        val selected = state.stories.firstOrNull { it.id == selectedId }
        if (selected == null) {
            LaunchedEffect(selectedId) { viewerStoryId = null }
        } else {
            val authorStories = state.stories.filter { it.authorId == selected.authorId }
            val selectedIndex = authorStories.indexOfFirst { it.id == selected.id }
            StoryViewer(
                story = selected,
                stories = authorStories,
                selectedIndex = selectedIndex,
                myId = state.profile?.id,
                close = { viewerStoryId = null },
                previous = if (selectedIndex > 0) {{
                    val previousStory = authorStories[selectedIndex - 1]
                    viewerStoryId = previousStory.id
                    vm.viewStory(previousStory)
                }} else null,
                next = if (selectedIndex in 0 until authorStories.lastIndex) {{
                    val nextStory = authorStories[selectedIndex + 1]
                    viewerStoryId = nextStory.id
                    vm.viewStory(nextStory)
                }} else null,
                delete = { vm.deleteStory(selected); viewerStoryId = null },
            )
        }
    }
}

@Composable
private fun AppDownloadQrDialog(close: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = close,
        icon = { Icon(Icons.Default.Android, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Download ChatSpace") },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                    Image(
                        painter = painterResource(R.drawable.chatspace_download_qr),
                        contentDescription = "QR code to download the ChatSpace Android app",
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(12.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Scan this QR code on another Android device to download the latest ChatSpace app.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Download ChatSpace")
                    putExtra(Intent.EXTRA_TEXT, "Download ChatSpace for Android: $APP_DOWNLOAD_URL")
                }
                context.startActivity(Intent.createChooser(share, "Share ChatSpace"))
            }) {
                Icon(Icons.Default.Share, null)
                Spacer(Modifier.width(7.dp))
                Text("Share link")
            }
        },
        dismissButton = { TextButton(close) { Text("Close") } },
    )
}

@Composable
private fun AppUpdateDialog(update: AppUpdate, close: () -> Unit, reportError: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember(update.version) { mutableStateOf(false) }

    fun beginDownload() {
        if (downloading) return
        downloading = true
        scope.launch {
            runCatching { downloadUpdateApk(context, update) }
                .onSuccess { apk ->
                    downloading = false
                    runCatching { openUpdateInstaller(context, apk) }
                        .onFailure { reportError(it.message ?: "Could not open the Android installer") }
                }
                .onFailure { error ->
                    downloading = false
                    reportError(error.message ?: "Could not download the update")
                }
        }
    }

    val installPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()) {
            beginDownload()
        } else {
            reportError("Allow ChatSpace to install updates, then tap Update now again")
        }
    }

    fun requestUpdate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            installPermission.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                }
            )
        } else {
            beginDownload()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!downloading) close() },
        icon = { Icon(Icons.Default.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("ChatSpace ${update.version} is available") },
        text = {
            Column {
                Text("Download the latest version, then confirm the update in Android's installer. Your chats and app data will be preserved.")
                if (downloading) {
                    Spacer(Modifier.height(18.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Downloading update…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = ::requestUpdate, enabled = !downloading) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(7.dp))
                Text("Update now")
            }
        },
        dismissButton = { TextButton(onClick = close, enabled = !downloading) { Text("Later") } },
    )
}

private suspend fun downloadUpdateApk(context: Context, update: AppUpdate): File = withContext(Dispatchers.IO) {
    val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        ?: error("Update storage is unavailable")
    check(directory.exists() || directory.mkdirs()) { "Could not prepare update storage" }
    val destination = File(directory, "ChatSpace-${update.version}.apk")
    val connection = URL(update.downloadUrl).openConnection() as HttpURLConnection
    try {
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("User-Agent", "ChatSpace-Android/${BuildConfig.VERSION_NAME}")
        val status = connection.responseCode
        if (status !in 200..299) error("Update download failed ($status)")
        destination.outputStream().buffered().use { output ->
            connection.inputStream.buffered().use { input -> input.copyTo(output) }
        }
        check(destination.length() > 0) { "The downloaded update is empty" }
        destination
    } catch (error: Throwable) {
        destination.delete()
        throw error
    } finally {
        connection.disconnect()
    }
}

private fun openUpdateInstaller(context: Context, apk: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
    context.startActivity(
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
}

@Composable private fun StoriesRow(stories: List<Story>, me: Profile?, add:()->Unit, open:(Story)->Unit) {
    val storyGroups = stories.groupBy { it.authorId }.values.toList()
    LazyRow(contentPadding=PaddingValues(horizontal=16.dp,vertical=12.dp), horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.width(68.dp).clickable(onClick=add)){Box{Avatar(me,54.dp);Surface(color=Blue,shape=CircleShape,modifier=Modifier.size(20.dp).align(Alignment.BottomEnd)){Icon(Icons.Default.Add,null,tint=Color.White,modifier=Modifier.padding(3.dp))}};Spacer(Modifier.height(4.dp));Text("Add story",fontSize=11.sp)} }
        items(storyGroups,key={it.first().authorId}) { authorStories ->
            val story = authorStories.firstOrNull { !it.viewed } ?: authorStories.first()
            val author=Profile(story.authorId,story.authorName,story.authorAvatar)
            Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.width(68.dp).clickable{open(story)}) {
                Box {
                    Avatar(author,54.dp,authorStories.any { !it.viewed })
                    if (authorStories.size > 1) Surface(color=Blue,shape=CircleShape,modifier=Modifier.align(Alignment.BottomEnd).size(20.dp)) {
                        Box(contentAlignment=Alignment.Center) { Text(authorStories.size.toString(),color=Color.White,fontSize=10.sp,fontWeight=FontWeight.Bold) }
                    }
                }
                Spacer(Modifier.height(4.dp)); Text(if(story.authorId==me?.id) "Your story" else story.authorName,maxLines=1,fontSize=11.sp,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable private fun RoomItem(
    room: Room,
    unread: Boolean,
    currentUserId: String?,
    preference: ConversationPreference,
    open: (Room) -> Unit,
    change: (pinned: Boolean?, archived: Boolean?, muted: Boolean?) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable{open(room)}.background(MaterialTheme.colorScheme.surface).padding(start=14.dp,top=9.dp,bottom=9.dp),verticalAlignment=Alignment.CenterVertically) {
        Avatar(if(room.type=="direct") room.members.firstOrNull { it.id != currentUserId } else null,56.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Column(Modifier.padding(end=14.dp,bottom=10.dp,top=7.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(room.displayName,fontWeight=FontWeight.SemiBold,fontSize=16.sp,modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
                    if (preference.isPinned) Icon(Icons.Default.PushPin,"Pinned",tint=Muted,modifier=Modifier.padding(end=6.dp).size(15.dp))
                    if (preference.isMuted) Icon(Icons.Default.NotificationsOff,"Muted",tint=Muted,modifier=Modifier.padding(end=6.dp).size(15.dp))
                    room.row.lastMessageCreatedAt?.let { Text(formatTime(it),fontSize=11.sp,color=if(unread)Blue else Muted) }
                }
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text(roomPreview(room),color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=14.sp,modifier=Modifier.weight(1f))
                    if(unread) Surface(color=Blue,shape=CircleShape){Text("1",Modifier.padding(horizontal=7.dp,vertical=2.dp),color=Color.White,fontSize=11.sp,fontWeight=FontWeight.Bold)}
                }
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.25f),thickness=.5.dp)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Conversation actions", tint = Muted) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (preference.isPinned) "Unpin" else "Pin") },
                    onClick = { menu = false; change(!preference.isPinned, null, null) },
                    leadingIcon = { Icon(Icons.Default.PushPin, null) },
                )
                DropdownMenuItem(
                    text = { Text(if (preference.isMuted) "Unmute notifications" else "Mute notifications") },
                    onClick = { menu = false; change(null, null, !preference.isMuted) },
                    leadingIcon = { Icon(if (preference.isMuted) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff, null) },
                )
                DropdownMenuItem(
                    text = { Text(if (preference.isArchived) "Move to inbox" else "Archive") },
                    onClick = { menu = false; change(null, !preference.isArchived, null) },
                    leadingIcon = { Icon(if (preference.isArchived) Icons.Default.Unarchive else Icons.Default.Archive, null) },
                )
            }
        }
    }
}

private fun roomPreview(room: Room): String {
    if (!room.row.lastMessageText.isNullOrBlank()) return room.row.lastMessageText
    if (!room.row.lastFileName.isNullOrBlank()) return when {
        room.row.lastFileType?.startsWith("image/") == true -> "📷 Photo"
        room.row.lastFileType?.startsWith("video/") == true -> "🎥 Video"
        room.row.lastFileType?.startsWith("audio/") == true -> "🎤 Audio"
        else -> "📎 ${room.row.lastFileName}"
    }
    return room.row.description ?: "Start the conversation"
}

@Composable private fun EmptyRooms(searching:Boolean,create:()->Unit) { Column(Modifier.fillMaxSize().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer){Icon(if(searching)Icons.Default.SearchOff else Icons.Default.Forum,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(22.dp).size(40.dp))};Spacer(Modifier.height(18.dp));Text(if(searching)"No matching conversations" else "Your inbox is ready",fontSize=20.sp,fontWeight=FontWeight.Bold);Text(if(searching)"Try another name or message" else "Start a private chat or bring your people together in a group.",color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=androidx.compose.ui.text.style.TextAlign.Center,modifier=Modifier.padding(vertical=8.dp));if(!searching)Button(create,shape=RoundedCornerShape(14.dp)){Icon(Icons.Default.AddComment,null);Spacer(Modifier.width(8.dp));Text("Start a conversation")}} }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ChatScreen(state: ChatUiState, vm: ChatViewModel) {
    val room=requireNotNull(state.selectedRoom); val context=LocalContext.current; val prefs=remember{context.getSharedPreferences("chatspace",android.content.Context.MODE_PRIVATE)}
    val partner=if(room.type=="direct")room.members.firstOrNull{it.id!=state.profile?.id}else null
    var text by remember(room.row.id){mutableStateOf(prefs.getString("draft:${room.row.id}","") ?: "")}; var reply by remember{mutableStateOf<Message?>(null)}; var edit by remember{mutableStateOf<Message?>(null)}; var attachment by remember{mutableStateOf<android.net.Uri?>(null)}; var search by remember{mutableStateOf("")}; var settings by remember{mutableStateOf(false)}; var viewedProfile by remember{mutableStateOf<Profile?>(null)}; val list=rememberLazyListState()
    val recorder=remember{VoiceRecorder(context)}; var recording by remember{mutableStateOf(false)}; var seconds by remember{mutableIntStateOf(0)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){attachment=it}
    fun beginRecording(){runCatching{recorder.start();recording=true;seconds=0}.onFailure{vm.reportError(it.message?:"Microphone could not start")}}
    val microphone=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)beginRecording()else vm.reportError("Microphone permission is required for voice messages")}
    fun toggleRecording(){if(recording){val file=runCatching{recorder.stop()}.getOrNull();recording=false;file?.let{vm.sendFile(name=it.name,type="audio/mp4",bytes=it.readBytes())}}else if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)beginRecording()else microphone.launch(Manifest.permission.RECORD_AUDIO)}
    BackHandler {
        when {
            edit != null -> edit = null
            settings -> settings = false
            viewedProfile != null -> viewedProfile = null
            search.isNotBlank() -> search = ""
            reply != null -> reply = null
            attachment != null -> attachment = null
            recording -> {
                recorder.cancel()
                recording = false
                seconds = 0
            }
            else -> vm.closeRoom()
        }
    }
    DisposableEffect(Unit){onDispose{recorder.cancel()}}
    LaunchedEffect(recording){while(recording){kotlinx.coroutines.delay(1000);seconds++}}
    LaunchedEffect(text){if(text.isBlank())prefs.edit().remove("draft:${room.row.id}").apply()else prefs.edit().putString("draft:${room.row.id}",text).apply()}
    LaunchedEffect(text){vm.sendTyping(text.isNotBlank());if(text.isNotBlank()){kotlinx.coroutines.delay(1200);vm.sendTyping(false)}}
    LaunchedEffect(state.messages.size){if(state.messages.isNotEmpty())list.animateScrollToItem(state.messages.lastIndex)}
    Scaffold(
        containerColor=if(isSystemInDarkTheme())Color(0xFF0E171E)else ChatBackground,
        topBar={TopAppBar(
            colors=TopAppBarDefaults.topAppBarColors(containerColor=Blue, titleContentColor=Color.White, navigationIconContentColor=Color.White, actionIconContentColor=Color.White),
            title={Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.clickable{if(partner!=null)viewedProfile=partner else{vm.loadRoomSettings();settings=true}}){Avatar(partner,40.dp);Spacer(Modifier.width(10.dp));Column{Text(room.displayName,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);val subtitle=when{state.typingUsers.isNotEmpty()->"${state.typingUsers.joinToString()} typing…";partner?.id in state.onlineUserIds->"online";room.type=="personal"->"private space";else->"${room.members.size} member${if(room.members.size==1)"" else "s"}"};Text(subtitle,fontSize=11.sp,color=Color.White.copy(alpha=.78f))}}},
            navigationIcon={IconButton(onClick=vm::closeRoom){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}},
            actions={IconButton(onClick={search=if(search.isBlank())" " else ""}){Icon(if(search.isBlank())Icons.Default.Search else Icons.Default.Close,"Search")};IconButton(onClick={vm.loadRoomSettings();settings=true}){Icon(Icons.Default.MoreVert,"Conversation settings")}},
        )},
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if(search.isNotBlank()) OutlinedTextField(search.trimStart(),{search=" $it"},placeholder={Text("Search messages")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,shape=RoundedCornerShape(16.dp),colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=MaterialTheme.colorScheme.surface,focusedContainerColor=MaterialTheme.colorScheme.surface),modifier=Modifier.fillMaxWidth().padding(10.dp))
            val profiles=room.members.associateBy{it.id}; val shown=state.messages.filter{search.isBlank()||it.text.contains(search.trim(),true)}
            if(shown.isEmpty()) Column(Modifier.weight(1f).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Icon(Icons.Default.WavingHand,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(42.dp));Spacer(Modifier.height(12.dp));Text(if(search.isBlank())"Say hello" else "No messages found",fontWeight=FontWeight.Bold);Text(if(search.isBlank())"This is the beginning of your conversation." else "Try a different search.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
            else LazyColumn(Modifier.weight(1f).padding(horizontal=8.dp),state=list,contentPadding=PaddingValues(vertical=10.dp),verticalArrangement=Arrangement.spacedBy(2.dp)){
                shown.forEachIndexed { index,m ->
                    if(index==0 || messageDay(shown[index-1].createdAt)!=messageDay(m.createdAt)) item(key="day:${m.id}") { DaySeparator(m.createdAt) }
                    item(key=m.id){
                        val myUserId = state.profile?.id
                        MessageBubble(
                            m = m,
                            author = profiles[m.authorId],
                            mine = myUserId == m.authorId,
                            likes = state.likes.count { it.messageId == m.id },
                            likedByMe = state.likes.any { it.messageId == m.id && it.userId == myUserId },
                            likePending = m.id in state.likingMessageIds,
                            parent = state.messages.find { it.id == m.replyTo },
                            reply = { reply = m },
                            like = { vm.like(m) },
                            edit = { edit = m },
                            delete = { vm.delete(m) },
                        )
                    }
                }
            }
            reply?.let { ReplyStrip(it){reply=null} }; attachment?.let{ ReplyStrip(Message(0,room.row.id,"","Attached file",java.time.Instant.now().toString(),fileName=it.lastPathSegment)){attachment=null} }
            if(recording) Row(Modifier.fillMaxWidth().background(Color(0xFFFFF1F2)).padding(horizontal=16.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.GraphicEq,null,tint=Color(0xFFE11D48));Text(" Recording  ${seconds/60}:${(seconds%60).toString().padStart(2,'0')}",Modifier.weight(1f),color=Color(0xFFE11D48),fontWeight=FontWeight.Bold);Text("Tap stop to send",fontSize=11.sp,color=Muted)}
            Surface(color=MaterialTheme.colorScheme.surface,shadowElevation=10.dp){Row(Modifier.navigationBarsPadding().padding(horizontal=8.dp,vertical=9.dp),verticalAlignment=Alignment.Bottom){IconButton(onClick={picker.launch(arrayOf("image/*","video/*","audio/*","application/pdf","text/*","application/zip"))}){Icon(Icons.Default.AddCircle,"Attach",tint=MaterialTheme.colorScheme.onSurfaceVariant)};OutlinedTextField(text,{text=it.take(1000)},placeholder={Text(if(recording)"Recording voice message…" else "Message")},enabled=!recording,shape=RoundedCornerShape(22.dp),colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=Color.Transparent,focusedBorderColor=MaterialTheme.colorScheme.primary,unfocusedContainerColor=MaterialTheme.colorScheme.surfaceVariant,focusedContainerColor=MaterialTheme.colorScheme.surfaceVariant),modifier=Modifier.weight(1f),maxLines=5);Spacer(Modifier.width(5.dp));if(text.isBlank()&&attachment==null)FilledIconButton(onClick=::toggleRecording,colors=IconButtonDefaults.filledIconButtonColors(containerColor=if(recording)Color(0xFFE11D48)else Blue),modifier=Modifier.size(48.dp)){Icon(if(recording)Icons.Default.Stop else Icons.Default.Mic,if(recording)"Stop and send" else "Record voice message")}else FilledIconButton(onClick={val sent=text;text="";vm.send(sent,reply,attachment);reply=null;attachment=null},colors=IconButtonDefaults.filledIconButtonColors(containerColor=Blue),modifier=Modifier.size(48.dp)){Icon(Icons.AutoMirrored.Filled.Send,"Send")}}}
        }
    }
    edit?.let { message -> EditDialog(message,{edit=null}){newText -> vm.edit(message,newText);edit=null} }
    if(settings) RoomSettingsDialog(state,vm,{settings=false})
    viewedProfile?.let{ContactProfileDialog(it){viewedProfile=null}}
}

private fun localMessageTime(value:String):java.time.ZonedDateTime?=runCatching{java.time.OffsetDateTime.parse(value).toInstant().atZone(java.time.ZoneId.systemDefault())}.getOrNull()
private fun messageDay(value:String):java.time.LocalDate?=localMessageTime(value)?.toLocalDate()

@Composable private fun DaySeparator(value:String){
    val day=messageDay(value)
    val today=java.time.LocalDate.now()
    val label=when(day){today->"Today";today.minusDays(1)->"Yesterday";null->"";else->day.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))}
    Box(Modifier.fillMaxWidth().padding(vertical=8.dp),contentAlignment=Alignment.Center){Surface(color=Color(0xFF6F8793).copy(alpha=.82f),shape=RoundedCornerShape(12.dp)){Text(label,Modifier.padding(horizontal=10.dp,vertical=4.dp),color=Color.White,fontSize=11.sp,fontWeight=FontWeight.SemiBold)}}
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable private fun MessageBubble(m:Message,author:Profile?,mine:Boolean,likes:Int,likedByMe:Boolean,likePending:Boolean,parent:Message?,reply:()->Unit,like:()->Unit,edit:()->Unit,delete:()->Unit){
    var menu by remember{mutableStateOf(false)}; val uri=LocalUriHandler.current; val context=LocalContext.current
    var swipeOffset by remember(m.id){mutableFloatStateOf(0f)}
    var imageFailed by remember(m.fileUrl){mutableStateOf(false)}
    val copyText=m.text.ifBlank{m.fileName.orEmpty()}
    fun copyMessage(){
        if(copyText.isBlank())return
        val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Chat message",copyText))
        android.widget.Toast.makeText(context,"Message copied",android.widget.Toast.LENGTH_SHORT).show()
    }
    fun shareMessage(){
        if(copyText.isBlank())return
        val intent=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,copyText)}
        context.startActivity(Intent.createChooser(intent,"Share message"))
    }
    Column(Modifier.fillMaxWidth(),horizontalAlignment=if(mine)Alignment.End else Alignment.Start){
        if(!mine)Text(author?.name?:"Member",fontSize=11.sp,color=Blue,modifier=Modifier.padding(start=10.dp))
        Box(contentAlignment=Alignment.CenterStart){
        if(swipeOffset>8f)Icon(Icons.AutoMirrored.Filled.Reply,"Release to reply",tint=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(start=10.dp))
        Surface(color=if(mine){if(isSystemInDarkTheme())Color(0xFF255B45)else Color(0xFFE2FFC7)}else MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.onSurface,shape=RoundedCornerShape(topStart=16.dp,topEnd=16.dp,bottomStart=if(mine)16.dp else 4.dp,bottomEnd=if(mine)4.dp else 16.dp),shadowElevation=1.dp,modifier=Modifier.widthIn(max=320.dp).offset{IntOffset(swipeOffset.roundToInt(),0)}.draggable(state=rememberDraggableState{amount->swipeOffset=(swipeOffset+amount).coerceIn(0f,120f)},orientation=Orientation.Horizontal,onDragStopped={if(swipeOffset>=72f)reply();swipeOffset=0f}).combinedClickable(onClick={},onDoubleClick=like,onLongClick={menu=true})){
            Column(Modifier.padding(11.dp)){
                parent?.let{Text("↩ ${it.text}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)}
                if(m.fileName!=null){
                    if(m.fileType?.startsWith("image/")==true&&m.fileUrl!=null&&!imageFailed)Box(Modifier.fillMaxWidth().heightIn(min=140.dp,max=300.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable{uri.openUri(m.fileUrl)},contentAlignment=Alignment.Center){CircularProgressIndicator(Modifier.size(28.dp),strokeWidth=2.dp);AsyncImage(model=m.fileUrl,contentDescription=m.fileName,contentScale=ContentScale.Crop,onError={imageFailed=true},modifier=Modifier.fillMaxWidth().heightIn(min=140.dp,max=300.dp))}
                    else Surface(color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.72f),shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().clickable(enabled=m.fileUrl!=null){m.fileUrl?.let(uri::openUri)}){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Icon(when{m.fileType?.startsWith("audio/")==true->Icons.Default.GraphicEq;m.fileType?.startsWith("video/")==true->Icons.Default.PlayCircle;m.fileType=="application/pdf"->Icons.Default.PictureAsPdf;else->Icons.Default.Description},null,tint=MaterialTheme.colorScheme.primary);Column(Modifier.weight(1f).padding(horizontal=8.dp)){Text(m.fileName,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold);Text(formatBytes(m.fileSize),fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Icon(Icons.Default.OpenInNew,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)}}
                    Spacer(Modifier.height(5.dp))
                }
                if(m.text.isNotBlank())Text(m.text)
                Row(Modifier.align(Alignment.End),verticalAlignment=Alignment.CenterVertically){
                    Text(formatTime(m.createdAt),fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(likes>0)TextButton(onClick=like,enabled=!likePending,contentPadding=PaddingValues(horizontal=5.dp),modifier=Modifier.height(28.dp)){Text("♥ $likes",color=if(likedByMe)Color(0xFFFF5C7A)else MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp)}
                    if(m.editedAt!=null)Text(" · edited",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        }
        DropdownMenu(menu,{menu=false}){
            DropdownMenuItem({Text("Reply")},{menu=false;reply()},leadingIcon={Icon(Icons.AutoMirrored.Filled.Reply,null)})
            DropdownMenuItem({Text(if(likedByMe)"Remove like" else "Like")},{menu=false;like()},enabled=!likePending,leadingIcon={Icon(if(likedByMe)Icons.Default.Favorite else Icons.Default.FavoriteBorder,null,tint=if(likedByMe)Color(0xFFE11D48)else LocalContentColor.current)})
            DropdownMenuItem({Text("Copy message")},{menu=false;copyMessage()},enabled=copyText.isNotBlank(),leadingIcon={Icon(Icons.Default.ContentCopy,null)})
            DropdownMenuItem({Text("Share")},{menu=false;shareMessage()},enabled=copyText.isNotBlank(),leadingIcon={Icon(Icons.Default.Share,null)})
            if(mine){DropdownMenuItem({Text("Edit")},{menu=false;edit()});DropdownMenuItem({Text("Delete",color=Color.Red)},{menu=false;delete()})}
        }
        Spacer(Modifier.height(7.dp))
    }
}

private fun formatBytes(value:Long?):String{val size=value?:return "Attachment";return when{size>=1024*1024->"%.1f MB".format(size/1048576.0);size>=1024->"%.0f KB".format(size/1024.0);else->"$size B"}}
private fun formatTime(value:String):String=localMessageTime(value)?.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))?:""

@Composable private fun ReplyStrip(message:Message,close:()->Unit){Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(8.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.AutoMirrored.Filled.Reply,null,tint=MaterialTheme.colorScheme.primary);Text(message.fileName?:message.text,Modifier.weight(1f).padding(8.dp),maxLines=1);IconButton(close){Icon(Icons.Default.Close,"Cancel")}}}

@Composable private fun Avatar(profile:Profile?,size:androidx.compose.ui.unit.Dp,border:Boolean=false,onClick:(()->Unit)?=null){val modifier=Modifier.size(size).then(if(border)Modifier.background(MaterialTheme.colorScheme.primary,CircleShape).padding(3.dp)else Modifier).then(if(onClick!=null)Modifier.clickable(onClick=onClick)else Modifier).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer);Box(modifier,contentAlignment=Alignment.Center){Text(profile?.name?.take(1)?.uppercase()?:"#",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onPrimaryContainer);if(!profile?.avatarUrl.isNullOrBlank())AsyncImage(model=profile.avatarUrl,contentDescription="${profile.name} profile photo",contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())}}

@Composable
private fun ContactProfileDialog(profile: Profile, close: () -> Unit) {
    var showPhoto by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Profile") },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(profile, 96.dp, onClick = { showPhoto = true })
                Text("Tap photo to view full size", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 7.dp))
                Spacer(Modifier.height(8.dp))
                Text(profile.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (profile.username.isNotBlank()) Text("@${profile.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(close) { Text("Close") } },
    )
    if (showPhoto) FullProfilePhotoDialog(profile) { showPhoto = false }
}

@Composable
private fun NewConversationDialog(state: ChatUiState, vm: ChatViewModel, close: () -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var showingPhoneMatches by remember { mutableStateOf(false) }

    fun findPhoneContacts() {
        val phoneNumbers = readContactPhoneNumbers(context)
        showingPhoneMatches = true
        query = ""
        vm.loadPhoneContacts(phoneNumbers)
    }

    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) findPhoneContacts()
        else vm.reportError("Contacts permission is needed to find people already on ChatSpace")
    }

    LaunchedEffect(Unit) { vm.loadPeople() }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("New conversation") },
        text = {
            Column {
                TabRow(tab) {
                    Tab(tab == 0, { tab = 0 }) { Text("Direct", Modifier.padding(10.dp)) }
                    Tab(tab == 1, { tab = 1 }) { Text("Group", Modifier.padding(10.dp)) }
                }
                if (tab == 1) {
                    Field(name, { name = it }, "Group name")
                    Field(desc, { desc = it }, "Description")
                }
                Field(query, {
                    query = it
                    showingPhoneMatches = false
                    vm.loadPeople(it)
                }, "Find by name or @username")
                if (tab == 0) {
                    OutlinedButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                                findPhoneContacts()
                            } else {
                                contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Contacts, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Find from phone contacts")
                    }
                    if (showingPhoneMatches) {
                        Text(
                            if (state.people.isEmpty()) "No registered ChatSpace contacts found" else "Contacts already on ChatSpace",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(state.people) { person ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (tab == 0) {
                                    vm.direct(person.id)
                                    close()
                                } else selected = if (person.id in selected) selected - person.id else selected + person.id
                            }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(person, 38.dp)
                            Text("${person.name}  @${person.username}", Modifier.weight(1f).padding(8.dp))
                            if (tab == 1) Checkbox(person.id in selected, {})
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (tab == 1) Button(
                onClick = { vm.createGroup(name, desc, selected.toList()); close() },
                enabled = name.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(close) { Text("Cancel") } },
    )
}

private fun readContactPhoneNumbers(context: Context): List<String> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        return emptyList()
    }
    val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    val country = telephony?.networkCountryIso?.takeIf { it.isNotBlank() }
        ?: telephony?.simCountryIso?.takeIf { it.isNotBlank() }
        ?: Locale.getDefault().country
    val result = linkedSetOf<String>()
    val columns = arrayOf(
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
    )
    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        columns,
        null,
        null,
        null,
    )?.use { cursor ->
        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val normalizedIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)
        while (cursor.moveToNext() && result.size < 500) {
            val normalized = normalizedIndex.takeIf { it >= 0 }?.let(cursor::getString)
            val raw = numberIndex.takeIf { it >= 0 }?.let(cursor::getString).orEmpty()
            val e164 = normalized?.takeIf { it.matches(Regex("^\\+[1-9][0-9]{7,14}$")) }
                ?: PhoneNumberUtils.formatNumberToE164(raw, country.uppercase(Locale.ROOT))
            if (e164?.matches(Regex("^\\+[1-9][0-9]{7,14}$")) == true) result += e164
        }
    }
    return result.toList()
}

@Composable private fun ProfileDialog(profile:Profile?,vm:ChatViewModel,close:()->Unit){if(profile==null)return;var name by remember{mutableStateOf(profile.name)};var username by remember{mutableStateOf(profile.username)};var phone by remember{mutableStateOf(profile.phoneNumber?:"")};AlertDialog(close,title={Text("Your profile")},text={Column{Avatar(profile,72.dp);Field(name,{name=it},"Display name");Field(username,{username=it},"Username");Field(phone,{phone=it},"Mobile number")}},confirmButton={Button({vm.saveProfile(name,username,phone);close()}){Text("Save")}},dismissButton={TextButton(close){Text("Cancel")}})}
@Composable private fun StoryDialog(vm:ChatViewModel,close:()->Unit){var text by remember{mutableStateOf("")};var color by remember{mutableStateOf("#2563EB")};AlertDialog(close,title={Text("Add a 24-hour story")},text={Column{Field(text,{text=it},"What’s happening?");Row{listOf("#2563EB","#7C3AED","#DB2777","#059669").forEach{c->Box(Modifier.padding(5.dp).size(38.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(c))).clickable{color=c})}}}},confirmButton={Button({vm.addStory(text,color);close()},enabled=text.isNotBlank()){Text("Publish")}},dismissButton={TextButton(close){Text("Cancel")}})}
@Composable private fun UnlockDialog(room:Room,close:()->Unit,unlock:(String)->Unit){var password by remember{mutableStateOf("")};AlertDialog(close,title={Text("Unlock ${room.displayName}")},text={Field(password,{password=it},"Room password",true)},confirmButton={Button({unlock(password)}){Text("Unlock")}},dismissButton={TextButton(close){Text("Cancel")}})}
@Composable private fun EditDialog(message:Message,close:()->Unit,save:(String)->Unit){var value by remember{mutableStateOf(message.text)};AlertDialog(close,title={Text("Edit message")},text={Field(value,{value=it},"Message")},confirmButton={Button({save(value)}){Text("Save")}},dismissButton={TextButton(close){Text("Cancel")}})}

@Composable
private fun ProfileEditorDialog(profile: Profile?, vm: ChatViewModel, close: () -> Unit) {
    if (profile == null) return
    var name by remember { mutableStateOf(profile.name) }
    var username by remember { mutableStateOf(profile.username) }
    var phone by remember { mutableStateOf(profile.phoneNumber.orEmpty()) }
    var showPhoto by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::uploadAvatar) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Your profile") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clickable { showPhoto = true }) {
                    Avatar(profile, 82.dp)
                }
                Text("Tap photo to view full size", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                TextButton(onClick = { picker.launch(arrayOf("image/*")) }) {
                    Icon(Icons.Default.PhotoCamera, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Change profile photo")
                }
                Field(name, { name = it }, "Display name")
                Field(username, { username = it }, "Username")
                Field(phone, { phone = it }, "Mobile number")
            }
        },
        confirmButton = { Button({ vm.saveProfile(name, username, phone); close() }) { Text("Save") } },
        dismissButton = { TextButton(close) { Text("Cancel") } },
    )
    if (showPhoto) FullProfilePhotoDialog(profile) { showPhoto = false }
}

@Composable
private fun FullProfilePhotoDialog(profile: Profile, close: () -> Unit) {
    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (!profile.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = profile.avatarUrl,
                    contentDescription = "${profile.name} full profile photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(profile, 150.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("No profile photo", color = Color.White.copy(alpha = .75f))
                }
            }
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().background(Color.Black.copy(alpha = .4f)).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(profile.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 8.dp))
                IconButton(close) { Icon(Icons.Default.Close, "Close full profile photo", tint = Color.White) }
            }
        }
    }
}

@Composable
private fun StoryCreatorDialog(vm: ChatViewModel, close: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("#4F46E5") }
    var media by remember { mutableStateOf<android.net.Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { media = it }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Create story") },
        text = {
            Column {
                Field(text, { text = it.take(500) }, "Caption or text")
                OutlinedButton({ picker.launch(arrayOf("image/*", "video/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(if (media == null) Icons.Default.AddPhotoAlternate else Icons.Default.CheckCircle, null)
                    Spacer(Modifier.width(8.dp)); Text(media?.lastPathSegment ?: "Choose photo or video", maxLines = 1)
                }
                Text("Visible to your contacts for 24 hours", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(vertical = 8.dp))
                Row {
                    listOf("#4F46E5", "#7C3AED", "#DB2777", "#EA580C", "#059669").forEach { value ->
                        Box(Modifier.padding(5.dp).size(38.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(value))).clickable { color = value }.then(if (color == value) Modifier.border(3.dp, Color.White, CircleShape) else Modifier))
                    }
                }
            }
        },
        confirmButton = { Button({ media?.let { vm.addMediaStory(text, color, it) } ?: vm.addStory(text, color); close() }, enabled = text.isNotBlank() || media != null) { Text("Publish") } },
        dismissButton = { TextButton(close) { Text("Cancel") } },
    )
}

@Composable
private fun StoryViewer(
    story: Story,
    stories: List<Story>,
    selectedIndex: Int,
    myId: String?,
    close: () -> Unit,
    previous: (() -> Unit)?,
    next: (() -> Unit)?,
    delete: () -> Unit,
) {
    val background = runCatching { Color(android.graphics.Color.parseColor(story.backgroundColor)) }.getOrDefault(Blue)
    val storyProgress = remember(story.id) { Animatable(0f) }
    val timeoutAction by rememberUpdatedState(next ?: close)
    LaunchedEffect(story.id) {
        storyProgress.snapTo(0f)
        storyProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 15_000, easing = LinearEasing),
        )
        timeoutAction()
    }
    Dialog(close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when {
                story.mediaType == "image" && story.mediaUrl != null -> AsyncImage(story.mediaUrl, story.caption, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                story.mediaType == "video" && story.mediaUrl != null -> AndroidView(
                    factory = { ctx -> android.widget.VideoView(ctx).apply { setVideoURI(android.net.Uri.parse(story.mediaUrl)); setOnPreparedListener { start() } } },
                    modifier = Modifier.fillMaxSize(),
                )
                else -> Box(Modifier.fillMaxSize().background(background), contentAlignment = Alignment.Center) {
                    Text(story.caption.orEmpty(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(32.dp))
                }
            }
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                stories.forEachIndexed { index, _ ->
                    Box(
                        Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = .35f))
                    ) {
                        val progress = when {
                            index < selectedIndex -> 1f
                            index == selectedIndex -> storyProgress.value
                            else -> 0f
                        }
                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(Color.White))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(Profile(story.authorId, story.authorName, story.authorAvatar), 40.dp, true)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text(story.authorName, color = Color.White, fontWeight = FontWeight.Bold); Text("15-second story", color = Color.White.copy(alpha = .7f), fontSize = 11.sp) }
                if (story.authorId == myId) IconButton(delete) { Icon(Icons.Default.Delete, "Delete", tint = Color.White) }
                IconButton(close) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            }
            previous?.let {
                FilledIconButton(
                    onClick = it,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = .42f)),
                    modifier = Modifier.align(Alignment.CenterStart).padding(12.dp),
                ) { Icon(Icons.Default.ChevronLeft, "Previous story", tint = Color.White) }
            }
            next?.let {
                FilledIconButton(
                    onClick = it,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = .42f)),
                    modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp),
                ) { Icon(Icons.Default.ChevronRight, "Next story", tint = Color.White) }
            }
            if (story.mediaType != "text" && !story.caption.isNullOrBlank()) Text(story.caption, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().fillMaxWidth().background(Color.Black.copy(alpha = .55f)).padding(20.dp), color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun RoomSettingsDialog(state: ChatUiState, vm: ChatViewModel, close: () -> Unit) {
    val room = state.selectedRoom ?: return
    val isAdmin = state.profile?.id in room.admins
    var name by remember(room.row.id) { mutableStateOf(room.displayName) }
    var description by remember(room.row.id) { mutableStateOf(room.row.description.orEmpty()) }
    var selected by remember { mutableStateOf(state.roomMemberships.map { it.userId }.toSet()) }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var deleteConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(state.roomMemberships) { selected = state.roomMemberships.map { it.userId }.toSet() }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (room.type == "personal") "Personal space" else "Conversation info") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                Surface(color = SoftBlue, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(room.displayName, fontWeight = FontWeight.Bold)
                        Text(when (room.type) { "direct" -> "Private one-to-one conversation"; "personal" -> "Only you can access this space"; else -> "${room.members.size} members" }, color = Muted, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (room.type == "group" && isAdmin) {
                    Field(name, { name = it }, "Group name")
                    Field(description, { description = it }, "Description")
                    Text("Members", fontWeight = FontWeight.Bold)
                    state.people.forEach { person ->
                        Row(Modifier.fillMaxWidth().clickable { selected = if (person.id in selected) selected - person.id else selected + person.id }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(person, 34.dp)
                            Text("${person.name}  @${person.username}", Modifier.weight(1f).padding(8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Checkbox(person.id in selected, { checked -> selected = if (checked) selected + person.id else selected - person.id })
                        }
                    }
                    Button({ vm.updateRoom(name, description, selected); close() }, modifier = Modifier.fillMaxWidth()) { Text("Save conversation") }
                    HorizontalDivider(Modifier.padding(vertical = 14.dp))
                    Text("Administrators", fontWeight = FontWeight.Bold)
                    room.members.forEach { person ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(person.name, Modifier.weight(1f))
                            Switch(person.id in room.admins, { vm.toggleAdmin(person.id, it) }, enabled = person.id != state.profile?.id)
                        }
                    }
                }
                if (room.type == "personal") {
                    Text("Password protection", fontWeight = FontWeight.Bold)
                    Text(if (state.roomAccess?.hasPassword == true) "This space is password protected." else "Add a password to protect saved notes and files.", fontSize = 12.sp, color = Muted)
                    Spacer(Modifier.height(10.dp))
                    Field(password, { password = it }, "New password", true)
                    Field(confirmation, { confirmation = it }, "Confirm password", true)
                    Button({ if (password == confirmation) vm.setPersonalPassword(password) else vm.reportError("Passwords do not match") }, enabled = password.length >= 6, modifier = Modifier.fillMaxWidth()) { Text(if (state.roomAccess?.hasPassword == true) "Change password" else "Set password") }
                    if (state.roomAccess?.hasPassword == true) {
                        OutlinedButton({ vm.lockPersonalRoom(); close() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Lock, null); Text(" Lock now") }
                        TextButton(vm::removePersonalPassword, modifier = Modifier.fillMaxWidth()) { Text("Remove password", color = Color(0xFFBE123C)) }
                    }
                }
                if (room.type == "direct") {
                    Text("Members", fontWeight = FontWeight.Bold)
                    room.members.forEach { person -> Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Avatar(person, 40.dp); Column(Modifier.padding(8.dp)) { Text(person.name, fontWeight = FontWeight.SemiBold); Text("@${person.username}", fontSize = 11.sp, color = Muted) } } }
                }
                if (room.type == "group" && room.row.createdBy == state.profile?.id) {
                    HorizontalDivider(Modifier.padding(vertical = 14.dp))
                    if (deleteConfirm) Button({ vm.deleteCurrentRoom(); close() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBE123C)), modifier = Modifier.fillMaxWidth()) { Text("Confirm delete group") }
                    else TextButton({ deleteConfirm = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete group", color = Color(0xFFBE123C)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(close) { Text("Close") } },
    )
}
