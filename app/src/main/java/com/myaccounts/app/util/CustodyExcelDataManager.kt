package com.myaccounts.app.util

import android.content.Context
import android.net.Uri
import android.util.Xml
import androidx.room.withTransaction
import com.myaccounts.app.data.custody.*
import com.myaccounts.app.data.local.AppDatabase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.xmlpull.v1.XmlPullParser

object CustodyExcelDataManager {

    const val MIME_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    const val SUGGESTED_FILE_NAME = "MyAccounts_Custodies.xlsx"

    private const val SHEET_NAME = "بيانات العُهَد"

    private val HEADERS = listOf(
        "معرف العهدة",
        "معرف العملية",
        "معرف الشخص",
        "اسم صاحب العهدة",
        "هاتف صاحب العهدة",
        "عنوان صاحب العهدة",
        "ملاحظات صاحب العهدة",
        "اسم الجهة",
        "هاتف الجهة",
        "عنوان الجهة",
        "ملاحظات الجهة",
        "اسم الشخص",
        "هاتف الشخص",
        "عنوان الشخص",
        "ملاحظات الشخص",
        "العملة",
        "نوع العملية",
        "المبلغ",
        "البيان",
        "التاريخ",
        "التصنيف"
    )

    private val SUPPORTED_CURRENCIES = setOf(
        "YER",
        "SAR",
        "USD"
    )

    private val ALLOWED_TYPES = setOf(
        CustodyTransactionType.RECEIVED_FROM_ORG,
        CustodyTransactionType.PAID_TO_PERSON,
        CustodyTransactionType.RETURNED_FROM_PERSON,
        CustodyTransactionType.RETURNED_TO_ORG,
        CustodyTransactionType.ORG_LOAN_FROM_OWNER,
        CustodyTransactionType.ORG_LOAN_REPAYMENT,
        CustodyTransactionType.PERSON_LOAN_TO_OWNER,
        CustodyTransactionType.OWNER_REPAY_PERSON_LOAN
    )

    private val PERSON_TYPES = setOf(
        CustodyTransactionType.PAID_TO_PERSON,
        CustodyTransactionType.RETURNED_FROM_PERSON,
        CustodyTransactionType.PERSON_LOAN_TO_OWNER,
        CustodyTransactionType.OWNER_REPAY_PERSON_LOAN
    )

    private val ORGANIZATION_TYPES = setOf(
        CustodyTransactionType.RECEIVED_FROM_ORG,
        CustodyTransactionType.RETURNED_TO_ORG,
        CustodyTransactionType.ORG_LOAN_FROM_OWNER,
        CustodyTransactionType.ORG_LOAN_REPAYMENT
    )

    private val ACCOUNT_CURRENCIES = listOf(
        "YER",
        "SAR",
        "USD"
    )

    data class ExportSummary(
        val custodies: Int,
        val people: Int,
        val accounts: Int,
        val transactions: Int
    )

    data class ImportPreview(
        val custodies: Int,
        val people: Int,
        val accounts: Int,
        val transactions: Int,
        val errors: List<String>
    ) {
        val isValid: Boolean
            get() = errors.isEmpty()
    }

    data class ImportSummary(
        val custodiesAdded: Int,
        val peopleAdded: Int,
        val accountsAdded: Int,
        val transactionsAdded: Int
    )

    private data class Row(
        val n: Int,
        val custodyId: String,
        val transactionId: String,
        val personId: String,
        val owner: String,
        val ownerPhone: String,
        val ownerAddress: String,
        val ownerNotes: String,
        val org: String,
        val orgPhone: String,
        val orgAddress: String,
        val orgNotes: String,
        val person: String,
        val personPhone: String,
        val personAddress: String,
        val personNotes: String,
        val currency: String,
        val type: String,
        val amount: Long?,
        val description: String,
        val date: Long?,
        val category: String
    )

    private fun openInput(
        context: Context,
        uri: Uri
    ): InputStream {
        return if (uri.scheme == "file") {
            File(requireNotNull(uri.path)).inputStream()
        } else {
            context.contentResolver.openInputStream(uri)
                ?: error("تعذر فتح ملف Excel.")
        }
    }

