package iq.edu.almaaqal.attendance

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * كل ما يحتفظ به هاتف الطالب للامتحان: حزمة الأسئلة المشفّرة، وإجاباته الجارية،
 * وأي محاولة منتهية لم تصل السيرفر بعد (ExamStore في quiz_student.dart).
 */
object ExamStore {
    private lateinit var sp: SharedPreferences
    fun init(ctx: Context) { sp = ctx.applicationContext.getSharedPreferences("exam_store", Context.MODE_PRIVATE) }

    private val uid get() = Api.user?.optInt("id") ?: 0
    private fun pkgKey(e: Int) = "exam_pkg_${uid}_$e"
    private fun stateKey(e: Int) = "exam_state_${uid}_$e"
    private val pendingKey get() = "exam_pending_$uid"

    private fun obj(k: String): JSONObject? = try { sp.getString(k, null)?.let { JSONObject(it) } } catch (_: Exception) { null }

    fun pkg(e: Int): JSONObject? = obj(pkgKey(e))

    /** يحمّل حزمة الأسئلة المشفّرة. لا يُخزَّن شيء مقروء: تحتاج رمز الفتح. */
    suspend fun download(e: Int): JSONObject {
        val r = Api.getObj("/api/exams/$e/package", fresh = true)
        sp.edit().putString(pkgKey(e), r.toString()).apply()
        return r
    }

    fun state(e: Int): JSONObject? = obj(stateKey(e))
    fun saveState(e: Int, st: JSONObject) { sp.edit().putString(stateKey(e), st.toString()).apply() }

