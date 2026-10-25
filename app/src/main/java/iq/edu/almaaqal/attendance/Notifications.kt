package iq.edu.almaaqal.attendance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private val notifIcons: Map<String, ImageVector> = mapOf(
    "announcement" to Icons.Outlined.Campaign, "lecture" to Icons.Outlined.Sensors,
    "attendance" to Icons.Outlined.HowToReg, "absence" to Icons.Outlined.EventBusy,
    "exam" to Icons.Outlined.EditNote, "grade" to Icons.Outlined.WorkspacePremium, "profile" to Icons.Outlined.Badge,
)

/** الجرس في الشريط الرئيسي: يفتح الإشعارات ويعرض عدد غير المقروء. */
@Composable
fun Bell() {
    Box {
        IconButton(onClick = { Overlay.push { close -> NotificationsPage(close) } }) {
            Icon(Icons.Outlined.Notifications, "الإشعارات", tint = Color.White)
        }
        if (Inbox.notifications > 0) Badge(Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp)) { Text("${Inbox.notifications}") }
    }
}

// =============================================================== الإشعارات

/** كل إشعارات المستخدم، الأحدث أولًا. فتح الصفحة يجعلها مقروءة. */
@Composable
fun NotificationsPage(onBack: () -> Unit) {
    val t = bt
    var items by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var more by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var load by remember { mutableIntStateOf(0) }
    BackHandler(onBack = onBack)

    LaunchedEffect(load) {
        loading = true; error = null
        try {
            val before = items.lastOrNull()?.optInt("id")
            val r = Api.getList("/api/notifications" + (if (before == null || load == 0) "" else "?before=$before"), fresh = true).objs()
            items = if (load == 0) r else items + r
            more = r.size >= 40
            if (r.any { it.isNull("read_at") || !it.has("read_at") }) {
                Api.post("/api/notifications/read")
                Inbox.refresh()
            }
        } catch (e: ApiError) {
            error = e.message
        }
        loading = false
    }

    Column(Modifier.fillMaxSize().background(t.page)) {
        BayanBand("الإشعارات", subtitle = "كل ما يخصّك", onBack = onBack)
        when {
            error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(error!!, color = t.err) }
            items.isEmpty() && !loading -> Empty("لا إشعارات", "ستصلك هنا التبليغات والحضور والغياب والامتحانات", Icons.Outlined.NotificationsNone)
            else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp)) {
                items(items) { n ->
                    val unread = n.isNull("read_at") || !n.has("read_at")
                    val kind = n.optString("kind")
                    val bad = kind == "absence"
                    Panel(accent = if (unread) (if (bad) t.err else t.gold) else null, padding = PaddingValues(12.dp), bottomGap = 8.dp) {
                        Row(verticalAlignment = Alignment.Top) {
                            IconTile(notifIcons[kind] ?: Icons.Outlined.Notifications, 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(n.optString("title"), fontWeight = if (unread) FontWeight.ExtraBold else FontWeight.Bold)
                                n.str("body")?.takeIf { it.isNotEmpty() }?.let { Text(it, color = t.tx2, lineHeight = 24.sp, fontSize = 13.5.sp, modifier = Modifier.padding(top = 3.dp)) }
                                Text(meta(n.str("by_name"), fmtDay(n.dbl("at")), fmtClock(n.dbl("at"))), color = t.tx3, fontSize = 11.5.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                item {
                    when {
                        loading -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = t.primary) }
                        more -> TextButton(onClick = { load++ }, modifier = Modifier.fillMaxWidth()) { Text("عرض المزيد") }
                        else -> Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

// =============================================================== تبليغ جديد

/**
 * إرسال تبليغ لجمهور تختاره: شخص، مادة، مرحلة، دوام، الجميع.
 * التدريسي لا يصل إلا لطلابه، والمشرف لقسمه، والمدير للجميع.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnnouncePage(courseId: Int? = null, userId: Int? = null, userName: String? = null, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = bt
    val staffOnly = Api.role == "teacher"
    var to by remember { mutableStateOf(if (userId != null) "user" else "students") }
    var course by remember { mutableStateOf(courseId?.toString()) }
    var level by remember { mutableStateOf<Int?>(null) }
    var shift by remember { mutableStateOf<String?>(null) }
    var gender by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var courses by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var count by remember { mutableStateOf<Int?>(null) }
    var counting by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    fun spec() = JSONObject().put("to", to).also { o ->
        userId?.let { o.put("user_id", it) }
        course?.toIntOrNull()?.let { o.put("course_id", it) }
        level?.let { o.put("level", it) }
        shift?.let { o.put("shift", it) }
        gender?.let { o.put("gender", it) }
    }

    LaunchedEffect(Unit) { courses = try { Api.getList("/api/courses").objs() } catch (_: ApiError) { emptyList() } }
    // عدد المستلمين يتحدث مع كل تغيير في الاختيار
    LaunchedEffect(to, course, level, shift, gender) {
        counting = true
        delay(250)
        count = try { (Api.post("/api/announce/preview", spec()) as? JSONObject)?.optInt("count") } catch (_: ApiError) { null }
        counting = false
    }

    @Composable
    fun Label(s: String) = Text(s, color = t.tx3, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp, top = 14.dp, bottom = 6.dp))

    @Composable
    fun <T> Choice(options: List<Pair<T, String>>, value: T, onPick: (T) -> Unit) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (k, l) -> FilterChip(selected = value == k, onClick = { onPick(k) }, label = { Text(l) }) }
        }
    }

    Column(Modifier.fillMaxSize().background(t.page).imePadding()) {
        BayanBand("تبليغ جديد", subtitle = if (userId != null) "إلى $userName" else "اختر من يصله", onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
            if (userId == null) {
                Label("إلى")
                Choice(
                    buildList { add("students" to "الطلاب"); if (!staffOnly) { add("teachers" to "التدريسيون"); add("everyone" to "الجميع") } }, to,
                ) { to = it }
                Label("المادة")
                DropField(
                    "المادة", course, courses.map { "${it.optInt("id")}" to it.optString("name") }, allowClear = true,
                ) { course = it }
                if (to != "teachers") {
                    Label("الدوام")
                    Choice(listOf<Pair<String?, String>>(null to "الكل", "morning" to "الصباحي", "evening" to "المسائي"), shift) { shift = it }
                    Label("المرحلة")
                    Choice(listOf<Pair<Int?, String>>(null to "الكل", 1 to "الأولى", 2 to "الثانية", 3 to "الثالثة", 4 to "الرابعة"), level) { level = it }
                    Label("الجنس")
                    Choice(listOf<Pair<String?, String>>(null to "الكل", "male" to "ذكور", "female" to "إناث"), gender) { gender = it }
                }
            }
            Label("العنوان")
            OutlinedTextField(title, { if (it.length <= 120) title = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("مثال: تأجيل محاضرة الغد") })
            Label("النص")
            OutlinedTextField(body, { if (it.length <= 2000) body = it }, Modifier.fillMaxWidth(), minLines = 4, maxLines = 8, placeholder = { Text("اكتب التبليغ…") })
        }
        Box(Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp)) {
            PrimaryButton(
                if (counting) "جارٍ العدّ…" else (count?.let { "إرسال إلى $it" } ?: "إرسال"), Icons.Rounded.Send, sending,
            ) {
                if (title.isBlank() || body.isBlank()) toast(ctx, "اكتب العنوان والنص")
                else if (count == 0) toast(ctx, "لا مستلمين بهذا الاختيار")
                else confirm = true
            }
        }
    }

    if (confirm) ConfirmDialog(
        "إرسال التبليغ؟", if (count == null) "سيُرسل إلى المستلمين المحددين." else "سيصل إلى $count شخصًا.", "إرسال", danger = false,
        onDismiss = { confirm = false },
    ) {
        confirm = false
        sending = true
        scope.launch {
            val ok = guard(ctx, "أُرسل التبليغ") { Api.post("/api/announce", spec().put("title", title.trim()).put("body", body.trim())) }
            sending = false
            if (ok) onBack()
        }
    }
}
