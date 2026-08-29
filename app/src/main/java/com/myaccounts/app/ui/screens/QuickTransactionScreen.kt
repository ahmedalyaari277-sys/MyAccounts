package com.myaccounts.app.ui.screens

import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.myaccounts.app.data.local.CurrencyAccountEntity
import com.myaccounts.app.data.local.TransactionAttachmentEntity
import com.myaccounts.app.data.local.TransactionEntity
import com.myaccounts.app.data.local.TransactionType
import com.myaccounts.app.security.AppSecurityManager
import com.myaccounts.app.ui.components.CalculatorButton
import com.myaccounts.app.ui.components.CalculatorOverlay
import com.myaccounts.app.ui.components.LocalCalculatorController
import com.myaccounts.app.util.TransactionAttachmentStorage
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun QuickTransactionScreen(
    personName: String,
    accounts: List<CurrencyAccountEntity>,
    onSave: (TransactionEntity, List<TransactionAttachmentStorage.SelectedAttachment>) -> Unit,
    onCancel: () -> Unit,
    initialTransaction: TransactionEntity? = null,
    existingAttachments: List<TransactionAttachmentEntity> = emptyList(),
    onEditSave: ((TransactionEntity, List<TransactionAttachmentStorage.SelectedAttachment>, List<TransactionAttachmentEntity>) -> Unit)? = null
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val calculatorController = LocalCalculatorController.current
    val amountFocusRequester = remember { FocusRequester() }
    val today = remember { Calendar.getInstance() }
    val editMode = initialTransaction != null && onEditSave != null
    val initialAccountCurrency = initialTransaction?.let { transaction -> accounts.firstOrNull { it.id == transaction.accountId }?.currencyCode }

    var selectedCurrency by remember(initialTransaction?.id, accounts) {
        mutableStateOf(initialAccountCurrency ?: accounts.firstOrNull()?.currencyCode ?: "YER")
    }
    var selectedType by remember(initialTransaction?.id) {
        mutableStateOf(initialTransaction?.type ?: TransactionType.RECEIVABLE)
    }
    var amount by remember(initialTransaction?.id) {
        mutableStateOf(initialTransaction?.let { formatAmount(it.amountMinor) } ?: "")
    }
    var description by remember(initialTransaction?.id) {
        mutableStateOf(initialTransaction?.description ?: "")
    }
    var transactionDate by remember(initialTransaction?.id) {
        mutableStateOf(initialTransaction?.transactionDate ?: today.timeInMillis)
    }
    var amountError by remember(initialTransaction?.id) { mutableStateOf(false) }
    var attachments by remember(initialTransaction?.id) {
        mutableStateOf<List<TransactionAttachmentStorage.SelectedAttachment>>(emptyList())
    }
    var deletedExistingAttachments by remember(initialTransaction?.id) {
        mutableStateOf<List<TransactionAttachmentEntity>>(emptyList())
    }

    DisposableEffect(calculatorController, initialTransaction?.id) {
        calculatorController.setResultConsumer { value ->
            amount = value
            amountError = false
        }
        onDispose {
            calculatorController.setResultConsumer(null)
        }
    }

    val currencyLabels = mapOf("YER" to "ريال يمني", "SAR" to "ريال سعودي", "USD" to "دولار")
    val currencyOrder = mapOf("YER" to 0, "SAR" to 1, "USD" to 2)
    val dateText = remember(transactionDate) { SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(transactionDate)) }
    val visibleExistingAttachments = remember(existingAttachments, deletedExistingAttachments) {
        existingAttachments.filterNot { existing -> deletedExistingAttachments.any { it.id == existing.id } }
    }

    LaunchedEffect(editMode) {
        if (!editMode) {
            amountFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            if (editMode) "تعديل العملية" else "إضافة عملية",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(personName, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it; amountError = false },
                modifier = Modifier.weight(1.35f).focusRequester(if (editMode) FocusRequester() else amountFocusRequester),
                label = { Text("المبلغ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = amountError,
                shape = MaterialTheme.shapes.small,
                trailingIcon = { CalculatorButton(onClick = calculatorController::open) }
            )
            OutlinedTextField(
                value = dateText,
                onValueChange = {},
                modifier = Modifier.weight(1f),
                label = { Text("التاريخ") },
                readOnly = true,
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                trailingIcon = {
                    IconButton(onClick = {
                        val selected = Calendar.getInstance().apply { timeInMillis = transactionDate }
                        DatePickerDialog(context, { _, year, month, day ->
                            selected.set(year, month, day, 12, 0, 0)
                            selected.set(Calendar.MILLISECOND, 0)
                            transactionDate = selected.timeInMillis
                        }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH)).show()
                    }) { Icon(Icons.Default.CalendarToday, contentDescription = "اختيار التاريخ") }
                }
            )
        }
        if (amountError) Text("أدخل مبلغًا صحيحًا أكبر من صفر وبحد أقصى منزلتين عشريتين.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(value = description, onValueChange = { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text("التفاصيل") }, singleLine = true, shape = MaterialTheme.shapes.small)

        Text("العملة", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            accounts.sortedBy { currencyOrder[it.currencyCode] ?: Int.MAX_VALUE }.forEach { account ->
                FilterChip(selected = selectedCurrency == account.currencyCode, onClick = { selectedCurrency = account.currencyCode }, label = { Text(currencyLabels[account.currencyCode] ?: account.currencyCode, style = MaterialTheme.typography.labelMedium) })
            }
        }

        Text("نوع العملية", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val receivableColors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
            val payableColors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)
            if (selectedType == TransactionType.RECEIVABLE) Button(onClick = { selectedType = TransactionType.RECEIVABLE }, modifier = Modifier.weight(1f), colors = receivableColors, shape = MaterialTheme.shapes.small) { Text("✓ عليه", fontWeight = FontWeight.Bold) } else OutlinedButton(onClick = { selectedType = TransactionType.RECEIVABLE }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) { Text("عليه") }
            if (selectedType == TransactionType.PAYABLE) Button(onClick = { selectedType = TransactionType.PAYABLE }, modifier = Modifier.weight(1f), colors = payableColors, shape = MaterialTheme.shapes.small) { Text("✓ له", fontWeight = FontWeight.Bold) } else OutlinedButton(onClick = { selectedType = TransactionType.PAYABLE }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) { Text("له") }
        }

        if (editMode && visibleExistingAttachments.isNotEmpty()) {
            ExistingAttachmentsSection(attachments = visibleExistingAttachments, onDelete = { attachment -> deletedExistingAttachments = deletedExistingAttachments + attachment })
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = MaterialTheme.shapes.medium, elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("المرفقات", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                TransactionAttachmentPicker(selectedAttachments = attachments, onAttachmentsChanged = { attachments = it })
            }
        }

        Spacer(Modifier.height(2.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val parsedAmount = runCatching { BigDecimal(amount.trim()).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact() }.getOrNull()
                    if (parsedAmount == null || parsedAmount <= 0L) { amountError = true; return@Button }
                    val account = accounts.firstOrNull { it.currencyCode == selectedCurrency }
                    if (account == null) { amountError = true; return@Button }
                    keyboardController?.hide()
                    val transaction = if (editMode) initialTransaction!!.copy(accountId = account.id, type = selectedType, amountMinor = parsedAmount, description = description.trim(), transactionDate = transactionDate) else TransactionEntity(accountId = account.id, type = selectedType, amountMinor = parsedAmount, description = description.trim(), transactionDate = transactionDate)
                    if (editMode) onEditSave!!(transaction, attachments, deletedExistingAttachments) else onSave(transaction, attachments)
                },
                modifier = Modifier.weight(1f).semantics { contentDescription = "حفظ العملية" },
                shape = MaterialTheme.shapes.small
            ) { Text("حفظ", fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = { keyboardController?.hide(); onCancel() }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) { Text("إلغاء") }
        }
    }

    if (calculatorController.isOpen) {
        Dialog(onDismissRequest = calculatorController::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Card(modifier = Modifier.fillMaxWidth(0.92f).imePadding(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.large, elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)) {
                CalculatorOverlay(expression = calculatorController.expression, result = calculatorController.result.orEmpty(), onKey = calculatorController::press, onClear = calculatorController::clear, onBackspace = calculatorController::backspace, onDismiss = calculatorController::close, onUseResult = calculatorController::useResult)
            }
        }
    }
}