    private fun openOutput(
        context: Context,
        uri: Uri
    ): OutputStream {
        return if (uri.scheme == "file") {
            File(requireNotNull(uri.path)).outputStream()
        } else {
            context.contentResolver.openOutputStream(uri)
                ?: error("تعذر فتح ملف Excel للكتابة.")
        }
    }

    suspend fun exportActive(
        context: Context,
        uri: Uri
    ): Result<ExportSummary> = runCatching {

        val db = AppDatabase.getInstance(context)
        val dao = db.custodyDao()

        val custodies = dao.getAllCustodies(false)

        val people = custodies.flatMap {
            dao.getAllPersons(it.id)
        }

        val accounts = custodies.flatMap {
            dao.getAllAccounts(it.id)
        }

        val transactions = custodies.flatMap {
            dao.getAllTransactions(it.id, false)
        }

        val peopleById = people.associateBy { it.id }
        val custodyById = custodies.associateBy { it.id }

        val transactionsByCustody =
            transactions.groupBy { it.custodyId }

        val peopleWithTransactions =
            transactions.mapNotNull { it.personId }.toSet()

        val rows = mutableListOf<Row>()

        /*
         * تصدير جميع العمليات.
         */
        transactions.forEach { transaction ->

            val custody =
                custodyById[transaction.custodyId]
                    ?: error(
                        "بيانات العهدة المرتبطة بالعملية غير موجودة."
                    )

            val person =
                transaction.personId?.let(peopleById::get)

            rows += Row(
                n = 0,
                custodyId = custody.externalId,
                transactionId = transaction.externalId,
                personId = person?.externalId ?: "",
                owner = custody.name,
                ownerPhone = custody.phone,
                ownerAddress = custody.address,
                ownerNotes = custody.notes,
                org = custody.organizationName,
                orgPhone = custody.organizationPhone,
                orgAddress = custody.organizationAddress,
                orgNotes = custody.organizationNotes,
                person = person?.name ?: "",
                personPhone = person?.phone ?: "",
                personAddress = person?.address ?: "",
                personNotes = person?.notes ?: "",
                currency = transaction.currencyCode,
                type = transaction.type,
                amount = transaction.amountMinor,
                description = transaction.description,
                date = transaction.transactionDate,
                category = transaction.categoryName
            )
        }

        /*
         * تصدير العهد التي لا تحتوي على عمليات.
         */
        custodies.forEach { custody ->

            if (transactionsByCustody[custody.id].isNullOrEmpty()) {

                rows += Row(
                    n = 0,
                    custodyId = custody.externalId,
                    transactionId = "",
                    personId = "",
                    owner = custody.name,
                    ownerPhone = custody.phone,
                    ownerAddress = custody.address,
                    ownerNotes = custody.notes,
                    org = custody.organizationName,
                    orgPhone = custody.organizationPhone,
                    orgAddress = custody.organizationAddress,
                    orgNotes = custody.organizationNotes,
                    person = "",
                    personPhone = "",
                    personAddress = "",
                    personNotes = "",
                    currency = "",
                    type = "",
                    amount = null,
                    description = "",
                    date = null,
                    category = ""
                )
            }
        }

        /*
         * تصدير الأشخاص الذين لا توجد لهم عمليات.
         */
        people
            .filter { it.id !in peopleWithTransactions }
            .forEach { person ->

                val custody =
                    custodyById[person.custodyId]
                        ?: error(
                            "بيانات العهدة المرتبطة بالشخص غير موجودة."
                        )

                rows += Row(
                    n = 0,
                    custodyId = custody.externalId,
                    transactionId = "",
                    personId = person.externalId,
                    owner = custody.name,
                    ownerPhone = custody.phone,
                    ownerAddress = custody.address,
                    ownerNotes = custody.notes,
                    org = custody.organizationName,
                    orgPhone = custody.organizationPhone,
                    orgAddress = custody.organizationAddress,
                    orgNotes = custody.organizationNotes,
                    person = person.name,
                    personPhone = person.phone,
                    personAddress = person.address,
                    personNotes = person.notes,
                    currency = "",
                    type = "",
                    amount = null,
                    description = "",
                    date = null,
                    category = ""
                )
            }

        openOutput(context, uri).use { output ->
            createWorkbook(output, rows)
        }

        ExportSummary(
            custodies = custodies.size,
            people = people.size,
            accounts = accounts.size,
            transactions = transactions.size
        )
    }

