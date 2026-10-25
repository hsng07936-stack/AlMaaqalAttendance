package iq.edu.almaaqal.attendance

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

private fun JSONObject.hasOpen() = has("open_lecture") && !isNull("open_lecture")
private fun JSONObject.orNull(k: String): Any = if (!has(k) || isNull(k)) JSONObject.NULL else opt(k)!!

private val genderAr = mapOf("male" to "ذكر", "female" to "أنثى")
private val shiftOptions = listOf("morning" to "صباحي", "evening" to "مسائي")
private val levelOptions = listOf(1, 2, 3, 4).map { "$it" to "$it" }

// =============================================================== المستخدمون

@Composable
fun UsersTab(filter: Int, onFilter: (Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val roles = listOf("", "student", "teacher", "supervisor", "admin")
    var k by remember { mutableIntStateOf(0) }
    var q by remember { mutableStateOf("") }
    var fLevel by remember { mutableStateOf<String?>(null) }
    var fShift by remember { mutableStateOf<String?>(null) }
    var fGender by remember { mutableStateOf<String?>(null) } // male | female | none
    var addDeps by remember { mutableStateOf<List<JSONObject>?>(null) }
    var edit by remember { mutableStateOf<JSONObject?>(null) }
    var whose by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            BayanBand("المستخدمون", trailing = {
                Row {
                    IconButton(onClick = { scope.launch { exportExcel(ctx, users = true) } }) { Icon(Icons.Outlined.FileDownload, "تصدير Excel", tint = Color.White) }
                    IconButton(onClick = { whose = true }) { Icon(Icons.Outlined.Nfc, "لمن هذه البطاقة؟", tint = Color.White) }
                }
            })
            key(k) {
                Load(fetch = { Api.getList("/api/users").objs() }) { all, _ ->
                    val t = bt
                    val counts = listOf(all.size) + roles.drop(1).map { r -> all.count { it.optString("role") == r } }
                    fun fits(u: JSONObject): Boolean {
                        if (fLevel == null && fShift == null && fGender == null) return true
                        if (u.optString("role") != "student") return false
                        if (fLevel != null && u.str("level") != fLevel) return false
                        if (fShift != null && u.str("shift") != fShift) return false
                        if (fGender == "none") return u.str("gender") == null
                        return fGender == null || u.str("gender") == fGender
                    }
                    val shown = all.filter {
                        fits(it) && (filter == 0 || it.optString("role") == roles[filter]) &&
                            (q.isEmpty() || "${it.optString("full_name")} ${it.optString("username")} ${it.str("ref_no") ?: ""}".contains(q))
                    }
                    val levels = all.filter { it.optString("role") == "student" && it.str("level") != null }.map { it.optInt("level") }.distinct().sorted()
                    Column(Modifier.fillMaxSize()) {
                        Spacer(Modifier.height(4.dp))
                        Chips(listOf("الكل", "الطلاب", "التدريسيون", "المشرفون", "المدراء"), filter, onFilter, counts = counts)
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)) {
                            BayanField(q, { q = it.trim() }, "بحث بالاسم أو الرقم المعرفي", leading = Icons.Outlined.Search)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DropField("المرحلة", fLevel, levels.map { "$it" to "المرحلة $it" }, Modifier.weight(1f), allowClear = true) { fLevel = it }
                                DropField("الدوام", fShift, shiftOptions, Modifier.weight(1f), allowClear = true) { fShift = it }
                                DropField("الجنس", fGender, listOf("male" to "ذكور", "female" to "إناث", "none" to "غير محدد"), Modifier.weight(1f), allowClear = true) { fGender = it }
                            }
                            Text("${shown.size} نتيجة", color = t.tx3, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                        }
                        if (shown.isEmpty()) Empty("لا نتائج", icon = Icons.Outlined.SearchOff)
                        else androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 90.dp)) {
                            items(shown.size) { i ->
                                val u = shown[i]
                                val dim = u.optInt("active") != 1
                                Panel(
                                    Modifier.alpha(if (dim) .5f else 1f), PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    onClick = { edit = u }, bottomGap = 8.dp,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Avatar(u.optString("full_name"), 42.dp)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(u.optString("full_name"), fontWeight = FontWeight.Bold)
                                            val isStudent = u.optString("role") == "student"
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    meta(
                                                        u.str("ref_no") ?: u.optString("username"),
                                                        if (isStudent) u.str("level")?.let { "م$it" } else null,
                                                        if (isStudent) shiftAr(u.str("shift")).ifEmpty { null } else null,
                                                        if (isStudent) genderAr[u.str("gender")] else null,
                                                    ),
                                                    color = t.tx3, fontSize = 12.sp,
                                                )
                                                if (isStudent) {
                                                    Spacer(Modifier.width(8.dp))
                                                    Icon(Icons.Outlined.Nfc, null, Modifier.size(14.dp), tint = if (u.str("card_uid") != null) t.ok else t.line2)
                                                }
                                            }
                                        }
                                        Tag(roleAr[u.optString("role")] ?: u.optString("role"), if (u.optString("role") == "student") Kind.Plum else Kind.Warn, dot = false)
                                        IconButton(onClick = {
                                            scope.launch {
                                                if (guard(ctx) { Api.patch("/api/users/${u.optInt("id")}", JSONObject().put("active", if (dim) 1 else 0)) }) { Api.forgetCache(); k++ }
                                            }
                                        }) { Icon(if (dim) Icons.Outlined.CheckCircleOutline else Icons.Outlined.Block, if (dim) "تفعيل" else "تعطيل", Modifier.size(20.dp), tint = t.tx3) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = {
                scope.launch {
                    try { addDeps = Api.getList("/api/departments").objs() } catch (e: ApiError) { toast(ctx, e.message ?: "تعذّر التحميل") }
                }
            },
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            containerColor = bt.primary, contentColor = Color.White,
            icon = { Icon(Icons.Outlined.PersonAdd, null) }, text = { Text("مستخدم", fontWeight = FontWeight.ExtraBold) },
        )
    }

    addDeps?.let { deps ->
        AddUserDialog(deps, onDismiss = { addDeps = null }) { body ->
            addDeps = null
            scope.launch {
                if (guard(ctx, "تمت الإضافة. سيغيّر كلمة المرور عند أول دخول.") { Api.post("/api/users", body) }) { Api.forgetCache(); k++ }
            }
        }
    }
    edit?.let { u ->
        EditUserDialog(u, onDismiss = { edit = null }) { changed -> edit = null; if (changed) { Api.forgetCache(); k++ } }
    }
    if (whose) WhoseCardFlow { whose = false }
}