    fun pending(): List<JSONObject> = try {
        sp.getString(pendingKey, null)?.let { JSONArray(it).objs() } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    fun setPending(list: List<JSONObject>) { sp.edit().putString(pendingKey, JSONArray(list).toString()).apply() }

    fun addPending(sub: JSONObject) {
        val l = pending().filter { it.optInt("exam_id") != sub.optInt("exam_id") }.toMutableList()
        l.add(sub)
        setPending(l)
    }

    fun isFinished(e: Int): Boolean = pending().any { it.optInt("exam_id") == e } || state(e)?.optBoolean("done") == true

    /** يفتح الحزمة بالرمز (PBKDF2-HMAC-SHA256 ثم AES-256-GCM). يرمي استثناء إن كان الرمز خطأ. */
    suspend fun decrypt(pkg: JSONObject, code: String): JSONObject = withContext(Dispatchers.Default) {
        val salt = Base64.decode(pkg.getString("salt"), Base64.DEFAULT)
        val nonce = Base64.decode(pkg.getString("nonce"), Base64.DEFAULT)
        val all = Base64.decode(pkg.getString("data"), Base64.DEFAULT) // النص المشفّر ثم وسم 16 بايت
        val key = pbkdf2(code.trim().toByteArray(Charsets.UTF_8), salt, pkg.getInt("iterations"), 32)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        JSONObject(String(c.doFinal(all), Charsets.UTF_8))
    }

    // مكتوبة يدويًا لأن PBKDF2WithHmacSHA256 غير متاح قبل Android 8 (والتطبيق يدعم 7)
    private fun pbkdf2(pass: ByteArray, salt: ByteArray, iter: Int, len: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(pass, "HmacSHA256"))
        val h = 32
        val blocks = (len + h - 1) / h
        val out = ByteArray(blocks * h)
        for (i in 1..blocks) {
            mac.update(salt)
            mac.update(byteArrayOf((i ushr 24).toByte(), (i ushr 16).toByte(), (i ushr 8).toByte(), i.toByte()))
            var u = mac.doFinal()
            val t = u.copyOf()
            repeat(iter - 1) {
                u = mac.doFinal(u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            System.arraycopy(t, 0, out, (i - 1) * h, h)
        }
        return out.copyOf(len)
    }
}

/** يرسل المحاولات المنتهية التي لم تُرفع في القاعة. يعمل كلما كان للتطبيق اتصال. */
object ExamSync {
    private var busy = false

    suspend fun flush(): Int {
        if (busy || Api.token == null) return 0
        busy = true
        var sent = 0
        try {
            val all = ExamStore.pending()
            val left = ArrayList<JSONObject>()
            for (s in all) {
                try {
                    Api.post(
                        "/api/exams/${s.optInt("exam_id")}/submit",
                        JSONObject().put("answers", s.opt("answers")).put("started_at", s.opt("started_at"))
                            .put("finished_at", s.opt("finished_at")).put("offline", s.optBoolean("offline", true)),
                    )
                    sent++
                } catch (e: ApiError) {
                    // لا اتصال أو عطل سيرفر أو جلسة منتهية: نُبقيها. رفض 4xx حقيقي لن يتحسن: نُسقطها
                    if (e.code == null || e.code >= 500 || e.code == 401) left.add(s)
                } catch (_: Exception) {
                    left.add(s)
                }
            }
            if (sent > 0 || left.size != all.size) ExamStore.setPending(left)
        } finally {
            busy = false
        }
        return sent
    }
}

private fun leftStr(ms: Long): String {
    if (ms < 0) return "00:00"
    val s = ms / 1000
    val h = s / 3600; val m = (s / 60) % 60; val sec = s % 60
    val mm = m.toString().padStart(2, '0'); val ss = sec.toString().padStart(2, '0')
    return if (h > 0) "$h:$mm:$ss" else "$mm:$ss"
}

fun examKindName(k: String?) = mapOf("quiz" to "كوز", "midterm" to "ميد", "final" to "نهائي")[k] ?: "امتحان"

private fun JSONObject.intOr(k: String, d: Int) = if (!has(k) || isNull(k)) d else optInt(k, d)

// =============================================================== صفحة الطالب لامتحان واحد

@Composable
fun StudentExamPage(exam: JSONObject, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = bt
    val eid = exam.optInt("id")
    val atMs = ((exam.optDouble("at")) * 1000).toLong()
    val dur = exam.intOr("duration_min", 30)
    val closes = atMs + (dur + exam.intOr("late_min", 10)) * 60_000L

    var pkg by remember { mutableStateOf(ExamStore.pkg(eid)) }
    var finished by remember { mutableStateOf(ExamStore.isFinished(eid)) }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var busy by remember { mutableStateOf(false) }
    var taking by remember { mutableStateOf<Pair<JSONObject, Long>?>(null) }
    var codeAsk by remember { mutableStateOf<CompletableDeferred<String?>?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pendingTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }

    suspend fun refresh() {
        pkg = ExamStore.pkg(eid)
        finished = ExamStore.isFinished(eid)
        try {
            val r = Api.getObj("/api/exams/$eid/my-result", fresh = true)
            result = r
            if (r.optBoolean("submitted")) finished = true
        } catch (_: ApiError) {
        }
        pendingTick++
    }
    LaunchedEffect(Unit) { refresh() }

    fun download() {
        busy = true
        scope.launch {
            try {
                pkg = ExamStore.download(eid)
                toast(ctx, "تم تحميل الأسئلة. ستُفتح في وقت الامتحان حتى بدون إنترنت.")
            } catch (e: ApiError) { toast(ctx, e.message ?: "تعذّر التحميل") }
            busy = false
        }
    }

    fun start() {
        busy = true
        scope.launch {
            try {
                val st = ExamStore.state(eid)
                if (pkg == null) {
                    try { pkg = ExamStore.download(eid) } catch (e: ApiError) { throw ApiError("حمّل الأسئلة أولًا وأنت متصل بالإنترنت") }
                }
                var code = st?.str("code")
                if (code == null) {
                    try {
                        code = withTimeout(6000) { Api.getObj("/api/exams/$eid/unlock", fresh = true) }.str("code")
                    } catch (e: ApiError) {
                        if (e.code == 425 || e.code == 410 || e.code == 409) throw e
                    } catch (_: Exception) {
                    }
                    if (code == null) {
                        val d = CompletableDeferred<String?>()
                        codeAsk = d
                        code = d.await()
                    }
                }
                if (code == null) return@launch
                val data = try { ExamStore.decrypt(pkg!!, code) } catch (_: Exception) { throw ApiError("الرمز غير صحيح") }
                // محاولة بدأت سابقًا تُستأنف بساعتها الأصلية
                val started = st?.optLong("started_ms", 0L)?.takeIf { it > 0 } ?: System.currentTimeMillis()
                ExamStore.saveState(
                    eid,
                    JSONObject().put("started_ms", started).put("answers", st?.optJSONObject("answers") ?: JSONObject()).put("code", code),
                )
                taking = data to started
            } catch (e: ApiError) {
                toast(ctx, e.message ?: "تعذّر البدء")
            } finally {
                busy = false
            }
        }
    }

    taking?.let { (data, started) ->
        ExamTakeScreen(exam, data, started, dur) { taking = null; scope.launch { refresh() } }
        return
    }
    BackHandler(onBack = onBack)

    val before = now < atMs
    val after = now > closes
    Column(Modifier.fillMaxSize().background(t.page)) {
        BayanBand(exam.optString("title"), subtitle = meta(examKindName(exam.str("kind")), "$dur دقيقة"), onBack = onBack)
        ScrollPage {
            Panel(padding = PaddingValues(18.dp)) {
                Text("${fmtDay(exam.dbl("at"))}  ·  ${fmtClock(exam.dbl("at"))}", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                when {
                    finished -> Tag("تم التسليم", Kind.Ok)
                    before -> Text("يبدأ بعد ${leftStr(atMs - now)}", color = t.goldText, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    !after -> Tag("الامتحان مفتوح الآن", Kind.Ok)
                    else -> Tag("انتهى الوقت", Kind.Err)
                }
            }
            if (!finished) {
                SectionHeader("قبل الامتحان")
                val downloaded = pkg != null
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (downloaded) Icons.Rounded.CheckCircle else Icons.Outlined.DownloadForOffline, null, Modifier.size(30.dp), tint = if (downloaded) t.ok else t.tx3)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (downloaded) "الأسئلة محمّلة على جهازك ومقفلة حتى يبدأ الامتحان. لا تحتاج إنترنتًا في القاعة."
                            else "حمّل الأسئلة الآن وأنت متصل بالإنترنت، حتى تفتح في القاعة بدون إنترنت.",
                            lineHeight = 26.sp,
                        )
                    }
                }
                if (!downloaded) PrimaryButton("تحميل الأسئلة", Icons.Rounded.Download, busy) { download() }
                Spacer(Modifier.height(10.dp))
                if (!before && !after) PrimaryButton("ابدأ الامتحان", Icons.Rounded.PlayArrow, busy) { start() }
                if (before) Text("يُفتح الزر عند الموعد. في القاعة يعطيك الدكتور رمز الفتح إن لم يكن عندك إنترنت.", color = t.tx3, lineHeight = 26.sp)
            } else {
                Spacer(Modifier.height(10.dp))
                val r = result
                Panel {
                    Text(
                        if (r?.optBoolean("shown") == true) {
                            "درجتك: ${fmtNum(r.optDouble("score"))} من ${fmtNum(r.optDouble("max"))}" +
                                (if (r.optBoolean("pending")) "\n(قد تتغير بعد تصحيح الأسئلة الكتابية)" else "")
                        } else "سُلّمت إجاباتك. ستظهر الدرجة بعد انتهاء وقت الامتحان وتصحيحه.",
                        fontWeight = FontWeight.Bold, lineHeight = 28.sp,
                    )
                }
                val hasPending = remember(pendingTick) { ExamStore.pending().any { it.optInt("exam_id") == eid } }
                if (hasPending) {
                    Panel(color = t.errBg) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CloudUpload, null, tint = t.err)
                            Spacer(Modifier.width(10.dp))
                            Text("إجاباتك محفوظة على الجهاز ولم تُرفع بعد. افتح التطبيق وأنت متصل بالإنترنت وسترفع تلقائيًا.", lineHeight = 26.sp)
                        }
                    }
                }
                SecondaryButton("تحقق من الرفع", Icons.Outlined.Sync) { scope.launch { ExamSync.flush(); refresh() } }
            }
        }
    }

    codeAsk?.let { d -> CodeDialog { v -> codeAsk = null; d.complete(v) } }
}

