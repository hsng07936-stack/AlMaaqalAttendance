package iq.edu.almaaqal.attendance

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

private fun JSONObject.isPresent() = optString("status").let { it == "present" || it == "late" }
private fun lectureTitle(l: JSONObject) = l.str("title")?.takeIf { it.isNotEmpty() } ?: "محاضرة"

// =============================================================== صفحة المادة

/** صفحة المادة (CoursePage): شريط ملخص وتبويبات بحسب الدور. */
@Composable
fun CoursePage(course: JSONObject, initialTab: Int, onBack: () -> Unit) {
    val role = Api.role
    val id = course.optInt("id")
    val tabs = remember(role) {
        buildList {
            if (role == "teacher" || role == "admin") add("الحضور")
            if (role == "student") add("المحاضرات")
            if (role != "student") add("التقرير")
            add("الامتحانات"); add("الدرجات"); add("الملفات")
            if (role != "supervisor") add("الرسائل")
        }
    }
    var idx by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, tabs.size - 1)) }
    var lecture by remember { mutableStateOf<JSONObject?>(null) }
    var examView by remember { mutableStateOf<Pair<String, JSONObject>?>(null) }

    lecture?.let { l ->
        BackHandler { lecture = null }
        LectureRosterPage(l) { lecture = null }
        return
    }
    examView?.let { (mode, e) ->
        BackHandler { examView = null }
        if (mode == "manage") ExamManagePage(e) { examView = null } else StudentExamPage(e) { examView = null }
        return
    }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(bt.page)) {
        BayanBand(
            course.optString("name"),
            subtitle = meta(course.str("code"), course.str("stage"), course.str("teacher")),
            onBack = onBack,
        ) { Strip(course, tabs) { idx = it } }
        Spacer(Modifier.height(6.dp))
        Chips(tabs, idx, { idx = it })
        Spacer(Modifier.height(6.dp))
        Box(Modifier.weight(1f)) {
            when (tabs[idx]) {
                "الحضور" -> CourseAttendanceTab(id) { lecture = it }
                "المحاضرات" -> CourseLecturesTab(id)
                "التقرير" -> CourseReportTab(id)
                "الدرجات" -> CourseGradesTab(course)
                "الرسائل" -> ChatTab(id)
                "الامتحانات" -> ExamsTab(id) { mode, e -> examView = mode to e }
                else -> Empty(
                    "${tabs[idx]} قيد النقل", "تأتي في مرحلة لاحقة من خطة النقل",
                    Icons.Outlined.Construction, Modifier.padding(top = 40.dp),
                )
            }
        }
    }
}

/** ثلاث خانات في الشريط تنقل إلى التبويب المناسب عند اللمس. */
@Composable
private fun Strip(c: JSONObject, tabs: List<String>, go: (Int) -> Unit) {
    val items: List<Triple<String, String, String>> =
        if (c.has("attendance") && !c.isNull("attendance")) {
            val a = c.obj("attendance")
            listOf(
                Triple("المحاضرات", "${a.optInt("lectures")}", "المحاضرات"),
                Triple("حضرت", "${a.optInt("attended")}", "المحاضرات"),
                Triple("غبت", "${a.optInt("absent")}", "المحاضرات"),
            )
        } else listOf(
            Triple("الطلاب", c.str("students") ?: "—", "التقرير"),
            Triple("المحاضرات", c.str("lectures") ?: "—", if ("الحضور" in tabs) "الحضور" else "التقرير"),
            Triple("الحضور", pct(c.dbl("attendance_rate")), "التقرير"),
        )
    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (label, value, tab) ->
            val shape = RoundedCornerShape(6.dp)
            Column(
                Modifier.weight(1f).clip(shape).background(Color.White.copy(alpha = .09f))
                    .border(1.dp, Color.White.copy(alpha = .12f), shape)
                    .clickable { tabs.indexOf(tab).takeIf { it >= 0 }?.let(go) }
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(value, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text(label, color = Color.White.copy(alpha = .75f), fontSize = 12.sp)
            }
        }
    }
}

// =============================================================== حضور اليوم (التدريسي)

