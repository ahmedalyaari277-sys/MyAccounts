package com.myaccounts.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
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
    onSave: (
        TransactionEntity,
        List<TransactionAttachmentStorage.SelectedAttachment>
    ) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val today = remember { Calendar.getInstance() }
    var selectedCurrency by remember {
        mutableStateOf(accounts.firstOrNull()?.currencyCode ?: "YER")
    }
    var selectedType by remember { mutableStateOf(TransactionType.RECEIVABLE) }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var transactionDate by remember { mutableStateOf(today.timeInMillis) }
    var amountError by remember { mutableStateOf(false) }
    var attachments by remember {
        mutableStateOf<List<TransactionAttachmentStorage.SelectedAttachment>>(emptyList())
    }

    val currencyLabels = mapOf(
        "YER" to "يمني",
        "SAR" to "سعودي",
        "USD" to "دولار"
    )

    val dateText = remember(transactionDate) {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(transactionDate))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "إضافة عملية — $personName",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Text("العملة", fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            accounts.sortedBy { it.currencyCode }.forEach { account ->
                FilterChip(
                    selected = selectedCurrency == account.currencyCode,
                    onClick = { selectedCurrency = account.currencyCode },
                    label = {
                        Text(
                            text = currencyLabels[account.currencyCode] ?: account.currencyCode,
                            maxLines = 1
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }

        Text("نوع العملية", fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedType == TransactionType.RECEIVABLE,
                onClick = { selectedType = TransactionType.RECEIVABLE },
                label = { Text("عليه", maxLines = 1) },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.error,
                    labelColor = MaterialTheme.colorScheme.onSurface
                )
            )
            FilterChip(
                selected = selectedType == TransactionType.PAYABLE,
                onClick = { selectedType = TransactionType.PAYABLE },
                label = { Text("له", maxLines = 1) },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.secondary,
                    labelColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }

        OutlinedTextField(
            value = amount,
            onValueChange = {
                amount = it
                amountError = false
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("المبلغ") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = amountError
        )
        if (amountError) {
            Text(
                text = "أدخل مبلغًا صحيحًا أكبر من صفر",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("البيان") },
            placeholder = { Text("مثال: شراء بضاعة") },
            minLines = 2,
            maxLines = 3
        )

        OutlinedTextField(
            value = dateText,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { Text("التاريخ") },
            readOnly = true,
            trailingIcon = {
                IconButton(onClick = {
                    val selected = Calendar.getInstance().apply {
                        timeInMillis = transactionDate
                    }
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            selected.set(year, month, day, 12, 0, 0)
                            selected.set(Calendar.MILLISECOND, 0)
                            transactionDate = selected.timeInMillis
                        },
                        selected.get(Calendar.YEAR),
                        selected.get(Calendar.MONTH),
                        selected.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "اختيار التاريخ"
                    )
                }
            }
        )

        TransactionAttachmentPicker(
            selectedAttachments = attachments,
            onAttachmentsChanged = { attachments = it }
        )

        Spacer(Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val parsedAmount = runCatching {
                        BigDecimal(amount.trim())
                            .movePointRight(2)
                            .longValueExact()
                    }.getOrNull()

                    if (parsedAmount == null || parsedAmount <= 0L) {
                        amountError = true
                        return@Button
                    }

                    val account = accounts.firstOrNull {
                        it.currencyCode == selectedCurrency
                    }
                    if (account == null) {
                        amountError = true
                        return@Button
                    }

                    onSave(
                        TransactionEntity(
                            accountId = account.id,
                            type = selectedType,
                            amountMinor = parsedAmount,
                            description = description.trim(),
                            transactionDate = transactionDate
                        ),
                        attachments
                    )
                },
                modifier = Modifier.weight(1f)
            ) { Text("حفظ") }

            Button(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) { Text("إلغاء") }
        }
    }
}