@Composable
private fun ExistingAttachmentsSection(attachments: List<TransactionAttachmentEntity>, onDelete: (TransactionAttachmentEntity) -> Unit) {
    val context = LocalContext.current
    val security = remember(context) { AppSecurityManager(context.applicationContext) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("المرفقات الحالية", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        attachments.forEach { attachment ->
            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Row(modifier = Modifier.weight(1f)) {
                        Icon(imageVector = if (attachment.mimeType.startsWith("image/")) Icons.Default.Image else Icons.Default.Description, contentDescription = null)
                        Text(attachment.fileName, modifier = Modifier.padding(start = 8.dp), maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = {
                        runCatching {
                            val file = TransactionAttachmentStorage.fileFor(context, attachment)
                            if (!file.exists()) return@runCatching
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, attachment.mimeType); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                            security.markExternalActivityPending()
                            context.startActivity(Intent.createChooser(intent, "فتح المرفق"))
                        }.onFailure { security.clearExternalActivityPending(); if (it is ActivityNotFoundException) return@onFailure }
                    }) { Icon(Icons.Default.OpenInNew, contentDescription = "فتح المرفق") }
                    IconButton(onClick = { onDelete(attachment) }) { Icon(Icons.Default.Delete, contentDescription = "حذف المرفق") }
                }
            }
        }
    }
}

private fun formatAmount(amountMinor: Long): String = BigDecimal(amountMinor).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY).stripTrailingZeros().toPlainString()
