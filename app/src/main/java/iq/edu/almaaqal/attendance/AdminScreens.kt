package iq.edu.almaaqal.attendance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val AUDIT_PAGE_SIZE = 60
private val WhiteColor = androidx.compose.ui.graphics.Color.White

// =============================================================== الصفحة الرئيسية

/** الصفحة الرئيسية للمدير: ست تبويبات وصفحتان فرعيتان (طلبات الحساب، سجل النشاط). */
@Composable
fun AdminHome(onLogout: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var usersFilter by rememberSaveable { mutableIntStateOf(0) }
    var course by remember { mutableStateOf<JSONObject?>(null) }
    var page by remember { mutableStateOf<String?>(null) }

    // الصفحات الفرعية تحل محل الصدفة بالكامل
    course?.let { c ->
        BackHandler { course = null }
        CoursePage(c, 0) { course = null }
        return
    }
    when (page) {
        "signups" -> { BackHandler { page = null }; SignupsPage { page = null }; return }
        "audit" -> { BackHandler { page = null }; AuditPage { page = null }; return }
    }

    AppShell(
        selected = tab,
        onSelect = { tab = it },
        tabs = listOf(
            ShellTab("الرئيسية", Icons.Outlined.Dashboard, Icons.Rounded.Dashboard) {
                Dashboard(
                    onPage = { page = it },
                    onUsers = { filter -> usersFilter = filter; tab = 1 },
                )
            },
            ShellTab("المستخدمون", Icons.Outlined.People, Icons.Rounded.People) {
                UsersTab(usersFilter, { usersFilter = it })
            },
            ShellTab("المواد", Icons.Outlined.MenuBook, Icons.Rounded.MenuBook) {
                AdminCourses { course = it }
            },
            ShellTab("الهيكل", Icons.Outlined.AccountTree, Icons.Rounded.AccountTree) { StructureTab() },
            ShellTab("الرسائل", Icons.Outlined.ChatBubbleOutline, Icons.Rounded.ChatBubble, badge = true) { ThreadsTab() },
            ShellTab("حسابي", Icons.Outlined.Person, Icons.Rounded.Person) { AccountTab(onLogout) },
        ),
    )
}

private fun JSONObject.hasOpen() = has("open_lecture") && !isNull("open_lecture")

// =============================================================== لوحة المدير

@Composable
private fun Dashboard(onPage: (String) -> Unit, onUsers: (Int) -> Unit) {
    val go = LocalShellGo.current
    var whose by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        HomeBand()
        Load(fetch = { Api.getObj("/api/home") }) { d, _ ->
            val t = bt
            val people = d.obj("people")
            val today = d.obj("today")
            val totals = d.obj("totals")
            val courses = d.optJSONArray("courses")?.objs().orEmpty()

            val signups = d.optInt("signups")
            val cardsMissing = d.optInt("cards_missing")
            val noTeacher = courses.count { it.optInt("teacher_count") == 0 }
            val open = courses.filter { it.hasOpen() }
            val watch = courses
                .filter { it.optInt("flagged") > 0 }
                .sortedByDescending { it.optInt("flagged") }

            ScrollPage {
                // ---- الإحصاءات
                StatGrid(
                    { m -> StatTile("الطلاب", "${people.optInt("student")}", m, caption = "حساب فعّال", icon = Icons.Outlined.School, onClick = { onUsers(1) }) },
                    { m -> StatTile("التدريسيون", "${people.optInt("teacher")}", m, caption = "حساب فعّال", icon = Icons.Outlined.Badge, onClick = { onUsers(2) }) },
                    { m -> StatTile("المواد", "${totals.optInt("courses")}", m, caption = "هذا الفصل", icon = Icons.Outlined.MenuBook, onClick = { go(2) }) },
                    { m -> StatTile("محاضرات اليوم", "${today.optInt("lectures")}", m, caption = "${today.optInt("present")} حضور مسجّل", icon = Icons.Outlined.EventAvailable, onClick = { go(2) }) },
                )

                // ---- الأزرار السريعة
                QuickActions(
                    onWhose = { whose = true },
                    onAudit = { onPage("audit") },
                )

                // ---- تحتاج إجراءك
                if (signups > 0 || noTeacher > 0 || cardsMissing > 0) {
                    SectionHeader("تحتاج إجراءك")
                    if (signups > 0) {
                        ActionPanel(
                            icon = Icons.Outlined.HowToReg,
                            title = "$signups طلب حساب جديد",
                            subtitle = "بانتظار موافقتك",
                            accent = t.gold,
                            showChevron = true,
                            onClick = { onPage("signups") },
                        )
                    }
                    if (noTeacher > 0) {
                        ActionPanel(
                            icon = Icons.Outlined.PersonSearch,
                            title = "$noTeacher مادة بلا تدريسي",
                            subtitle = "عيّن تدريسيًا من تبويب المواد",
                            onClick = { go(2) },
                        )
                    }
                    if (cardsMissing > 0) {
                        ActionPanel(
                            icon = Icons.Outlined.Nfc,
                            title = "$cardsMissing طالب بلا بطاقة NFC",
                            subtitle = "اربط البطاقة من تبويب المستخدمين",
                            onClick = { onUsers(1) },
                        )
                    }
                }

                // ---- محاضرات جارية
                if (open.isNotEmpty()) {
                    SectionHeader("محاضرات جارية الآن", count = open.size)
                    open.forEach { OpenLecturePanel(it) }
                }

                // ---- تحتاج متابعة
                if (watch.isNotEmpty()) {
                    SectionHeader("تحتاج متابعة", count = watch.size)
                    watch.take(5).forEach { WatchPanel(it) }
                }
            }
        }
    }

    if (whose) WhoseCardFlow { whose = false }
}

