package com.myaccounts.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.myaccounts.app.security.AppSecurityManager
import com.myaccounts.app.ui.theme.AppearanceMode

private const val PIN_LENGTH = 9

@Composable
fun SettingsScreen(
    security: AppSecurityManager,
    appearanceMode: AppearanceMode,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    onBack: () -> Unit,
    onDetailsClick: () -> Unit
) {
    var protectionEnabled by remember { mutableStateOf(security.isProtectionEnabled()) }
    var showSetup by remember { mutableStateOf(false) }
    var showDisableConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        TextButton(onClick = onBack) { Text("رجوع") }
        Spacer(Modifier.height(8.dp))
        Text("الإعدادات", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("المظهر", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    when (appearanceMode) {
                        AppearanceMode.LIGHT -> "الوضع الفاتح"
                        AppearanceMode.DARK -> "الوضع الداكن"
                        AppearanceMode.SYSTEM -> "حسب إعدادات النظام"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                AppearanceOption(
                    label = "فاتح",
                    selected = appearanceMode == AppearanceMode.LIGHT,
                    onClick = { onAppearanceModeChange(AppearanceMode.LIGHT) }
                )
                AppearanceOption(
                    label = "داكن",
                    selected = appearanceMode == AppearanceMode.DARK,
                    onClick = { onAppearanceModeChange(AppearanceMode.DARK) }
                )
                AppearanceOption(
                    label = "حسب النظام",
                    selected = appearanceMode == AppearanceMode.SYSTEM,
                    onClick = { onAppearanceModeChange(AppearanceMode.SYSTEM) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("حماية التطبيق عند الدخول", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (protectionEnabled) "البصمة أولًا، ورمز الدخول كخيار احتياطي"
                    else "التطبيق يفتح مباشرة دون طلب حماية",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Switch(
                    checked = protectionEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            if (security.hasPin() && security.hasRecoveryEmail()) {
                                security.setProtectionEnabled(true)
                                protectionEnabled = true
                            } else {
                                showSetup = true
                            }
                        } else {
                            showDisableConfirmation = true
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = onDetailsClick,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("تفاصيل التطبيق", modifier = Modifier.fillMaxWidth())
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "عند تفعيل الحماية، يتم تشغيل التحقق بالبصمة تلقائيًا عند فتح التطبيق إذا كانت البصمة متاحة على الهاتف.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showSetup) {
        SecuritySetupDialog(
            security = security,
            onDismiss = { showSetup = false },
            onCompleted = {
                security.setProtectionEnabled(true)
                protectionEnabled = true
                showSetup = false
            }
        )
    }

    if (showDisableConfirmation) {
        DisableProtectionDialog(
            security = security,
            onDismiss = { showDisableConfirmation = false },
            onDisabled = {
                security.setProtectionEnabled(false)
                protectionEnabled = false
                showDisableConfirmation = false
            }
        )
    }
}

@Composable
private fun AppearanceOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        TextButton(onClick = onClick, modifier = Modifier.weight(1f)) {
            Text(label, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SecuritySetupDialog(
    security: AppSecurityManager,
    onDismiss: () -> Unit,
    onCompleted: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(security.recoveryEmail()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعداد حماية الدخول") },
        text = {
            Column {
                Text("رمز الدخول يجب أن يتكون من 9 أرقام. البريد الإلكتروني يستخدم لاستعادة الرمز فقط.")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= PIN_LENGTH && it.all(Char::isDigit)) { pin = it; error = null } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("رمز الدخول") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { if (it.length <= PIN_LENGTH && it.all(Char::isDigit)) { confirmation = it; error = null } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("تأكيد رمز الدخول") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("البريد الإلكتروني") },
                    singleLine = true
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    pin.length != PIN_LENGTH -> error = "رمز الدخول يجب أن يتكون من 9 أرقام."
                    pin != confirmation -> error = "رمزا الدخول غير متطابقين."
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> error = "أدخل بريدًا إلكترونيًا صحيحًا."
                    else -> {
                        security.saveCredentials(pin, email)
                        onCompleted()
                    }
                }
            }) { Text("تفعيل الحماية") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun DisableProtectionDialog(
    security: AppSecurityManager,
    onDismiss: () -> Unit,
    onDisabled: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إيقاف حماية الدخول") },
        text = {
            Column {
                Text("لإيقاف الحماية، أدخل رمز الدخول الحالي المكون من 9 أرقام.")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= PIN_LENGTH && it.all(Char::isDigit)) { pin = it; error = null } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("رمز الدخول الحالي") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    pin.length != PIN_LENGTH -> error = "أدخل رمز الدخول المكون من 9 أرقام."
                    !security.verifyPin(pin) -> {
                        pin = ""
                        error = "رمز الدخول غير صحيح."
                    }
                    else -> onDisabled()
                }
            }) { Text("إيقاف الحماية") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
