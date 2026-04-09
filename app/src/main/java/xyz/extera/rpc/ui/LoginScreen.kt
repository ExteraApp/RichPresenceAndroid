package xyz.extera.rpc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.extera.rpc.data.CredentialsStore
import xyz.extera.rpc.data.MatrixApi

/**
 * Login screen supporting two modes:
 * 1. Matrix ID + Password (resolves homeserver via .well-known)
 * 2. Homeserver URL + Username + Password
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // false = Matrix ID mode, true = manual homeserver mode
    var useManualHomeserver by remember { mutableStateOf(false) }

    // Fields
    var matrixId by remember { mutableStateOf("") }
    var homeserverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // State
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Login to Matrix") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            if (useManualHomeserver) {
                // Manual mode: homeserver URL + username + password
                OutlinedTextField(
                    value = homeserverUrl,
                    onValueChange = { homeserverUrl = it },
                    label = { Text("Homeserver URL") },
                    placeholder = { Text("https://matrix.org") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    placeholder = { Text("alice") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Matrix ID mode: @user:server + password
                OutlinedTextField(
                    value = matrixId,
                    onValueChange = { matrixId = it },
                    label = { Text("Matrix ID") },
                    placeholder = { Text("@user:example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            // Toggle between login modes
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Manual homeserver", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = useManualHomeserver,
                    onCheckedChange = { useManualHomeserver = it }
                )
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = {
                    errorMessage = null
                    isLoading = true
                    scope.launch {
                        try {
                            if (useManualHomeserver) {
                                // Manual homeserver mode
                                if (homeserverUrl.isBlank() || username.isBlank() || password.isBlank()) {
                                    errorMessage = "All fields are required"
                                    isLoading = false
                                    return@launch
                                }
                                val result = MatrixApi.login(homeserverUrl.trim(), username.trim(), password)
                                result.fold(
                                    onSuccess = { credentials ->
                                        CredentialsStore.saveCredentials(context, credentials)
                                        onLoginSuccess()
                                    },
                                    onFailure = { e ->
                                        errorMessage = e.message ?: "Login failed"
                                    }
                                )
                            } else {
                                // Matrix ID mode
                                val id = matrixId.trim()
                                if (!id.startsWith("@") || !id.contains(":")) {
                                    errorMessage = "Invalid Matrix ID. Expected format: @user:server.com"
                                    isLoading = false
                                    return@launch
                                }
                                if (password.isBlank()) {
                                    errorMessage = "Password is required"
                                    isLoading = false
                                    return@launch
                                }

                                val localpart = id.substring(1, id.indexOf(':'))
                                val serverName = id.substring(id.indexOf(':') + 1)

                                // Resolve homeserver via .well-known
                                val resolveResult = MatrixApi.resolveHomeserver(serverName)
                                val resolvedUrl = resolveResult.getOrElse {
                                    errorMessage = "Failed to resolve homeserver: ${it.message}"
                                    isLoading = false
                                    return@launch
                                }

                                val loginResult = MatrixApi.login(resolvedUrl, localpart, password)
                                loginResult.fold(
                                    onSuccess = { credentials ->
                                        CredentialsStore.saveCredentials(context, credentials)
                                        onLoginSuccess()
                                    },
                                    onFailure = { e ->
                                        errorMessage = e.message ?: "Login failed"
                                    }
                                )
                            }
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Unexpected error"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Login")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
