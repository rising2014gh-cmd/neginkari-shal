package com.example.neginkarimanager

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.util.*

// NeginKari Manager - final local/offline version

data class Work(val id: Long, val date: String, val count: Int, val note: String)
data class Finance(val id: Long, val date: String, val amount: Long, val type: String, val due: String, val note: String)

class MainActivity : Activity() {
    private lateinit var store: Store
    private val df = DecimalFormat("#,###")
    private lateinit var root: LinearLayout
    private val backupCreate = 2001
    private val backupOpen = 2002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        showHome()
    }

    private fun screen(title: String): LinearLayout {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 20)
            layoutDirection = LinearLayout.LAYOUT_DIRECTION_RTL
            textDirection = android.view.View.TEXT_DIRECTION_RTL
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titleView = TextView(this).apply { text = title; textSize = 23f; setTypeface(null, Typeface.BOLD); gravity = Gravity.RIGHT }
        header.addView(titleView, LinearLayout.LayoutParams(0, 64, 1f))
        if (title != "مدیریت نگین‌کاری") header.addView(button("خانه") { showHome() }, LinearLayout.LayoutParams(100, 56))
        root.addView(header)
        setContentView(root)
        return root
    }

    private fun label(text: String, size: Float = 17f): TextView = TextView(this).apply {
        this.text = text; textSize = size; setPadding(6, 10, 6, 10); gravity = Gravity.RIGHT
        layoutDirection = LinearLayout.LAYOUT_DIRECTION_RTL
    }

    private fun button(text: String, action: () -> Unit): Button = Button(this).apply { this.text = text; setOnClickListener { action() } }
    private fun field(hint: String, number: Boolean = false): EditText = EditText(this).apply { this.hint = hint; textSize = 16f; setPadding(14, 4, 14, 4); if (number) inputType = 2; gravity = Gravity.RIGHT }
    private fun row(v: android.view.View, h: Int = 62) { root.addView(v, LinearLayout.LayoutParams(-1, h).apply { setMargins(0, 4, 0, 4) }) }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun toman(n: Long) = "${df.format(n)} تومان"

    private fun showHome() {
        val r = screen("مدیریت نگین‌کاری")
        val total = store.works.sumOf { it.count }
        val claims = store.finance.filter { it.type == "طلب" }.sumOf { it.amount }
        val received = store.finance.filter { it.type == "نقدی / واریز" || it.type == "چک" }.sumOf { it.amount }
        val outstanding = (claims - received).coerceAtLeast(0)
        row(label("امروز: ${Jalali.today()}", 17f), 55)
        row(label("📦 مجموع کار ثبت‌شده: $total عدد", 19f), 58)
        row(label("💰 کل طلب ثبت‌شده: ${toman(claims)}", 18f), 58)
        row(label("💵 دریافتی + چک: ${toman(received)}", 18f), 58)
        row(label("📌 مطالبات باقی‌مانده: ${toman(outstanding)}", 19f), 62)
        row(button("📝 ثبت کار روزانه") { showAddWork() }, 62)
        row(button("📋 سوابق کارها") { showWorks() }, 62)
        row(button("💰 بخش مالی") { showFinance() }, 62)
        row(button("📄 گزارش ماهانه و PDF") { showReport() }, 62)
        row(button("⚙️ تنظیمات گزارش") { showSettings() }, 62)
        row(button("☁️ پشتیبان‌گیری در Google Drive") { backupToGoogleDrive() }, 62)
        row(button("♻️ بازیابی پشتیبان") { restoreBackup() }, 62)
    }

    private fun showAddWork() {
        val r = screen("ثبت کار روزانه")
        val date = field("تاریخ (مثلاً ۱۴۰۵/۰۷/۰۸)").apply { setText(Jalali.today()) }
        val count = field("تعداد کار / قطعه", true)
        val note = field("توضیحات کار").apply { minLines = 3 }
        row(label("قیمت در این بخش ثبت نمی‌شود؛ قیمت‌ها فقط در بخش مالی هستند."), 62)
        row(date); row(count); row(note, 110)
        row(button("ثبت کار") {
            val c = count.text.toString().trim().toIntOrNull()
            if (c == null || c <= 0) { toast("تعداد معتبر وارد کنید"); return@button }
            store.addWork(Work(System.currentTimeMillis(), date.text.toString().trim(), c, note.text.toString().trim()))
            toast("کار ثبت شد"); showHome()
        })
    }

    private fun showWorks() {
        val r = screen("سوابق کارها")
        if (store.works.isEmpty()) row(label("هنوز کاری ثبت نشده است."), 60)
        store.works.sortedByDescending { it.id }.forEach { w ->
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8, 4, 8, 4) }
            box.addView(label("${w.date}  |  ${w.count} عدد", 18f))
            if (w.note.isNotBlank()) box.addView(label(w.note, 15f))
            box.addView(button("حذف") { store.deleteWork(w.id); showWorks() })
            row(box, if (w.note.isBlank()) 105 else 145)
        }
    }

    private fun showFinance() {
        val r = screen("بخش مالی")
        val claims = store.finance.filter { it.type == "طلب" }.sumOf { it.amount }
        val cash = store.finance.filter { it.type == "نقدی / واریز" }.sumOf { it.amount }
        val checks = store.finance.filter { it.type == "چک" }.sumOf { it.amount }
        val outstanding = (claims - cash - checks).coerceAtLeast(0)
        row(label("کل طلب: ${toman(claims)}", 19f), 52)
        row(label("نقدی / واریز: ${toman(cash)}", 18f), 52)
        row(label("چک دریافتی: ${toman(checks)}", 18f), 52)
        row(label("مطالبات باقی‌مانده: ${toman(outstanding)}", 20f), 58)
        row(button("➕ ثبت طلب / کارکرد") { showAddFinance("طلب") }, 60)
        row(button("➕ ثبت دریافتی نقدی / واریز") { showAddFinance("نقدی / واریز") }, 60)
        row(button("➕ ثبت چک دریافتی") { showAddFinance("چک") }, 60)
        if (store.finance.isEmpty()) row(label("هنوز رکورد مالی ثبت نشده است."), 60)
        store.finance.sortedByDescending { it.id }.forEach { p ->
            val text = buildString {
                append("${p.date} | ${p.type}\n${toman(p.amount)}")
                if (p.due.isNotBlank()) append("\nسررسید: ${p.due}")
                if (p.note.isNotBlank()) append("\n${p.note}")
            }
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            box.addView(label(text, 16f)); box.addView(button("حذف") { store.deleteFinance(p.id); showFinance() })
            row(box, if (p.due.isBlank() && p.note.isBlank()) 115 else 155)
        }
    }

    private fun showAddFinance(type: String) {
        val r = screen("ثبت $type")
        val date = field("تاریخ").apply { setText(Jalali.today()) }
        val amount = field("مبلغ (تومان)", true)
        val due = field("تاریخ سررسید چک (فقط برای چک)")
        val note = field("توضیحات")
        row(date); row(amount)
        if (type == "چک") row(due)
        row(note)
        row(button("ثبت") {
            val a = amount.text.toString().trim().replace(",", "").toLongOrNull()
            if (a == null || a <= 0) { toast("مبلغ معتبر وارد کنید"); return@button }
            store.addFinance(Finance(System.currentTimeMillis(), date.text.toString().trim(), a, type, if (type == "چک") due.text.toString().trim() else "", note.text.toString().trim()))
            toast("ثبت شد"); showFinance()
        })
    }

    private fun showReport() {
        val r = screen("گزارش ماهانه")
        val month = field("ماه گزارش (مثلاً ۱۴۰۵/۰۷)").apply { setText(Jalali.monthNow()) }
        val filtered = { m: String -> store.works.filter { it.date.replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3').replace('۴','4').replace('۵','5').replace('۶','6').replace('۷','7').replace('۸','8').replace('۹','9').startsWith(m) } }
        row(label("گزارش کارفرما فقط شامل تاریخ، تعداد و توضیحات است و اطلاعات مالی داخل آن قرار نمی‌گیرد."), 82)
        row(month)
        row(button("نمایش گزارش") {
            val list = filtered(normalizeDigits(month.text.toString()).take(7))
            showReportList(list, normalizeDigits(month.text.toString()).take(7))
        })
        row(button("ساخت PDF و اشتراک‌گذاری") {
            val m = normalizeDigits(month.text.toString()).take(7)
            val list = filtered(m)
            if (list.isEmpty()) { toast("برای این ماه کاری ثبت نشده است"); return@button }
            val file = createPdf(m, list)
            sharePdf(file)
        })
    }

    private fun showReportList(list: List<Work>, month: String) {
        val r = screen("گزارش $month")
        row(label("تاریخ                 تعداد        توضیحات", 16f), 50)
        if (list.isEmpty()) row(label("برای این ماه رکوردی وجود ندارد."), 60)
        list.sortedBy { it.date }.forEach { w -> row(label("${w.date}     ${w.count} عدد     ${w.note}"), 72) }
        row(label("جمع کل: ${list.sumOf { it.count }} عدد", 20f), 58)
        row(button("ساخت PDF") { if (list.isNotEmpty()) sharePdf(createPdf(month, list)) }, 62)
    }

    private fun backupToGoogleDrive() {
        val intent = android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(android.content.Intent.EXTRA_TITLE, "NeginKari-Backup-${Jalali.today()}.json")
        }
        startActivityForResult(intent, backupCreate)
    }

    private fun restoreBackup() {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, backupOpen)
    }

    @Deprecated("Deprecated in Android API, kept for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        try {
            if (requestCode == backupCreate) {
                contentResolver.openOutputStream(data.data!!)?.use { out ->
                    out.write(store.exportBackup().toByteArray(Charsets.UTF_8))
                }
                toast("پشتیبان با موفقیت ذخیره شد. اگر Google Drive را انتخاب کنید، در حساب گوگل شما نگهداری می‌شود.")
            } else if (requestCode == backupOpen) {
                val json = contentResolver.openInputStream(data.data!!)?.bufferedReader()?.use { it.readText() } ?: return
                store.importBackup(json)
                toast("اطلاعات با موفقیت بازیابی شد")
                showHome()
            }
        } catch (e: Exception) {
            toast("عملیات پشتیبان‌گیری/بازیابی انجام نشد")
        }
    }

    private fun showSettings() {
        val r = screen("تنظیمات گزارش")
        val name = field("نام شما").apply { setText(store.name) }
        val employer = field("نام کارفرما / شرکت").apply { setText(store.employer) }
        row(label("این اطلاعات در بالای PDF گزارش کارفرما نمایش داده می‌شوند."), 65)
        row(name); row(employer)
        row(button("ذخیره") { store.name = name.text.toString().trim(); store.employer = employer.text.toString().trim(); store.saveMeta(); toast("ذخیره شد"); showHome() })
    }

    private fun createPdf(month: String, list: List<Work>): File {
        val doc = PdfDocument(); val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val c = page.canvas; val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 16f; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        val bold = Paint(p).apply { typeface = Typeface.DEFAULT_BOLD; textSize = 20f }
        fun t(s: String, x: Float, y: Float, paint: Paint = p) { c.drawText(s, x, y, paint) }
        t("گزارش کار نگین‌کاری - $month", 310f, 55f, bold)
        if (store.name.isNotBlank()) t("نام: ${store.name}", 430f, 85f)
        if (store.employer.isNotBlank()) t("کارفرما: ${store.employer}", 300f, 85f)
        t("تاریخ", 500f, 125f, bold); t("تعداد", 390f, 125f, bold); t("توضیحات", 220f, 125f, bold)
        c.drawLine(40f, 135f, 555f, 135f, p)
        var y = 165f
        list.sortedBy { it.date }.forEach { w ->
            t(w.date, 455f, y); t(w.count.toString(), 385f, y); t(w.note.take(38), 70f, y)
            y += 32f
            if (y > 760f) return@forEach
        }
        c.drawLine(40f, y + 4f, 555f, y + 4f, p)
        t("جمع کل: ${list.sumOf { it.count }} عدد", 390f, y + 35f, bold)
        doc.finishPage(page)
        val file = File(cacheDir, "report_$month.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }; doc.close(); return file
    }

    private fun sharePdf(file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "application/pdf"; putExtra(android.content.Intent.EXTRA_STREAM, uri); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        startActivity(android.content.Intent.createChooser(intent, "ارسال گزارش PDF"))
    }

    private fun normalizeDigits(s: String): String = s.map { when (it) { '۰' -> '0'; '۱' -> '1'; '۲' -> '2'; '۳' -> '3'; '۴' -> '4'; '۵' -> '5'; '۶' -> '6'; '۷' -> '7'; '۸' -> '8'; '۹' -> '9'; else -> it } }.joinToString("")

    private class Store(ctx: Context) {
        private val pref = ctx.getSharedPreferences("data", Context.MODE_PRIVATE)
        val works = mutableListOf<Work>(); val finance = mutableListOf<Finance>()
        var name: String = pref.getString("name", "") ?: ""; var employer: String = pref.getString("employer", "") ?: ""
        init { load() }
        fun addWork(w: Work) { works.add(w); save() }
        fun deleteWork(id: Long) { works.removeAll { it.id == id }; save() }
        fun addFinance(f: Finance) { finance.add(f); save() }
        fun deleteFinance(id: Long) { finance.removeAll { it.id == id }; save() }
        fun exportBackup(): String {
            val root = JSONObject()
            root.put("version", 1)
            root.put("name", name)
            root.put("employer", employer)
            val wa = JSONArray()
            works.forEach { wa.put(JSONObject().apply { put("id", it.id); put("date", it.date); put("count", it.count); put("note", it.note) }) }
            val fa = JSONArray()
            finance.forEach { fa.put(JSONObject().apply { put("id", it.id); put("date", it.date); put("amount", it.amount); put("type", it.type); put("due", it.due); put("note", it.note) }) }
            root.put("works", wa)
            root.put("finance", fa)
            return root.toString(2)
        }

        fun importBackup(json: String) {
            val root = JSONObject(json)
            val newWorks = mutableListOf<Work>()
            val newFinance = mutableListOf<Finance>()
            val wa = root.optJSONArray("works") ?: JSONArray()
            for (i in 0 until wa.length()) {
                val o = wa.getJSONObject(i)
                newWorks.add(Work(o.getLong("id"), o.getString("date"), o.getInt("count"), o.optString("note")))
            }
            val fa = root.optJSONArray("finance") ?: JSONArray()
            for (i in 0 until fa.length()) {
                val o = fa.getJSONObject(i)
                newFinance.add(Finance(o.getLong("id"), o.getString("date"), o.getLong("amount"), o.getString("type"), o.optString("due"), o.optString("note")))
            }
            works.clear(); works.addAll(newWorks)
            finance.clear(); finance.addAll(newFinance)
            name = root.optString("name", name)
            employer = root.optString("employer", employer)
            saveMeta()
            save()
        }

        fun saveMeta() { pref.edit().putString("name", name).putString("employer", employer).apply() }
        private fun save() {
            val wa = JSONArray(); works.forEach { wa.put(JSONObject().apply { put("id",it.id);put("date",it.date);put("count",it.count);put("note",it.note) }) }
            val fa = JSONArray(); finance.forEach { fa.put(JSONObject().apply { put("id",it.id);put("date",it.date);put("amount",it.amount);put("type",it.type);put("due",it.due);put("note",it.note) }) }
            pref.edit().putString("works", wa.toString()).putString("finance", fa.toString()).apply()
        }
        private fun load() {
            try { val a=JSONArray(pref.getString("works","[]")); for(i in 0 until a.length()){val o=a.getJSONObject(i);works.add(Work(o.getLong("id"),o.getString("date"),o.getInt("count"),o.optString("note"))) } } catch(_:Exception){}
            try { val a=JSONArray(pref.getString("finance","[]")); for(i in 0 until a.length()){val o=a.getJSONObject(i);finance.add(Finance(o.getLong("id"),o.getString("date"),o.getLong("amount"),o.getString("type"),o.optString("due"),o.optString("note"))) } } catch(_:Exception){}
        }
    }
}