@Composable
private fun AddUserDialog(deps: List<JSONObject>, onDismiss: () -> Unit, onAdd: (JSONObject) -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("teacher") }
    var dep by remember { mutableStateOf(deps.firstOrNull()?.optInt("id")?.toString()) }
    var ref by remember { mutableStateOf("") }
    var level by remember { mutableStateOf<String?>(null) }
    var shift by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = bt.card,
        title = { Text("مستخدم جديد", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BayanField(name, { name = it }, "الاسم الكامل")
                BayanField(username, { username = it }, "اسم المستخدم")
                PasswordField(password, { password = it }, "كلمة مرور مؤقتة (6 أحرف+)")
                DropField("الصلاحية", role, roleAr.entries.map { it.key to it.value }) { role = it ?: role }
                DropField("القسم", dep, deps.map { "${it.optInt("id")}" to it.optString("name") }) { dep = it }
                if (role == "student") {
                    BayanField(ref, { ref = it }, "الرقم المعرفي", keyboard = KeyboardType.Number)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DropField("المرحلة", level, levelOptions, Modifier.weight(1f)) { level = it }
                        DropField("الدوام", shift, shiftOptions, Modifier.weight(1f)) { shift = it }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank() || username.isBlank() || password.length < 6) { toast(ctx, "أكمل الاسم واسم المستخدم وكلمة مرور من 6 أحرف"); return@TextButton }
                onAdd(
                    JSONObject().put("full_name", name.trim()).put("username", username.trim()).put("password", password)
                        .put("role", role).put("department_id", dep?.toInt() ?: JSONObject.NULL).put("ref_no", ref.trim())
                        .put("level", level?.toInt() ?: JSONObject.NULL).put("shift", shift ?: JSONObject.NULL).put("must_change", true),
                )
            }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun EditUserDialog(u: JSONObject, onDismiss: () -> Unit, onDone: (changed: Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val isMe = u.optInt("id") == Api.user?.optInt("id")
    val isStudent = u.optString("role") == "student"
    var card by remember { mutableStateOf(u.str("card_uid") ?: "") }
    var pw by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(u.optString("role")) }
    var level by remember { mutableStateOf(u.str("level")) }
    var shift by remember { mutableStateOf(u.str("shift")) }
    var gender by remember { mutableStateOf(u.str("gender")) }
    var scanning by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    DisposableEffect(scanning) {
        if (scanning) NfcHub.listener = { r -> card = r.uid; scanning = false }
        onDispose { if (scanning) NfcHub.listener = null }
    }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = bt.card,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(u.optString("full_name"), 40.dp)
                Spacer(Modifier.width(12.dp))
                Text(u.optString("full_name"), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    card, { card = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(6.dp),
                    label = { Text("رقم بطاقة NFC (UID)") },
                    trailingIcon = {
                        IconButton(onClick = {
                            if (!NfcHub.available(ctx)) toast(ctx, "NFC غير متاح على هذا الجهاز. اكتب الرقم يدويًا.")
                            else { scanning = true; toast(ctx, "قرّب البطاقة من ظهر الهاتف") }
                        }) { Icon(Icons.Outlined.Nfc, "مسح بطاقة", tint = if (scanning) bt.gold else bt.tx3) }
                    },
                )
                PasswordField(pw, { pw = it }, "كلمة مرور جديدة (اتركها فارغة)")
                if (!isMe) {
                    DropField("الصلاحية", role, roleAr.entries.map { it.key to it.value }) { role = it ?: role }
                    TextButton(onClick = {
                        scope.launch {
                            if (guard(ctx, "أُخرج من كل الأجهزة وأُلغيت بصمته") { Api.post("/api/users/${u.optInt("id")}/logout-all") }) onDone(false)
                        }
                    }) { Icon(Icons.AutoMirrored.Outlined.Logout, null); Spacer(Modifier.width(6.dp)); Text("إخراجه من كل الأجهزة") }
                    TextButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = bt.err)) {
                        Icon(Icons.Outlined.DeleteOutline, null); Spacer(Modifier.width(6.dp)); Text("حذف المستخدم")
                    }
                }
                if (isStudent) {
                    TextButton(onClick = {
                        onDismiss()
                        Overlay.push { close -> StudentProfilePage(u.optInt("id"), u.optString("full_name"), false, close) }
                    }) { Icon(Icons.Outlined.Badge, null); Spacer(Modifier.width(6.dp)); Text("ملف الطالب ومستمسكاته") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DropField("المرحلة", level, levelOptions, Modifier.weight(1f)) { level = it }
                        DropField("الدوام", shift, shiftOptions, Modifier.weight(1f)) { shift = it }
                    }
                    DropField("الجنس", gender, genderAr.entries.map { it.key to it.value }) { gender = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val body = JSONObject().put("card_uid", card.trim())
                if (isStudent) body.put("level", level?.toInt() ?: JSONObject.NULL).put("shift", shift ?: JSONObject.NULL).put("gender", gender ?: JSONObject.NULL)
                if (!isMe && role != u.optString("role")) body.put("role", role)
                if (pw.isNotEmpty()) body.put("password", pw).put("must_change", 1)
                scope.launch { if (guard(ctx, "تم الحفظ") { Api.patch("/api/users/${u.optInt("id")}", body) }) onDone(true) }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
    if (confirmDelete) {
        ConfirmDialog(
            "حذف ${u.optString("full_name")}؟",
            "يُحذف الحساب نهائيًا مع حضوره ودرجاته ورسائله. لا يمكن التراجع. إن أردت إيقافه فقط فعطّله بدل الحذف.",
            onDismiss = { confirmDelete = false },
        ) {
            confirmDelete = false
            scope.launch { if (guard(ctx, "حُذف المستخدم") { Api.delete("/api/users/${u.optInt("id")}") }) onDone(true) }
        }
    }
}

// =============================================================== الهيكل: الأقسام والمراحل

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StructureTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var k by remember { mutableIntStateOf(0) }
    var addDep by remember { mutableStateOf(false) }
    var depMenu by remember { mutableStateOf<JSONObject?>(null) }
    var renameDep by remember { mutableStateOf<JSONObject?>(null) }
    var delDep by remember { mutableStateOf<JSONObject?>(null) }
    var stageMenu by remember { mutableStateOf<JSONObject?>(null) }
    var editStage by remember { mutableStateOf<JSONObject?>(null) }
    var delStage by remember { mutableStateOf<JSONObject?>(null) }
    var addStageFor by remember { mutableStateOf<JSONObject?>(null) }

    fun done() { Api.forgetCache(); k++ }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            BayanBand("الهيكل", subtitle = "الأقسام والمراحل")
            key(k) {
                Load(fetch = { Api.getList("/api/departments").objs() to Api.getList("/api/stages").objs() }) { (deps, stages), _ ->
                    ScrollPage {
                        if (deps.isEmpty()) Empty("لا توجد أقسام", "اضغط «قسم» لإضافة أول قسم", Icons.Outlined.AccountTree, Modifier.height(260.dp))
                        deps.forEach { dep ->
                            Panel(padding = PaddingValues(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconTile(Icons.Outlined.AccountBalance, 40.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(dep.optString("name"), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { depMenu = dep }) { Icon(Icons.Rounded.MoreVert, "خيارات القسم") }
                                }
                                Spacer(Modifier.height(14.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    stages.filter { it.optInt("department_id") == dep.optInt("id") }.forEach { s ->
                                        AssistChip(onClick = { stageMenu = s }, label = { Text(s.optString("name")) })
                                    }
                                    AssistChip(onClick = { addStageFor = dep }, label = { Text("مرحلة") }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)) })
                                }
                            }
                        }
                        Spacer(Modifier.height(80.dp))
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { addDep = true }, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            containerColor = bt.primary, contentColor = Color.White,
            icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("قسم", fontWeight = FontWeight.ExtraBold) },
        )
    }

    if (addDep) AskFieldsDialog("قسم جديد", listOf("name" to "اسم القسم"), onDismiss = { addDep = false }) { r ->
        addDep = false
        val n = r["name"].orEmpty().trim()
        if (n.isNotEmpty()) scope.launch { if (guard(ctx) { Api.post("/api/departments", JSONObject().put("name", n)) }) done() }
    }
    depMenu?.let { d ->
        ActionSheet(
            d.optString("name"),
            listOf(
                Triple(Icons.Outlined.Edit, "تغيير الاسم") { renameDep = d },
                Triple(Icons.Outlined.DeleteOutline, "حذف القسم (يُمنع إن كان فيه مستخدمون أو مراحل أو مواد)") { delDep = d },
            ),
            onDismiss = { depMenu = null },
        )
    }
    renameDep?.let { d ->
        AskFieldsDialog("اسم القسم", listOf("name" to "اسم القسم"), mapOf("name" to d.optString("name")), onDismiss = { renameDep = null }) { r ->
            renameDep = null
            val n = r["name"].orEmpty().trim()
            if (n.isNotEmpty()) scope.launch { if (guard(ctx) { Api.patch("/api/departments/${d.optInt("id")}", JSONObject().put("name", n)) }) done() }
        }
    }
    delDep?.let { d ->
        ConfirmDialog("حذف ${d.optString("name")}؟", "لا يُحذف إلا إذا كان فارغًا.", onDismiss = { delDep = null }) {
            delDep = null
            scope.launch { if (guard(ctx, "حُذف القسم") { Api.delete("/api/departments/${d.optInt("id")}") }) done() }
        }
    }
    stageMenu?.let { s ->
        ActionSheet(
            s.optString("name"),
            listOf(
                Triple(Icons.Outlined.Edit, "تعديل الاسم والسنة") { editStage = s },
                Triple(Icons.Outlined.DeleteOutline, "حذف المرحلة (يُمنع إن كان فيها مواد أو طلاب)") { delStage = s },
            ),
            onDismiss = { stageMenu = null },
        )
    }
    editStage?.let { s ->
        AskFieldsDialog(
            "تعديل المرحلة", listOf("name" to "اسم المرحلة", "level" to "رقم السنة (1-4)"),
            mapOf("name" to s.optString("name"), "level" to (s.str("level") ?: "")), onDismiss = { editStage = null },
        ) { r ->
            editStage = null
            val n = r["name"].orEmpty().trim()
            if (n.isNotEmpty()) scope.launch {
                val b = JSONObject().put("name", n).put("level", r["level"]?.trim()?.toIntOrNull() ?: JSONObject.NULL)
                if (guard(ctx) { Api.patch("/api/stages/${s.optInt("id")}", b) }) done()
            }
        }
    }
    delStage?.let { s ->
        ConfirmDialog("حذف ${s.optString("name")}؟", "لا تُحذف إلا إذا كانت فارغة.", onDismiss = { delStage = null }) {
            delStage = null
            scope.launch { if (guard(ctx, "حُذفت المرحلة") { Api.delete("/api/stages/${s.optInt("id")}") }) done() }
        }
    }
    addStageFor?.let { dep ->
        AskFieldsDialog(
            "مرحلة جديدة في ${dep.optString("name")}", listOf("name" to "اسم المرحلة", "level" to "رقم السنة (1-4)"),
            onDismiss = { addStageFor = null },
        ) { r ->
            addStageFor = null
            val n = r["name"].orEmpty().trim()
            if (n.isNotEmpty()) scope.launch {
                val b = JSONObject().put("name", n).put("department_id", dep.optInt("id")).put("level", r["level"]?.trim()?.toIntOrNull() ?: JSONObject.NULL)
                if (guard(ctx) { Api.post("/api/stages", b) }) done()
            }
        }
    }
}