@Composable
private fun CourseAttendanceTab(courseId: Int, onOpenLecture: (JSONObject) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = bt
    val nfc = remember { NfcHub.available(ctx) }

    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<JSONObject?>(null) }
    var past by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var roster by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var banner by remember { mutableStateOf<String?>(null) }
    var bannerName by remember { mutableStateOf<String?>(null) }
    var bannerBad by remember { mutableStateOf(false) }
    var filter by remember { mutableIntStateOf(0) }
    var nameQ by remember { mutableStateOf("") }
    var uid by remember { mutableStateOf("") }
    var startDialog by remember { mutableStateOf(false) }
    var endDialog by remember { mutableStateOf(false) }
    var editRow by remember { mutableStateOf<JSONObject?>(null) }
    val busyScan = remember { booleanArrayOf(false) }

    suspend fun loadRoster(l: JSONObject) {
        roster = Api.getList("/api/lectures/${l.optInt("id")}/attendance", fresh = true).objs()
    }

    LaunchedEffect(reloadKey) {
        try {
            val ls = Api.getList("/api/courses/$courseId/lectures", fresh = true).objs()
            past = ls.filter { !it.isNull("ended_at") && it.has("ended_at") }
            open = ls.firstOrNull { !it.has("ended_at") || it.isNull("ended_at") }
            if (open != null) loadRoster(open!!) else roster = emptyList()
        } catch (e: ApiError) {
            banner = e.message; bannerBad = true
        }
        loading = false
    }

    fun scan(u: String) {
        val o = open ?: return
        if (busyScan[0]) return
        busyScan[0] = true
        scope.launch {
            try {
                val r = Api.post("/api/lectures/${o.optInt("id")}/scan", JSONObject().put("card_uid", u)) as? JSONObject ?: JSONObject()
                bannerName = r.str("student")
                banner = if (r.optBoolean("duplicate")) "مسجّل مسبقًا في هذه المحاضرة" else "تم تسجيل الحضور"
                bannerBad = false
                loadRoster(o)
            } catch (e: ApiError) {
                bannerName = null; banner = e.message; bannerBad = true
            } finally {
                busyScan[0] = false
            }
        }
    }
    val scanNow by rememberUpdatedState(newValue = { u: String -> scan(u) })

    // أي بطاقة تُقرَّب ما دامت المحاضرة مفتوحة تُسجَّل
    val openId = open?.optInt("id")
    DisposableEffect(openId, nfc) {
        if (openId != null && nfc) NfcHub.listener = { r -> scanNow(r.uid) }
        onDispose { NfcHub.listener = null }
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = t.primary) }
        return
    }

    val o = open
    if (o == null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp)) {
            if (banner != null) Panel(color = t.errBg) { Text(banner!!, color = t.err) }
            Panel(padding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 22.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(84.dp).background(t.plumBg, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.EventAvailable, null, Modifier.size(40.dp), tint = t.plumText)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("لا توجد محاضرة مفتوحة", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("ابدأ محاضرة ثم قرّب بطاقات الطلاب لتسجيل الحضور", textAlign = TextAlign.Center, color = t.tx3, lineHeight = 26.sp)
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton("بدء محاضرة جديدة", Icons.Rounded.PlayArrow) { startDialog = true }
                }
            }
            SectionHeader("المحاضرات السابقة")
            if (past.isEmpty()) Empty("لا توجد محاضرات سابقة", icon = Icons.Outlined.History, modifier = Modifier.height(160.dp))
            past.forEach { l ->
                Panel(onClick = { onOpenLecture(l) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Outlined.History)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(lectureTitle(l), fontWeight = FontWeight.Bold)
                            Text("${fmtDay(l.dbl("started_at"))}  ·  ${fmtClock(l.dbl("started_at"))}", color = t.tx3, fontSize = 12.5.sp)
                        }
                        Icon(Icons.Rounded.ChevronLeft, null, tint = t.tx3)
                    }
                }
            }
        }
    } else {
        val url = o.str("online_url")
        val present = roster.count { it.isPresent() }
        val shown = roster.filter { r ->
            (nameQ.isEmpty() || r.optString("full_name").contains(nameQ)) &&
                (filter == 0 || (filter == 1 && r.isPresent()) || (filter == 2 && r.optString("status") == "absent"))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp)) {
            Panel(padding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag("مفتوحة الآن", Kind.Ok)
                    Spacer(Modifier.width(10.dp))
                    Text("منذ ${fmtClock(o.dbl("started_at"))}", color = t.tx3, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { endDialog = true }, colors = ButtonDefaults.textButtonColors(contentColor = t.err)) { Text("إنهاء") }
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    NfcPulse(live = nfc)
                    Text(
                        if (nfc) "قرّب بطاقة الطالب من ظهر الهاتف" else "NFC غير متاح هنا. أدخل رقم البطاقة يدويًا.",
                        textAlign = TextAlign.Center, fontWeight = FontWeight.ExtraBold, fontSize = 15.5.sp,
                    )
                }
                banner?.let { b ->
                    Row(
                        Modifier.padding(top = 14.dp).fillMaxWidth().background(if (bannerBad) t.errBg else t.okBg, RoundedCornerShape(6.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (bannerName != null) { Avatar(bannerName!!, 38.dp); Spacer(Modifier.width(10.dp)) }
                        else Icon(Icons.Outlined.ErrorOutline, null, tint = t.err)
                        Column {
                            bannerName?.let { Text(it, color = if (bannerBad) t.err else t.ok, fontWeight = FontWeight.ExtraBold) }
                            Text(b, color = if (bannerBad) t.err else t.ok, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        uid, { uid = it }, Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(6.dp),
                        placeholder = { Text("رقم البطاقة (UID) يدويًا") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    )
                    Button(
                        onClick = { val v = uid.trim(); if (v.isNotEmpty()) { scan(v); uid = "" } },
                        modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = t.primary, contentColor = Color.White),
                    ) { Text("تسجيل") }
                }
                if (url != null) {
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton("فتح المحاضرة الأونلاين", Icons.Outlined.Videocam) {
                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { toast(ctx, "تعذّر فتح الرابط") }
                    }
                }
            }
            SectionHeader("حضور هذه المحاضرة", count = roster.size)
            Counts(present, roster.size - present, roster.size) { filter = it }
            Spacer(Modifier.height(12.dp))
            Chips(listOf("الكل", "الحاضرون", "الغائبون"), filter, { filter = it }, hPad = 0.dp)
            Spacer(Modifier.height(10.dp))
            BayanField(nameQ, { nameQ = it.trim() }, "ابحث باسم الطالب لتحضيره", leading = Icons.Outlined.Search)
            Spacer(Modifier.height(10.dp))
            RosterList(shown, onTap = { editRow = it }, onPresent = { s ->
                scope.launch {
                    try {
                        Api.post(
                            "/api/lectures/${o.optInt("id")}/mark",
                            JSONObject().put("student_id", s.opt("student_id")).put("status", "present").put("note", "تحضير يدوي بالاسم"),
                        )
                        toast(ctx, "حُضّر ${s.optString("full_name")}")
                        loadRoster(o)
                    } catch (e: ApiError) { toast(ctx, e.message ?: "تعذّر التحضير") }
                }
            })
        }
    }

    // ---- النوافذ
    if (startDialog) StartLectureDialog(
        onDismiss = { startDialog = false },
        onStart = { title, url, shift ->
            startDialog = false
            scope.launch {
                try {
                    Api.post("/api/courses/$courseId/lectures", JSONObject().put("title", title).put("online_url", url).put("shift", shift))
                    loading = true; banner = null; reloadKey++
                } catch (e: ApiError) { toast(ctx, e.message ?: "تعذّر بدء المحاضرة") }
            }
        },
    )
    if (endDialog && open != null) AlertDialog(
        onDismissRequest = { endDialog = false }, containerColor = t.card,
        title = { Text("إنهاء المحاضرة؟", fontWeight = FontWeight.ExtraBold) },
        text = { Text("بعد الإنهاء لا يمكن مسح بطاقات جديدة، وتُحتسب الغيابات.", lineHeight = 26.sp) },
        confirmButton = {
            TextButton(onClick = {
                endDialog = false
                val id = open!!.optInt("id")
                scope.launch {
                    try { Api.post("/api/lectures/$id/end"); banner = null; loading = true; reloadKey++ }
                    catch (e: ApiError) { toast(ctx, e.message ?: "تعذّر الإنهاء") }
                }
            }) { Text("إنهاء") }
        },
        dismissButton = { TextButton(onClick = { endDialog = false }) { Text("إلغاء") } },
    )
    editRow?.let { row ->
        EditAttendanceDialog(open?.optInt("id") ?: 0, row, onDismiss = { editRow = null }) { saved ->
            editRow = null
            if (saved) scope.launch { open?.let { try { loadRoster(it) } catch (_: ApiError) {} } }
        }
    }
}

@Composable
private fun StartLectureDialog(onDismiss: () -> Unit, onStart: (String, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var shift by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = bt.card,
        title = { Text("محاضرة جديدة", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BayanField(title, { title = it }, "عنوان المحاضرة (اختياري)")
                BayanField(url, { url = it }, "رابط المحاضرة الأونلاين (اختياري)", keyboard = KeyboardType.Uri)
                Text("لمن هذه المحاضرة؟")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("" to "الكل", "morning" to "الصباحي", "evening" to "المسائي").forEach { (k, l) ->
                        FilterChip(selected = shift == k, onClick = { shift = k }, label = { Text(l) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onStart(title.trim(), url.trim(), shift) }) { Text("ابدأ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

/** تعديل يدوي للحضور (السبب إلزامي). onDone(true) عند الحفظ. */
@Composable
fun EditAttendanceDialog(lectureId: Int, s: JSONObject, onDismiss: () -> Unit, onDone: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(s.optString("status")) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = bt.card,
        title = { Text(s.optString("full_name"), fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    statusAr.forEach { (k, l) -> FilterChip(selected = status == k, onClick = { status = k }, label = { Text(l) }) }
                }
                BayanField(note, { note = it }, "سبب التعديل (إلزامي)")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    try {
                        Api.post(
                            "/api/lectures/$lectureId/mark",
                            JSONObject().put("student_id", s.opt("student_id")).put("status", status).put("note", note.trim()),
                        )
                        onDone(true)
                    } catch (e: ApiError) { toast(ctx, e.message ?: "تعذّر الحفظ") }
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun Counts(present: Int, absent: Int, total: Int, onPick: (Int) -> Unit) {
    val t = bt
    @Composable
    fun RowScope.box(label: String, v: Int, c: Color, bg: Color, pick: Int) {
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(bg).clickable { onPick(pick) }.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$v", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = c)
            Text(label, color = c, fontSize = 12.5.sp)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        box("حاضر", present, t.ok, t.okBg, 1)
        box("غائب", absent, t.err, t.errBg, 2)
        box("المجموع", total, t.plumText, t.plumBg, 0)
    }
}

@Composable
private fun RosterList(rows: List<JSONObject>, onTap: ((JSONObject) -> Unit)? = null, onPresent: ((JSONObject) -> Unit)? = null) {
    val t = bt
    if (rows.isEmpty()) { Empty("لا أحد هنا", icon = Icons.Outlined.Groups, modifier = Modifier.height(140.dp)); return }
    rows.forEach { r ->
        val st = r.optString("status")
        Panel(
            padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), bottomGap = 8.dp,
            accent = kindColor(statusKind(st)), onClick = onTap?.let { f -> { f(r) } },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(r.optString("full_name"), 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.optString("full_name"), fontWeight = FontWeight.Bold)
                    if (r.has("at") && !r.isNull("at")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (r.optString("method") == "nfc") Icons.Outlined.Nfc else Icons.Outlined.Edit, null, Modifier.size(14.dp), tint = t.tx3)
                            Spacer(Modifier.width(4.dp))
                            Text(fmtClock(r.dbl("at")) + (r.str("note")?.let { "  ·  $it" } ?: ""), color = t.tx3, fontSize = 12.sp)
                        }
                    }
                }
                Tag(statusAr[st] ?: st, statusKind(st))
                if (onPresent != null && !r.isPresent()) {
                    IconButton(onClick = { onPresent(r) }) { Icon(Icons.Outlined.CheckCircleOutline, "تحضير", tint = t.ok) }
                }
            }
        }
    }
}

/** كشف محاضرة سابقة (LectureRosterPage): يمكن تعديل أي طالب منه. */
@Composable
private fun LectureRosterPage(l: JSONObject, onBack: () -> Unit) {
    var k by remember { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<JSONObject?>(null) }
    Column(Modifier.fillMaxSize().background(bt.page)) {
        BayanBand(lectureTitle(l), subtitle = fmtTime(l.dbl("started_at")), onBack = onBack)
        key(k) {
            Load(fetch = { Api.getList("/api/lectures/${l.optInt("id")}/attendance").objs() }) { rows, _ ->
                val present = rows.count { it.isPresent() }
                ScrollPage {
                    Counts(present, rows.size - present, rows.size) {}
                    Spacer(Modifier.height(14.dp))
                    RosterList(rows, onTap = { edit = it })
                }
            }
        }
    }
    edit?.let { row ->
        EditAttendanceDialog(l.optInt("id"), row, onDismiss = { edit = null }) { saved -> edit = null; if (saved) { Api.forgetCache(); k++ } }
    }
}

// =============================================================== محاضرات الطالب

@Composable
private fun CourseLecturesTab(courseId: Int) {
    val ctx = LocalContext.current
    Load(fetch = { Api.getList("/api/courses/$courseId/my_attendance").objs() }) { ls, _ ->
        val t = bt
        if (ls.isEmpty()) {
            ScrollPage { Empty("لا توجد محاضرات بعد", "تظهر هنا حين يبدأ التدريسي أول محاضرة", Icons.Outlined.EventNote, Modifier.height(300.dp)) }
            return@Load
        }
        ScrollPage {
            ls.forEach { l ->
                val isOpen = !l.has("ended_at") || l.isNull("ended_at")
                val status = if (isOpen) "open" else (l.str("status") ?: "absent")
                val url = l.str("online_url")
                val (kind, icon) = when (status) {
                    "present" -> Kind.Ok to Icons.Rounded.Check
                    "late", "excused" -> Kind.Warn to Icons.Rounded.Schedule
                    "open" -> Kind.Ok to Icons.Outlined.Sensors
                    else -> Kind.Err to Icons.Rounded.Close
                }
                val col = kindColor(kind)
                Panel(accent = if (isOpen) t.ok else null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).background(col.copy(alpha = .13f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = col) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(lectureTitle(l), fontWeight = FontWeight.ExtraBold)
                            Text("${fmtDay(l.dbl("started_at"))}  ·  ${fmtClock(l.dbl("started_at"))}", color = t.tx3, fontSize = 12.5.sp)
                        }
                        if (isOpen && url != null) {
                            Button(
                                onClick = { try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { toast(ctx, "تعذّر فتح الرابط") } },
                                colors = ButtonDefaults.buttonColors(containerColor = t.primary, contentColor = Color.White),
                                shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(horizontal = 14.dp),
                            ) { Text("انضمام") }
                        } else Tag(if (isOpen) "جارية" else (statusAr[status] ?: status), kind)
                    }
                }
            }
        }
    }
}

// =============================================================== تقرير المادة (التدريسي/المشرف)

@Composable
private fun CourseReportTab(courseId: Int) {
    var filter by remember { mutableIntStateOf(0) }
    Load(fetch = { Api.getList("/api/courses/$courseId/report").objs() }) { data, _ ->
        val t = bt
        val all = data.sortedByDescending { it.optDouble("absence_rate", 0.0) }
        if (all.isEmpty()) {
            ScrollPage { Empty("لا يوجد طلاب مسجلون", icon = Icons.Outlined.Groups, modifier = Modifier.height(300.dp)) }
            return@Load
        }
        val flagged = all.count { it.optBoolean("flagged") }
        val withLect = all.filter { it.optInt("lectures") > 0 }
        val avg = if (withLect.isEmpty()) null else withLect.sumOf { 1 - it.optDouble("absence_rate", 0.0) } / withLect.size
        val shown = if (filter == 0) all else all.filter { it.optBoolean("flagged") }
        ScrollPage {
            val rctx = LocalContext.current
            val rscope = rememberCoroutineScope()
            SecondaryButton("تصدير Excel", Icons.Outlined.FileDownload) { rscope.launch { exportExcel(rctx, courseId = courseId) } }
            Spacer(Modifier.height(12.dp))
            StatGrid(
                { m -> StatTile("متوسط الحضور", pct(avg), m, caption = "لكل الطلاب", icon = Icons.Outlined.FactCheck, onClick = { filter = 0 }) },
                { m ->
                    StatTile(
                        "تجاوزوا الحد", "$flagged", m, caption = if (flagged == 0) "لا أحد" else "من ${all.size} طالب",
                        icon = Icons.Rounded.WarningAmber, valueColor = if (flagged > 0) t.err else null, onClick = { filter = 1 },
                    )
                },
            )
            Spacer(Modifier.height(14.dp))
            Chips(listOf("كل الطلاب", "المتجاوزون"), filter, { filter = it }, counts = listOf(all.size, flagged), hPad = 0.dp)
            Spacer(Modifier.height(10.dp))
            if (shown.isEmpty()) Empty("لا أحد تجاوز حد الغياب", icon = Icons.Outlined.Verified, modifier = Modifier.height(140.dp))
            shown.forEach { r ->
                val fl = r.optBoolean("flagged")
                val n = r.optInt("lectures")
                val rate = if (n == 0) null else 1 - r.optDouble("absence_rate", 0.0)
                Panel(
                    accent = if (fl) t.err else null, padding = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
                    onClick = { openStudent(courseId, if (r.has("student_id")) r.optInt("student_id") else r.optInt("id"), r.optString("full_name")) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(r.optString("full_name"), 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.optString("full_name"), fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (rate ?: 0.0).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(6.dp)),
                                color = if (fl) t.err else t.ok, trackColor = t.bar,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("حضر ${r.optInt("attended")} من $n  ·  غاب ${r.optInt("absent")}", color = t.tx3, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(pct(rate), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = if (fl) t.err else t.ok)
                    }
                }
            }
        }
    }
}

// =============================================================== درجات المادة

@Composable
private fun CourseGradesTab(course: JSONObject) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cid = course.optInt("id")
    val isStudent = Api.role == "student"
    val canEdit = Api.role == "teacher" || Api.role == "admin"
    var k by remember { mutableIntStateOf(0) }
    var addFor by remember { mutableStateOf<Int?>(null) } // -1 = أول طالب
    var students by remember { mutableStateOf<List<JSONObject>>(emptyList()) }

    fun openAdd(who: Int?) {
        scope.launch {
            try {
                students = Api.getList("/api/courses/$cid/students").objs()
                if (students.isNotEmpty()) addFor = who ?: students.first().optInt("id")
            } catch (e: ApiError) { toast(ctx, e.message ?: "تعذّر تحميل الطلاب") }
        }
    }

    Box(Modifier.fillMaxSize()) {
        key(k) {
            Load(fetch = {
                if (isStudent) Api.getList("/api/my/grades").objs().filter { it.optString("course") == course.optString("name") }
                else Api.getList("/api/courses/$cid/grades").objs()
            }) { gs, _ ->
                val t = bt
                if (gs.isEmpty()) {
                    ScrollPage {
                        Empty(
                            "لا توجد درجات بعد", if (canEdit) "اضغط «درجة» لإدخال أول درجة" else "تظهر حين يدخلها التدريسي",
                            Icons.Outlined.WorkspacePremium, Modifier.height(300.dp),
                        )
                    }
                    return@Load
                }
                if (isStudent) {
                    val sum = gs.sumOf { it.optDouble("score") }
                    val mxs = gs.sumOf { it.optDouble("max_score") }
                    ScrollPage {
                        Panel(padding = PaddingValues(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Ring(if (mxs == 0.0) 0.0 else sum / mxs, size = 84.dp, stroke = 9.dp) {
                                    Text(pct(if (mxs == 0.0) null else sum / mxs), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                                }
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text("مجموع درجاتك", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                    Text("${fmtNum(sum)} من ${fmtNum(mxs)}", color = t.tx2, fontSize = 15.sp)
                                }
                            }
                        }
                        gs.forEach { g ->
                            val sc = g.optDouble("score"); val mx = g.optDouble("max_score")
                            Panel {
                                Row { Text(g.optString("kind"), fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f)); Text("${fmtNum(sc)} / ${fmtNum(mx)}", fontWeight = FontWeight.ExtraBold, color = t.plumText, fontSize = 16.sp) }
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { if (mx > 0) (sc / mx).toFloat().coerceIn(0f, 1f) else 0f },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(6.dp)), color = t.primary, trackColor = t.bar,
                                )
                            }
                        }
                    }
                } else {
                    val by = LinkedHashMap<Int, MutableList<JSONObject>>()
                    val names = HashMap<Int, String>()
                    gs.forEach { g -> by.getOrPut(g.optInt("student_id")) { ArrayList() }.add(g); names[g.optInt("student_id")] = g.optString("full_name") }
                    ScrollPage {
                        by.forEach { (sid, list) ->
                            Panel(onClick = if (canEdit) ({ openAdd(sid) }) else null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(names[sid].orEmpty(), 40.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(names[sid].orEmpty(), fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                                    Tag("${fmtNum(list.sumOf { it.optDouble("score") })} / ${fmtNum(list.sumOf { it.optDouble("max_score") })}", dot = false)
                                }
                                Spacer(Modifier.height(10.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    list.forEach { g ->
                                        Text(
                                            "${g.optString("kind")}: ${fmtNum(g.optDouble("score"))}/${fmtNum(g.optDouble("max_score"))}",
                                            fontSize = 12.5.sp, color = t.tx2,
                                            modifier = Modifier.background(t.card2, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(70.dp))
                    }
                }
            }
        }
        if (canEdit) ExtendedFloatingActionButton(
            onClick = { openAdd(null) },
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            containerColor = bt.primary, contentColor = Color.White,
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("درجة", fontWeight = FontWeight.ExtraBold) },
        )
    }

    addFor?.let { first ->
        AddGradeDialog(students, first, onDismiss = { addFor = null }) { sid, kind, score, max ->
            addFor = null
            scope.launch {
                try {
                    Api.put("/api/courses/$cid/grades", JSONObject().put("student_id", sid).put("kind", kind).put("score", score).put("max_score", max))
                    toast(ctx, "تم الحفظ"); k++
                } catch (e: ApiError) { toast(ctx, e.message ?: "تعذّر الحفظ") }
            }
        }
    }
}

@Composable
private fun AddGradeDialog(students: List<JSONObject>, firstId: Int, onDismiss: () -> Unit, onSave: (Int, String, Double, Double) -> Unit) {
    val ctx = LocalContext.current
    var sid by remember { mutableIntStateOf(firstId) }
    var kind by remember { mutableStateOf("نصف الفصل") }
    var score by remember { mutableStateOf("") }
    var mx by remember { mutableStateOf("100") }
    var menu by remember { mutableStateOf(false) }
    val cur = students.firstOrNull { it.optInt("id") == sid }?.optString("full_name").orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = bt.card,
        title = { Text("إدخال درجة", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp)) {
                        Text(cur, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.ArrowDropDown, null)
                    }
                    DropdownMenu(menu, { menu = false }) {
                        students.forEach { s -> DropdownMenuItem(text = { Text(s.optString("full_name")) }, onClick = { sid = s.optInt("id"); menu = false }) }
                    }
                }
                BayanField(kind, { kind = it }, "نوع الدرجة (سعي، نصف الفصل، نهائي...)")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BayanField(score, { score = it }, "الدرجة", Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                    BayanField(mx, { mx = it }, "من", Modifier.weight(1f), keyboard = KeyboardType.Decimal)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = score.trim().toDoubleOrNull(); val m = mx.trim().toDoubleOrNull()
                if (s == null || m == null) toast(ctx, "أدخل أرقامًا صحيحة") else onSave(sid, kind.trim(), s, m)
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
