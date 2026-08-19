package com.myaccounts.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myaccounts.app.data.local.CurrencyAccountEntity
import com.myaccounts.app.data.local.TransactionEntity
import com.myaccounts.app.data.local.TransactionType
import com.myaccounts.app.util.TransactionAttachmentStorage
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun QuickTransactionScreen(
    personName: String,
    accounts: List<CurrencyAccountEntity>,
    onSave: (TransactionEntity, List<TransactionAttachmentStorage.SelectedAttachment>) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val today = remember { Calendar.getInstance() }
    var selectedCurrency by remember { mutableStateOf(accounts.firstOrNull()?.currencyCode ?: "YER") }
    var selectedType by remember { mutableStateOf(TransactionType.RECEIVABLE) }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var transactionDate by remember { mutableStateOf(today.timeInMillis) }
    var amountError by remember { mutableStateOf(false) }
    var attachments by remember { mutableStateOf<List<TransactionAttachmentStorage.SelectedAttachment>>(emptyList()) }

    val currencyLabels = mapOf("YER" to "ريال يمني", "SAR" to "ريال سعودي", "USD" to "دولار")
    val currencyOrder = mapOf("YER" to 0, "SAR" to 1, "USD" to 2)
    val dateText = remember(transactionDate) { SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(transactionDate)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text("إضافة عملية", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(personName, modifier = Modifier.fillMaxWidth(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it; amountError = false },
                modifier = Modifier.weight(1.35f),
                label = { Text("المبلغ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = amountError
            )
            OutlinedTextField(
                value = dateText,
                onValueChange = {},
                modifier = Modifier.weight(1f),
                label = { Text("التاريخ") },
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        val selected = Calendar.getInstance().apply { timeInMillis = transactionDate }
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                selected.set(year, month, day, 12, 0, 0)
                                selected.set(Calendar.MILLISECOND, 0)
                                transactionDate = selected.timeInMillis
                            },
                            selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }) { Icon(Icons.Default.CalendarToday, contentDescription = "اختيار التاريخ") }
                }
            )
        }
        if (amountError) Text("أدخل مبلغًا صحيحًا أكبر من صفر وبحد أقصى منزلتين عشريتين.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("التفاصيل") },
            singleLine = true
        )

        Text("العملة", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            accounts.sortedBy { currencyOrder[it.currencyCode] ?: Int.MAX_VALUE }.forEach { account ->
                FilterChip(
                    selected = selectedCurrency == account.currencyCode,
                    onClick = { selectedCurrency = account.currencyCode },
                    label = { Text(currencyLabels[account.currencyCode] ?: account.currencyCode, fontSize = 12.sp) }
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            val receivableColors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
            val payableColors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)
            if (selectedType == TransactionType.RECEIVABLE) Button(onClick = { selectedType = TransactionType.RECEIVABLE }, modifier = Modifier.weight(1f), colors = receivableColors) { Text("✓ عليه") }
            else OutlinedButton(onClick = { selectedType = TransactionType.RECEIVABLE }, modifier = Modifier.weight(1f)) { Text("عليه") }
            if (selectedType == TransactionType.PAYABLE) Button(onClick = { selectedType = TransactionType.PAYABLE }, modifier = Modifier.weight(1f), colors = payableColors) { Text("✓ له") }
            else OutlinedButton(onClick = { selectedType = TransactionType.PAYABLE }, modifier = Modifier.weight(1f)) { Text("له") }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("المرفقات", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                TransactionAttachmentPicker(selectedAttachments = attachments, onAttachmentsChanged = { attachments = it })
            }
        }

        Spacer(Modifier.height(1.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Button(
                onClick = {
                    val parsedAmount = runCatching { BigDecimal(amount.trim()).setScale(2).movePointRight(2).longValueExact() }.getOrNull()
                    if (parsedAmount == null || parsedAmount <= 0L) {
                        amountError = true
                        return@Button
                    }
                    val account = accounts.firstOrNull { it.currencyCode == selectedCurrency }
                    if (account == null) {
                        amountError = true
                        return@Button
                    }
                    onSave(
                        TransactionEntity(accountId = account.id, type = selectedType, amountMinor = parsedAmount, description = description.trim(), transactionDate = transactionDate),
                        attachments
                    )
                },
                modifier = Modifier.weight(1f)
            ) { Text("حفظ", fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("إلغاء") }
        }
    }
}
