package iq.edu.almaaqal.attendance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** يفتح محادثة فوق التطبيق كله (openThread). courseId = 0 تعني محادثة مع الإدارة. */
fun openThread(courseId: Int, participantId: Int, title: String) {
    Overlay.push { close -> ThreadPage(courseId, participantId, title, close) }
}

// =============================================================== كل محادثاتي

@Composable
fun ThreadsTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var k by remember { mutableIntStateOf(0) }
    var people by remember { mutableStateOf<List<JSONObject>?>(null) }
    var courseChoice by remember { mutableStateOf<JSONObject?>(null) }
    val canStart = Api.role != "student"

    LaunchedEffect(Unit) { while (true) { delay(20_000); k++ } } // تحديث دوري
    val depth = Overlay.stack.size
    LaunchedEffect(depth) { if (depth == 0) { Api.forgetCache(); k++; Inbox.refresh() } } // عند الرجوع من محادثة

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            BayanBand("الرسائل", subtitle = "محادثاتك الخاصة")
            key(k) {
                Load(fetch = { Api.getList("/api/threads", fresh = true).objs() }) { list, _ ->
                    val t = bt
                    if (list.isEmpty()) {
                        ScrollPage { Empty("لا محادثات بعد", if (canStart) "اضغط «محادثة» لتبدأ واحدة" else null, Icons.Outlined.ChatBubbleOutline, Modifier.height(320.dp)) }
                        return@Load
                    }
                    ScrollPage {
                        list.forEach { x ->
                            val unread = x.optInt("unread")
                            Panel(
                                padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), bottomGap = 8.dp,
                                accent = if (unread > 0) t.gold else null,
                                onClick = { openThread(x.optInt("course_id"), x.optInt("participant_id"), x.optString("title")) },
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(x.optString("title"), 44.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(x.optString("title"), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (unread > 0) FontWeight.ExtraBold else FontWeight.Bold)
                                        Text(
                                            x.str("last_body") ?: meta(x.str("sub")), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            color = if (unread > 0) t.tx else t.tx3, fontSize = 12.5.sp,
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        x.dbl("last_at")?.let { Text(fmtClock(it), color = t.tx3, fontSize = 11.5.sp) }
                                        if (unread > 0) Text(
                                            "$unread", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(top = 4.dp).background(t.primary, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
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
        if (canStart) ExtendedFloatingActionButton(
            onClick = {
                scope.launch {
                    try {
                        people = if (Api.role == "teacher" || Api.role == "supervisor") Api.getList("/api/my-students").objs()
                        else Api.getList("/api/users").objs().filter { it.optInt("id") != Api.user?.optInt("id") }
                    } catch (e: ApiError) { toast(ctx, e.message ?: "تعذّر التحميل") }
                }
            },
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            containerColor = bt.primary, contentColor = Color.White,
            icon = { Icon(Icons.Outlined.Edit, null) }, text = { Text("محادثة", fontWeight = FontWeight.ExtraBold) },
        )
    }

    people?.let { list ->
        PersonPickSheet("محادثة جديدة مع…", list, onDismiss = { people = null }) { pick ->
            people = null
            val ids = pick.optJSONArray("course_ids")
            if (ids != null && ids.length() > 1) courseChoice = pick
            else {
                val cid = if (ids != null && ids.length() == 1) ids.optInt(0) else 0
                openThread(cid, if (pick.has("student_id")) pick.optInt("student_id") else pick.optInt("id"), pick.optString("full_name"))
            }
        }
    }
    courseChoice?.let { pick ->
        val ids = pick.optJSONArray("course_ids")!!
        val names = pick.optJSONArray("courses")
        AlertDialog(
            onDismissRequest = { courseChoice = null }, containerColor = bt.card,
            title = { Text("في أي مادة؟", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column {
                    for (i in 0 until ids.length()) {
                        TextButton(
                            onClick = {
                                courseChoice = null
                                openThread(ids.optInt(i), if (pick.has("student_id")) pick.optInt("student_id") else pick.optInt("id"), pick.optString("full_name"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(names?.optString(i) ?: "مادة ${i + 1}") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { courseChoice = null }) { Text("إلغاء") } },
        )
    }
}

/** ورقة اختيار شخص بالبحث بالاسم (حتى 60 نتيجة). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonPickSheet(title: String, people: List<JSONObject>, onDismiss: () -> Unit, onPick: (JSONObject) -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = people.filter { q.isEmpty() || it.optString("full_name").contains(q) }.take(60)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = bt.card) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(horizontal = 16.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Spacer(Modifier.height(10.dp))
            BayanField(q, { q = it.trim() }, "ابحث بالاسم", leading = Icons.Outlined.Search)
            LazyColumn(Modifier.weight(1f, fill = false).padding(top = 8.dp)) {
                items(shown) { u ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(u) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(u.optString("full_name"), 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(u.optString("full_name"), fontWeight = FontWeight.Bold)
                            val sub = if (u.has("role") && !u.isNull("role")) roleAr[u.optString("role")] ?: ""
                            else meta(*(u.optJSONArray("courses")?.let { a -> List(minOf(a.length(), 2)) { a.optString(it) } } ?: emptyList()).toTypedArray())
                            if (sub.isNotEmpty()) Text(sub, color = bt.tx3, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// =============================================================== محادثة واحدة

@Composable
fun ThreadPage(courseId: Int, participantId: Int, title: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(bt.page)) {
        BayanBand(title, subtitle = if (courseId == 0) "محادثة مع الإدارة" else "محادثة خاصة", onBack = onBack)
        ThreadBody(courseId, participantId)
    }
}

/** رسائل محادثة واحدة وصندوق الكتابة. تتحدث نفسها كل 6 ثوانٍ ما دامت على الشاشة. */
@Composable
fun ThreadBody(courseId: Int, participantId: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = bt
    val me = Api.user?.optInt("id")
    val path = "/api/threads/$courseId/$participantId"
    var msgs by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var seen by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val maxBubble = (LocalConfiguration.current.screenWidthDp * 0.78f).dp

    suspend fun load() {
        try {
            val r = Api.getObj(path, fresh = true)
            msgs = r.optJSONArray("messages")?.objs().orEmpty()
            error = null
            if (msgs.size != seen) { seen = msgs.size; Inbox.refresh() } // القراءة تُصفّر العدّاد
        } catch (e: ApiError) {
            error = e.message
        }
        loading = false
    }
    LaunchedEffect(Unit) { while (true) { load(); delay(6000) } }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1) }

    fun send() {
        val body = text.trim()
        if (body.isEmpty() || sending) return
        sending = true
        scope.launch {
            if (guard(ctx) { Api.post(path, JSONObject().put("body", body)) }) { text = ""; load() }
            sending = false
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = t.primary) }
                error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(error!!, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                msgs.isEmpty() -> Empty("ابدأ المحادثة", "رسالتك تصل فورًا ويصله إشعار", Icons.Outlined.ChatBubbleOutline)
                else -> LazyColumn(state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
                    items(msgs.size) { i ->
                        val m = msgs[i]
                        val mine = m.optInt("sender_id") == me
                        val newDay = i == 0 || fmtDay(msgs[i - 1].dbl("at")) != fmtDay(m.dbl("at"))
                        Column(Modifier.fillMaxWidth()) {
                            if (newDay) Text(fmtDay(m.dbl("at")), color = t.tx3, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp))
                            val shape = RoundedCornerShape(6.dp)
                            Column(
                                Modifier.align(if (mine) Alignment.Start else Alignment.End).widthIn(max = maxBubble).padding(bottom = 8.dp)
                                    .background(if (mine) t.primary else t.card, shape)
                                    .then(if (mine) Modifier else Modifier.border(1.dp, t.line, shape))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                if (!mine) Text(m.optString("sender"), color = t.goldText, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
                                Text(m.optString("body"), color = if (mine) Color.White else t.tx, lineHeight = 24.sp)
                                Text(fmtClock(m.dbl("at")), color = if (mine) Color.White.copy(alpha = .7f) else t.tx3, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                text, { text = it }, Modifier.weight(1f), minLines = 1, maxLines = 4, shape = RoundedCornerShape(6.dp),
                placeholder = { Text("اكتب رسالة…") },
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = { send() }, enabled = !sending, modifier = Modifier.size(50.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = t.primary, contentColor = Color.White),
            ) {
                if (sending) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(Icons.Rounded.Send, "إرسال")
            }
        }
    }
}

// =============================================================== تبويب «الرسائل» داخل المادة

/** الطالب: محادثته الخاصة مع تدريسيي المادة. الكادر: محادثات هذه المادة. */
@Composable
fun ChatTab(courseId: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    if (Api.role == "student") {
        ThreadBody(courseId, Api.user?.optInt("id") ?: 0)
        return
    }
    var k by remember { mutableIntStateOf(0) }
    var studs by remember { mutableStateOf<List<JSONObject>?>(null) }
    val depth = Overlay.stack.size
    LaunchedEffect(depth) { if (depth == 0) { Api.forgetCache(); k++ } }

    Box(Modifier.fillMaxSize()) {
        key(k) {
            Load(fetch = { Api.getList("/api/threads", fresh = true).objs().filter { it.optInt("course_id") == courseId } }) { list, _ ->
                val t = bt
                if (list.isEmpty()) {
                    ScrollPage { Empty("لا محادثات في هذه المادة", "اضغط «محادثة» لمراسلة طالب", Icons.Outlined.ChatBubbleOutline, Modifier.height(300.dp)) }
                    return@Load
                }
                ScrollPage {
                    list.forEach { x ->
                        val unread = x.optInt("unread")
                        Panel(
                            accent = if (unread > 0) t.gold else null, padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            onClick = { openThread(courseId, x.optInt("participant_id"), x.optString("title")) },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(x.optString("title"), 42.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(x.optString("title"), fontWeight = FontWeight.Bold)
                                    Text(x.str("last_body") ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, color = t.tx3, fontSize = 12.5.sp)
                                }
                                if (unread > 0) Tag("$unread جديدة", Kind.Warn, dot = false)
                            }
                        }
                    }
                    Spacer(Modifier.height(70.dp))
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = {
                scope.launch {
                    try { studs = Api.getList("/api/courses/$courseId/students").objs() } catch (e: ApiError) { toast(ctx, e.message ?: "تعذّر التحميل") }
                }
            },
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            containerColor = bt.primary, contentColor = Color.White,
            icon = { Icon(Icons.Outlined.Edit, null) }, text = { Text("محادثة", fontWeight = FontWeight.ExtraBold) },
        )
    }
    studs?.let { list ->
        PersonPickSheet("محادثة مع طالب", list, onDismiss = { studs = null }) { u ->
            studs = null
            openThread(courseId, u.optInt("id"), u.optString("full_name"))
        }
    }
}