    fun previewImport(
        context: Context,
        uri: Uri
    ): Result<ImportPreview> = runCatching {

        validate(
            parseWorkbook(context, uri)
        )
    }

    suspend fun import(
        context: Context,
        uri: Uri
    ): Result<ImportSummary> = runCatching {

        val rows = parseWorkbook(context, uri)

        val preview = validate(rows)

        check(preview.isValid) {
            preview.errors.joinToString("\n")
        }

        val db = AppDatabase.getInstance(context)
        val dao = db.custodyDao()

        db.withTransaction {

            var custodiesAdded = 0
            var peopleAdded = 0
            var accountsAdded = 0
            var transactionsAdded = 0

            val custodyIds = mutableMapOf<String, Long>()
            val personIds = mutableMapOf<String, Long>()

            /*
             * إنشاء العهد والأشخاص والحسابات أولاً.
             */
            rows
                .groupBy { it.custodyId }
                .values
                .forEach { group ->

                    val first = group.first()

                    val existingCustody =
                        dao.getCustodyByExternalId(
                            first.custodyId
                        )

                    val custodyId =
                        existingCustody?.id
                            ?: dao.insertCustody(
                                CustodyEntity(
                                    name = first.owner,
                                    phone = first.ownerPhone,
                                    address = first.ownerAddress,
                                    notes = first.ownerNotes,
                                    organizationName = first.org,
                                    organizationPhone = first.orgPhone,
                                    organizationAddress = first.orgAddress,
                                    organizationNotes = first.orgNotes,
                                    externalId = first.custodyId
                                )
                            ).also {
                                custodiesAdded++
                            }

                    custodyIds[first.custodyId] = custodyId

                    /*
                     * التأكد من وجود حسابات صاحب العهدة
                     * للعملات الثلاث فقط عند الحاجة.
                     */
                    var currentAccounts =
                        dao.getAllAccounts(custodyId)

                    val existingOwnerCurrencies =
                        currentAccounts
                            .filter {
                                it.holderType == "OWNER" &&
                                    it.personId == null
                            }
                            .map { it.currencyCode }
                            .toSet()

                    val missingOwnerCurrencies =
                        ACCOUNT_CURRENCIES.filter {
                            it !in existingOwnerCurrencies
                        }

                    if (missingOwnerCurrencies.isNotEmpty()) {

                        val newAccounts =
                            missingOwnerCurrencies.map { currency ->

                                CustodyAccountEntity(
                                    custodyId = custodyId,
                                    holderType = "OWNER",
                                    currencyCode = currency
                                )
                            }

                        dao.insertAccounts(newAccounts)

                        accountsAdded += newAccounts.size

                        currentAccounts =
                            dao.getAllAccounts(custodyId)
                    }

                    /*
                     * إنشاء الأشخاص وحساباتهم.
                     */
                    group
                        .filter { it.personId.isNotBlank() }
                        .groupBy { it.personId }
                        .values
                        .forEach { personGroup ->

                            val row = personGroup.first()

                            val existingPerson =
                                dao.getPersonByExternalId(
                                    custodyId,
                                    row.personId
                                )

                            val personId =
                                existingPerson?.id
                                    ?: dao.insertPerson(
                                        CustodyPersonEntity(
                                            custodyId = custodyId,
                                            name = row.person,
                                            phone = row.personPhone,
                                            address = row.personAddress,
                                            notes = row.personNotes,
                                            externalId = row.personId
                                        )
                                    ).also {
                                        peopleAdded++
                                    }

                            personIds[
                                "${first.custodyId}|${row.personId}"
                            ] = personId

                            currentAccounts =
                                dao.getAllAccounts(custodyId)

                            val existingPersonCurrencies =
                                currentAccounts
                                    .filter {
                                        it.holderType == "PERSON" &&
                                            it.personId == personId
                                    }
                                    .map { it.currencyCode }
                                    .toSet()

                            val missingPersonCurrencies =
                                ACCOUNT_CURRENCIES.filter {
                                    it !in existingPersonCurrencies
                                }

                            if (missingPersonCurrencies.isNotEmpty()) {

                                val newAccounts =
                                    missingPersonCurrencies.map { currency ->

                                        CustodyAccountEntity(
                                            custodyId = custodyId,
                                            holderType = "PERSON",
                                            personId = personId,
                                            currencyCode = currency
                                        )
                                    }

                                dao.insertAccounts(newAccounts)

                                accountsAdded += newAccounts.size
                            }
                        }
                }

            /*
             * إدخال العمليات.
             *
             * accountId يبقى حساب صاحب العهدة كما هو
             * في التصميم المالي الحالي للتطبيق، بينما personId
             * يحدد الطرف المرتبط بالعملية عند الحاجة.
             */
            rows.forEach { row ->

                if (row.transactionId.isBlank()) {
                    return@forEach
                }

                /*
                 * الاستيراد Idempotent:
                 * العملية الموجودة مسبقاً بنفس externalId لا تعاد إضافتها.
                 */
                if (
                    dao.getTransactionByExternalId(
                        row.transactionId
                    ) != null
                ) {
                    return@forEach
                }

                val custodyId =
                    custodyIds[row.custodyId]
                        ?: error(
                            "الصف ${row.n}: معرف العهدة غير صالح."
                        )

                val personId =
                    if (row.personId.isBlank()) {
                        null
                    } else {
                        personIds[
                            "${row.custodyId}|${row.personId}"
                        ]
                            ?: error(
                                "الصف ${row.n}: الشخص المرتبط بالعملية غير موجود."
                            )
                    }

                val ownerAccount =
                    dao.getOwnerAccount(
                        custodyId = custodyId,
                        currency = row.currency
                    )
                        ?: error(
                            "الصف ${row.n}: حساب العملة ${row.currency} لصاحب العهدة غير موجود."
                        )

                val amount =
                    row.amount
                        ?: error(
                            "الصف ${row.n}: المبلغ غير صالح."
                        )

                val date =
                    row.date
                        ?: error(
                            "الصف ${row.n}: التاريخ غير صالح."
                        )

                dao.insertTransaction(
                    CustodyTransactionEntity(
                        custodyId = custodyId,
                        accountId = ownerAccount.id,
                        personId = personId,
                        currencyCode = row.currency,
                        type = row.type,
                        amountMinor = amount,
                        description = row.description,
                        transactionDate = date,
                        categoryName = row.category,
                        externalId = row.transactionId
                    )
                )

                transactionsAdded++
            }

            /*
             * إعادة بناء أرصدة جميع العهد المتأثرة
             * داخل نفس Room transaction.
             */
            custodyIds
                .values
                .distinct()
                .forEach { custodyId ->

                    CustodyBalanceRebuilder
                        .rebuildCustodyInTransaction(
                            db,
                            custodyId
                        )
                }

            ImportSummary(
                custodiesAdded = custodiesAdded,
                peopleAdded = peopleAdded,
                accountsAdded = accountsAdded,
                transactionsAdded = transactionsAdded
            )
        }
    }

