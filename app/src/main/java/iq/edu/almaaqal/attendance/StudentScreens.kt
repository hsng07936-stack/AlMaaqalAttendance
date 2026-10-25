package iq.edu.almaaqal.attendance

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

val examKinds = mapOf("quiz" to "كوز", "midterm" to "ميد (نصف السنة)", "final" to "نهائي", "other" to "امتحان آخر")

/** الصفحة الرئيسية للطالب: خمسة تبويبات (StudentHome في student.dart). */
@Composable
fun StudentHome(onLogout: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var course by remember { mutableStateOf<JSONObject?>(null) }

    course?.let { c ->
        BackHandler { course = null }
        CoursePage(c, 0) { course = null }
        return
    }

    AppShell(
        selected = tab, onSelect = { tab = it },
        tabs = listOf(
            ShellTab("الرئيسية", Icons.Outlined.Home, Icons.Rounded.Home) { HomeTab { course = it } },
            ShellTab("حضوري", Icons.Outlined.FactCheck, Icons.Rounded.FactCheck) { AttendanceTab { course = it } },
            ShellTab("درجاتي", Icons.Outlined.WorkspacePremium, Icons.Rounded.WorkspacePremium) { GradesTab() },
            ShellTab("الرسائل", Icons.Outlined.ChatBubbleOutline, Icons.Rounded.ChatBubble, badge = true) { ThreadsTab() },
            ShellTab("حسابي", Icons.Outlined.Person, Icons.Rounded.Person) { AccountTab(onLogout) },
        ),
    )
}

// ---------------------------------------------------------------- الشريط الرئيسي

@Composable
fun HomeBand() {
    val u = Api.user
    val name = u?.optString("full_name").orEmpty()
    BayanBand("الحضور الجامعي", trailing = { Bell() }) {
        Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(name, 54.dp, onBand = true)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(greeting(), color = Color.White.copy(alpha = .75f), fontSize = 13.5.sp)
                Text(name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(userLine(), color = Color.White.copy(alpha = .78f), fontSize = 12.5.sp)
            }
        }
    }
}

private suspend fun loadHome(): JSONObject = Api.getObj("/api/home")

private fun JSONObject.hasOpenLecture() = has("open_lecture") && !isNull("open_lecture")

// ---------------------------------------------------------------- تبويب الرئيسية

