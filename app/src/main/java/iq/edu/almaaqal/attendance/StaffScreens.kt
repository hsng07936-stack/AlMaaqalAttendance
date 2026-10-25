package iq.edu.almaaqal.attendance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

/** الصفحة الرئيسية للتدريسي والمشرف (StaffHome في staff.dart). المشرف يرى تبويب «القسم» أيضًا. */
@Composable
fun StaffHome(onLogout: () -> Unit) {
    val supervisor = Api.role == "supervisor"
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var course by remember { mutableStateOf<JSONObject?>(null) }
    var courseTab by remember { mutableIntStateOf(0) }

    course?.let { c ->
        BackHandler { course = null }
        CoursePage(c, courseTab) { course = null }
        return
    }
    val open: (JSONObject) -> Unit = { courseTab = 0; course = it }

    val tabs = buildList {
        add(ShellTab("الرئيسية", Icons.Outlined.Home, Icons.Rounded.Home) { StaffHomeTab(open) })
        add(ShellTab("المواد", Icons.Outlined.MenuBook, Icons.Rounded.MenuBook) { StaffCoursesTab(open) })
        if (supervisor) add(ShellTab("القسم", Icons.Outlined.Groups, Icons.Rounded.Groups) { PeopleTab() })
        add(ShellTab("الرسائل", Icons.Outlined.ChatBubbleOutline, Icons.Rounded.ChatBubble, badge = true) { ThreadsTab() })
        add(ShellTab("حسابي", Icons.Outlined.Person, Icons.Rounded.Person) { AccountTab(onLogout) })
    }
    AppShell(tabs, tab.coerceIn(0, tabs.size - 1), { tab = it })
}

private suspend fun loadHome(): JSONObject = Api.getObj("/api/home")
private fun JSONObject.hasOpen() = has("open_lecture") && !isNull("open_lecture")

// ---------------------------------------------------------------- الرئيسية

@Composable
private fun StaffHomeTab(openCourse: (JSONObject) -> Unit) {
    val go = LocalShellGo.current
    val teacher = Api.role == "teacher"
    Column(Modifier.fillMaxSize()) {
        HomeBand()
        Load(fetch = { loadHome() }) { d, _ ->
            val t = bt
            val courses = d.optJSONArray("courses")?.objs().orEmpty()
            val tot = d.obj("totals")
            val open = courses.filter { it.hasOpen() }
            val flagged = tot.optInt("flagged")
            ScrollPage {
                open.forEach { OpenHero(it, teacher, openCourse) }
                if (open.isNotEmpty()) Spacer(Modifier.height(4.dp))
                StatGrid(
                    { m ->
                        StatTile(
                            if (teacher) "طلابي" else "طلاب القسم", "${tot.optInt("students")}", m,
                            caption = "في كل المواد", icon = Icons.Outlined.Groups,
                            onClick = { if (Api.role == "supervisor") go(2) else Overlay.push { close -> PeoplePage(close) } },
                        )
                    },
                    { m -> StatTile("المواد", "${tot.optInt("courses")}", m, caption = "هذا الفصل", icon = Icons.Outlined.MenuBook, onClick = { go(1) }) },
                    { m ->
                        val r = tot.dbl("rate")
                        StatTile("متوسط الحضور", pct(r), m, caption = if (r == null) "لا محاضرات منتهية" else "كل المواد", icon = Icons.Outlined.FactCheck, onClick = { go(1) })
                    },
                    { m ->
                        StatTile(
                            "تجاوزوا الحد", "$flagged", m, caption = if (flagged == 0) "لا أحد" else "يحتاجون متابعة",
                            icon = Icons.Rounded.WarningAmber, valueColor = if (flagged > 0) t.err else null,
                            onClick = { Overlay.push { close -> FlaggedPage(close) } },
                        )
                    },
                )
                UpcomingExams()
                Spacer(Modifier.height(10.dp))
                SecondaryButton("تبليغ جديد", Icons.Outlined.Campaign) { Overlay.push { close -> AnnouncePage(onBack = close) } }
                Spacer(Modifier.height(10.dp))
                val ectx = androidx.compose.ui.platform.LocalContext.current
                val escope = rememberCoroutineScope()
                SecondaryButton("تصدير كل موادي Excel", Icons.Outlined.FileDownload) { escope.launch { exportExcel(ectx) } }
                SectionHeader(if (teacher) "موادي" else "مواد القسم", count = courses.size)
                if (courses.isEmpty()) Empty("لا توجد مواد مرتبطة بحسابك", "يربطها المدير بحسابك", Icons.Outlined.MenuBook, Modifier.height(240.dp))
                courses.forEach { StaffCourseRow(it, openCourse) }
            }
        }
    }
}

