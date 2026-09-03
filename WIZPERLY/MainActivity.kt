package com.maphutimoviousteffo.wizprly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maphutimoviousteffo.wizprly.ui.screens.ChatListScreen
import com.maphutimoviousteffo.wizprly.ui.screens.ChatScreen
import com.maphutimoviousteffo.wizprly.ui.screens.NewChatScreen
import com.maphutimoviousteffo.wizprly.ui.screens.ProfileScreen
import com.maphutimoviousteffo.wizprly.ui.theme.WizPrlyTheme
import com.maphutimoviousteffo.wizprly.ui.viewmodel.ChatViewModel
import com.maphutimoviousteffo.wizprly.utils.NotificationHelper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.createNotificationChannel(this)

        setContent {
            val viewModel: ChatViewModel = viewModel()
            val isDarkMode by viewModel.isDarkMode.collectAsState()

            WizPrlyTheme(darkTheme = isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    WizPrlyApp(viewModel)
                }
            }
        }
    }
}

enum class Screen {
    LIST, CHAT, NEW_CHAT, PROFILE
}

data class AppState(val screen: Screen, val chatId: String? = null)

@Composable
fun WizPrlyApp(viewModel: ChatViewModel) {
    var appState by remember { mutableStateOf(AppState(Screen.LIST)) }

    AnimatedContent(
        targetState = appState,
        transitionSpec = {
            fadeIn(tween(400)) togetherWith fadeOut(tween(400))
        },
        label = "ScreenTransition"
    ) { state ->
        when (state.screen) {
            Screen.NEW_CHAT -> {
                NewChatScreen(
                    onBack = { appState = AppState(Screen.LIST) },
                    onChatCreated = { id ->
                        appState = AppState(Screen.CHAT, id)
                    },
                    viewModel = viewModel
                )
            }
            Screen.CHAT -> {
                ChatScreen(
                    chatId = state.chatId!!,
                    onBack = { appState = AppState(Screen.LIST) },
                    viewModel = viewModel
                )
            }
            Screen.PROFILE -> {
                ProfileScreen(
                    onBack = { appState = AppState(Screen.LIST) },
                    viewModel = viewModel
                )
            }
            Screen.LIST -> {
                ChatListScreen(
                    onChatClick = { id -> appState = AppState(Screen.CHAT, id) },
                    onNewChat = { appState = AppState(Screen.NEW_CHAT) },
                    onProfileClick = { appState = AppState(Screen.PROFILE) },
                    viewModel = viewModel
                )
            }
        }
    }
}
