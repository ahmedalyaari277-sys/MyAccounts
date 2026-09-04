package com.myaccounts.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.myaccounts.app.data.currency.CurrencyCatalog
import com.myaccounts.app.data.local.AppDatabase
import com.myaccounts.app.security.AppSecurityManager
import com.myaccounts.app.ui.theme.AppearanceMode
import kotlinx.coroutines.launch

private const val PIN_LENGTH = 9
private const val NUMBER_FORMAT_PREFS = "myaccounts_number_format"
private const val KEY_FIXED_DECIMALS = "fixed_decimals"

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
    var showAddCurrency by remember { mutableStateOf(false) }
    var fixedDecimals by remember { mutableStateOf(LocalContext.current.getSharedPreferences(NUMBER_FORMAT_PREFS, 0).getBoolean(KEY_FIXED_DECIMALS, false)) }
    var defaultCurrency by remember { mutableStateOf(CurrencyCatalog.defaultCode()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("رجوع") }
            Spacer(Modifier.height(8.dp))
            Text("الإعدادات", style = MaterialTheme.typography.headlineMedium)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("المظهر", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when (appearanceMode) {
                            AppearanceMode.LIGHT -> "الوضع الفاتح"
                            AppearanceMode.DARK -> "الوضع الداكن"
                            AppearanceMode.SYSTEM -> "حسب إعدادات النظام"
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    AppearanceOption("فاتح", appearanceMode == AppearanceMode.LIGHT) { onAppearanceModeChange(AppearanceMode.LIGHT) }
                    AppearanceOption("داكن", appearanceMode == AppearanceMode.DARK) { onAppearanceModeChange(AppearanceMode.DARK) }
                    AppearanceOption("حسب النظام", appearanceMode == AppearanceMode.SYSTEM) { onAppearanceModeChange(AppearanceMode.SYSTEM) }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("العملات", style = MaterialTheme.typography.titleMedium)
                    Text("العملات المفعلة متاحة للحسابات والتعاملات والتقارير والعُهَد.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CurrencyCatalog.definitions.forEach { currency ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(currency.name, style = MaterialTheme.typography.bodyLarge)
                                Text(currency.code, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RadioButton(
                                selected = defaultCurrency == currency.code,
                                onClick = { defaultCurrency = currency.code; CurrencyCatalog.setDefault(currency.code) }
                            )
                        }
                    }
                    Text("العملة الافتراضية للحسابات الجديدة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { showAddCurrency = true }, modifier = Modifier.fillMaxWidth()) { Text("إضافة عملة") }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("تنسيق الأرقام", style = MaterialTheme.typography.titleMedium)
                        Text(if (fixedDecimals) "عرض منزلتين عشريتين دائمًا" else "عرض الرقم بدون أصفار عشرية زائدة", style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = fixedDecimals,
                        onCheckedChange = {
                            fixedDecimals = it
                            context.getSharedPreferences(NUMBER_FORMAT_PREFS, 0).edit().putBoolean(KEY_FIXED_DECIMALS, it).apply()
                        }
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("النسخ الاحتياطي والتصدير", style = MaterialTheme.typography.titleMedium)
                    Text("يمكنك تنفيذ النسخ والاستعادة ونقل البيانات والتصدير من شاشة النسخ الاحتياطي والاستعادة.", style = MaterialTheme.typography.bodyMedium)
                    Text("تظل ملفات Excel وPDF والمرفقات ضمن وظائفها الحالية دون تغيير.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("حماية التطبيق عند الدخول", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(if (protectionEnabled) "البصمة أولًا، ورمز الدخول كخيار احتياطي" else "التطبيق يفتح مباشرة دون طلب حماية", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Switch(
                        checked = protectionEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                if (security.hasPin() && security.hasRecoveryEmail()) {
                                    security.setProtectionEnabled(true)
                                    protectionEnabled = true
                                } else showSetup = true
                            } else showDisableConfirmation = true
                        }
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDetailsClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("تفاصيل التطبيق", modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (showAddCurrency) {
        AddCurrencyDialog(
            onDismiss = { showAddCurrency = false },
            onAdded = { code, name ->
                if (CurrencyCatalog.add(code, name)) {
                    scope.launch { AppDatabase.getInstance(context).ledgerDao().addCurrencyToAllPeople(code.trim().uppercase()) }
                    defaultCurrency = code.trim().uppercase()
                }
                showAddCurrency = false
            }
        )
    }

    if (showSetup) {
        SecuritySetupDialog(security, { showSetup = false }) {
            security.setProtectionEnabled(true)
            protectionEnabled = true
            showSetup = false
        }
    }

    if (showDisableConfirmation) {
        DisableProtectionDialog(security, { showDisableConfirmation = false }) {
            security.setProtectionEnabled(false)
            protectionEnabled = false
            showDisableConfirmation = false
        }
    }
}

@Composable
private fun AddCurrencyDialog(onDismiss: () -> Unit, onAdded: (String, String) -> Unit) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة عملة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("أدخل رمز العملة واسمها. الرمز من 3 إلى 6 أحرف أو أرقام.")
                OutlinedTextField(value = code, onValueChange = { code = it.uppercase(); error = null }, label = { Text("رمز العملة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = name, onValueChange = { name = it; error = null }, label = { Text("اسم العملة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (code.trim().matches(Regex("[A-Z0-9]{3,6}")) && name.trim().isNotBlank() && CurrencyCatalog.codes.none { it == code.trim() }) onAdded(code, name)
                else error = "رمز العملة غير صالح أو العملة موجودة مسبقًا."
            }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AppearanceOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        TextButton(onClick = onClick, modifier = Modifier.weight(1f)) { Text(label, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun SecuritySetupDialog(security: AppSecurityManager, onDismiss: () -> Unit, onCompleted: () -> Unit) {
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
                OutlinedTextField(value = pin, onValueChange = { if (it.length <= PIN_LENGTH && it.all(Char::isDigit)) { pin = it; error = null } }, modifier = Modifier.fillMaxWidth(), label = { Text("رمز الدخول") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = confirmation, onValueChange = { if (it.length <= PIN_LENGTH && it.all(Char::isDigit)) { confirmation = it; error = null } }, modifier = Modifier.fillMaxWidth(), label = { Text("تأكيد رمز الدخول") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = email, onValueChange = { email = it; error = null }, modifier = Modifier.fillMaxWidth(), label = { Text("البريد الإلكتروني") }, singleLine = true)
                error?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    pin.length != PIN_LENGTH -> error = "رمز الدخول يجب أن يتكون من 9 أرقام."
                    pin != confirmation -> error = "رمزا الدخول غير متطابقين."
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> error = "أدخل بريدًا إلكترونيًا صحيحًا."
                    else -> { security.saveCredentials(pin, email); onCompleted() }
                }
            }) { Text("تفعيل الحماية") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun DisableProtectionDialog(security: AppSecurityManager, onDismiss: () -> Unit, onDisabled: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إيقاف حماية الدخول") },
        text = {
            Column {
                Text("لإيقاف الحماية، أدخل رمز الدخول الحالي المكون من 9 أرقام.")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = pin, onValueChange = { if (it.length <= PIN_LENGTH && it.all(Char::isDigit)) { pin = it; error = null } }, modifier = Modifier.fillMaxWidth(), label = { Text("رمز الدخول الحالي") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                error?.let { Spacer(Modifier.height(6.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    pin.length != PIN_LENGTH -> error = "أدخل رمز الدخول المكون من 9 أرقام."
                    !security.verifyPin(pin) -> { pin = ""; error = "رمز الدخول غير صحيح." }
                    else -> onDisabled()
                }
            }) { Text("إيقاف الحماية") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