@Composable
private fun OpenHero(m: JSONObject, canRun: Boolean, openCourse: (JSONObject) -> Unit) {
    val t = bt
    val l = m.obj("open_lecture")
    Panel(accent = t.ok, padding = PaddingValues(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tag("محاضرة مفتوحة الآن", Kind.Ok)
            Spacer(Modifier.weight(1f))
            Text("منذ ${fmtClock(l.dbl("started_at"))}", color = t.tx3, fontSize = 12.5.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(m.optString("name"), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        l.str("title")?.takeIf { it.isNotEmpty() }?.let { Text(it, color = t.tx2) }
        if (canRun) {
            Spacer(Modifier.height(14.dp))
            PrimaryButton("متابعة التحضير", Icons.Outlined.Nfc) { openCourse(m) }
        }
    }
}

@Composable
private fun StaffCourseRow(c: JSONObject, openCourse: (JSONObject) -> Unit) {
    val t = bt
    val rate = c.dbl("attendance_rate")
    val flagged = c.optInt("flagged")
    Panel(accent = if (flagged > 0) t.err else null, onClick = { openCourse(c) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.MenuBook)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.optString("name"), fontWeight = FontWeight.ExtraBold, fontSize = 15.5.sp)
                Spacer(Modifier.height(2.dp))
                Text(meta(c.str("code"), "${c.optInt("students")} طالب", "${c.optInt("lectures")} محاضرة"), color = t.tx3, fontSize = 12.5.sp)
                if (c.hasOpen() || flagged > 0) {
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (c.hasOpen()) Tag("مفتوحة", Kind.Ok)
                        if (flagged > 0) Tag("$flagged تجاوزوا الحد", Kind.Err)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Ring(rate ?: 0.0, color = if (flagged > 0) t.err else t.primary) {
                Text(if (rate == null) "—" else pct(rate), fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

// ---------------------------------------------------------------- المواد

@Composable
private fun StaffCoursesTab(openCourse: (JSONObject) -> Unit) {
    var f by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        BayanBand(if (Api.role == "teacher") "موادي" else "مواد القسم")
        Load(fetch = { loadHome() }) { d, _ ->
            val all = d.optJSONArray("courses")?.objs().orEmpty()
            val shown = all.filter { f == 0 || (f == 1 && it.hasOpen()) || (f == 2 && it.optInt("flagged") > 0) }
            ScrollPage {
                Spacer(Modifier.height(4.dp))
                Chips(listOf("الكل", "مفتوحة الآن", "فيها متجاوزون"), f, { f = it }, hPad = 0.dp)
                Spacer(Modifier.height(12.dp))
                if (shown.isEmpty()) Empty("لا توجد مواد هنا", icon = Icons.Outlined.MenuBook, modifier = Modifier.height(260.dp))
                shown.forEach { StaffCourseRow(it, openCourse) }
            }
        }
    }
}

// ---------------------------------------------------------------- القسم (المشرف)

@Composable
private fun PeopleTab() {
    var f by remember { mutableIntStateOf(0) }
    var q by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        BayanBand("القسم", subtitle = "تدريسيو القسم وطلابه")
        Load(fetch = { Api.getList("/api/users").objs() }) { all, _ ->
            val t = bt
            val roles = listOf("", "student", "teacher")
            val shown = all.filter { u ->
                (f == 0 || u.optString("role") == roles[f]) &&
                    (q.isEmpty() || "${u.optString("full_name")} ${u.str("ref_no") ?: ""}".contains(q))
            }
            ScrollPage {
                Spacer(Modifier.height(4.dp))
                Chips(listOf("الكل", "الطلاب", "التدريسيون"), f, { f = it }, hPad = 0.dp)
                Spacer(Modifier.height(10.dp))
                BayanField(q, { q = it.trim() }, "بحث بالاسم أو الرقم المعرفي", leading = Icons.Outlined.Search)
                Spacer(Modifier.height(10.dp))
                if (shown.isEmpty()) Empty("لا نتائج", icon = Icons.Outlined.Groups, modifier = Modifier.height(200.dp))
                shown.forEach { u ->
                    Panel(padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), bottomGap = 8.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(u.optString("full_name"), 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(u.optString("full_name"), fontWeight = FontWeight.Bold)
                                u.str("ref_no")?.let { Text(it, color = t.tx3, fontSize = 12.sp) }
                            }
                            Tag(roleAr[u.optString("role")] ?: u.optString("role"), if (u.optString("role") == "teacher") Kind.Warn else Kind.Plum, dot = false)
                        }
                    }
                }
            }
        }
    }
}
