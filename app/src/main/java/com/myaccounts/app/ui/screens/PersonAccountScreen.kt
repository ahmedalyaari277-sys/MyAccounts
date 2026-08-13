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
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myaccounts.app.data.local.dao.PersonWithAccounts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonAccountScreen(
    personWithAccounts: PersonWithAccounts,
    onBack: () -> Unit
) {

    Scaffold(

        topBar = {

            TopAppBar(

                title = {
                    Text(
                        text = personWithAccounts.person.name,
                        fontWeight = FontWeight.Bold
                    )
                },

                navigationIcon = {

                    IconButton(
                        onClick = onBack
                    ) {

                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "رجوع"
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
                .verticalScroll(rememberScrollState())

        ) {

            PersonInformationCard(
                personWithAccounts = personWithAccounts
            )

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            Text(
                text = "الحسابات",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            CurrencyAccountCard(
                title = "الريال اليمني",
                currency = "YER",
                balance = 0
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            CurrencyAccountCard(
                title = "الريال السعودي",
                currency = "SAR",
                balance = 0
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            CurrencyAccountCard(
                title = "الدولار الأمريكي",
                currency = "USD",
                balance = 0
            )

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            Button(

                onClick = {
                    // سيتم ربط زر إضافة حركة مالية في المرحلة التالية
                },

                modifier = Modifier.fillMaxWidth()

            ) {

                Icon(
                    imageVector = Icons.Default.AttachMoney,
                    contentDescription = null
                )

                Spacer(
                    modifier = Modifier.width(8.dp)
                )

                Text(
                    text = "إضافة حركة مالية"
                )
            }
        }
    }
}


@Composable
private fun PersonInformationCard(
    personWithAccounts: PersonWithAccounts
) {

    Card(

        modifier = Modifier.fillMaxWidth(),

        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceVariant
        )

    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = personWithAccounts.person.name,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            if (
                personWithAccounts.person.phone.isNotBlank()
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    Text(
                        text = personWithAccounts.person.phone,
                        fontSize = 15.sp
                    )
                }
            }

            if (
                personWithAccounts.person.address.isNotBlank()
            ) {

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    Text(
                        text = personWithAccounts.person.address,
                        fontSize = 15.sp
                    )
                }
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

        modifier = Modifier.fillMaxWidth(),

        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surface
        )

    ) {

        Row(

            modifier = Modifier
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
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = currency,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Text(
                text = balance.toString(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