@Composable
private fun QuickActions(onWhose: () -> Unit, onAudit: () -> Unit) {
    Spacer(Modifier.height(10.dp))
    SecondaryButton("لمن هذه البطاقة؟", Icons.Outlined.Nfc, onClick = onWhose)
    Spacer(Modifier.height(10.dp))
    SecondaryButton("تبليغ جديد", Icons.Outlined.Campaign) {
        Overlay.push { close -> AnnouncePage(onBack = close) }
    }
    Spacer(Modifier.height(10.dp))
    SecondaryButton("نواقص ملفات الطلاب", Icons.Outlined.AssignmentLate) {
        Overlay.push { close -> MissingProfilesPage(close) }
    }
    Spacer(Modifier.height(10.dp))
    SecondaryButton("سجل النشاط", Icons.Outlined.History, onClick = onAudit)
}

/** بطاقة إجراء مطلوب: أيقونة + عنوان + وصف (وسهم اختياري). */
@Composable
private fun ActionPanel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    accent: androidx.compose.ui.graphics.Color? = null,
    showChevron: Boolean = false,
) {
    val t = bt
    Panel(accent = accent, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = t.tx3, fontSize = 12.5.sp)
            }
            if (showChevron) Icon(Icons.Rounded.ChevronLeft, null, tint = t.tx3)
        }
    }
}

@Composable
private fun OpenLecturePanel(m: JSONObject) {
    val t = bt
    Panel(accent = t.ok) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(t.okBg, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Sensors, null, tint = t.ok) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(m.optString("name"), fontWeight = FontWeight.ExtraBold)
                Text(
                    "${m.str("teacher") ?: "—"}  ·  منذ ${fmtClock(m.obj("open_lecture").dbl("started_at"))}",
                    color = t.tx3, fontSize = 12.5.sp,
                )
            }
        }
    }
}

@Composable
private fun WatchPanel(m: JSONObject) {
    val t = bt
    Panel(accent = t.err) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.MenuBook)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.optString("name"), fontWeight = FontWeight.ExtraBold)
                Text(meta(m.str("code"), m.str("teacher")), color = t.tx3, fontSize = 12.5.sp)
            }
            Tag("${m.optInt("flagged")} تجاوزوا الحد", Kind.Err)
        }
    }
}

// =============================================================== لمن هذه البطاقة؟