object Jalali {
    fun today(): String = gregorianToJalali(Calendar.getInstance()).let { "${it[0]}/${it[1].toString().padStart(2,'0')}/${it[2].toString().padStart(2,'0')}" }
    fun monthNow(): String = today().take(7)
    private fun gregorianToJalali(c: Calendar): IntArray {
        var gy=c.get(Calendar.YEAR); var gm=c.get(Calendar.MONTH)+1; val gd=c.get(Calendar.DAY_OF_MONTH)
        val gdm=intArrayOf(0,31,28,31,30,31,30,31,31,30,31,30,31); val jdm=intArrayOf(0,31,31,31,31,31,30,30,30,30,30,30,29)
        var gy2=gy-1600; val gm2=gm-1; val gd2=gd-1
        var days=365*gy2+(gy2+3)/4-(gy2+99)/100+(gy2+399)/400
        for(i in 0 until gm2) days+=gdm[i+1]; if(gm2>1 && (gy%4==0 && gy%100!=0 || gy%400==0)) days++
        days+=gd2; var jdays=days-79; val jnp=jdays/12053; jdays%=12053; var jy=979+33*jnp+4*(jdays/1461); jdays%=1461
        if(jdays>=366){jy+=(jdays-1)/365; jdays=(jdays-1)%365}
        var jm=1; while(jm<=11 && jdays>=jdm[jm]){jdays-=jdm[jm];jm++}; val jd=jdays+1
        return intArrayOf(jy,jm,jd)
    }
}