// =============================================================== المواد (المدير)

@Composable
fun AdminCourses(openCourse: (JSONObject) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var k by remember { mutableIntStateOf(0) }
    var f by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf<JSONObject?>(null) }
    var assign by remember { mutableStateOf<Pair<JSONObject, List<JSONObject>>?>(null) }
    var enroll by remember { mutableStateOf<JSONObject?>(null) }
    var delete by remember { mutableStateOf<JSONObject?>(null) }
    var addData by remember { mutableStateOf<Triple<List<JSONObject>, List<JSONObject>, List<JSONObject>>?>(null) }

    fun done() { Api.forgetCache(); k++ }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            BayanBand("المواد", subtitle = "تعيين التدريسيين وتسجيل الطلاب")
            key(k) {
                Load(fetch = { Api.getObj("/api/home") }) { d, _ ->
                    val t = bt
                    val all = d.optJSONArray("courses")?.objs().orEmpty()
                    val noTeacher = all.count { it.optInt("teacher_count") == 0 }
                    val shown = all.filter { f == 0 || (f == 1 && it.optInt("teacher_count") == 0) || (f == 2 && it.hasOpen()) }
                    ScrollPage {
                        Chips(listOf("الكل", "بلا تدريسي", "مفتوحة الآن"), f, { f = it }, counts = listOf(all.size, noTeacher, all.count { it.hasOpen() }), hPad = 0.dp)
                        Spacer(Modifier.height(12.dp))
                        if (shown.isEmpty()) Empty("لا توجد مواد", icon = Icons.Outlined.MenuBook, modifier = Modifier.height(260.dp))
                        shown.take(60).forEach { c ->
                            Panel(onClick = { menu = c }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconTile(Icons.Outlined.MenuBook)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(c.optString("name"), fontWeight = FontWeight.ExtraBold, fontSize = 15.5.sp)
                                        Text(meta(c.str("code"), c.str("stage"), "${c.optInt("students")} طالب"), color = t.tx3, fontSize = 12.5.sp)
                                        Spacer(Modifier.height(6.dp))
                                        val teacher = c.str("teacher")
                                        if (teacher == null) Tag("بلا تدريسي", Kind.Warn)
                                        else Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.Badge, null, Modifier.size(15.dp), tint = t.tx3)
                                            Spacer(Modifier.width(5.dp))
                                            Text(teacher, color = t.tx2, fontSize = 13.sp)
                                        }
                                    }
                                    Icon(Icons.Rounded.MoreVert, null, tint = t.tx3)
                                }
                            }
                        }
                        Spacer(Modifier.height(80.dp))
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = {
                scope.launch {
                    try {
                        val deps = Api.getList("/api/departments").objs()
                        val stages = Api.getList("/api/stages").objs()
                        val teachers = Api.getList("/api/users?role=teacher").objs()
                        if (deps.isEmpty() || stages.isEmpty()) toast(ctx, "أضف قسمًا ومرحلة أولًا من تبويب الهيكل")
                        else addData = Triple(deps, stages, teachers)
                    } catch (e: ApiError) { toast(ctx, e.message ?: "تعذّر التحميل") }
                }
            },
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            containerColor = bt.primary, contentColor = Color.White,
            icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("مادة", fontWeight = FontWeight.ExtraBold) },
        )
    }

    menu?.let { c ->
        ActionSheet(
            c.optString("name"),
            listOf(
                Triple(Icons.Outlined.PersonSearch, "تعيين التدريسي") {
                    scope.launch {
                        try {
                            val teachers = Api.getList("/api/users?role=teacher").objs()
                            if (teachers.isEmpty()) toast(ctx, "أنشئ حساب تدريسي أولًا من تبويب المستخدمين") else assign = c to teachers
                        } catch (e: ApiError) { toast(ctx, e.message ?: "تعذّر التحميل") }
                    }
                },
                Triple(Icons.Outlined.GroupAdd, "تسجيل طلاب المرحلة") { enroll = c },
                Triple(Icons.AutoMirrored.Outlined.OpenInNew, "فتح المادة") { openCourse(c) },
                Triple(Icons.Outlined.DeleteOutline, "حذف المادة") { delete = c },
            ),
            onDismiss = { menu = null },
        )
    }
    assign?.let { (c, teachers) ->
        val sel = remember {
            mutableStateListOf<Int>().apply { c.optJSONArray("teacher_ids")?.let { a -> for (i in 0 until a.length()) add(a.optInt(i)) } }
        }
        AlertDialog(
            onDismissRequest = { assign = null }, containerColor = bt.card,
            title = { Text("تدريسيو ${c.optString("name")}", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    teachers.forEach { tc ->
                        val id = tc.optInt("id")
                        Row(Modifier.fillMaxWidth().clickable { if (id in sel) sel.remove(id) else sel.add(id) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(id in sel, { if (it) sel.add(id) else sel.remove(id) })
                            Text(tc.optString("full_name"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    assign = null
                    scope.launch {
                        val arr = org.json.JSONArray().also { a -> sel.forEach { a.put(it) } }
                        if (guard(ctx, "تم حفظ التدريسيين") { Api.patch("/api/courses/${c.optInt("id")}", JSONObject().put("teacher_ids", arr)) }) done()
                    }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { assign = null }) { Text("إلغاء") } },
        )
    }
    enroll?.let { c ->
        ConfirmDialog(
            "تسجيل طلاب المرحلة؟",
            "سيُسجَّل كل طلاب قسم ومرحلة «${c.optString("name")}» في هذه المادة. المسجلون سابقًا لا يتكررون.",
            "تسجيل", danger = false, onDismiss = { enroll = null },
        ) {
            enroll = null
            scope.launch {
                try {
                    val r = Api.post("/api/courses/${c.optInt("id")}/enroll_group", JSONObject()) as? JSONObject
                    toast(ctx, "مجموع طلاب المرحلة: ${r?.optInt("count") ?: 0}")
                    done()
                } catch (e: ApiError) { toast(ctx, e.message ?: "تعذّر التسجيل") }
            }
        }
    }
    delete?.let { c ->
        ConfirmDialog(
            "حذف ${c.optString("name")}؟",
            "تُحذف المادة مع محاضراتها وحضورها ودرجاتها وملفاتها ورسائلها. الطلاب أنفسهم لا يُحذفون. لا يمكن التراجع.",
            onDismiss = { delete = null },
        ) {
            delete = null
            scope.launch { if (guard(ctx, "حُذفت المادة") { Api.delete("/api/courses/${c.optInt("id")}") }) done() }
        }
    }
    addData?.let { (deps, stages, teachers) ->
        AddCourseDialog(deps, stages, teachers, onDismiss = { addData = null }) { body ->
            addData = null
            scope.launch { if (guard(ctx) { Api.post("/api/courses", body) }) done() }
        }
    }
}

@Composable
private fun AddCourseDialog(
    deps: List<JSONObject>, stages: List<JSONObject>, teachers: List<JSONObject>,
    onDismiss: () -> Unit, onAdd: (JSONObject) -> Unit,
) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var dep by remember { mutableStateOf(deps.first().optInt("id").toString()) }
    var stage by remember { mutableStateOf<String?>(null) }
    var teacher by remember { mutableStateOf<String?>(null) }
    val stageOpts = stages.filter { it.optInt("department_id").toString() == dep }.map { "${it.optInt("id")}" to it.optString("name") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = bt.card,
        title = { Text("مادة جديدة", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BayanField(name, { name = it }, "اسم المادة")
                DropField("القسم", dep, deps.map { "${it.optInt("id")}" to it.optString("name") }) { dep = it ?: dep; stage = null }
                DropField("المرحلة", stage, stageOpts) { stage = it }
                DropField("التدريسي", teacher, teachers.map { "${it.optInt("id")}" to it.optString("full_name") }) { teacher = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank() || stage == null) { toast(ctx, "أدخل الاسم واختر المرحلة"); return@TextButton }
                onAdd(
                    JSONObject().put("name", name.trim()).put("department_id", dep.toInt()).put("stage_id", stage!!.toInt())
                        .put("teacher_id", teacher?.toInt() ?: JSONObject.NULL),
                )
            }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
