package com.example.mark.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mark.repository.AuthRepository
import com.example.mark.utils.Constants
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * One-time Google sign-in. Links the existing anonymous account so no data
 * is lost, and gives the watch and laptop the same uid to work with.
 */
@Composable
fun SignInScreen(onSignedIn: () -> Unit) {
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
                error = e.message ?: "Sign-in failed."
            } finally {
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Mark",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Sign in so your phone, watch and laptop share the same assistant.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(32.dp))

            if (busy) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        val client = auth.signInClient(context, Constants.WEB_CLIENT_ID)
                        launcher.launch(client.signInIntent)
                    }
                ) {
                    Text("Continue with Google")
                }
            }

            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}