@Composable
private fun HomeTab(openCourse: (JSONObject) -> Unit) {
    val go = LocalShellGo.current
    Column(Modifier.fillMaxSize()) {
        HomeBand()
        Load(fetch = { loadHome() }) { d, _ ->
            val t = bt
            val courses = d.optJSONArray("courses")?.objs().orEmpty()
            val o = d.obj("overall")
            val open = courses.filter { it.hasOpenLecture() }
            val grades = d.optJSONArray("grades")?.objs().orEmpty()
            val flagged = o.optInt("flagged")
            val lectures = o.optInt("lectures")
            ScrollPage {
                StatGrid(
                    { m ->
                        StatTile(
                            "نسبة حضوري", pct(o.dbl("rate")), m,
                            caption = if (lectures == 0) "لا محاضرات منتهية بعد" else "حضرت ${o.optInt("attended")} من $lectures محاضرة",
                            icon = Icons.Outlined.FactCheck, valueColor = if (flagged > 0) t.err else null, onClick = { go(1) },
                        )
                    },
                    { m -> StatTile("موادي", "${courses.size}", m, caption = "مسجّل هذا الفصل", icon = Icons.Outlined.MenuBook, onClick = { go(1) }) },
                    { m ->
                        StatTile(
                            "مرات الغياب", "${o.optInt("absent")}", m,
                            caption = if (flagged > 0) "تجاوزت الحد في $flagged مادة" else "ضمن الحد المسموح",
                            icon = Icons.Outlined.EventBusy, valueColor = if (flagged > 0) t.err else null, onClick = { go(1) },
                        )
                    },
                    { m ->
                        StatTile(
                            "محاضرات الآن", "${open.size}", m,
                            caption = if (open.isEmpty()) "لا شيء مفتوح" else "جارية حاليًا", icon = Icons.Outlined.Sensors,
                            onClick = { if (open.isEmpty()) go(1) else openCourse(open.first()) },
                        )
                    },
                )
                ProfileNudge()
                UpcomingExams()
                if (open.isNotEmpty()) {
                    SectionHeader("محاضرات مفتوحة الآن")
                    open.forEach { OpenCard(it, openCourse) }
                }
                SectionHeader("موادي", count = courses.size)
                if (courses.isEmpty()) Empty("لا توجد مواد مسجلة لك", "تواصل مع إدارة القسم", Icons.Outlined.MenuBook, Modifier.height(260.dp))
                courses.forEach { CourseRow(it, openCourse) }
                if (grades.isNotEmpty()) {
                    SectionHeader("آخر الدرجات", action = "كل الدرجات", onAction = { go(2) })
                    Panel(padding = PaddingValues(0.dp)) {
                        grades.forEachIndexed { i, g ->
                            GradeLine(g)
                            if (i < grades.lastIndex) HorizontalDivider(color = t.line)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpcomingExams() {
    var list by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    LaunchedEffect(Unit) {
        // قسم اختياري: أي خطأ هنا لا يجب أن يعطل الصفحة
        list = try { Api.getList("/api/exams/upcoming").objs().take(3) } catch (_: Exception) { emptyList() }
    }
    if (list.isEmpty()) return
    val t = bt
    SectionHeader("الامتحانات القادمة")
    list.forEach { e ->
        Panel(accent = t.gold) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Rounded.EditNote)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("${examKinds[e.str("kind")] ?: "امتحان"} · ${e.optString("course")}", fontWeight = FontWeight.ExtraBold)
                    Text(meta(fmtDay(e.dbl("at")), fmtClock(e.dbl("at")), e.str("room")), color = t.tx2, fontSize = 12.5.sp)
                }
            }
        }
    }
}

@Composable
private fun OpenCard(m: JSONObject, openCourse: (JSONObject) -> Unit) {
    val t = bt
    val ctx = LocalContext.current
    val l = m.obj("open_lecture")
    val url = l.str("online_url")
    Panel(accent = t.ok, onClick = { openCourse(m) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(t.okBg, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Sensors, null, tint = t.ok)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.optString("name"), fontWeight = FontWeight.ExtraBold, fontSize = 15.5.sp)
                Text(l.str("title")?.takeIf { it.isNotEmpty() } ?: "محاضرة جارية", color = t.tx2, fontSize = 13.sp)
                Text("بدأت ${fmtTime(l.dbl("started_at"))}", color = t.tx3, fontSize = 12.sp)
            }
            if (url != null) {
                Button(
                    onClick = { try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { toast(ctx, "تعذّر فتح الرابط") } },
                    colors = ButtonDefaults.buttonColors(containerColor = t.primary, contentColor = Color.White),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) { Text("انضمام") }
            } else Tag("حضورية", Kind.Ok)
        }
    }
}

@Composable
private fun CourseRow(c: JSONObject, openCourse: (JSONObject) -> Unit) {
    val t = bt
    val a = c.obj("attendance")
    val n = a.optInt("lectures")
    val rate = if (n == 0) 0.0 else a.optInt("attended").toDouble() / n
    val flagged = a.optBoolean("flagged")
    Panel(accent = if (flagged) t.err else null, onClick = { openCourse(c) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.MenuBook)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.optString("name"), fontWeight = FontWeight.ExtraBold, fontSize = 15.5.sp)
                Spacer(Modifier.height(2.dp))
                Text(meta(c.str("code"), c.str("teacher")), color = t.tx3, fontSize = 12.5.sp)
                if (c.hasOpenLecture() || flagged) {
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (c.hasOpenLecture()) Tag("محاضرة مفتوحة", Kind.Ok)
                        if (flagged) Tag("تجاوزت حد الغياب", Kind.Err)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Ring(rate, color = if (flagged) t.err else t.primary) {
                Text(if (n == 0) "—" else pct(rate), fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = if (flagged) t.err else t.tx)
            }
        }
    }
}

@Composable
private fun GradeLine(g: JSONObject) {
    val t = bt
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(g.optString("course"), fontWeight = FontWeight.Bold)
            Text(g.optString("kind"), color = t.tx3, fontSize = 12.5.sp)
        }
        Text("${fmtNum(g.optDouble("score"))} ", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = t.plumText)
        Text("/ ${fmtNum(g.optDouble("max_score"))}", color = t.tx3)
    }
}

// ---------------------------------------------------------------- تبويب حضوري