    private fun validate(
        rows: List<Row>
    ): ImportPreview {

        val errors = mutableListOf<String>()

        val custodyIds =
            rows
                .map { it.custodyId }
                .filter { it.isNotBlank() }
                .toSet()

        val personIds =
            rows
                .mapNotNull {
                    if (it.personId.isBlank()) {
                        null
                    } else {
                        "${it.custodyId}|${it.personId}"
                    }
                }
                .toSet()

        val transactionIds =
            mutableSetOf<String>()

        val custodyDefinitions =
            mutableMapOf<String, String>()

        val personDefinitions =
            mutableMapOf<String, String>()

        var transactionCount = 0

        rows.forEach { row ->

            val rowNumber = row.n

            if (row.custodyId.isBlank()) {
                errors +=
                    "الصف $rowNumber: معرف العهدة مطلوب."
            }

            if (row.owner.isBlank()) {
                errors +=
                    "الصف $rowNumber: اسم صاحب العهدة مطلوب."
            }

            if (row.org.isBlank()) {
                errors +=
                    "الصف $rowNumber: اسم الجهة مطلوب."
            }

            /*
             * التأكد من أن نفس معرف العهدة لا يحمل
             * تعريفاً مختلفاً في صف آخر.
             */
            if (row.custodyId.isNotBlank()) {

                val definition =
                    listOf(
                        row.owner,
                        row.ownerPhone,
                        row.ownerAddress,
                        row.ownerNotes,
                        row.org,
                        row.orgPhone,
                        row.orgAddress,
                        row.orgNotes
                    ).joinToString("\u001F")

                val previous =
                    custodyDefinitions.putIfAbsent(
                        row.custodyId,
                        definition
                    )

                if (
                    previous != null &&
                    previous != definition
                ) {
                    errors +=
                        "الصف $rowNumber: بيانات العهدة ذات المعرف ${row.custodyId} غير متطابقة بين الصفوف."
                }
            }

            if (
                row.personId.isNotBlank() &&
                row.person.isBlank()
            ) {
                errors +=
                    "الصف $rowNumber: اسم الشخص مطلوب عندما يكون معرف الشخص موجوداً."
            }

            /*
             * التأكد من اتساق تعريف الشخص داخل العهدة.
             */
            if (
                row.custodyId.isNotBlank() &&
                row.personId.isNotBlank()
            ) {

                val personKey =
                    "${row.custodyId}|${row.personId}"

                val definition =
                    listOf(
                        row.person,
                        row.personPhone,
                        row.personAddress,
                        row.personNotes
                    ).joinToString("\u001F")

                val previous =
                    personDefinitions.putIfAbsent(
                        personKey,
                        definition
                    )

                if (
                    previous != null &&
                    previous != definition
                ) {
                    errors +=
                        "الصف $rowNumber: بيانات الشخص ذات المعرف ${row.personId} غير متطابقة داخل نفس العهدة."
                }
            }

            if (row.transactionId.isBlank()) {
                return@forEach
            }

            transactionCount++

            if (
                !transactionIds.add(
                    row.transactionId
                )
            ) {
                errors +=
                    "الصف $rowNumber: معرف العملية مكرر داخل ملف Excel."
            }

            if (row.currency !in SUPPORTED_CURRENCIES) {
                errors +=
                    "الصف $rowNumber: العملة يجب أن تكون YER أو SAR أو USD."
            }

            if (row.type !in ALLOWED_TYPES) {
                errors +=
                    "الصف $rowNumber: نوع عملية العهدة غير صالح."
            }

            if (
                row.amount == null ||
                row.amount <= 0
            ) {
                errors +=
                    "الصف $rowNumber: المبلغ يجب أن يكون موجباً وبمنزلتين عشريتين كحد أقصى."
            }

            if (row.date == null) {
                errors +=
                    "الصف $rowNumber: التاريخ غير صالح."
            }

            if (
                row.type in PERSON_TYPES &&
                row.personId.isBlank()
            ) {
                errors +=
                    "الصف $rowNumber: هذه العملية تتطلب شخصاً."
            }

            if (
                row.type in PERSON_TYPES &&
                row.person.isBlank()
            ) {
                errors +=
                    "الصف $rowNumber: اسم الشخص مطلوب لهذه العملية."
            }

            if (
                row.type in ORGANIZATION_TYPES &&
                row.personId.isNotBlank()
            ) {
                errors +=
                    "الصف $rowNumber: عملية الجهة لا ترتبط بشخص."
            }
        }

        return ImportPreview(
            custodies = custodyIds.size,
            people = personIds.size,
            accounts =
                custodyIds.size * ACCOUNT_CURRENCIES.size +
                    personIds.size * ACCOUNT_CURRENCIES.size,
            transactions = transactionCount,
            errors = errors
                .distinct()
                .take(100)
        )
    }