@Composable
private fun CodeDialog(onResult: (String?) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onResult(null) }, containerColor = bt.card,
        title = { Text("رمز فتح الامتحان", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text("يعطيك الدكتور الرمز في القاعة عند بدء الامتحان.", lineHeight = 24.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    code, { if (it.length <= 6) code = it }, Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 26.sp, letterSpacing = 6.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onResult(code.trim()) }) { Text("فتح") } },
        dismissButton = { TextButton(onClick = { onResult(null) }) { Text("إلغاء") } },
    )
}

// =============================================================== أداء الامتحان

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExamTakeScreen(exam: JSONObject, data: JSONObject, startedMs: Long, durationMin: Int, onFinished: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = bt
    val eid = exam.optInt("id")
    val endMs = startedMs + durationMin * 60_000L
    val pages = remember { data.optJSONArray("pages")?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList() }

    // الترتيب المختلط ثابت لكل طالب: نفس البذرة عند إعادة الفتح
    val (qs, order) = remember {
        val rnd = Random((Api.user?.optInt("id") ?: 0) * 1000 + eid)
        val list = (data.optJSONArray("questions")?.objs() ?: emptyList()).toMutableList()
        if (pages.isEmpty()) list.shuffle(rnd)
        val ord = list.associate { q ->
            val n = q.optJSONArray("options")?.length() ?: 0
            q.optInt("id") to (0 until n).toList().shuffled(if (pages.isEmpty()) rnd else Random(0))
        }
        list.toList() to ord
    }

    val answers = remember { mutableStateMapOf<String, Any>() }
    var rev by remember { mutableIntStateOf(0) }
    var submitting by remember { mutableStateOf(false) }
    var closed by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var viewPages by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun answersJson() = JSONObject().also { o ->
        answers.forEach { (k, v) -> o.put(k, if (v is List<*>) JSONArray(v) else v) }
    }

    LaunchedEffect(Unit) {
        ExamStore.state(eid)?.optJSONObject("answers")?.let { a ->
            for (k in a.keys()) {
                val v = a.opt(k)
                if (v is JSONArray) answers[k] = List(v.length()) { v.optString(it) }
                else if (v is Number) answers[k] = v.toInt()
            }
        }
    }
    // حفظ تلقائي بعد توقف الكتابة 600ms
    LaunchedEffect(rev) {
        if (rev == 0) return@LaunchedEffect
        delay(600)
        val st = ExamStore.state(eid) ?: JSONObject()
        st.put("answers", answersJson())
        ExamStore.saveState(eid, st)
    }

    fun submit(auto: Boolean) {
        if (submitting) return
        submitting = true
        scope.launch {
            val finishedMs = minOf(System.currentTimeMillis(), endMs)
            val sub = JSONObject().put("exam_id", eid).put("answers", answersJson())
                .put("started_at", startedMs / 1000.0).put("finished_at", finishedMs / 1000.0).put("offline", false)
            var sent = false
            try {
                withTimeout(12_000) {
                    Api.post("/api/exams/$eid/submit", JSONObject().put("answers", sub.get("answers")).put("started_at", sub.get("started_at")).put("finished_at", sub.get("finished_at")))
                }
                sent = true
            } catch (e: ApiError) {
                sent = e.code == 409 // استُلمت سابقًا: لا شيء لإعادته. أي شيء آخر يُحفظ ويُعاد
            } catch (_: Exception) {
            }
            if (!sent) ExamStore.addPending(sub.put("offline", true))
            val st = ExamStore.state(eid) ?: JSONObject()
            st.put("done", true).put("answers", answersJson())
            ExamStore.saveState(eid, st)
            toast(ctx, if (sent) "تم تسليم الامتحان" else "حُفظت إجاباتك وسترفع تلقائيًا عند توفر الإنترنت")
            onFinished()
        }
    }

    // الساعة: التسليم التلقائي عند انتهاء الوقت
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
            if (now > endMs && !closed) { closed = true; submit(true) }
        }
    }

    BackHandler { toast(ctx, "لا يمكن الخروج قبل التسليم. اضغط «تسليم الامتحان» عند الانتهاء.") }

    val left = endMs - now
    val low = left < 120_000
    Column(Modifier.fillMaxSize().background(t.page).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(if (low) t.err else t.primary).padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(exam.optString("title"), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1)
            Text(leftStr(left), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)) {
            if (pages.isNotEmpty()) {
                SecondaryButton("عرض ورقة الأسئلة (${pages.size} صفحة)", Icons.Outlined.Description) { viewPages = true }
                Spacer(Modifier.height(12.dp))
            }
            qs.forEachIndexed { n, q ->
                val id = "${q.optInt("id")}"
                val opts = q.optJSONArray("options")?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList()
                val fields = q.intOr("fields", 1).coerceAtLeast(1)
                val pts = q.optDouble("points")
                Panel(padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(30.dp).background(t.plumBg, CircleShape), contentAlignment = Alignment.Center) {
                            Text(if (pages.isNotEmpty()) id else "${n + 1}", fontWeight = FontWeight.ExtraBold, color = t.plumText)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(q.str("text") ?: "سؤال $id", fontWeight = FontWeight.Bold, fontSize = 15.5.sp, lineHeight = 26.sp, modifier = Modifier.weight(1f))
                        Text("${fmtNum(pts)} د", color = t.tx3, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (opts.isNotEmpty()) {
                        (order[q.optInt("id")] ?: emptyList()).forEach { oi ->
                            val sel = answers[id] == oi
                            val shape = RoundedCornerShape(6.dp)
                            Row(
                                Modifier.padding(top = 6.dp).fillMaxWidth().clickable { answers[id] = oi; rev++ }
                                    .background(if (sel) t.plumBg else Color.Transparent, shape)
                                    .androidBorder(if (sel) t.primary else t.line, shape)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(if (sel) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked, null, Modifier.size(20.dp), tint = if (sel) t.primary else t.tx3)
                                Spacer(Modifier.width(10.dp))
                                Text(opts[oi], lineHeight = 24.sp)
                            }
                        }
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        val cur = (answers[id] as? List<String>) ?: List(fields) { "" }
                        for (i in 0 until fields) {
                            OutlinedTextField(
                                cur.getOrElse(i) { "" },
                                { v -> answers[id] = List(fields) { j -> if (j == i) v else cur.getOrElse(j) { "" } }; rev++ },
                                Modifier.padding(top = 8.dp).fillMaxWidth(), minLines = 2, maxLines = 6, shape = RoundedCornerShape(6.dp),
                                placeholder = { Text(if (fields > 1) "الإجابة ${i + 1}" else "اكتب إجابتك") },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            PrimaryButton("تسليم الامتحان", Icons.Rounded.Check, submitting) { confirm = true }
        }
    }

    if (confirm) {
        val blank = qs.count { q ->
            val a = answers["${q.optInt("id")}"]
            a == null || (a is List<*> && a.all { "$it".isBlank() })
        }
        ConfirmDialog(
            "تسليم الامتحان؟",
            if (blank == 0) "لا يمكنك التعديل بعد التسليم." else "بقي $blank سؤال بدون إجابة. لا يمكنك التعديل بعد التسليم.",
            "تسليم", danger = false, onDismiss = { confirm = false },
        ) { confirm = false; submit(false) }
    }
    if (viewPages) {
        val bitmaps = remember {
            pages.mapNotNull { p ->
                try { Base64.decode(p, Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size) } } catch (_: Exception) { null }
            }
        }
        Dialog(onDismissRequest = { viewPages = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                val pager = rememberPagerState { bitmaps.size }
                HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                    Image(bitmaps[i].asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                Text(
                    "${pager.currentPage + 1} / ${bitmaps.size}", color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
                IconButton(onClick = { viewPages = false }, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)) {
                    Icon(Icons.Rounded.Close, "إغلاق", tint = Color.White)
                }
            }
        }
    }
}

private fun Modifier.androidBorder(color: Color, shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.then(Modifier.border(1.dp, color, shape))
