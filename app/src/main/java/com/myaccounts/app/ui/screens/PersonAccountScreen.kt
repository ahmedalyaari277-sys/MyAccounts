package com.myaccounts.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myaccounts.app.data.local.CurrencyAccountEntity
import com.myaccounts.app.data.local.dao.PersonWithAccounts
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonAccountScreen(
personWithAccounts: PersonWithAccounts,
onBack: () -> Unit,
onUpdatePerson: (
String,
String,
String,
String
) -> Unit,
onDeletePerson: () -> Unit,
onAccountClick: (Long) -> Unit
) {
var showEditDialog by remember {
mutableStateOf(false)
}

```
var showDeleteDialog by remember {
    mutableStateOf(false)
}

val person = personWithAccounts.person

Scaffold(
    topBar = {
        TopAppBar(
            title = {
                Text(
                    text = person.name,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack
                ) {
                    Icon(
                        imageVector =
                            Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع"
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        showEditDialog = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "تعديل"
                    )
                }

                IconButton(
                    onClick = {
                        showDeleteDialog = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف"
                    )
                }
            }
        )
    }
) { paddingValues ->

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
    ) {

        if (person.phone.isNotBlank()) {
            Text(
                text = "الهاتف: ${person.phone}",
                fontSize = 14.sp
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )
        }

        if (person.address.isNotBlank()) {
            Text(
                text = "العنوان: ${person.address}",
                fontSize = 14.sp
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )
        }

        if (person.notes.isNotBlank()) {
            Text(
                text = "الملاحظات: ${person.notes}",
                fontSize = 14.sp
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )
        }

        Text(
            text = "الحسابات",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        CurrencyAccountCard(
            account = account(
                personWithAccounts.accounts,
                "YER"
            ),
            currencyName = "الريال اليمني",
            onClick = onAccountClick
        )

        CurrencyAccountCard(
            account = account(
                personWithAccounts.accounts,
                "SAR"
            ),
            currencyName = "الريال السعودي",
            onClick = onAccountClick
        )

        CurrencyAccountCard(
            account = account(
                personWithAccounts.accounts,
                "USD"
            ),
            currencyName = "الدولار الأمريكي",
            onClick = onAccountClick
        )
    }
}

if (showEditDialog) {
    EditPersonDialog(
        name = person.name,
        phone = person.phone,
        address = person.address,
        notes = person.notes,
        onDismiss = {
            showEditDialog = false
        },
        onSave = {
                name,
                phone,
                address,
                notes ->

            onUpdatePerson(
                name,
                phone,
                address,
                notes
            )

            showEditDialog = false
        }
    )
}

if (showDeleteDialog) {
    AlertDialog(
        onDismissRequest = {
            showDeleteDialog = false
        },
        title = {
            Text("حذف الحساب")
        },
        text = {
            Text(
                "هل أنت متأكد من حذف هذا الشخص؟"
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    showDeleteDialog = false
                    onDeletePerson()
                }
            ) {
                Text("حذف")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    showDeleteDialog = false
                }
            ) {
                Text("إلغاء")
            }
        }
    )
}
```

}

private fun account(
accounts: List<CurrencyAccountEntity>,
currencyCode: String
): CurrencyAccountEntity? {
return accounts.firstOrNull {
it.currencyCode == currencyCode
}
}

@Composable
private fun CurrencyAccountCard(
account: CurrencyAccountEntity?,
currencyName: String,
onClick: (Long) -> Unit
) {
val balanceMinor =
account?.balanceMinor ?: 0L

```
Card(
    modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)
        .then(
            if (account != null) {
                Modifier.clickable {
                    onClick(account.id)
                }
            } else {
                Modifier
            }
        ),
    colors = CardDefaults.cardColors(
        containerColor =
            MaterialTheme.colorScheme.surfaceVariant
    )
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
        horizontalArrangement =
            Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = currencyName,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = account?.currencyCode ?: "---",
                fontSize = 12.sp
            )
        }

        Text(
            text = formatBalance(balanceMinor),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
    }
}
```

}

private fun formatBalance(
balanceMinor: Long
): String {
return BigDecimal(balanceMinor)
.movePointLeft(2)
.stripTrailingZeros()
.toPlainString()
}

@Composable
private fun EditPersonDialog(
name: String,
phone: String,
address: String,
notes: String,
onDismiss: () -> Unit,
onSave: (
String,
String,
String,
String
) -> Unit
) {
var editedName by remember {
mutableStateOf(name)
}

```
var editedPhone by remember {
    mutableStateOf(phone)
}

var editedAddress by remember {
    mutableStateOf(address)
}

var editedNotes by remember {
    mutableStateOf(notes)
}

var nameError by remember {
    mutableStateOf(false)
}

AlertDialog(
    onDismissRequest = onDismiss,
    title = {
        Text("تعديل بيانات الشخص")
    },
    text = {
        Column {

            OutlinedTextField(
                value = editedName,
                onValueChange = {
                    editedName = it
                    nameError = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("الاسم")
                },
                singleLine = true,
                isError = nameError
            )

            if (nameError) {
                Text(
                    text = "الاسم مطلوب",
                    color =
                        MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            OutlinedTextField(
                value = editedPhone,
                onValueChange = {
                    editedPhone = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("الهاتف")
                },
                singleLine = true
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            OutlinedTextField(
                value = editedAddress,
                onValueChange = {
                    editedAddress = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("العنوان")
                }
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            OutlinedTextField(
                value = editedNotes,
                onValueChange = {
                    editedNotes = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("الملاحظات")
                },
                minLines = 2
            )
        }
    },
    confirmButton = {
        Button(
            onClick = {

                if (editedName.isBlank()) {
                    nameError = true
                } else {
                    onSave(
                        editedName.trim(),
                        editedPhone.trim(),
                        editedAddress.trim(),
                        editedNotes.trim()
                    )
                }
            }
        ) {
            Text("حفظ")
        }
    },
    dismissButton = {
        TextButton(
            onClick = onDismiss
        ) {
            Text("إلغاء")
        }
    }
)
```

}

```
```