/** يمسح بطاقة: يخبر بصاحبها، أو يربطها بطالب يختاره المدير بالاسم. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhoseCardFlow(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var stage by remember { mutableStateOf("scan") } // scan | owner | pick | none
    var read by remember { mutableStateOf<CardRead?>(null) }
    var owner by remember { mutableStateOf<JSONObject?>(null) }
    var move by remember { mutableStateOf(false) }
    var conflict by remember { mutableStateOf<Pair<JSONObject, String>?>(null) }

    fun assign(u: JSONObject, mv: Boolean) {
        val uid = read?.uid ?: return
        scope.launch {
            try {
                Api.post(
                    "/api/admin/card-assign",
                    JSONObject().put("card_uid", uid).put("user_id", u.opt("id")).put("move", mv),
                )
                toast(ctx, "رُبطت البطاقة بـ ${u.optString("full_name")}")
                onClose()
            } catch (e: ApiError) {
                if (e.code == 409 && !mv) {
                    conflict = u to (e.message ?: "")
                    stage = "none"
                } else {
                    toast(ctx, e.message ?: "تعذّر الربط")
                    onClose()
                }
            }
        }
    }

    fun onCardScanned(c: CardRead?) {
        if (c == null) { onClose(); return }
        read = c
        scope.launch {
            try {
                val res = Api.post("/api/admin/card-owner", JSONObject().put("card_uid", c.uid)) as? JSONObject ?: JSONObject()
                if (res.optBoolean("found")) {
                    owner = res.optJSONObject("user")
                    stage = "owner"
                } else {
                    move = false
                    stage = "pick"
                }
            } catch (e: ApiError) {
                toast(ctx, e.message ?: "تعذّر التعرف")
                onClose()
            }
        }
    }

    when (stage) {
        "scan" -> {
            if (!NfcHub.available(ctx)) {
                toast(ctx, "NFC غير متاح أو مغلق. شغّله من إعدادات الهاتف.")
                onClose()
            } else {
                CardSheet(
                    "لمن هذه البطاقة؟",
                    "تُعرض صاحبها، أو تربطها بطالب إن كانت غير معروفة",
                ) { c -> onCardScanned(c) }
            }
        }
        "owner" -> owner?.let { u ->
            AlertDialog(
                onDismissRequest = onClose,
                containerColor = bt.card,
                title = { Text("البطاقة مربوطة", fontWeight = FontWeight.ExtraBold) },
                text = {
                    Column {
                        Text(u.optString("full_name"), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(roleAr[u.optString("role")] ?: u.optString("role"))
                        u.str("ref_no")?.let { Text("الرقم المعرفي: $it") }
                        Spacer(Modifier.height(8.dp))
                        Text("UID: ${read?.uid}", color = bt.tx3, fontSize = 12.5.sp)
                    }
                },
                confirmButton = { TextButton(onClick = onClose) { Text("تم") } },
                dismissButton = { TextButton(onClick = { move = true; stage = "pick" }) { Text("نقلها لشخص آخر") } },
            )
        }
        "pick" -> StudentPickSheet(
            title = if (move) "انقل البطاقة إلى…" else "بطاقة غير مربوطة. اربطها بطالب",
            onDismiss = onClose,
        ) { assign(it, move) }
    }

    conflict?.let { (u, msg) ->
        ConfirmDialog(
            "البطاقة لشخص آخر",
            "$msg. تريد نقلها إلى ${u.optString("full_name")}؟",
            "نقل",
            danger = false,
            onDismiss = { conflict = null; onClose() },
        ) { conflict = null; assign(u, true) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentPickSheet(title: String, onDismiss: () -> Unit, onPick: (JSONObject) -> Unit) {
    var students by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var q by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        students = try {
            Api.getList("/api/users?role=student").objs()
        } catch (_: ApiError) {
            emptyList()
        }
    }

    val shown = students
        .filter { q.isEmpty() || "${it.optString("full_name")} ${it.str("ref_no") ?: ""}".contains(q) }
        .take(60)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = bt.card) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(horizontal = 16.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Spacer(Modifier.height(10.dp))
            BayanField(q, { q = it.trim() }, "ابحث بالاسم أو الرقم المعرفي", leading = Icons.Outlined.Search)
            LazyColumn(Modifier.weight(1f, fill = false).padding(top = 8.dp)) {
                items(shown) { u -> StudentRow(u, onClick = { onPick(u) }) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StudentRow(u: JSONObject, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(u.optString("full_name"), 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(u.optString("full_name"), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(u.str("ref_no") ?: u.optString("username"), color = bt.tx3, fontSize = 12.sp)
        }
        if (u.str("card_uid") != null) Icon(Icons.Outlined.Nfc, null, Modifier.size(16.dp), tint = bt.ok)
    }
}

// =============================================================== طلبات الحساب

@Composable
fun SignupsPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var k by remember { mutableIntStateOf(0) } // يعيد التحميل عند تغيّره
    var approving by remember { mutableStateOf<JSONObject?>(null) }
    var rejecting by remember { mutableStateOf<JSONObject?>(null) }
    var deps by remember { mutableStateOf<List<JSONObject>>(emptyList()) }

    fun refresh() { Api.forgetCache(); k++ }

    fun approve(r: JSONObject, dep: Int?) {
        scope.launch {
            val ok = guard(ctx, "تمت الموافقة. يستطيع الدخول الآن.") {
                Api.post("/api/signups/${r.optInt("id")}/approve", JSONObject().put("department_id", dep ?: JSONObject.NULL))
            }
            if (ok) refresh()
        }
    }

    /** إن كان هناك قسم واحد (أو لا قسم) تتم الموافقة مباشرة، وإلا يُسأل المدير. */
    fun startApprove(r: JSONObject) {
        scope.launch {
            val list = try {
                Api.getList("/api/departments").objs()
            } catch (e: ApiError) {
                toast(ctx, e.message ?: "")
                return@launch
            }
            if (list.size <= 1) approve(r, list.firstOrNull()?.optInt("id")) else { deps = list; approving = r }
        }
    }

    Column(Modifier.fillMaxSize().background(bt.page)) {
        BayanBand("طلبات الحساب", onBack = onBack)
        key(k) {
            Load(fetch = { Api.getList("/api/signups").objs() }) { rs, _ ->
                if (rs.isEmpty()) {
                    ScrollPage {
                        Empty("لا توجد طلبات", "تظهر هنا طلبات التدريسيين الجدد", Icons.Outlined.HowToReg, Modifier.height(320.dp))
                    }
                    return@Load
                }
                ScrollPage {
                    rs.forEach { r ->
                        SignupCard(r, onApprove = { startApprove(r) }, onReject = { rejecting = r })
                    }
                }
            }
        }
    }

    approving?.let { r ->
        AlertDialog(
            onDismissRequest = { approving = null },
            containerColor = bt.card,
            title = { Text("قسم التدريسي", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column {
                    deps.forEach { d ->
                        TextButton(
                            onClick = { approving = null; approve(r, d.optInt("id")) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(d.optString("name")) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { approving = null }) { Text("إلغاء") } },
        )
    }

    rejecting?.let { r ->
        ConfirmDialog(
            "رفض الطلب؟",
            "سيُحذف طلب ${r.optString("full_name")}.",
            "رفض",
            onDismiss = { rejecting = null },
        ) {
            rejecting = null
            scope.launch {
                if (guard(ctx) { Api.post("/api/signups/${r.optInt("id")}/reject") }) refresh()
            }
        }
    }
}

@Composable
private fun SignupCard(r: JSONObject, onApprove: () -> Unit, onReject: () -> Unit) {
    val t = bt
    Panel(padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(r.optString("full_name"), 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.optString("full_name"), fontWeight = FontWeight.ExtraBold)
                Text(meta(r.str("username"), r.str("phone")), color = t.tx3, fontSize = 12.5.sp)
            }
            Tag(roleAr[r.optString("role")] ?: r.optString("role"), Kind.Warn, dot = false)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onApprove,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = t.primary, contentColor = WhiteColor),
            ) { Text("موافقة") }
            OutlinedButton(
                onClick = onReject,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = t.err),
            ) { Text("رفض") }
        }
    }
}