@Composable
private fun AttendanceTab(openCourse: (JSONObject) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        BayanBand("حضوري", subtitle = "نسبة حضورك في كل مادة")
        Load(fetch = { loadHome() }) { d, _ ->
            val t = bt
            val courses = d.optJSONArray("courses")?.objs().orEmpty()
            val o = d.obj("overall")
            val rate = o.dbl("rate")
            ScrollPage {
                Panel(padding = PaddingValues(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ring(rate ?: 0.0, size = 92.dp, stroke = 9.dp, color = if (o.optInt("flagged") > 0) t.err else t.primary) {
                            Text(pct(rate), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.width(18.dp))
                        Column {
                            Text("حضورك الإجمالي", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            Spacer(Modifier.height(6.dp))
                            Text("حضرت ${o.optInt("attended")} من ${o.optInt("lectures")} محاضرة منتهية", color = t.tx2)
                            Spacer(Modifier.height(8.dp))
                            Text("الحد المسموح للغياب 25% من كل مادة", color = t.tx3, fontSize = 12.5.sp)
                        }
                    }
                }
                SectionHeader("حسب المادة")
                courses.forEach { AttendanceCard(it, openCourse) }
            }
        }
    }
}

@Composable
private fun Mini(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(value, fontWeight = FontWeight.ExtraBold, color = color, fontSize = 15.sp)
        Spacer(Modifier.width(5.dp))
        Text(label, color = bt.tx3, fontSize = 12.5.sp)
    }
}

@Composable
private fun AttendanceCard(c: JSONObject, openCourse: (JSONObject) -> Unit) {
    val t = bt
    val a = c.obj("attendance")
    val n = a.optInt("lectures")
    val absence = a.optDouble("absence_rate", 0.0)
    val flagged = a.optBoolean("flagged")
    val color = if (flagged) t.err else if (absence > .15) t.gold else t.ok
    val present = if (n == 0) 0.0 else a.optInt("attended").toDouble() / n
    Panel(accent = if (flagged) t.err else null, onClick = { openCourse(c) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.optString("name"), fontWeight = FontWeight.ExtraBold, fontSize = 15.5.sp, modifier = Modifier.weight(1f))
            if (flagged) Tag("تجاوزت الحد", Kind.Err) else if (n > 0) Tag("ضمن الحد", Kind.Ok)
        }
        Spacer(Modifier.height(12.dp))
        // شريط الحضور وعليه علامة حد الغياب 25%. الاتجاه يتبع RTL تلقائيًا.
        Box(Modifier.fillMaxWidth().height(12.dp)) {
            Box(Modifier.fillMaxSize().background(t.bar, RoundedCornerShape(6.dp)))
            Box(Modifier.fillMaxWidth(present.toFloat().coerceIn(0f, 1f)).fillMaxHeight().align(Alignment.CenterStart).background(color, RoundedCornerShape(6.dp)))
            Box(Modifier.fillMaxWidth(.75f).fillMaxHeight().align(Alignment.CenterStart), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(t.tx3.copy(alpha = .5f)))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mini("حضر", "${a.optInt("attended")}", t.ok)
            Spacer(Modifier.width(18.dp))
            Mini("غاب", "${a.optInt("absent")}", if (a.optInt("absent") == 0) t.tx3 else t.err)
            Spacer(Modifier.width(18.dp))
            Mini("المحاضرات", "$n", t.tx2)
            Spacer(Modifier.weight(1f))
            Text(
                if (n == 0) "لا محاضرات منتهية بعد" else "${Math.round(absence * 100)}% غياب",
                color = if (n == 0) t.tx3 else color, fontWeight = FontWeight.ExtraBold, fontSize = if (n == 0) 12.5.sp else 14.sp,
            )
        }
    }
}

// ---------------------------------------------------------------- تبويب درجاتي

@Composable
private fun GradesTab() {
    Column(Modifier.fillMaxSize()) {
        BayanBand("درجاتي", subtitle = "درجات كل المواد")
        Load(fetch = { Api.getList("/api/my/grades").objs() }) { gs, _ ->
            val t = bt
            if (gs.isEmpty()) {
                ScrollPage { Empty("لا توجد درجات بعد", "تظهر هنا حين يدخلها التدريسي", Icons.Outlined.WorkspacePremium, Modifier.height(300.dp)) }
                return@Load
            }
            val by = LinkedHashMap<String, MutableList<JSONObject>>()
            gs.forEach { by.getOrPut(it.optString("course")) { ArrayList() }.add(it) }
            ScrollPage {
                by.forEach { (course, list) ->
                    Panel(padding = PaddingValues(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(course, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Tag("${fmtNum(list.sumOf { it.optDouble("score") })} / ${fmtNum(list.sumOf { it.optDouble("max_score") })}", dot = false)
                        }
                        Spacer(Modifier.height(12.dp))
                        list.forEach { g ->
                            val score = g.optDouble("score")
                            val max = g.optDouble("max_score")
                            Row {
                                Text(g.optString("kind"), color = t.tx2)
                                Spacer(Modifier.weight(1f))
                                Text("${fmtNum(score)} / ${fmtNum(max)}", fontWeight = FontWeight.ExtraBold)
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { if (max > 0) (score / max).toFloat().coerceIn(0f, 1f) else 0f },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(6.dp)),
                                color = t.primary, trackColor = t.bar,
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}
