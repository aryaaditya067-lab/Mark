package com.example.mark.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.mark.repository.AuthRepository
import com.example.mark.utils.Constants
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * One-time interactive sign-in on the watch itself.
 * After this the token is cached, so silent sign-in works on later launches.
 */
@Composable
fun WearSignInScreen(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { AuthRepository.instance }

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        scope.launch {
            busy = true
            error = null
            try {
                val account = GoogleSignIn
                    .getSignedInAccountFromIntent(result.data)
                    .await()
                auth.signInWithGoogle(account)
                onSignedIn()
            } catch (e: Exception) {
                error = e.message?.take(60) ?: "Sign-in failed."
            } finally {
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            if (busy) {
                CircularProgressIndicator()
            } else {
                Text(
                    text = "Sign in to Mark",
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    val client = auth.signInClient(context, Constants.WEB_CLIENT_ID)
                    launcher.launch(client.signInIntent)
                }) {
                    Text("Google")
                }
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}