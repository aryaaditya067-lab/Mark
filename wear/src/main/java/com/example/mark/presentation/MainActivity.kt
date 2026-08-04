package com.example.mark.presentation

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.MaterialTheme
import com.example.mark.repository.AuthRepository
import com.example.mark.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.i("MarkTTS", "MainActivity onCreate started.")

        // NOTE: FirebaseApp.initializeApp() and NetworkProvider.start() are NOT
        // called here — WearApplication.onCreate already does both, on an IO
        // dispatcher. Doing them again on the main thread just delayed first frame.

        setContent {
            MaterialTheme {
                WearRoot(applicationContext)
            }
        }
    }
}

@Composable
private fun WearRoot(appContext: Context) {
    val auth = remember { AuthRepository.instance }

    // The assistant must never wait on the network to appear.
    //
    // Previously this screen started in a CHECKING state showing only a spinner
    // while trySilentSignIn() ran with no timeout. On the watch that call goes
    // out over Bluetooth and can take a minute or more, and until it returned
    // the orb, the greeting and the microphone did not exist. That was the
    // 1-2 minute freeze on launch.
    //
    // Sign-in only gates chat-history sync. Offline commands — which is the vast
    // majority of what Mark does — need nothing from it. So the UI starts
    // immediately and auth settles in the background.
    var signedIn by remember { mutableStateOf(auth.isSignedIn) }

    LaunchedEffect(Unit) {
        if (!signedIn) {
            val started = System.currentTimeMillis()
            val uid = withTimeoutOrNull(4000) {
                withContext(Dispatchers.IO) {
                    auth.trySilentSignIn(appContext, Constants.WEB_CLIENT_ID)
                }
            }
            val ms = System.currentTimeMillis() - started
            if (uid != null) {
                signedIn = true
                android.util.Log.d("MarkAuth", "silent sign-in ok in ${ms}ms")
            } else {
                // Not fatal. History sync is unavailable; everything else works.
                android.util.Log.d("MarkAuth", "silent sign-in unavailable after ${ms}ms — continuing offline")
            }
        }
    }

    val vm: WearChatViewModel = viewModel()
    WearChatScreen(vm)
}