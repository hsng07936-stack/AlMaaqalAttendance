package iq.edu.almaaqal.attendance

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

private const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val MIME_PDF = "application/pdf"

private fun num(d: Double) = fmtNum(d)

// =============================================================== إدارة امتحان (التدريسي)

/** جانب الدكتور: رفع الأسئلة (Excel أو PDF)، النشر، رمز الفتح، متابعة الإجابات، النقل إلى الدرجات. */
@Composable
fun ExamManagePage(exam: JSONObject, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard: ClipboardManager = LocalClipboardManager.current
    val eid = exam.optInt("id")
    val t = bt
    var k by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var pdfDialog by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var uploadResult by remember { mutableStateOf<JSONObject?>(null) }
    var fix by remember { mutableStateOf<JSONObject?>(null) }
    var grading by remember { mutableStateOf<JSONObject?>(null) }
    var toGrades by remember { mutableStateOf(false) }

    fun reload() { Api.forgetCache(); k++ }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            try {
                val (name, bytes) = withContext(Dispatchers.IO) {
                    var n = "file"
                    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (i >= 0 && c.moveToFirst()) n = c.getString(i)
                    }
                    n to (ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0))
                }
                uploadResult = Api.upload("/api/exams/$eid/questions", name, bytes, form) as? JSONObject
            } catch (e: ApiError) {
                toast(ctx, e.message ?: "تعذّر رفع الملف")
            }
            busy = false
            reload()
        }
    }

    grading?.let { s ->
        BackHandler { grading = null; reload() }
        ExamGradePage(eid, s.optInt("student_id"), s.optString("full_name")) { grading = null; reload() }
        return
    }
    BackHandler(onBack = onBack)

    fun publish(on: Boolean, late: Int? = null, show: Boolean? = null) {
        scope.launch {
            val b = JSONObject().put("published", on)
            late?.let { b.put("late_min", it) }
            show?.let { b.put("show_result", it) }
            if (guard(ctx, if (on) "نُشر للطلاب وبُلّغوا" else "أُوقف النشر") { Api.post("/api/exams/$eid/publish", b) }) reload()
        }
    }

    Column(Modifier.fillMaxSize().background(t.page)) {
        BayanBand(exam.optString("title"), subtitle = meta(fmtClock(exam.dbl("at")), "${if (exam.isNull("duration_min")) 30 else exam.optInt("duration_min")} دقيقة"), onBack = onBack)
        key(k) {
            Load(fetch = { Api.getObj("/api/exams/$eid/questions", fresh = true) to Api.getObj("/api/exams/$eid/attempts", fresh = true) }) { (q, a), _ ->
                val qs = q.optJSONArray("questions")?.objs().orEmpty()
                val students = a.optJSONArray("students")?.objs().orEmpty()
                val published = q.optBoolean("published")
                val total = qs.sumOf { it.optDouble("points") }
                ScrollPage {
                    if (qs.isEmpty()) {
                        Panel(padding = PaddingValues(16.dp)) {
                            Text("أسئلة الامتحان", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Excel: عمود «السؤال» ثم الخيارات A B C D ثم «الإجابة» (حرف الخيار الصحيح) و«الدرجة». السؤال بلا خيارات يصير إجابة كتابية، وعمود «حقول» يحدد عدد مربعات الإجابة. أو ارفع ملف PDF وحدد عدد الأسئلة.",
                                color = t.tx2, lineHeight = 26.sp, fontSize = 13.5.sp,
                            )
                        }
                        PrimaryButton("رفع أسئلة من Excel", Icons.Outlined.TableView, busy) { form = emptyMap(); picker.launch(arrayOf(MIME_XLSX)) }
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton("رفع ورقة أسئلة PDF", Icons.Outlined.PictureAsPdf, enabled = !busy) { pdfDialog = true }
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton("تحميل نموذج Excel", Icons.Outlined.FileDownload) { scope.launch { exportExcel(ctx, template = true) } }
                    } else {
                        Panel(accent = if (published) t.ok else t.gold, padding = PaddingValues(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${qs.size} سؤال · المجموع ${num(total)} درجة" + (if (q.optInt("pages") > 0) " · ${q.optInt("pages")} صفحة PDF" else ""),
                                    fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f),
                                )
                                Tag(if (published) "منشور" else "غير منشور", if (published) Kind.Ok else Kind.Warn)
                            }
                            Spacer(Modifier.height(12.dp))
                            if (!published) PrimaryButton("نشر للطلاب وتبليغهم", Icons.Rounded.Publish) { publish(true) }
                            else SecondaryButton("إيقاف النشر") { publish(false) }
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("إظهار الدرجة للطالب بعد انتهاء الوقت", modifier = Modifier.weight(1f))
                                Switch(q.optBoolean("show_result"), { publish(published, show = it) })
                            }
                            Spacer(Modifier.height(6.dp))
                            DropField(
                                "السماح بالبدء متأخرًا (دقيقة)", "${q.optInt("late_min", 10).let { if (it in listOf(0, 5, 10, 15, 30)) it else 10 }}",
                                listOf(0, 5, 10, 15, 30).map { "$it" to "$it" },
                            ) { v -> v?.toIntOrNull()?.let { publish(published, late = it) } }
                        }
                        SectionHeader("رمز الفتح")
                        Panel(padding = PaddingValues(16.dp)) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(q.optString("code"), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 10.sp, color = t.primary)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "الطلاب المتصلون بالإنترنت يُفتح لهم الامتحان تلقائيًا في موعده. أعطِ هذا الرمز للطلاب في القاعة عند البدء إن لم يكن عندهم إنترنت. لا تُظهره قبل الموعد.",
                                    textAlign = TextAlign.Center, color = t.tx2, lineHeight = 26.sp, fontSize = 13.sp,
                                )
                                TextButton(onClick = { clipboard.setText(AnnotatedString(q.optString("code"))); toast(ctx, "نُسخ الرمز") }) {
                                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("نسخ")
                                }
                            }
                        }
                        SectionHeader("النتائج", count = a.optInt("submitted"))
                        Panel {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("سلّم ${a.optInt("submitted")} من ${a.optInt("total")} طالب", fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                                TextButton(onClick = { reload() }) { Text("تحديث") }
                            }
                            if (a.optInt("submitted") > 0) {
                                Spacer(Modifier.height(6.dp))
                                SecondaryButton("نقل الدرجات إلى سجل الدرجات", Icons.Outlined.DriveFileMove) { toGrades = true }
                            }
                        }
                        students.forEach { s ->
                            val flags = s.str("flags")
                            val sub = s.optBoolean("submitted")
                            Panel(
                                padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), bottomGap = 8.dp,
                                accent = if (flags != null) t.err else null, onClick = if (sub) ({ grading = s }) else null,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(s.optString("full_name"), 38.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(s.optString("full_name"), fontWeight = FontWeight.Bold)
                                        if (sub) Text(meta(if (s.optBoolean("offline")) "رُفع بعد القاعة" else null, flags), color = if (flags != null) t.err else t.tx3, fontSize = 12.sp)
                                    }
                                    if (!sub) Tag("لم يسلّم", Kind.Mute, dot = false)
                                    else {
                                        if (s.optBoolean("pending")) { Tag("بانتظار التصحيح", Kind.Warn, dot = false); Spacer(Modifier.width(6.dp)) }
                                        Text("${fmtNum(s.optDouble("score"))}/${fmtNum(s.optDouble("max"))}", fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                        }
                        SectionHeader("الأسئلة")
                        qs.forEach { x ->
                            val opts = x.optJSONArray("options")?.let { o -> List(o.length()) { o.optString(it) } } ?: emptyList()
                            Panel(padding = PaddingValues(12.dp), onClick = { fix = x }) {
                                Text("${x.optInt("id")}. ${x.str("text") ?: "(في ملف PDF)"}", fontWeight = FontWeight.Bold, lineHeight = 24.sp)
                                Spacer(Modifier.height(4.dp))
                                if (opts.isEmpty()) Text("إجابة كتابية · ${num(x.optDouble("points"))} د", color = t.tx3, fontSize = 12.5.sp)
                                else {
                                    val ans = if (x.isNull("answer")) null else x.optInt("answer")
                                    Text(
                                        if (ans == null) "لم تُحدَّد الإجابة الصحيحة. اضغط لتحديدها" else "الصحيح: ${opts.getOrElse(ans) { "?" }} · ${num(x.optDouble("points"))} د",
                                        color = if (ans == null) t.err else t.ok, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton("استبدال الأسئلة (Excel)", Icons.Outlined.Refresh, enabled = !busy) { form = emptyMap(); picker.launch(arrayOf(MIME_XLSX)) }
                    }
                }
            }
        }
    }

    if (pdfDialog) PdfFormDialog(onDismiss = { pdfDialog = false }) { f -> pdfDialog = false; form = f; picker.launch(arrayOf(MIME_PDF)) }
    uploadResult?.let { r ->
        val warn = r.optJSONArray("warnings")?.let { w -> List(w.length()) { w.optString(it) } } ?: emptyList()
        AlertDialog(
            onDismissRequest = { uploadResult = null }, containerColor = bt.card,
            title = { Text("تم رفع الأسئلة", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("${r.optInt("count")} سؤال · مجموع الدرجات ${r.opt("total")}", fontWeight = FontWeight.ExtraBold)
                    if (warn.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text("ملاحظات:", fontWeight = FontWeight.Bold)
                        warn.take(8).forEach { Text("• $it", lineHeight = 24.sp) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("راجع الأسئلة ثم اضغط «نشر للطلاب».")
                }
            },
            confirmButton = { TextButton(onClick = { uploadResult = null }) { Text("حسنًا") } },
        )
    }
    fix?.let { x ->
        FixQuestionDialog(x, onDismiss = { fix = null }) { body ->
            fix = null
            scope.launch { if (guard(ctx) { Api.put("/api/exams/$eid/questions", JSONObject().put("questions", JSONArray().put(body))) }) reload() }
        }
    }
    if (toGrades) ConfirmDialog(
        "نقل الدرجات؟", "تُنقل درجات من سلّم إلى تبويب الدرجات ويُبلَّغ الطلاب. أكمل تصحيح الأسئلة الكتابية قبل النقل.",
        "نقل", danger = false, onDismiss = { toGrades = false },
    ) {
        toGrades = false
        scope.launch { guard(ctx, "نُقلت الدرجات") { Api.post("/api/exams/$eid/to-grades") } }
    }
}

@Composable
private fun PdfFormDialog(onDismiss: () -> Unit, onPick: (Map<String, String>) -> Unit) {
    var n by remember { mutableStateOf("20") }
    var mode by remember { mutableStateOf("mcq") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = bt.card,
        title = { Text("امتحان من ملف PDF", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("يرى الطالب صفحات الـ PDF ويُجيب في ورقة إجابة داخل التطبيق.", lineHeight = 24.sp)
                BayanField(n, { n = it }, "عدد الأسئلة", keyboard = KeyboardType.Number)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(mode == "mcq", { mode = "mcq" }, { Text("اختيار (A-D)") })
                    FilterChip(mode == "text", { mode = "text" }, { Text("إجابة كتابية") })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(mapOf("n_questions" to n.trim(), "mode" to mode, "options" to "4")) }) { Text("اختر الملف") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun FixQuestionDialog(q: JSONObject, onDismiss: () -> Unit, onSave: (JSONObject) -> Unit) {
    val opts = q.optJSONArray("options")?.let { o -> List(o.length()) { o.optString(it) } } ?: emptyList()
    var ans by remember { mutableStateOf(if (q.isNull("answer")) null else q.optInt("answer")) }
    var pts by remember { mutableStateOf(fmtNum(q.optDouble("points"))) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = bt.card,
        title = { Text("سؤال ${q.optInt("id")}", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (opts.isNotEmpty()) {
                    Text("الإجابة الصحيحة")
                    opts.forEachIndexed { i, o ->
                        Row(Modifier.fillMaxWidth().clickable { ans = i }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(ans == i, { ans = i })
                            Text(o)
                        }
                    }
                }
                BayanField(pts, { pts = it }, "الدرجة", keyboard = KeyboardType.Decimal)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val b = JSONObject().put("id", q.optInt("id")).put("points", pts.trim().toDoubleOrNull() ?: q.optDouble("points"))
                if (opts.isNotEmpty() && ans != null) b.put("answer", ans)
                onSave(b)
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}


// =============================================================== تصحيح إجابات طالب

@Composable
private fun ExamGradePage(examId: Int, studentId: Int, name: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(bt.page)) {
        BayanBand(name, subtitle = "إجابات الامتحان", onBack = onBack)
        Load(fetch = { Api.getObj("/api/exams/$examId/attempts/$studentId", fresh = true) }) { d, _ ->
            val t = bt
            val qs = d.optJSONArray("questions")?.objs().orEmpty()
            val ans = d.obj("answers")
            val ts = d.obj("text_scores")
            val hasText = qs.any { (it.optJSONArray("options")?.length() ?: 0) == 0 }
            val scores = remember(d) {
                mutableStateMapOf<String, String>().apply { qs.forEach { q -> put("${q.optInt("id")}", ts.str("${q.optInt("id")}") ?: "") } }
            }
            ScrollPage {
                d.str("flags")?.let { f -> Panel(color = t.errBg) { Text("تنبيه: $f", color = t.err, fontWeight = FontWeight.Bold) } }
                qs.forEach { q ->
                    val id = "${q.optInt("id")}"
                    val opts = q.optJSONArray("options")?.let { o -> List(o.length()) { o.optString(it) } } ?: emptyList()
                    Panel(padding = PaddingValues(12.dp)) {
                        Text("$id. ${q.str("text") ?: "(في ملف PDF)"}", fontWeight = FontWeight.Bold, lineHeight = 24.sp)
                        Spacer(Modifier.height(6.dp))
                        if (opts.isNotEmpty()) {
                            val a = if (!ans.has(id) || ans.isNull(id)) null else ans.optInt(id)
                            val right = if (q.isNull("answer")) null else q.optInt("answer")
                            val ok = right != null && a == right
                            Text(
                                if (a == null) "لم يُجب" else "أجاب: ${opts.getOrElse(a) { "?" }}" +
                                    (if (right == null) "" else if (ok) "  ✓" else "  ✗ (الصحيح: ${opts.getOrElse(right) { "?" }})"),
                                color = if (a == null) t.tx3 else if (ok) t.ok else t.err, fontWeight = FontWeight.Bold,
                            )
                        } else {
                            ans.optJSONArray(id)?.let { arr ->
                                for (i in 0 until arr.length()) {
                                    val x = arr.optString(i)
                                    Text(
                                        if (x.isEmpty()) "— فارغ —" else x, lineHeight = 26.sp,
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).background(t.plumBg, RoundedCornerShape(6.dp)).padding(10.dp),
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("الدرجة: ")
                                OutlinedTextField(
                                    scores[id].orEmpty(), { scores[id] = it }, Modifier.width(96.dp), singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center),
                                )
                                Text("  من ${fmtNum(q.optDouble("points"))}", color = t.tx3)
                            }
                        }
                    }
                }
                if (hasText) PrimaryButton("حفظ التصحيح", Icons.Rounded.Check, saving) {
                    saving = true
                    scope.launch {
                        val m = JSONObject()
                        scores.forEach { (k, v) -> m.put(k, v.trim().toDoubleOrNull() ?: 0.0) }
                        val ok = guard(ctx, "حُفظ التصحيح") {
                            Api.post("/api/exams/$examId/grade", JSONObject().put("student_id", studentId).put("scores", m))
                        }
                        saving = false
                        if (ok) onBack()
                    }
                }
            }
        }
    }
}

// =============================================================== تبويب «الامتحانات» في المادة

/** الجدولة للتدريسي، والقائمة للطالب. onOpen("manage"|"take", exam). */
@Composable
fun ExamsTab(courseId: Int, onOpen: (String, JSONObject) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val staff = Api.role == "teacher" || Api.role == "admin"
    var k by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<JSONObject?>(null) }

    fun reload() { Api.forgetCache(); k++ }

    Box(Modifier.fillMaxSize()) {
        key(k) {
            Load(fetch = { Api.getList("/api/courses/$courseId/exams", fresh = true).objs() }) { all, _ ->
                val t = bt
                val now = System.currentTimeMillis() / 1000.0
                if (all.isEmpty()) {
                    ScrollPage { Empty("لا امتحانات مجدولة", if (staff) "اضغط «امتحان» لجدولة كوز أو ميد" else null, Icons.Rounded.EditNote, Modifier.height(300.dp)) }
                    return@Load
                }
                val upcoming = all.filter { it.optDouble("at") >= now }
                val past = all.filter { it.optDouble("at") < now }.reversed()
                @Composable
                fun ExamRow(e: JSONObject, done: Boolean) {
                    val hasQuiz = e.optBoolean("has_quiz")
                    val click: (() -> Unit)? = if (staff) ({ onOpen("manage", e) }) else if (hasQuiz) ({ onOpen("take", e) }) else null
                    Panel(accent = if (done) null else t.gold, onClick = click) {
                        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Rounded.EditNote)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.optString("title"), fontWeight = FontWeight.ExtraBold)
                                Text(meta(examKinds[e.str("kind")], fmtDay(e.dbl("at")), fmtClock(e.dbl("at"))), color = t.tx2, fontSize = 12.5.sp)
                                Text(
                                    meta(
                                        e.dbl("duration_min")?.let { "${it.toInt()} دقيقة" }, e.dbl("max_score")?.let { "من ${fmtNum(it)}" },
                                        e.str("room"), e.str("shift")?.let { if (it == "morning") "صباحي" else if (it == "evening") "مسائي" else null },
                                    ),
                                    color = t.tx3, fontSize = 12.sp,
                                )
                            }
                            if (hasQuiz && !staff) Tag(if (done) "أسئلة" else "افتح", Kind.Ok, dot = false)
                            else if (!done && !(staff && e.optBoolean("has_questions"))) Tag("قادم", Kind.Warn)
                            if (staff && e.optBoolean("has_questions")) {
                                val pub = e.optInt("published") == 1
                                Tag(if (pub) "منشور" else "مسودة", if (pub) Kind.Ok else Kind.Mute, dot = false)
                            }
                            if (staff) {
                                IconButton(onClick = { editing = e }) { Icon(Icons.Outlined.Edit, "تعديل", tint = t.tx3) }
                                IconButton(onClick = { deleting = e }) { Icon(Icons.Outlined.DeleteOutline, "إلغاء", tint = t.tx3) }
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 90.dp)) {
                    if (upcoming.isNotEmpty()) SectionHeader("القادمة", count = upcoming.size)
                    upcoming.forEach { ExamRow(it, false) }
                    if (past.isNotEmpty()) SectionHeader("السابقة", count = past.size)
                    past.forEach { ExamRow(it, true) }
                }
            }
        }
        if (staff) ExtendedFloatingActionButton(
            onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            containerColor = bt.primary, contentColor = Color.White,
            icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("امتحان", fontWeight = FontWeight.ExtraBold) },
        )
    }

    if (adding || editing != null) {
        ExamDialog(editing, onDismiss = { adding = false; editing = null }) { body ->
            val old = editing
            adding = false; editing = null
            scope.launch {
                val ok = guard(ctx, "حُفظ الامتحان وبُلّغ الطلاب") {
                    if (old == null) Api.post("/api/courses/$courseId/exams", body) else Api.patch("/api/exams/${old.optInt("id")}", body)
                }
                if (ok) reload()
            }
        }
    }
    deleting?.let { e ->
        ConfirmDialog("إلغاء الامتحان؟", "سيُبلَّغ الطلاب بالإلغاء.", "إلغاء الامتحان", onDismiss = { deleting = null }) {
            deleting = null
            scope.launch { if (guard(ctx) { Api.delete("/api/exams/${e.optInt("id")}") }) reload() }
        }
    }
}

private fun defaultWhen(): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, 7); set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun utcMidnightOf(ms: Long): Long {
    val l = Calendar.getInstance().apply { timeInMillis = ms }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear(); set(l[Calendar.YEAR], l[Calendar.MONTH], l[Calendar.DAY_OF_MONTH])
    }.timeInMillis
}

private fun withDateFromUtc(whenMs: Long, utcMs: Long): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMs }
    return Calendar.getInstance().apply {
        timeInMillis = whenMs; set(u[Calendar.YEAR], u[Calendar.MONTH], u[Calendar.DAY_OF_MONTH])
    }.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamDialog(old: JSONObject?, onDismiss: () -> Unit, onSave: (JSONObject) -> Unit) {
    var kind by remember { mutableStateOf(old?.str("kind") ?: "quiz") }
    var title by remember { mutableStateOf(old?.str("title") ?: "") }
    var dur by remember { mutableStateOf(old?.dbl("duration_min")?.toInt()?.toString() ?: "") }
    var maxS by remember { mutableStateOf(old?.dbl("max_score")?.let { fmtNum(it) } ?: "") }
    var room by remember { mutableStateOf(old?.str("room") ?: "") }
    var shift by remember { mutableStateOf(old?.str("shift")?.takeIf { it.isNotEmpty() }) }
    var whenMs by remember { mutableLongStateOf(old?.dbl("at")?.let { (it * 1000).toLong() } ?: defaultWhen()) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = bt.card,
        title = { Text(if (old == null) "امتحان جديد" else "تعديل الامتحان", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    examKinds.entries.forEach { (k, l) -> FilterChip(kind == k, { kind = k }, { Text(l.substringBefore(" (")) }) }
                }
                BayanField(title, { title = it }, "العنوان (اختياري)")
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp)) {
                    Icon(Icons.Outlined.Event, null); Spacer(Modifier.width(8.dp)); Text(fmtTime(whenMs / 1000.0))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BayanField(dur, { dur = it }, "المدة (دقيقة)", Modifier.weight(1f), keyboard = KeyboardType.Number)
                    BayanField(maxS, { maxS = it }, "الدرجة العظمى", Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                }
                BayanField(room, { room = it }, "القاعة (اختياري)")
                Text("لمن؟")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf<Pair<String?, String>>(null to "الكل", "morning" to "الصباحي", "evening" to "المسائي").forEach { (k, l) ->
                        FilterChip(shift == k, { shift = k }, { Text(l) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    JSONObject().put("kind", kind).put("title", title.trim()).put("at", whenMs / 1000.0)
                        .put("duration_min", dur.trim().toIntOrNull() ?: JSONObject.NULL)
                        .put("max_score", maxS.trim().toDoubleOrNull() ?: JSONObject.NULL)
                        .put("room", room.trim()).put("shift", shift ?: ""),
                )
            }) { Text(if (old == null) "إضافة وتبليغ الطلاب" else "حفظ وتبليغ الطلاب") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
    if (pickDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = utcMidnightOf(whenMs))
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = { st.selectedDateMillis?.let { whenMs = withDateFromUtc(whenMs, it) }; pickDate = false; pickTime = true }) { Text("التالي") } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("إلغاء") } },
        ) { DatePicker(st) }
    }
    if (pickTime) {
        val c = Calendar.getInstance().apply { timeInMillis = whenMs }
        val ts = rememberTimePickerState(c[Calendar.HOUR_OF_DAY], c[Calendar.MINUTE], true)
        AlertDialog(
            onDismissRequest = { pickTime = false }, containerColor = bt.card,
            text = { TimePicker(ts) },
            confirmButton = {
                TextButton(onClick = {
                    whenMs = Calendar.getInstance().apply { timeInMillis = whenMs; set(Calendar.HOUR_OF_DAY, ts.hour); set(Calendar.MINUTE, ts.minute) }.timeInMillis
                    pickTime = false
                }) { Text("تم") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("إلغاء") } },
        )
    }
}