// =============================================================== سجل النشاط

private val actionAr = mapOf(
    // الدخول
    "login.ok" to "دخول",
    "login.card" to "دخول بالبطاقة",
    "login.device" to "دخول بالبصمة",
    "login.fail" to "محاولة دخول فاشلة",
    "login.locked" to "قُفل الحساب مؤقتًا",
    "password.change" to "تغيير كلمة المرور",
    "biometric.enable" to "تفعيل البصمة",
    "biometric.disable" to "إلغاء البصمة",
    // الحضور والمحاضرات
    "attendance.manual" to "تعديل حضور يدوي",
    "attendance.nfc" to "تسجيل حضور بالبطاقة",
    "lecture.start" to "بدء محاضرة",
    // المستخدمون والبطاقات
    "user.add" to "إضافة مستخدم",
    "user.edit" to "تعديل مستخدم",
    "user.logout_all" to "إخراج مستخدم من كل الأجهزة",
    "card.assign" to "ربط بطاقة",
    "card.link" to "ربط بطاقة",
    "signup.approve" to "قبول طلب تسجيل",
    "signup.reject" to "رفض طلب تسجيل",
    "signup.student" to "تفعيل حساب طالب",
    // المواد والهيكل
    "course.add" to "إضافة مادة",
    "course.edit" to "تعديل مادة",
    "course.enroll" to "تسجيل طلاب",
    "course.enroll_group" to "تسجيل طلاب مرحلة",
    "department.add" to "إضافة قسم",
    "stage.add" to "إضافة مرحلة",
    "teacher.courses" to "تعيين مواد لتدريسي",
    // بيانات
    "grade.put" to "تسجيل درجة",
    "material.upload" to "رفع ملف",
    "export.link" to "تصدير Excel",
)

