package com.myaccounts.app.ui.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.myaccounts.app.security.AppSecurityManager
import com.myaccounts.app.ui.components.InformationCard
import com.myaccounts.app.ui.components.PrimaryButton
import com.myaccounts.app.ui.components.SecondaryButton
import com.myaccounts.app.ui.components.SummaryCard

private const val PIN_LENGTH = 9

@Composable
fun AppLockGate(
    security: AppSecurityManager,
    onUnlocked: () -> Unit
) {
    if (!security.isProtectionEnabled()) {
        onUnlocked()
        return
    }

    if (!security.hasPin() || !security.hasRecoveryEmail()) {
        SecuritySetupScreen(security = security, onCompleted = onUnlocked)
    } else {
        LockScreen(security = security, onUnlocked = onUnlocked)
    }
}

@Composable
private fun SecuritySetupScreen(security: AppSecurityManager, onCompleted: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        SummaryCard(title = "إعداد حماية التطبيق", modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("أنشئ رمز دخول من 9 أرقام وأدخل بريدًا إلكترونيًا لاستعادة الرمز عند نسيانه.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = pin, onValueChange = { if (it.length <= PIN_LENGTH && it.all(Char::isDigit)) { pin = it; error = null } }, modifier = Modifier.fillMaxWidth(), label = { Text("رمز الدخول (9 أرقام)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), singleLine = true, shape = RoundedCornerShape(8.dp))
            OutlinedTextField(value = confirmation, onValueChange = { if (it.length <= PIN_LENGTH && it.all(Char::isDigit)) { confirmation = it; error = null } }, modifier = Modifier.fillMaxWidth(), label = { Text("تأكيد رمز الدخول") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), singleLine = true, shape = RoundedCornerShape(8.dp))
            OutlinedTextField(value = email, onValueChange = { email = it; error = null }, modifier = Modifier.fillMaxWidth(), label = { Text("البريد الإلكتروني للاسترداد") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, shape = RoundedCornerShape(8.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            PrimaryButton(
                text = "حفظ وتفعيل الحماية",
                onClick = {
                    when {
                        pin.length != PIN_LENGTH -> error = "يجب أن يتكون رمز الدخول من 9 أرقام."
                        pin != confirmation -> error = "رمزا الدخول غير متطابقين."
                        !isValidEmail(email) -> error = "أدخل بريدًا إلكترونيًا صحيحًا."
                        else -> { security.saveCredentials(pin, email); onCompleted() }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun LockScreen(security: AppSecurityManager, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showRecovery by remember { mutableStateOf(false) }
    var biometricStarted by remember { mutableStateOf(false) }

    val biometricManager = remember(context) { BiometricManager.from(context) }
    val biometricAvailable = remember(biometricManager) {
        biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    LaunchedEffect(biometricAvailable) {
        if (biometricAvailable && !biometricStarted) {
            biometricStarted = true
            val activity = context as? FragmentActivity ?: return@LaunchedEffect
            val prompt = BiometricPrompt(activity, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onUnlocked() }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { error = null }
            })
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("فتح دفتر الحسابات")
                .setSubtitle("ضع إصبعك على مستشعر البصمة")
                .setNegativeButtonText("استخدام رمز الدخول")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build()
            prompt.authenticate(promptInfo)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        SummaryCard(title = "حماية التطبيق", modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("دفتر الحسابات محمي", style = MaterialTheme.typography.titleLarge)
            if (biometricAvailable) {
                InformationCard {
                    Icon(Icons.Default.Fingerprint, contentDescription = "البصمة", tint = MaterialTheme.colorScheme.primary)
                    Text("استخدم البصمة مباشرة لفتح التطبيق", style = MaterialTheme.typography.bodyLarge)
                }
            }
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= PIN_LENGTH && it.all(Char::isDigit)) { pin = it; error = null } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("رمز الدخول") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
            PrimaryButton(
                text = "دخول",
                onClick = {
                    when {
                        pin.length != PIN_LENGTH -> error = "أدخل رمز الدخول المكون من 9 أرقام."
                        security.verifyPin(pin) -> onUnlocked()
                        else -> { pin = ""; error = "رمز الدخول غير صحيح." }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            SecondaryButton(text = "نسيت رمز الدخول؟", onClick = { showRecovery = true }, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }

    if (showRecovery) {
        RecoveryDialog(security = security, onDismiss = { showRecovery = false }, onRecovered = { showRecovery = false; onUnlocked() })
    }
}

@Composable
private fun RecoveryDialog(security: AppSecurityManager, onDismiss: () -> Unit, onRecovered: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("استعادة رمز الدخول", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("أدخل البريد الإلكتروني المسجل ثم أنشئ رمز دخول جديدًا من 9 أرقام.", style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(value = email, onValueChange = { email = it; error = null }, modifier = Modifier.fillMaxWidth(), label = { Text("البريد الإلكتروني") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, shape = RoundedCornerShape(8.dp))
                OutlinedTextField(value = newPin, onValueChange = { if (it.length <= PIN_LENGTH && it.all(Char::isDigit)) { newPin = it; error = null } }, modifier = Modifier.fillMaxWidth(), label = { Text("رمز الدخول الجديد (9 أرقام)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), singleLine = true, shape = RoundedCornerShape(8.dp))
                OutlinedTextField(value = confirmation, onValueChange = { if (it.length <= PIN_LENGTH && it.all(Char::isDigit)) { confirmation = it; error = null } }, modifier = Modifier.fillMaxWidth(), label = { Text("تأكيد الرمز الجديد") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation(), singleLine = true, shape = RoundedCornerShape(8.dp))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            PrimaryButton(text = "حفظ الرمز الجديد", onClick = {
                when {
                    !security.verifyRecoveryEmail(email) -> error = "البريد الإلكتروني غير مطابق."
                    newPin.length != PIN_LENGTH -> error = "رمز الدخول يجب أن يتكون من 9 أرقام."
                    newPin != confirmation -> error = "رمزا الدخول غير متطابقين."
                    else -> { security.resetPin(newPin); onRecovered() }
                }
            })
        },
        dismissButton = { SecondaryButton(text = "إلغاء", onClick = onDismiss) }
    )
}

private fun isValidEmail(email: String): Boolean = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