    private fun parseWorkbook(
        context: Context,
        uri: Uri
    ): List<Row> {

        openInput(context, uri).use { input ->

            val entries = readZip(input)

            val workbook =
                entries["xl/workbook.xml"]
                    ?: error("ملف Excel غير صالح.")

            check(
                sheetCount(workbook) == 1
            ) {
                "يجب أن يحتوي ملف Excel على Sheet واحد فقط."
            }

            val sheet =
                entries["xl/worksheets/sheet1.xml"]
                    ?: error("ورقة البيانات مفقودة.")

            val sharedStrings =
                entries["xl/sharedStrings.xml"]
                    ?.let(::sharedStrings)
                    ?: emptyList()

            return parseSheet(
                sheet,
                sharedStrings
            )
        }
    }

    private fun readZip(
        input: InputStream
    ): Map<String, ByteArray> {

        val entries = mutableMapOf<String, ByteArray>()

        ZipInputStream(
            input.buffered()
        ).use { zip ->

            while (true) {

                val entry =
                    zip.nextEntry
                        ?: break

                if (entry.isDirectory) {
                    continue
                }

                val output =
                    ByteArrayOutputStream()

                zip.copyTo(output)

                entries[entry.name] =
                    output.toByteArray()
            }
        }

        return entries
    }