private val auditKinds = listOf("", "login", "fail", "attendance", "admin", "data")
private val auditChips = listOf("الكل", "الدخول", "دخول فاشل", "الحضور", "إدارة", "بيانات")

/** سجل النشاط: من فعل ماذا ومتى، مع بحث وتصنيفات وتحميل صفحات ($AUDIT_PAGE_SIZE سجلًا لكل صفحة). */
@Composable
fun AuditPage(onBack: () -> Unit) {
    val t = bt
    var f by remember { mutableIntStateOf(0) }            // التصنيف المختار
    var q by remember { mutableStateOf("") }              // نص البحث المطبَّق
    var search by remember { mutableStateOf("") }         // نص خانة البحث
    var items by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var more by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var loadMore by remember { mutableIntStateOf(0) }

    // تحميل أول صفحة عند تغيّر الفلتر أو البحث
    LaunchedEffect(f, q, reload) {
        loading = true
        error = null
        try {
            val r = Api.getList(auditUrl(auditKinds[f], q, null), fresh = true).objs()
            items = r
            more = r.size >= AUDIT_PAGE_SIZE
        } catch (e: ApiError) {
            error = e.message
        }
        loading = false
    }

    // تحميل الصفحة التالية
    LaunchedEffect(loadMore) {
        if (loadMore == 0) return@LaunchedEffect
        loading = true
        try {
            val r = Api.getList(auditUrl(auditKinds[f], q, items.lastOrNull()?.optInt("id")), fresh = true).objs()
            items = items + r
            more = r.size >= AUDIT_PAGE_SIZE
        } catch (e: ApiError) {
            error = e.message
        }
        loading = false
    }

    Column(Modifier.fillMaxSize().background(t.page)) {
        BayanBand("سجل النشاط", subtitle = "من فعل ماذا ومتى", onBack = onBack)
        Spacer(Modifier.height(4.dp))
        Chips(auditChips, f, { f = it })

        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BayanField(search, { search = it }, "بحث باسم المستخدم أو التفاصيل", Modifier.weight(1f), leading = Icons.Outlined.Search)
            Button(
                onClick = { q = search.trim() },
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = t.primary, contentColor = WhiteColor),
            ) { Text("بحث") }
        }

        when {
            error != null -> Column(
                Modifier.fillMaxSize().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error!!, color = t.err)
                Spacer(Modifier.height(12.dp))
                PrimaryButton("إعادة المحاولة") { reload++ }
            }
            items.isEmpty() && !loading -> Empty("لا سجلات", icon = Icons.Outlined.History)
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
            ) {
                items(items) { AuditRow(it) }
                item {
                    when {
                        loading -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = t.primary)
                        }
                        more -> TextButton(onClick = { loadMore++ }, modifier = Modifier.fillMaxWidth()) { Text("عرض المزيد") }
                        else -> Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditRow(a: JSONObject) {
    val t = bt
    val action = a.optString("action")
    val bad = action == "login.fail" || action == "login.locked"
    val detail = a.str("detail")
    val fullName = a.str("full_name")
    // في محاولات الدخول الفاشلة يحمل الحقل detail اسم المستخدم المُدخَل
    val who = fullName ?: (if (bad && detail != null) detail else "غير معروف")

    Panel(
        padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        bottomGap = 8.dp,
        accent = if (bad) t.err else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(actionAr[action] ?: action, fontWeight = FontWeight.ExtraBold)
                Text(meta(who, a.str("role")?.let { roleAr[it] }), color = t.tx2, fontSize = 12.5.sp)
                if (!detail.isNullOrEmpty() && fullName != null) {
                    Text(detail, maxLines = 2, overflow = TextOverflow.Ellipsis, color = t.tx3, fontSize = 11.5.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(fmtClock(a.dbl("at")), color = t.tx3, fontSize = 12.5.sp)
                Text(fmtDay(a.dbl("at")), color = t.tx3, fontSize = 11.5.sp)
            }
        }
    }
}

private fun auditUrl(kind: String, q: String, before: Int?): String {
    val parts = ArrayList<String>()
    if (kind.isNotEmpty()) parts.add("kind=$kind")
    if (q.isNotEmpty()) parts.add("q=" + java.net.URLEncoder.encode(q, "UTF-8"))
    if (before != null) parts.add("before=$before")
    return "/api/audit?" + parts.joinToString("&")
}
