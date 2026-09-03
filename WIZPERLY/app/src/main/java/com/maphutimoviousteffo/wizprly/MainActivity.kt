package com.maphutimoviousteffo.wizprly

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maphutimoviousteffo.wizprly.ui.screens.ChatListScreen
import com.maphutimoviousteffo.wizprly.ui.screens.ChatScreen
import com.maphutimoviousteffo.wizprly.ui.screens.NewChatScreen
import com.maphutimoviousteffo.wizprly.ui.screens.OnboardingScreen
import com.maphutimoviousteffo.wizprly.ui.theme.WizPrlyTheme
import com.maphutimoviousteffo.wizprly.ui.viewmodel.ChatViewModel
import com.maphutimoviousteffo.wizprly.utils.NotificationHelper
import com.maphutimoviousteffo.wizprly.utils.InactivityCheckWorker
import androidx.compose.animation.*
import androidx.compose.animation.core.tween

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {
    private val notificationChatIdState = MutableStateFlow<String?>(null)
    private val notificationAutoStartCallState = MutableStateFlow<Boolean>(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        NotificationHelper.createNotificationChannel(this)
        InactivityCheckWorker.scheduleBackgroundInactivityCheck(this)

        // REQUEST NOTIFICATION PERMISSION FOR ANDROID 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Handle initial notification click
        intent?.getStringExtra("chatId")?.let { id ->
            notificationChatIdState.value = id
            notificationAutoStartCallState.value = intent.getBooleanExtra("autoStartCall", false)
        }

        setContent {
            val viewModel: ChatViewModel = viewModel()
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            val themeColorInt by viewModel.themeColor.collectAsState()
            val useDynamicColor by viewModel.useDynamicColor.collectAsState()
            val initialNotificationChatId by notificationChatIdState.collectAsState()
            val initialAutoStartCall by notificationAutoStartCallState.collectAsState()
            
            val sharedPrefs = remember { getSharedPreferences("wizprly_prefs", MODE_PRIVATE) }
            var showOnboarding by remember { mutableStateOf(!sharedPrefs.getBoolean("onboarding_done", false)) }

            WizPrlyTheme(
                darkTheme = isDarkMode, 
                dynamicColor = useDynamicColor,
                primaryColor = androidx.compose.ui.graphics.Color(themeColorInt)
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (showOnboarding) {
                        OnboardingScreen(onFinished = {
                            sharedPrefs.edit().putBoolean("onboarding_done", true).apply()
                            showOnboarding = false
                        })
                    } else {
                        WizPrlyApp(
                            viewModel = viewModel, 
                            initialChatId = initialNotificationChatId,
                            initialAutoStartCall = initialAutoStartCall
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("chatId")?.let { id ->
            notificationChatIdState.value = id
            notificationAutoStartCallState.value = intent.getBooleanExtra("autoStartCall", false)
        }
    }
}

data class ScreenState(val isNewChat: Boolean, val chatId: String?, val isProfile: Boolean = false)

@Composable
fun WizPrlyApp(viewModel: ChatViewModel, initialChatId: String? = null, initialAutoStartCall: Boolean = false) {
    var currentChatId by remember { mutableStateOf<String?>(initialChatId) }
    var autoStartCallForChat by remember { mutableStateOf(initialAutoStartCall) }
    var showNewChat by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }

    LaunchedEffect(initialChatId, initialAutoStartCall) {
        if (initialChatId != null) {
            currentChatId = initialChatId
            autoStartCallForChat = initialAutoStartCall
            viewModel.loadMessages(initialChatId)
        }
    }

    // BACK NAVIGATION HANDLER
    BackHandler(enabled = currentChatId != null || showNewChat || showProfile) {
        when {
            showProfile -> showProfile = false
            showNewChat -> showNewChat = false
            currentChatId != null -> currentChatId = null
        }
    }

    AnimatedContent(
        targetState = ScreenState(showNewChat, currentChatId, showProfile),
        transitionSpec = {
            fadeIn(tween(400)) togetherWith fadeOut(tween(400))
        },
        label = "ScreenTransition"
    ) { state ->
        when {
            state.isProfile -> {
                com.maphutimoviousteffo.wizprly.ui.screens.ProfileScreen(
                    onBack = { showProfile = false },
                    viewModel = viewModel
                )
            }
            state.isNewChat -> {
                NewChatScreen(
                    onBack = { showNewChat = false },
                    onChatCreated = { id ->
                        showNewChat = false
                        currentChatId = id
                    },
                    viewModel = viewModel
                )
            }
            state.chatId != null -> {
                ChatScreen(
                    chatId = state.chatId,
                    onBack = { currentChatId = null },
                    viewModel = viewModel,
                    initialAutoStartCall = autoStartCallForChat
                )
            }
            else -> {
                ChatListScreen(
                    onChatClick = { id -> 
                        autoStartCallForChat = false
                        currentChatId = id 
                    },
                    onNewChat = { showNewChat = true },
                    onProfileClick = { showProfile = true },
                    viewModel = viewModel
                )
            }
        }
    }
}