    private fun sheetCount(
        bytes: ByteArray
    ): Int {

        val parser =
            Xml.newPullParser()

        parser.setInput(
            ByteArrayInputStream(bytes),
            "UTF-8"
        )

        var count = 0

        var event =
            parser.eventType

        while (
            event != XmlPullParser.END_DOCUMENT
        ) {

            if (
                event == XmlPullParser.START_TAG &&
                parser.name == "sheet"
            ) {
                count++
            }

            event = parser.next()
        }

        return count
    }

    /*
     * قراءة sharedStrings.xml بشكل صحيح.
     *
     * Excel قد يستخدم أكثر من <t> داخل نفس <si>
     * خصوصاً عند وجود Rich Text.
     */
    private fun sharedStrings(
        bytes: ByteArray
    ): List<String> {

        val result = mutableListOf<String>()

        val parser =
            Xml.newPullParser()

        parser.setInput(
            ByteArrayInputStream(bytes),
            "UTF-8"
        )

        var inSharedString = false
        var inText = false

        val current =
            StringBuilder()

        var event =
            parser.eventType

        while (
            event != XmlPullParser.END_DOCUMENT
        ) {

            when (event) {

                XmlPullParser.START_TAG -> {

                    when (parser.name) {

                        "si" -> {
                            inSharedString = true
                            current.setLength(0)
                        }

                        "t" -> {
                            if (inSharedString) {
                                inText = true
                            }
                        }
                    }
                }

                XmlPullParser.TEXT -> {

                    if (
                        inSharedString &&
                        inText
                    ) {
                        current.append(parser.text)
                    }
                }

                XmlPullParser.END_TAG -> {

                    when (parser.name) {

                        "t" -> {
                            inText = false
                        }

                        "si" -> {

                            if (inSharedString) {
                                result += current.toString()
                            }

                            current.setLength(0)
                            inSharedString = false
                            inText = false
                        }
                    }
                }
            }

            event = parser.next()
        }

        return result
    }

