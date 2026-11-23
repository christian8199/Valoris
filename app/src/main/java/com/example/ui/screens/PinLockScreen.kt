package com.example.ui.screens

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.R

fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}

fun isBiometricAvailable(context: Context): Boolean {
    val manager = BiometricManager.from(context)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.BIOMETRIC_WEAK
    return manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
}

fun launchBiometricPrompt(
    activity: FragmentActivity,
    title: String = "Unlock Valoris",
    subtitle: String = "Use your fingerprint or biometrics to access your portfolio",
    onSuccess: () -> Unit,
    onPromptStateChanged: (Boolean) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    onPromptStateChanged(true)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onPromptStateChanged(false)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onPromptStateChanged(false)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        .setNegativeButtonText("Use PIN")
        .setConfirmationRequired(false)
        .build()

    prompt.authenticate(promptInfo)
}

@Composable
fun PinLockScreen(
    biometricEnabled: Boolean,
    errorMessage: String?,
    lockoutRemainingSeconds: Int = 0,
    onPinEntered: (String) -> Unit,
    onPinTypingChanged: () -> Unit,
    onBiometricUnlockSuccess: () -> Unit,
    onBiometricPromptStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    BackHandler(enabled = true) {
        activity?.moveTaskToBack(true)
    }
    val canUseBiometric = remember(context, biometricEnabled) {
        biometricEnabled && activity != null && isBiometricAvailable(context)
    }

    var enteredPin by remember { mutableStateOf("") }
    var hasAutoPrompted by remember { mutableStateOf(false) }

    LaunchedEffect(canUseBiometric) {
        if (canUseBiometric && !hasAutoPrompted && activity != null) {
            hasAutoPrompted = true
            launchBiometricPrompt(
                activity = activity,
                onSuccess = onBiometricUnlockSuccess,
                onPromptStateChanged = onBiometricPromptStateChanged
            )
        }
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            enteredPin = ""
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("pin_lock_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(
                        modifier = Modifier.size(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.valoris_minimal_fg_1795082083044),
                            contentDescription = "Valoris Vault Lock",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Valoris Locked",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Text(
                    text = if (canUseBiometric) {
                        "Enter your 4-digit PIN or use fingerprint"
                    } else {
                        "Enter your 4-digit PIN to unlock"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 4-Digit PIN Dot Indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { idx ->
                        val filled = idx < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        errorMessage != null -> MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
                                        filled -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = when {
                                        errorMessage != null -> MaterialTheme.colorScheme.error
                                        filled -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.outline
                                    },
                                    shape = CircleShape
                                )
                        )
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // Tactile Numeric Keypad
            val isLockedOut = lockoutRemainingSeconds > 0
            Column(
                modifier = Modifier.widthIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val onDigit: (String) -> Unit = { digit ->
                    if (!isLockedOut && enteredPin.length < 4) {
                        onPinTypingChanged()
                        val next = enteredPin + digit
                        enteredPin = next
                        if (next.length == 4) {
                            onPinEntered(next)
                        }
                    }
                }

                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )
                rows.forEach { rowDigits ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        rowDigits.forEach { d ->
                            KeypadDigitButton(
                                digit = d,
                                enabled = !isLockedOut,
                                onClick = { onDigit(d) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canUseBiometric && activity != null) {
                        Surface(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .clickable {
                                    launchBiometricPrompt(
                                        activity = activity,
                                        onSuccess = onBiometricUnlockSuccess,
                                        onPromptStateChanged = onBiometricPromptStateChanged
                                    )
                                }
                                .testTag("lock_biometric_button"),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Fingerprint,
                                    contentDescription = "Unlock with fingerprint",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.size(72.dp))
                    }

                    KeypadDigitButton(
                        digit = "0",
                        enabled = !isLockedOut,
                        onClick = { onDigit("0") }
                    )

                    Surface(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .clickable(enabled = !isLockedOut && enteredPin.isNotEmpty()) {
                                if (enteredPin.isNotEmpty()) {
                                    enteredPin = enteredPin.dropLast(1)
                                }
                            }
                            .testTag("lock_backspace_button"),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Delete digit",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun KeypadDigitButton(
    digit: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag("pin_key_$digit"),
        shape = CircleShape,
        color = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = digit,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

enum class PinDialogMode {
    ENABLE,
    CHANGE,
    DISABLE
}

@Composable
fun PinSetupDialog(
    mode: PinDialogMode,
    biometricAvailable: Boolean,
    initialBiometricEnabled: Boolean,
    onConfirmEnable: (pin: String, biometricEnabled: Boolean) -> Unit,
    onConfirmChange: (currentPin: String, newPin: String, onError: (String) -> Unit) -> Unit,
    onConfirmDisable: (currentPin: String, onError: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var biometricChecked by remember { mutableStateOf(biometricAvailable && initialBiometricEnabled) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val title = when (mode) {
        PinDialogMode.ENABLE -> "Set 4-Digit App PIN"
        PinDialogMode.CHANGE -> "Change App PIN"
        PinDialogMode.DISABLE -> "Disable App Lock"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (mode == PinDialogMode.CHANGE || mode == PinDialogMode.DISABLE) {
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = { input ->
                            currentPin = input.filter { it.isDigit() }.take(4)
                            errorText = null
                        },
                        label = { Text("Current 4-digit PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_dialog_current_input")
                    )
                }

                if (mode == PinDialogMode.ENABLE || mode == PinDialogMode.CHANGE) {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { input ->
                            newPin = input.filter { it.isDigit() }.take(4)
                            errorText = null
                        },
                        label = { Text(if (mode == PinDialogMode.CHANGE) "New 4-digit PIN" else "4-digit PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_dialog_new_input")
                    )
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { input ->
                            confirmPin = input.filter { it.isDigit() }.take(4)
                            errorText = null
                        },
                        label = { Text("Confirm 4-digit PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_dialog_confirm_input")
                    )
                }

                if (mode == PinDialogMode.ENABLE && biometricAvailable) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fingerprint Unlock",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Use biometrics instead of typing PIN",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = biometricChecked,
                            onCheckedChange = { biometricChecked = it }
                        )
                    }
                }

                errorText?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (mode) {
                        PinDialogMode.ENABLE -> {
                            when {
                                newPin.length != 4 -> errorText = "Enter a 4-digit PIN."
                                newPin != confirmPin -> errorText = "PINs do not match."
                                else -> onConfirmEnable(newPin, biometricChecked)
                            }
                        }
                        PinDialogMode.CHANGE -> {
                            when {
                                currentPin.length != 4 -> errorText = "Enter your current 4-digit PIN."
                                newPin.length != 4 -> errorText = "Enter a new 4-digit PIN."
                                newPin != confirmPin -> errorText = "New PINs do not match."
                                else -> onConfirmChange(currentPin, newPin) { msg -> errorText = msg }
                            }
                        }
                        PinDialogMode.DISABLE -> {
                            if (currentPin.length != 4) {
                                errorText = "Enter your current 4-digit PIN."
                            } else {
                                onConfirmDisable(currentPin) { msg -> errorText = msg }
                            }
                        }
                    }
                },
                modifier = Modifier.testTag("pin_dialog_confirm_button")
            ) {
                Text(
                    when (mode) {
                        PinDialogMode.ENABLE -> "Enable Lock"
                        PinDialogMode.CHANGE -> "Update PIN"
                        PinDialogMode.DISABLE -> "Disable"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
