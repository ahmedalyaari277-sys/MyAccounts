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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myaccounts.app.data.local.dao.PersonWithAccounts

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
    onDeletePerson: () -> Unit
) {

    var showEditDialog by remember {
        mutableStateOf(false)
    }

    var showDeleteDialog by remember {
        mutableStateOf(false)
    }

    Scaffold(

        topBar = {

            TopAppBar(

                title = {
                    Text(
                        text =
                            personWithAccounts
                                .person
                                .name,
                        fontWeight =
                            FontWeight.Bold
                    )
                },

                navigationIcon = {

                    IconButton(
                        onClick = onBack
                    ) {

                        Icon(
                            imageVector =
                                Icons.Default.ArrowBack,
                            contentDescription =
                                "رجوع"
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
                            imageVector =
                                Icons.Default.Edit,
                            contentDescription =
                                "تعديل"
                        )
                    }

                    IconButton(
                        onClick = {
                            showDeleteDialog = true
                        }
                    ) {

                        Icon(
                            imageVector =
                                Icons.Default.Delete,
                            contentDescription =
                                "حذف"
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
                .verticalScroll(
                    rememberScrollState()
                )
        ) {

            PersonInformationCard(
                personWithAccounts
            )

            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )

            Text(
                text = "الحسابات",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )

            CurrencyAccountCard(
                title = "الريال اليمني",
                currency = "YER",
                balance = 0
            )

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )

            CurrencyAccountCard(
                title = "الريال السعودي",
                currency = "SAR",
                balance = 0
            )

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )

            CurrencyAccountCard(
                title = "الدولار الأمريكي",
                currency = "USD",
                balance = 0
            )
        }
    }

    if (showEditDialog) {

        EditPersonDialog(

            personWithAccounts =
                personWithAccounts,

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
                Text(
                    "حذف الشخص"
                )
            },

            text = {
                Text(
                    "هل أنت متأكد من حذف هذا الشخص؟ سيتم إخفاؤه من القائمة مع الاحتفاظ ببياناته المالية."
                )
            },

            confirmButton = {

                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeletePerson()
                    }
                ) {
                    Text(
                        "حذف",
                        color =
                            MaterialTheme
                                .colorScheme
                                .error
                    )
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
}

@Composable
private fun PersonInformationCard(
    personWithAccounts:
        PersonWithAccounts
) {

    Card(

        modifier =
            Modifier.fillMaxWidth(),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme
                        .colorScheme
                        .surfaceVariant
            )
    ) {

        Column(
            modifier =
                Modifier.padding(16.dp)
        ) {

            Text(
                text =
                    personWithAccounts
                        .person
                        .name,
                fontSize = 22.sp,
                fontWeight =
                    FontWeight.Bold
            )

            if (
                personWithAccounts
                    .person
                    .phone
                    .isNotBlank()
            ) {

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector =
                            Icons.Default.Call,
                        contentDescription =
                            null,
                        tint =
                            MaterialTheme
                                .colorScheme
                                .primary
                    )

                    Spacer(
                        modifier =
                            Modifier.width(8.dp)
                    )

                    Text(
                        text =
                            personWithAccounts
                                .person
                                .phone
                    )
                }
            }

            if (
                personWithAccounts
                    .person
                    .address
                    .isNotBlank()
            ) {

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector =
                            Icons.Default.LocationOn,
                        contentDescription =
                            null,
                        tint =
                            MaterialTheme
                                .colorScheme
                                .primary
                    )

                    Spacer(
                        modifier =
                            Modifier.width(8.dp)
                    )

                    Text(
                        text =
                            personWithAccounts
                                .person
                                .address
                    )
                }
            }

            if (
                personWithAccounts
                    .person
                    .notes
                    .isNotBlank()
            ) {

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )

                Text(
                    text = "الملاحظات",
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )

                Text(
                    text =
                        personWithAccounts
                            .person
                            .notes
                )
            }
        }
    }
}

@Composable
private fun CurrencyAccountCard(
    title: String,
    currency: String,
    balance: Long
) {

    Card(

        modifier =
            Modifier.fillMaxWidth(),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme
                        .colorScheme
                        .surface
            )
    ) {

        Row(

            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),

            horizontalArrangement =
                Arrangement.SpaceBetween,

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Column {

                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )

                Text(
                    text = currency,
                    fontSize = 12.sp
                )
            }

            Text(
                text = balance.toString(),
                fontSize = 20.sp,
                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EditPersonDialog(
    personWithAccounts:
        PersonWithAccounts,
    onDismiss: () -> Unit,
    onSave: (
        String,
        String,
        String,
        String
    ) -> Unit
) {

    var name by remember {
        mutableStateOf(
            personWithAccounts
                .person
                .name
        )
    }

    var phone by remember {
        mutableStateOf(
            personWithAccounts
                .person
                .phone
        )
    }

    var address by remember {
        mutableStateOf(
            personWithAccounts
                .person
                .address
        )
    }

    var notes by remember {
        mutableStateOf(
            personWithAccounts
                .person
                .notes
        )
    }

    var nameError by remember {
        mutableStateOf(false)
    }

    AlertDialog(

        onDismissRequest =
            onDismiss,

        title = {
            Text(
                "تعديل بيانات الشخص"
            )
        },

        text = {

            Column {

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = false
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    label = {
                        Text("اسم الشخص")
                    },
                    singleLine = true,
                    isError = nameError
                )

                if (nameError) {

                    Text(
                        text =
                            "اسم الشخص مطلوب",
                        color =
                            MaterialTheme
                                .colorScheme
                                .error,
                        fontSize = 12.sp
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    label = {
                        Text("رقم الهاتف")
                    },
                    singleLine = true
                )

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    label = {
                        Text("العنوان")
                    },
                    minLines = 2
                )

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = {
                        notes = it
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
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

                    if (name.isBlank()) {
                        nameError = true
                    } else {

                        onSave(
                            name.trim(),
                            phone.trim(),
                            address.trim(),
                            notes.trim()
                        )
                    }
                }
            ) {

                Text("حفظ")
            }
        },

        dismissButton = {

            TextButton(
                onClick =
                    onDismiss
            ) {
                Text("إلغاء")
            }
        }
    )
}
