package com.myaccounts.app.util

import android.content.Context
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import com.myaccounts.app.data.local.AppDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/** Safe merge for scoped .myaccounts backups. Local primary keys are never used as identity. */
object ScopedBackupMergeManager {
    data class Report(
        val added: Int,
        val existing: Int,
        val skipped: Int,
        val warnings: List<String>,
        val errors: List<String>
    )

    suspend fun restore(context: Context, uri: Uri, scope: BackupScope): Result<Report> = runCatching {
        val temp = File.createTempFile("myaccounts-merge-", ".tmp", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
                ?: error("تعذر فتح ملف النسخة الاحتياطية.")
            val root = readBackup(temp)
            validate(root, scope)
            val work = File(context.cacheDir, "merge_${System.currentTimeMillis()}").apply { mkdirs() }
            try {
                extract(temp, work)
                mergeTables(context, root, work, scope)
            } finally { work.deleteRecursively() }
        } finally { temp.delete() }
    }

    private fun readBackup(file: File): JSONObject {
        val work = File.createTempFile("myaccounts-merge-read-", "", file.parentFile)
        return try {
            if (file.inputStream().buffered().use { it.read() == 'P'.code && it.read() == 'K'.code }) {
                ZipInputStream(file.inputStream().buffered()).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        if (e.name == "backup.json") return JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                    }
                }
                error("النسخة الاحتياطية لا تحتوي على backup.json.")
            } else JSONObject(file.readText(Charsets.UTF_8))
        } finally { work.delete() }
    }

    private fun extract(source: File, dir: File) {
        if (source.inputStream().buffered().use { it.read() == 'P'.code && it.read() == 'K'.code }) {
            ZipInputStream(source.inputStream().buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    val target = File(dir, e.name)
                    require(target.canonicalPath.startsWith(dir.canonicalPath + File.separator)) { "مسار مرفق غير آمن داخل النسخة الاحتياطية." }
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
            }
        }
    }

    private fun validate(root: JSONObject, scope: BackupScope) {
        require(root.optString("backupType") == "myaccounts_scoped_backup") { "ملف النسخة الاحتياطية غير صالح." }
        require(root.optInt("formatVersion", -1) == 1) { "إصدار النسخة الاحتياطية غير مدعوم." }
        require(root.optString("scope") == scope.key) { "هذه النسخة تخص ${root.optString("scope")} وليست ${scope.key}." }
    }

    private fun mergeTables(context: Context, root: JSONObject, files: File, scope: BackupScope): Report {
        val db = AppDatabase.getInstance(context).openHelper.writableDatabase
        val defs = root.getJSONArray("tables")
        val byName = buildMap<String, JSONObject> { for (i in 0 until defs.length()) put(defs.getJSONObject(i).getString("name"), defs.getJSONObject(i)) }
        val added = intArrayOf(0); val existing = intArrayOf(0); val skipped = intArrayOf(0)
        val warnings = mutableListOf<String>(); val errors = mutableListOf<String>()
        val people = mutableMapOf<Long, Long>(); val accounts = mutableMapOf<Long, Long>(); val transactions = mutableMapOf<Long, Long>()
        val custodyPeople = mutableMapOf<Long, Long>(); val custodyAccounts = mutableMapOf<Long, Long>(); val custodyTransactions = mutableMapOf<Long, Long>(); val custodies = mutableMapOf<Long, Long>()

        db.beginTransaction()
        try {
            if (scope == BackupScope.ACCOUNTS || scope == BackupScope.ALL) {
                mergePeople(db, byName["people"], people, added, existing, errors)
                mergeAccountAccounts(db, byName["currency_accounts"], people, accounts, added, existing, errors)
                mergeTransactions(db, byName["transactions"], accounts, transactions, added, existing, errors)
                mergeAttachments(context, db, byName["transaction_attachments"], transactions, files, "transaction_attachments", added, existing, skipped, warnings, errors)
            }
            if (scope == BackupScope.CUSTODY || scope == BackupScope.ALL) {
                mergeCustodies(db, byName["custodies"], custodies, added, existing, errors)
                mergeCustodyPeople(db, byName["custody_persons"], custodies, custodyPeople, added, existing, errors)
                mergeCustodyAccounts(db, byName["custody_accounts"], custodies, custodyPeople, custodyAccounts, added, existing, errors)
                mergeCustodyTransactions(db, byName["custody_transactions"], custodies, custodyAccounts, custodyPeople, custodyTransactions, added, existing, errors)
                mergeAttachments(context, db, byName["custody_transaction_attachments"], custodyTransactions, files, "custody_transaction_attachments", added, existing, skipped, warnings, errors)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return Report(added[0], existing[0], skipped[0], warnings.take(100), errors.take(100))
    }

    private fun mergePeople(db: SupportSQLiteDatabase, def: JSONObject?, map: MutableMap<Long, Long>, added: IntArray, existing: IntArray, errors: MutableList<String>) {
        forEachRow(def) { row, n ->
            runRow(errors, n) {
                val old = long(row, "id"); val ext = text(row, "externalId"); require(ext.isNotBlank()) { "معرف الشخص الخارجي فارغ." }
                val found = scalarLong(db, "SELECT id FROM people WHERE externalId=? LIMIT 1", arrayOf(ext))
                if (found != null) { map[old] = found; existing[0]++ } else { map[old] = insert(db, "people", row, setOf("id")); added[0]++ }
            }
        }
    }

    private fun mergeAccountAccounts(db: SupportSQLiteDatabase, def: JSONObject?, people: Map<Long, Long>, map: MutableMap<Long, Long>, added: IntArray, existing: IntArray, errors: MutableList<String>) {
        forEachRow(def) { row, n ->
            runRow(errors, n) {
                val old = long(row, "id"); val person = people[long(row, "personId")] ?: error("الشخص المرتبط بحساب الحسابات غير موجود."); val currency = text(row, "currencyCode")
                val found = scalarLong(db, "SELECT id FROM currency_accounts WHERE personId=? AND currencyCode=? LIMIT 1", arrayOf(person, currency))
                if (found != null) { map[old] = found; existing[0]++ } else { val r = row.copy(); r.put("personId", encInt(person)); map[old] = insert(db, "currency_accounts", r, setOf("id")); added[0]++ }
            }
        }
    }

    private fun mergeTransactions(db: SupportSQLiteDatabase, def: JSONObject?, accounts: Map<Long, Long>, map: MutableMap<Long, Long>, added: IntArray, existing: IntArray, errors: MutableList<String>) {
        forEachRow(def) { row, n ->
            runRow(errors, n) {
                val old = long(row, "id"); val ext = text(row, "externalId"); val account = accounts[long(row, "accountId")] ?: error("الحساب المرتبط بالعملية غير موجود.")
                val found = scalarLong(db, "SELECT id FROM transactions WHERE externalId=? LIMIT 1", arrayOf(ext))
                if (found != null) { map[old] = found; existing[0]++ } else { val r=row.copy(); r.put("accountId", encInt(account)); map[old]=insert(db,"transactions",r,setOf("id")); added[0]++ }
            }
        }
    }

    private fun mergeCustodies(db: SupportSQLiteDatabase, def: JSONObject?, map: MutableMap<Long, Long>, added: IntArray, existing: IntArray, errors: MutableList<String>) {
        forEachRow(def) { row,n -> runRow(errors,n) { val old=long(row,"id"); val ext=text(row,"externalId"); require(ext.isNotBlank()){ "معرف العهدة الخارجي فارغ." }; val found=scalarLong(db,"SELECT id FROM custodies WHERE externalId=? LIMIT 1",arrayOf(ext)); if(found!=null){map[old]=found;existing[0]++}else{map[old]=insert(db,"custodies",row,setOf("id"));added[0]++} } }
    }

    private fun mergeCustodyPeople(db: SupportSQLiteDatabase, def: JSONObject?, custodies: Map<Long, Long>, map: MutableMap<Long, Long>, added: IntArray, existing: IntArray, errors: MutableList<String>) {
        forEachRow(def) { row,n -> runRow(errors,n) { val old=long(row,"id"); val custody=custodies[long(row,"custodyId")] ?: error("العهدة المرتبطة بالطرف غير موجودة."); val ext=text(row,"externalId"); val found=scalarLong(db,"SELECT id FROM custody_persons WHERE custodyId=? AND externalId=? LIMIT 1",arrayOf(custody,ext)); if(found!=null){map[old]=found;existing[0]++}else{val r=row.copy();r.put("custodyId",encInt(custody));map[old]=insert(db,"custody_persons",r,setOf("id"));added[0]++} } }
    }

    private fun mergeCustodyAccounts(db: SupportSQLiteDatabase, def: JSONObject?, custodies: Map<Long, Long>, people: Map<Long, Long>, map: MutableMap<Long, Long>, added: IntArray, existing: IntArray, errors: MutableList<String>) {
        forEachRow(def) { row,n -> runRow(errors,n) { val old=long(row,"id"); val custody=custodies[long(row,"custodyId")] ?: error("العهدة المرتبطة بالحساب غير موجودة."); val personOld=rowLongOrNull(row,"personId"); val person=personOld?.let{people[it]}; if(personOld!=null&&person==null) error("الطرف المرتبط بحساب العهدة غير موجود."); val holder=text(row,"holderType"); val currency=text(row,"currencyCode"); val found=if(person==null) scalarLong(db,"SELECT id FROM custody_accounts WHERE custodyId=? AND holderType=? AND personId IS NULL AND currencyCode=? LIMIT 1",arrayOf(custody,holder,currency)) else scalarLong(db,"SELECT id FROM custody_accounts WHERE custodyId=? AND holderType=? AND personId=? AND currencyCode=? LIMIT 1",arrayOf(custody,holder,person,currency)); if(found!=null){map[old]=found;existing[0]++}else{val r=row.copy();r.put("custodyId",encInt(custody));if(person==null)r.put("personId",encNull())else r.put("personId",encInt(person));map[old]=insert(db,"custody_accounts",r,setOf("id"));added[0]++} } }
    }

    private fun mergeCustodyTransactions(db: SupportSQLiteDatabase, def: JSONObject?, custodies: Map<Long, Long>, accounts: Map<Long, Long>, people: Map<Long, Long>, map: MutableMap<Long, Long>, added: IntArray, existing: IntArray, errors: MutableList<String>) {
        forEachRow(def) { row,n -> runRow(errors,n) { val old=long(row,"id"); val ext=text(row,"externalId"); val custody=custodies[long(row,"custodyId")] ?: error("العهدة المرتبطة بالعملية غير موجودة."); val account=accounts[long(row,"accountId")] ?: error("الحساب المرتبط بعملية العهدة غير موجود."); val personOld=rowLongOrNull(row,"personId"); val person=personOld?.let{people[it]}; val found=scalarLong(db,"SELECT id FROM custody_transactions WHERE externalId=? LIMIT 1",arrayOf(ext)); if(found!=null){map[old]=found;existing[0]++}else{val r=row.copy();r.put("custodyId",encInt(custody));r.put("accountId",encInt(account));if(person==null)r.put("personId",encNull())else r.put("personId",encInt(person));map[old]=insert(db,"custody_transactions",r,setOf("id"));added[0]++} } }
    }

    private fun mergeAttachments(context: Context, db: SupportSQLiteDatabase, def: JSONObject?, txMap: Map<Long, Long>, files: File, table: String, added: IntArray, existing: IntArray, skipped: IntArray, warnings: MutableList<String>, errors: MutableList<String>) {
        forEachRow(def) { row,n -> runRow(errors,n) {
            val tx=txMap[long(row,"transactionId")] ?: run { skipped[0]++; warnings += "$table الصف $n: العملية غير موجودة؛ تم تخطي المرفق."; return@runRow }
            val fileName=text(row,"fileName"); val size=long(row,"sizeBytes")
            val dup=scalarLong(db,"SELECT id FROM $table WHERE transactionId=? AND fileName=? AND sizeBytes=? LIMIT 1",arrayOf(tx,fileName,size))
            if(dup!=null){existing[0]++;skipped[0]++;return@runRow}
            val sourcePath=safePath(text(row,"relativePath")); val source=File(files,"files/$sourcePath"); require(source.isFile){ "ملف المرفق غير موجود: $sourcePath" }; require(source.length()==size){ "حجم المرفق لا يطابق البيانات: $sourcePath" }
            val root=File(context.filesDir,table); root.mkdirs(); val destination=uniqueDestination(root,tx,fileName); destination.parentFile?.mkdirs(); source.copyTo(destination,overwrite=false)
            val r=row.copy();r.put("transactionId",encInt(tx));r.put("relativePath",encText(destination.relativeTo(context.filesDir).path.replace(File.separatorChar,'/')));insert(db,table,r,setOf("id"));added[0]++
        } }
    }

    private fun uniqueDestination(root: File, tx: Long, name: String): File { val clean=name.replace(Regex("[^A-Za-z0-9._-]"),"_").ifBlank{"attachment"}; var i=0; while(true){val suffix=if(i==0)"" else "_$i";val f=File(root,"$tx/$clean$suffix");if(!f.exists())return f;i++} }
    private fun safePath(path:String):String{val p=path.replace('\\','/').trimStart('/');require(p.isNotBlank()&&!p.split('/').any{it==".."}){"مسار مرفق غير آمن."};return p}

    private fun forEachRow(def: JSONObject?, block: (JSONObject,Int)->Unit){if(def==null)return;val rows=def.getJSONArray("rows");for(i in 0 until rows.length()){val arr=rows.getJSONArray(i);val cols=def.getJSONArray("columns");val row=JSONObject();for(c in 0 until cols.length())row.put(cols.getString(c),arr.get(c));block(row,i+1)}}
    private fun runRow(errors:MutableList<String>,n:Int,block:()->Unit){try{block()}catch(t:Throwable){errors+="الصف $n: ${t.message ?: "خطأ غير معروف"}"}}
    private fun text(row:JSONObject,col:String)=value(row,col).optString("value","")
    private fun long(row:JSONObject,col:String)=value(row,col).optLong("value")
    private fun rowLongOrNull(row:JSONObject,col:String):Long?{val v=value(row,col);return if(v.optString("type")=="null")null else v.optLong("value")}
    private fun value(row:JSONObject,col:String):JSONObject=row.optJSONObject(col)?:error("العمود $col مفقود.")
    private fun scalarLong(db:SupportSQLiteDatabase,sql:String,args:Array<Any?>):Long?{db.query(sql,args).use{if(it.moveToFirst())return it.getLong(0)};return null}
    private fun insert(db:SupportSQLiteDatabase,table:String,row:JSONObject,exclude:Set<String>):Long{val cols=row.keys().asSequence().filter{it !in exclude}.toList();val sql="INSERT INTO \"$table\" (${cols.joinToString(","){"\"$it\""}}) VALUES (${cols.joinToString(","){"?"}})";val st=db.compileStatement(sql);try{cols.forEachIndexed{i,c->bind(st,i+1,value(row,c))};return st.executeInsert()}finally{st.close()}}
    private fun bind(st:SupportSQLiteStatement,i:Int,v:JSONObject){when(v.getString("type")){"null"->st.bindNull(i);"integer"->st.bindLong(i,v.getLong("value"));"float"->st.bindDouble(i,v.getDouble("value"));"text"->st.bindString(i,v.getString("value"));"blob"->st.bindBlob(i,android.util.Base64.decode(v.getString("value"),android.util.Base64.NO_WRAP));else->error("نوع بيانات غير مدعوم.")}}
    private fun encInt(v:Long)=JSONObject().put("type","integer").put("value",v)
    private fun encText(v:String)=JSONObject().put("type","text").put("value",v)
    private fun encNull()=JSONObject().put("type","null")
}