    private fun parseSheet(
        bytes: ByteArray,
        sharedStrings: List<String>
    ): List<Row> {

        val result = mutableListOf<Row>()

        val parser =
            Xml.newPullParser()

        parser.setInput(
            ByteArrayInputStream(bytes),
            "UTF-8"
        )

        var cells =
            mutableMapOf<Int, String>()

        var currentColumn = -1

        var currentType = ""

        var currentCellText =
            StringBuilder()

        var inCellValue = false

        var rowNumber = 0

        var event =
            parser.eventType

        while (
            event != XmlPullParser.END_DOCUMENT
        ) {

            when (event) {

                XmlPullParser.START_TAG -> {

                    when (parser.name) {

                        "row" -> {

                            cells = mutableMapOf()

                            rowNumber =
                                parser
                                    .getAttributeValue(
                                        null,
                                        "r"
                                    )
                                    ?.toIntOrNull()
                                    ?: (rowNumber + 1)
                        }

                        "c" -> {

                            currentColumn =
                                column(
                                    parser.getAttributeValue(
                                        null,
                                        "r"
                                    ).orEmpty()
                                )

                            currentType =
                                parser.getAttributeValue(
                                    null,
                                    "t"
                                ).orEmpty()

                            currentCellText =
                                StringBuilder()
                        }

                        "v",
                        "t" -> {

                            if (currentColumn >= 0) {
                                inCellValue = true
                            }
                        }
                    }
                }

                XmlPullParser.TEXT -> {

                    if (inCellValue) {
                        currentCellText.append(
                            parser.text
                        )
                    }
                }

                XmlPullParser.END_TAG -> {

                    when (parser.name) {

                        "v",
                        "t" -> {
                            inCellValue = false
                        }

                        "c" -> {

                            if (currentColumn >= 0) {

                                val raw =
                                    currentCellText
                                        .toString()

                                val value =
                                    if (
                                        currentType == "s"
                                    ) {

                                        val index =
                                            raw
                                                .trim()
                                                .toIntOrNull()

                                        if (index == null) {
                                            raw
                                        } else {
                                            sharedStrings
                                                .getOrNull(index)
                                                ?: raw
                                        }

                                    } else {
                                        raw
                                    }

                                cells[
                                    currentColumn
                                ] = value
                            }

                            currentColumn = -1
                            currentType = ""
                            currentCellText =
                                StringBuilder()
                            inCellValue = false
                        }

                        "row" -> {

                            if (rowNumber == 1) {

                                val headers =
                                    (0..20).map {
                                        cells[it].orEmpty()
                                    }

                                val legacyHeaders = HEADERS.dropLast(1)
                                val currentHeaders = HEADERS

                                require(
                                    headers == currentHeaders ||
                                        headers.take(20) == legacyHeaders
                                ) {
                                    "أعمدة ملف Excel للعُهَد غير مطابقة للصيغة المعتمدة."
                                }

                            } else if (rowNumber > 1) {

                                result += row(
                                    rowNumber,
                                    (0..20).map {
                                        cells[it].orEmpty()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            event = parser.next()
        }

        check(result.isNotEmpty()) {
            "ملف Excel لا يحتوي على بيانات."
        }

        return result
    }

    private fun row(
        n: Int,
        values: List<String>
    ): Row {

        return Row(
            n = n,
            custodyId = values[0].trim(),
            transactionId = values[1].trim(),
            personId = values[2].trim(),
            owner = values[3].trim(),
            ownerPhone = values[4].trim(),
            ownerAddress = values[5].trim(),
            ownerNotes = values[6].trim(),
            org = values[7].trim(),
            orgPhone = values[8].trim(),
            orgAddress = values[9].trim(),
            orgNotes = values[10].trim(),
            person = values[11].trim(),
            personPhone = values[12].trim(),
            personAddress = values[13].trim(),
            personNotes = values[14].trim(),
            currency = values[15]
                .trim()
                .uppercase(Locale.ROOT),
            type = values[16].trim(),
            amount = parseAmount(values[17]),
            description = values[18].trim(),
            date = parseDate(values[19]),
            category = values.getOrNull(20)?.trim().orEmpty()
        )
    }

    private fun parseAmount(
        value: String
    ): Long? {

        val normalized =
            value
                .trim()
                .replace("٬", "")
                .replace("٫", ".")
                .replace(",", ".")

        if (normalized.isBlank()) {
            return null
        }

        return runCatching {

            BigDecimal(normalized)
                .setScale(
                    2,
                    RoundingMode.UNNECESSARY
                )
                .movePointRight(2)
                .longValueExact()

        }.getOrNull()
    }

    private fun parseDate(
        value: String
    ): Long? {

        val normalized =
            value.trim()

        if (normalized.isBlank()) {
            return null
        }

        return runCatching {

            SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
            ).apply {
                isLenient = false
            }.parse(normalized)?.time

        }.getOrNull()
    }

    private fun column(
        reference: String
    ): Int {

        val letters =
            reference.takeWhile {
                it.isLetter()
            }

        if (letters.isEmpty()) {
            return -1
        }

        var value = 0

        letters.forEach { letter ->

            value =
                value * 26 +
                    (
                        letter.uppercaseChar() -
                            'A' +
                            1
                    )
        }

        return value - 1
    }

    private fun esc(
        value: String
    ): String {

        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun createWorkbook(
        output: OutputStream,
        rows: List<Row>
    ) {

        ZipOutputStream(
            output.buffered()
        ).use { zip ->

            entry(
                zip,
                "[Content_Types].xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                    <Default Extension="rels"
                        ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                    <Default Extension="xml"
                        ContentType="application/xml"/>
                    <Override PartName="/xl/workbook.xml"
                        ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                    <Override PartName="/xl/worksheets/sheet1.xml"
                        ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>
                """.trimIndent()
            )

            entry(
                zip,
                "_rels/.rels",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship
                        Id="rId1"
                        Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
                        Target="xl/workbook.xml"/>
                </Relationships>
                """.trimIndent()
            )

            entry(
                zip,
                "xl/workbook.xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <workbook
                    xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                    <sheets>
                        <sheet
                            name="$SHEET_NAME"
                            sheetId="1"
                            r:id="rId1"/>
                    </sheets>
                </workbook>
                """.trimIndent()
            )

            entry(
                zip,
                "xl/_rels/workbook.xml.rels",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship
                        Id="rId1"
                        Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"
                        Target="worksheets/sheet1.xml"/>
                </Relationships>
                """.trimIndent()
            )

            entry(
                zip,
                "xl/worksheets/sheet1.xml",
                sheetXml(rows)
            )
        }
    }

    private fun entry(
        zip: ZipOutputStream,
        name: String,
        content: String
    ) {

        zip.putNextEntry(
            ZipEntry(name)
        )

        zip.write(
            content.toByteArray(
                Charsets.UTF_8
            )
        )

        zip.closeEntry()
    }

    private fun sheetXml(
        rows: List<Row>
    ): String {

        val data =
            mutableListOf<List<String>>()

        data += HEADERS

        rows.forEach { row ->

            data += listOf(
                row.custodyId,
                row.transactionId,
                row.personId,
                row.owner,
                row.ownerPhone,
                row.ownerAddress,
                row.ownerNotes,
                row.org,
                row.orgPhone,
                row.orgAddress,
                row.orgNotes,
                row.person,
                row.personPhone,
                row.personAddress,
                row.personNotes,
                row.currency,
                row.type,
                row.amount
                    ?.let {
                        BigDecimal(it)
                            .movePointLeft(2)
                            .toPlainString()
                    }
                    ?: "",
                row.description,
                row.date
                    ?.let {
                        SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.US
                        ).format(Date(it))
                    }
                    ?: "",
                row.category
            )
        }

        return buildString {

            append(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                    <sheetData>
                """.trimIndent()
            )

            data.forEachIndexed { rowIndex, row ->

                val excelRow =
                    rowIndex + 1

                append(
                    "<row r=\"$excelRow\">"
                )

                row.forEachIndexed { columnIndex, value ->

                    val reference =
                        "${colName(columnIndex)}$excelRow"

                    append(
                        "<c r=\"$reference\" t=\"inlineStr\">" +
                            "<is>" +
                            "<t xml:space=\"preserve\">" +
                            esc(value) +
                            "</t>" +
                            "</is>" +
                            "</c>"
                    )
                }

                append("</row>")
            }

            append(
                """
                    </sheetData>
                </worksheet>
                """.trimIndent()
            )
        }
    }

    private fun colName(
        index: Int
    ): String {

        var number =
            index + 1

        val result =
            StringBuilder()

        while (number > 0) {

            val remainder =
                (number - 1) % 26

            result.append(
                ('A'.code + remainder).toChar()
            )

            number =
                (number - 1) / 26
        }

        return result
            .reverse()
            .toString()
    }
}
