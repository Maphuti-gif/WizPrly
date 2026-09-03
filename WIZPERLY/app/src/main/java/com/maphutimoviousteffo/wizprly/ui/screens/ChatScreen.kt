package com.maphutimoviousteffo.wizprly.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.database.Cursor
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.maphutimoviousteffo.wizprly.data.Chat
import com.maphutimoviousteffo.wizprly.data.Message
import com.maphutimoviousteffo.wizprly.ui.viewmodel.ChatViewModel
import com.maphutimoviousteffo.wizprly.utils.formatTime
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    chatId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel(),
    initialAutoStartCall: Boolean = false
) {
    // 1. STATE & UTILS
    var input by remember { mutableStateOf("") }
    val messages by viewModel.messages.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val voiceEnabled by viewModel.voiceFeedbackEnabled.collectAsState()
    val chats by viewModel.chats.collectAsState()
    val globalBackground by viewModel.globalBackgroundUri.collectAsState()
    val isAiRecording by viewModel.isAiRecording.collectAsState()
    val chat = chats.find { it.id == chatId }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val sharedPrefs = remember { context.getSharedPreferences("wizprly_prefs", Context.MODE_PRIVATE) }
    
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newChatName by remember { mutableStateOf(chat?.name ?: "") }
    var isCalling by remember { mutableStateOf(initialAutoStartCall) }
    var isCallMinimized by remember { mutableStateOf(false) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val autoPlayer = remember { MediaPlayer() }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var partialText by remember { mutableStateOf("") }
    var showCompanionInvite by remember { mutableStateOf(false) }

    // VOICE NOTE STATE
    var isRecording by remember { mutableStateOf(false) }
    var recordingTimer by remember { mutableIntStateOf(0) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    var recordingTranscription by remember { mutableStateOf("") }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    // FUNCTIONS
    fun setSystemMute(mute: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_SYSTEM,
                    if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                    0
                )
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_NOTIFICATION,
                    if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                    0
                )
            } else {
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, mute)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun startListeningSilently() {
        // CIVILIZED TURN-TAKING: Only listen if AI is not thinking or speaking
        if (!isCalling || isMuted || isSending || tts?.isSpeaking == true || autoPlayer.isPlaying || isListening) return
        try {
            setSystemMute(true)
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(p: android.os.Bundle?) { 
                            (context as? Activity)?.runOnUiThread { 
                                isListening = true 
                                setSystemMute(false)
                            } 
                        }
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(r: Float) {}
                        override fun onBufferReceived(b: ByteArray?) {}
                        override fun onEndOfSpeech() { 
                            (context as? Activity)?.runOnUiThread { isListening = false } 
                        }
                        override fun onError(e: Int) { 
                            (context as? Activity)?.runOnUiThread { 
                                isListening = false 
                                setSystemMute(false)
                                if (isCalling && !isSending && !autoPlayer.isPlaying && tts?.isSpeaking != true) {
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        startListeningSilently()
                                    }, 400)
                                }
                            }
                        }
                        override fun onResults(r: android.os.Bundle?) {
                            val text = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: ""
                            (context as? Activity)?.runOnUiThread {
                                isListening = false
                                partialText = ""
                                setSystemMute(false)
                                if (text.isNotBlank() && isCalling) {
                                    viewModel.sendMessage(text, isFromCall = true)
                                } else if (isCalling && !isSending && !autoPlayer.isPlaying && tts?.isSpeaking != true) {
                                    startListeningSilently()
                                }
                            }
                        }
                        override fun onPartialResults(p: android.os.Bundle?) {
                            val text = p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: ""
                            (context as? Activity)?.runOnUiThread { partialText = text }
                        }
                        override fun onEvent(ev: Int, p: android.os.Bundle?) {}
                    })
                }
            }
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) { 
            setSystemMute(false)
            isListening = false
        }
    }

    fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        try {
            audioManager.isSpeakerphoneOn = isSpeakerOn
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Toast.makeText(context, if (isSpeakerOn) "Speaker On" else "Handset Mode", Toast.LENGTH_SHORT).show()
    }

    fun startRecording() {
        try {
            setSystemMute(true)
            val file = File(context.filesDir, "recording_${System.currentTimeMillis()}.m4a")
            audioFile = file
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            
            recordingTranscription = ""
            isRecording = true
            recordingTimer = 0
        } catch (e: Exception) {
            setSystemMute(false)
            Toast.makeText(context, "Recorder error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopRecording(send: Boolean) {
        try {
            setSystemMute(false)
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            isRecording = false
            
            if (send && audioFile != null) {
                viewModel.sendMessage("", 
                    audioUri = Uri.fromFile(audioFile).toString(), 
                    audioDuration = recordingTimer,
                    audioTranscription = null, // Transcription done in ViewModel via Whisper
                    isFromCall = isCalling
                )
            }
        } catch (e: Exception) {
            recorder = null
            isRecording = false
        }
    }

    // EFFECTS
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                delay(1000)
                recordingTimer++
            }
        }
    }

    val activity = LocalContext.current as? Activity
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager }
    val wakeLock = remember { 
        powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "WizPrly:CallWakeLock")
    }

    LaunchedEffect(isCalling) {
        viewModel.setCallActive(isCalling)
        if (isCalling) {
            try {
                activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } catch (e: Exception) {}
            try { wakeLock.acquire(30 * 60 * 1000L) } catch (e: Exception) {}
        } else {
            try {
                activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } catch (e: Exception) {}
            if (wakeLock.isHeld) wakeLock.release()
            isCallMinimized = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    LaunchedEffect(isCalling, isSending, isMuted, isListening) {
        if (isCalling && !isSending && !isMuted && !isListening && tts?.isSpeaking != true && !autoPlayer.isPlaying) {
            delay(100) // Immediate restart for "Flow"
            startListeningSilently()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, false)
            audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, false)
            audioManager.setStreamMute(AudioManager.STREAM_ALARM, false)
            audioManager.setStreamMute(AudioManager.STREAM_RING, false)
            autoPlayer.release()
        }
    }

    // TTS INIT
    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val aiGender = chat?.aiGender ?: "neutral"
                val preferredVoice = tts?.voices?.find { 
                    val name = it.name.lowercase()
                    if (aiGender == "female") name.contains("female") || name.contains("soft") || name.contains("en-us-x-sfg")
                    else if (aiGender == "male") name.contains("male") || name.contains("en-us-x-iol")
                    else false
                }
                preferredVoice?.let { tts?.voice = it } ?: run { tts?.language = Locale.US }

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        (context as? Activity)?.runOnUiThread { isListening = false }
                    }
                    override fun onDone(utteranceId: String?) {
                        setSystemMute(false)
                        if (isCalling) (context as? Activity)?.runOnUiThread { startListeningSilently() }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        setSystemMute(false)
                        if (isCalling) (context as? Activity)?.runOnUiThread { startListeningSilently() }
                    }
                })
            }
        }
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    // AUTO-PLAY LOGIC
    LaunchedEffect(messages.lastOrNull()) {
        val lastMsg = messages.lastOrNull()
        if (lastMsg?.isAI == true && voiceEnabled) {
            if (isCalling) {
                audioManager.isSpeakerphoneOn = isSpeakerOn
                if (isSpeakerOn) {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                }

                if (lastMsg.audioUri != null) {
                    val isMasterRecord = lastMsg.content.startsWith("Full Call Record")
                    // Auto-play ONLY if in call
                    if (!isMasterRecord) {
                        try {
                            speechRecognizer?.cancel()
                            isListening = false
                            delay(200) // Brief pause to prevent first words from being truncated by audio stream switching
                            autoPlayer.reset()
                            autoPlayer.setDataSource(context, Uri.parse(lastMsg.audioUri))
                            autoPlayer.prepare()
                            autoPlayer.start()
                            autoPlayer.setOnCompletionListener {
                                if (isCalling) startListeningSilently()
                            }
                        } catch (e: Exception) {
                            speechRecognizer?.cancel()
                            isListening = false
                            delay(200)
                            val params = android.os.Bundle().apply {
                                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "ai_response")
                                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_VOICE_CALL)
                            }
                            tts?.speak(stripEmojis(lastMsg.content), TextToSpeech.QUEUE_FLUSH, params, "ai_response")
                        }
                    }
                } else {
                    // Fallback to TTS only for calls if audioUri is null
                    speechRecognizer?.cancel()
                    isListening = false
                    delay(200)
                    val params = android.os.Bundle().apply {
                        putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "ai_response")
                        putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_VOICE_CALL)
                    }
                    tts?.speak(stripEmojis(lastMsg.content), TextToSpeech.QUEUE_FLUSH, params, "ai_response")
                }
            }
        }
    }

    // LAUNCHERS
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) isCalling = true }
    val bgPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let { viewModel.setChatBackground(chatId, it.toString()) } }
    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let { viewModel.sendMessage("", imageUri = it.toString()) } }
    val docPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { viewModel.sendMessage("", fileUri = it.toString(), fileName = getFileName(context, it)) } }

    // UI SYNC
    LaunchedEffect(chatId) { viewModel.loadMessages(chatId) }
    DisposableEffect(chatId) { viewModel.setChatScreenActive(true); onDispose { viewModel.setChatScreenActive(false) } }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1) }

    val isKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(isKeyboardOpen) { if (isKeyboardOpen && messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    // UI ROOT
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0,0,0,0),
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            modifier = Modifier.clickable { viewModel.recordActivity() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(chat?.name?.take(1)?.uppercase() ?: "C", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Box(modifier = Modifier.size(10.dp).align(Alignment.BottomEnd).clip(CircleShape).background(if (chat?.isOnline == true) Color(0xFF10B981) else Color.Gray).border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                Text(chat?.name ?: "Chat", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                AnimatedContent(targetState = Triple(isSending, loading, isAiRecording), label = "TypingIndicator") { (sending, typing, aiRec) ->
                                    when {
                                        aiRec -> Text("Recording...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        sending || typing -> Text("Texting...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        else -> Text(if (chat?.isOnline == true) "Online" else "Offline", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                    actions = {
                        IconButton(onClick = { if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) isCalling = true else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                            Icon(Icons.Default.Call, contentDescription = "Call", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Rename Chat") }, onClick = { showMenu = false; showRenameDialog = true }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                            DropdownMenuItem(text = { Text("Clear Chat") }, onClick = { showMenu = false; viewModel.clearChat(chatId) }, leadingIcon = { Icon(Icons.Default.ClearAll, null) })
                            DropdownMenuItem(text = { Text("Delete Chat") }, onClick = { showMenu = false; viewModel.deleteChat(chatId); onBack() }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                            DropdownMenuItem(text = { Text("Set Background") }, onClick = { showMenu = false; bgPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, leadingIcon = { Icon(Icons.Default.Wallpaper, null) })
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding).statusBarsPadding().navigationBarsPadding().imePadding()) {
                val backgroundToUse = chat?.backgroundUri ?: globalBackground
                if (backgroundToUse != null) {
                    Image(painter = rememberAsyncImagePainter(backgroundToUse), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        items(messages) { msg ->
                            MessageBubble(
                                message = msg, 
                                viewModel = viewModel,
                                onReaction = { viewModel.addReaction(msg.id, it) }, 
                                onReply = { 
                                    input = "[Replying to: ${msg.content.take(20)}...] "
                                    viewModel.recordActivity()
                                }
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = if (isKeyboardOpen) 0.dp else 12.dp)
                            .fillMaxWidth(),
                        shape = if (isKeyboardOpen) RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp) else RoundedCornerShape(28.dp),
                        color = if (isDarkMode) Color(0xB3161625) else Color(0xB3FFFFFF),
                        shadowElevation = if (isKeyboardOpen) 0.dp else 4.dp
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            var showAttachMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showAttachMenu = true }) {
                                    Icon(Icons.Default.Add, contentDescription = "Attach", tint = MaterialTheme.colorScheme.primary)
                                }
                                DropdownMenu(expanded = showAttachMenu, onDismissRequest = { showAttachMenu = false }) {
                                    DropdownMenuItem(text = { Text("Photos") }, onClick = { showAttachMenu = false; photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, leadingIcon = { Icon(Icons.Default.Image, null) })
                                    DropdownMenuItem(text = { Text("Document") }, onClick = { showAttachMenu = false; docPickerLauncher.launch("*/*") }, leadingIcon = { Icon(Icons.Default.Description, null) })
                                }
                            }
                            
                            if (isRecording) {
                                Text("Recording ${String.format(Locale.getDefault(), "%02d:%02d", recordingTimer / 60, recordingTimer % 60)}", color = Color.Red, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), fontWeight = FontWeight.Bold)
                            } else {
                                BasicTextField(
                                    value = input,
                                    onValueChange = { input = it; viewModel.recordActivity() },
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 12.dp),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                    decorationBox = { innerTextField ->
                                        if (input.isEmpty()) Text("Type a message...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                        innerTextField()
                                    }
                                )
                            }

                            if (input.isBlank()) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onPress = {
                                                    if (sharedPrefs.getBoolean("haptics_enabled", true)) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                    startRecording()
                                                    try {
                                                        awaitRelease()
                                                        if (sharedPrefs.getBoolean("haptics_enabled", true)) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        }
                                                        stopRecording(true)
                                                    } catch (e: Exception) {
                                                        stopRecording(false)
                                                    }
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(if (isRecording) Icons.Default.MicNone else Icons.Default.Mic, contentDescription = "Voice", tint = if (isRecording) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            IconButton(onClick = { 
                                if (input.isNotBlank()) { 
                                    if (sharedPrefs.getBoolean("haptics_enabled", true)) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    viewModel.sendMessage(input)
                                    input = "" 
                                } 
                            }, colors = IconButtonDefaults.iconButtonColors(containerColor = if (input.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Transparent)) {
                                Icon(Icons.Default.Send, contentDescription = "Send", tint = if (input.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        if (showRenameDialog) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename Chat") },
                text = { OutlinedTextField(value = newChatName, onValueChange = { newChatName = it }, label = { Text("Name") }) },
                confirmButton = { TextButton(onClick = { viewModel.renameChat(chatId, newChatName); showRenameDialog = false }) { Text("Save") } },
                dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } }
            )
        }

        val activeCompanions by viewModel.activeCompanions.collectAsState()
        if (isCalling) {
            if (!isCallMinimized) {
                VoiceCallOverlay(
                    chatName = chat?.name ?: "WizPrly",
                    activeCompanions = activeCompanions,
                    onRemoveCompanion = { viewModel.removeCompanionFromCall(it) },
                    onEndCall = { 
                        isCalling = false 
                        viewModel.setCallActive(false)
                        isCallMinimized = false
                        isMuted = false
                        speechRecognizer?.stopListening()
                        speechRecognizer?.destroy()
                        speechRecognizer = null
                        tts?.stop()
                        if (autoPlayer.isPlaying) autoPlayer.stop()
                        setSystemMute(false)
                    },
                    onMinimize = { isCallMinimized = true },
                    isMuted = isMuted,
                    onMuteToggle = { isMuted = !isMuted; if (!isMuted) startListeningSilently() else speechRecognizer?.stopListening() },
                    isSpeakerOn = isSpeakerOn,
                    onSpeakerToggle = { toggleSpeaker() },
                    onInviteClick = { showCompanionInvite = true },
                    isListening = isListening,
                    isSending = isSending,
                    partialText = partialText
                )
            } else {
                Surface(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp).clickable { isCallMinimized = false }, color = Color(0xFF10B981), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Call with ${chat?.name ?: "WizPrly"}", color = Color.White, fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.OpenInFull, null, tint = Color.White)
                    }
                }
            }
        }

        if (showCompanionInvite) {
            AlertDialog(onDismissRequest = { showCompanionInvite = false }, title = { Text("Invite Companion") }, text = { LazyColumn { items(chats.filter { it.id != chatId }) { other -> ListItem(headlineContent = { Text(other.name) }, modifier = Modifier.clickable { viewModel.inviteToCall(other.id); showCompanionInvite = false }) } } }, confirmButton = { TextButton(onClick = { showCompanionInvite = false }) { Text("Close") } })
        }
    }
}

@Composable
fun VoiceCallOverlay(
    chatName: String,
    activeCompanions: List<Chat>,
    onRemoveCompanion: (String) -> Unit,
    onEndCall: () -> Unit,
    onMinimize: () -> Unit,
    isMuted: Boolean,
    onMuteToggle: () -> Unit,
    isSpeakerOn: Boolean,
    onSpeakerToggle: () -> Unit,
    onInviteClick: () -> Unit,
    isListening: Boolean,
    isSending: Boolean,
    partialText: String = ""
) {
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); seconds++ } }
    val timeString = String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B141B)) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 64.dp, bottom = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(32.dp).clickable { onMinimize() })
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(chatName, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(if (isSending) "Thinking..." else if (isListening) "Listening..." else timeString, color = Color.White.copy(alpha = 0.7f))
                }
                Icon(Icons.Default.PersonAdd, null, tint = Color.White, modifier = Modifier.size(28.dp).clickable { onInviteClick() })
            }
            Spacer(modifier = Modifier.height(24.dp))
            
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 24.dp)) {
                items(activeCompanions) { comp ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF3B82F6))))
                                    .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(comp.name.take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            }
                            if (activeCompanions.size > 1) {
                                IconButton(
                                    onClick = { onRemoveCompanion(comp.id) },
                                    modifier = Modifier.size(24.dp).background(Color.Red, CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(comp.name, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            
            if (partialText.isNotBlank()) {
                Text("\"$partialText\"", color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(32.dp))
            }

            Spacer(modifier = Modifier.weight(1.2f))

            Surface(modifier = Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(32.dp), color = Color(0xFF1F2C34)) {
                Row(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CallActionItem(icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeMute, label = "Speaker", active = isSpeakerOn, onClick = onSpeakerToggle)
                    CallActionItem(icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, label = "Mute", active = isMuted, onClick = onMuteToggle)
                    FloatingActionButton(onClick = onEndCall, containerColor = Color.Red, contentColor = Color.White, shape = CircleShape) { Icon(Icons.Default.CallEnd, null) }
                }
            }
        }
    }
}

@Composable
fun CallActionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = onClick, shape = CircleShape, color = if (active) Color.White else Color(0xFF3B4A54), modifier = Modifier.size(56.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = if (active) Color.Black else Color.White) }
        }
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(message: Message, viewModel: ChatViewModel, onReaction: (String?) -> Unit, onReply: () -> Unit) {
    if (message.type == "CALL_STATUS") {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp)) {
                Text("${message.content} • ${message.timestamp.formatTime()}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
        return
    }

    val isUser = message.senderId == "user"
    var offsetX by remember { mutableStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    var showEmojiRow by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > 80f || offsetX < -80f) {
                            onReply()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        offsetX = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount).coerceIn(-100f, 100f)
                    }
                )
            },
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        if (offsetX != 0f) {
            Icon(
                Icons.Default.Reply,
                null,
                modifier = Modifier
                    .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 16.dp)
                    .alpha((kotlin.math.abs(offsetX) / 80f).coerceAtMost(1f)),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            Surface(
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
                    .widthIn(max = 280.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showEmojiRow = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                        onDoubleClick = { onReaction("❤️"); haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val isMasterRecord = message.content.startsWith("Full Call Record")
                    if (message.audioUri != null && (message.type == "VOICE_NOTE" || isMasterRecord)) {
                        AudioPlayerComponent(message.id, message.audioDuration, isUser, viewModel)
                    } else {
                        if (message.imageUri != null) {
                            Image(
                                painter = rememberAsyncImagePainter(message.imageUri),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        if (message.content.isNotBlank()) {
                            Text(message.content, color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            message.timestamp.formatTime(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUser) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        if (isUser) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.DoneAll, null, modifier = Modifier.size(12.dp), tint = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
            }

            if (message.reaction != null) {
                Surface(
                    modifier = Modifier.offset(y = (-8).dp, x = if (isUser) (-8).dp else 8.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 2.dp
                ) {
                    Text(message.reaction, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 12.sp)
                }
            }
        }

        if (showEmojiRow) {
            WhatsAppMessageOptionsDialog(
                message = message,
                onDismiss = { showEmojiRow = false },
                onReactionSelected = { reaction ->
                    onReaction(reaction)
                    showEmojiRow = false
                },
                onReplySelected = {
                    onReply()
                    showEmojiRow = false
                },
                onDeleteSelected = {
                    viewModel.deleteSingleMessage(message.id)
                    showEmojiRow = false
                }
            )
        }
    }
}

@Composable
fun AudioPlayerComponent(messageId: String, duration: Int, isUser: Boolean, viewModel: ChatViewModel) {
    val playingId by viewModel.currentlyPlayingMessageId.collectAsState()
    val progress by viewModel.audioPlaybackProgress.collectAsState()
    val speed by viewModel.playbackSpeed.collectAsState()
    
    val isPlayingThis = playingId == messageId

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = { viewModel.playAudio(messageId) }) {
            Icon(
                if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow, 
                null, 
                tint = if (isUser) Color.White else MaterialTheme.colorScheme.primary
            )
        }
        
        Slider(
            value = if (isPlayingThis) progress else 0f,
            onValueChange = { if (isPlayingThis) viewModel.seekAudio(it) },
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = if (isUser) Color.White else MaterialTheme.colorScheme.primary,
                activeTrackColor = if (isUser) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary
            )
        )
        
        if (isPlayingThis) {
            TextButton(
                onClick = { viewModel.togglePlaybackSpeed() },
                modifier = Modifier.width(48.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    "${speed}x",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) Color.White else MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Text(
                String.format(Locale.getDefault(), "%02d:%02d", duration / 60, duration % 60),
                style = MaterialTheme.typography.labelSmall,
                color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun WhatsAppMessageOptionsDialog(
    message: Message,
    onDismiss: () -> Unit,
    onReactionSelected: (String?) -> Unit,
    onReplySelected: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val emojis = listOf("❤️", "😂", "😮", "😢", "🔥", "👍")

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    emojis.forEach { emoji ->
                        Text(
                            emoji,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onReactionSelected(emoji) }
                                .padding(6.dp),
                            fontSize = 24.sp
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                val textToCopy = when {
                    message.content.isNotBlank() -> message.content
                    message.audioTranscription != null -> message.audioTranscription
                    else -> ""
                }

                if (textToCopy.isNotBlank()) {
                    DropdownMenuItem(
                        text = { Text("Copy Text", style = MaterialTheme.typography.bodyLarge) },
                        onClick = {
                            clipboardManager.setText(AnnotatedString(textToCopy))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                    )
                }

                DropdownMenuItem(
                    text = { Text("Reply", style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        onReplySelected()
                        onDismiss()
                    },
                    leadingIcon = { Icon(Icons.Default.Reply, contentDescription = null) }
                )

                if (message.reaction != null) {
                    DropdownMenuItem(
                        text = { Text("Remove Reaction", style = MaterialTheme.typography.bodyLarge) },
                        onClick = {
                            onReactionSelected(null)
                            onDismiss()
                        },
                        leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, contentDescription = null) }
                    )
                }

                DropdownMenuItem(
                    text = { Text("Delete Message", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        onDeleteSelected()
                        onDismiss()
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                )
            }
        }
    )
}

fun stripEmojis(text: String): String {
    val cleaned = if (text.startsWith("[") && text.contains("]: ")) text.substringAfter("]: ") else text
    return cleaned.replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+"), "").trim()
}

fun getFileName(context: Context, uri: Uri): String {
    var name = "Document"
    context.contentResolver.query(uri, null, null, null, null)?.use { if (it.moveToFirst()) { val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (idx != -1) name = it.getString(idx) } }
    return name
}
