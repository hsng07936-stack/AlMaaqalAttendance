package iq.edu.almaaqal.attendance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** يفتح سجل طالب في مادة فوق التطبيق (openStudent في people.dart). */
fun openStudent(courseId: Int, studentId: Int, name: String) {
    Overlay.push { close -> StudentDetailPage(courseId, studentId, name, close) }
}

// =============================================================== طالب واحد في مادة واحدة

@Composable
fun StudentDetailPage(courseId: Int, studentId: Int, name: String, onBack: () -> Unit) {
    var k by remember { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<Pair<Int, JSONObject>?>(null) }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(bt.page)) {
        BayanBand(name, subtitle = "سجل الحضور في هذه المادة", onBack = onBack, trailing = {
            Row {
                IconButton(onClick = { Overlay.push { close -> StudentProfilePage(studentId, name, false, close) } }) {
                    Icon(Icons.Outlined.Badge, "ملف الطالب", tint = Color.White)
                }
                IconButton(onClick = { openThread(courseId, studentId, name) }) {
                    Icon(Icons.Outlined.ChatBubbleOutline, "مراسلة", tint = Color.White)
                }
            }
        })
        key(k) {
            Load(fetch = { Api.getObj("/api/courses/$courseId/students/$studentId/attendance", fresh = true) }) { d, _ ->
                val t = bt
                val st = d.obj("student")
                val sum = d.obj("summary")
                val lects = d.optJSONArray("lectures")?.objs().orEmpty()
                val n = sum.optInt("lectures")
                val att = sum.optInt("attended")
                val flagged = sum.optBoolean("flagged")
                ScrollPage {
                    Panel(padding = PaddingValues(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Ring(if (n == 0) 0.0 else att.toDouble() / n, size = 86.dp, stroke = 9.dp, color = if (flagged) t.err else t.primary) {
                                Text(if (n == 0) "—" else "${Math.round(att.toDouble() / n * 100)}%", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(meta(shiftAr(st.str("shift")).ifEmpty { null }, st.str("ref_no")), color = t.tx3, fontSize = 12.5.sp)
                                Spacer(Modifier.height(6.dp))
                                Text("حضر $att من $n محاضرة", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                Text("غاب ${sum.optInt("absent")}", color = if (sum.optInt("absent") > 0) t.err else t.tx3)
                                Spacer(Modifier.height(8.dp))
                                when {
                                    flagged -> Tag("تجاوز حد الغياب", Kind.Err)
                                    n < sum.optInt("min_lectures") ->
                                        Text("التنبيه بتجاوز الحد يبدأ بعد ${sum.optInt("min_lectures")} محاضرات منتهية", color = t.tx3, fontSize = 12.5.sp, lineHeight = 20.sp)
                                    else -> Tag("ضمن الحد", Kind.Ok)
                                }
                            }
                        }
                    }
                    SectionHeader("المحاضرات", count = lects.size)
                    if (lects.isEmpty()) Empty("لا محاضرات لهذا الطالب بعد", icon = Icons.Outlined.EventNote, modifier = Modifier.height(200.dp))
                    lects.forEach { l ->
                        val open = l.isNull("ended_at") || !l.has("ended_at")
                        val status = if (open) "open" else (l.str("status") ?: "absent")
                        val kind = if (open) Kind.Mute else statusKind(status)
                        Panel(
                            accent = if (open) null else kindColor(kind),
                            onClick = if (open) null else ({
                                edit = l.optInt("id") to JSONObject().put("student_id", studentId).put("full_name", name).put("status", status)
                            }),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(l.str("title")?.takeIf { it.isNotEmpty() } ?: "محاضرة", fontWeight = FontWeight.ExtraBold)
                                    Text(meta(fmtDay(l.dbl("started_at")), fmtClock(l.dbl("started_at")), shiftAr(l.str("shift")).ifEmpty { null }), color = t.tx3, fontSize = 12.5.sp)
                                    l.str("note")?.let { Text(it, color = t.tx2, fontSize = 12.5.sp) }
                                }
                                Tag(if (open) "جارية" else (statusAr[status] ?: status), kind)
                            }
                        }
                    }
                }
            }
        }
    }
    edit?.let { (lid, row) ->
        EditAttendanceDialog(lid, row, onDismiss = { edit = null }) { saved -> edit = null; if (saved) { Api.forgetCache(); k++ } }
    }
}

// =============================================================== المتجاوزون حد الغياب

@Composable
fun FlaggedPage(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(bt.page)) {
        BayanBand("المتجاوزون حد الغياب", subtitle = "اضغط على طالب لترى سجله", onBack = onBack)
        Load(fetch = { Api.getList("/api/flagged", fresh = true).objs() }) { rows, _ ->
            val t = bt
            if (rows.isEmpty()) {
                ScrollPage { Empty("لا أحد تجاوز الحد", "يبدأ التنبيه بعد 4 محاضرات منتهية في المادة", Icons.Outlined.Verified, Modifier.height(320.dp)) }
                return@Load
            }
            ScrollPage {
                rows.forEach { r ->
                    Panel(
                        accent = t.err, padding = PaddingValues(horizontal = 12.dp, vertical = 11.dp),
                        onClick = { openStudent(r.optInt("course_id"), r.optInt("student_id"), r.optString("full_name")) },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(r.optString("full_name"), 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.optString("full_name"), fontWeight = FontWeight.Bold)
                                Text(meta(r.str("course"), shiftAr(r.str("shift")).ifEmpty { null }), color = t.tx3, fontSize = 12.5.sp)
                                Text("غاب ${r.optInt("absent")} من ${r.optInt("lectures")} محاضرة", color = t.tx2, fontSize = 12.5.sp)
                            }
                            Text("${Math.round(r.optDouble("absence_rate") * 100)}%", color = t.err, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

// =============================================================== كل طلابي

@Composable
fun PeoplePage(onBack: () -> Unit) {
    var f by remember { mutableIntStateOf(0) }
    var q by remember { mutableStateOf("") }
    var choose by remember { mutableStateOf<JSONObject?>(null) }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(bt.page)) {
        BayanBand("طلابي", subtitle = "كل طلاب موادّك", onBack = onBack)
        Load(fetch = { Api.getList("/api/my-students", fresh = true).objs() }) { all, _ ->
            val t = bt
            val flagged = all.count { it.optInt("flagged_courses") > 0 }
            val shown = all.filter { (f == 0 || it.optInt("flagged_courses") > 0) && (q.isEmpty() || "${it.optString("full_name")} ${it.str("ref_no") ?: ""}".contains(q)) }
            ScrollPage {
                Spacer(Modifier.height(4.dp))
                Chips(listOf("كل الطلاب", "المتجاوزون"), f, { f = it }, counts = listOf(all.size, flagged), hPad = 0.dp)
                Spacer(Modifier.height(10.dp))
                BayanField(q, { q = it.trim() }, "بحث بالاسم أو الرقم المعرفي", leading = Icons.Outlined.Search)
                Spacer(Modifier.height(10.dp))
                if (shown.isEmpty()) Empty("لا نتائج", icon = Icons.Outlined.SearchOff, modifier = Modifier.height(220.dp))
                shown.forEach { p ->
                    val fl = p.optInt("flagged_courses")
                    val ids = p.optJSONArray("course_ids")
                    Panel(
                        padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), bottomGap = 8.dp, accent = if (fl > 0) t.err else null,
                        onClick = {
                            if (ids != null && ids.length() == 1) openStudent(ids.optInt(0), p.optInt("student_id"), p.optString("full_name")) else choose = p
                        },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(p.optString("full_name"), 42.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.optString("full_name"), fontWeight = FontWeight.Bold)
                                Text(meta(shiftAr(p.str("shift")).ifEmpty { null }, "${ids?.length() ?: 0} مواد"), color = t.tx3, fontSize = 12.5.sp)
                            }
                            if (fl > 0) { Tag("تجاوز في $fl", Kind.Err, dot = false); Spacer(Modifier.width(4.dp)) }
                            Icon(Icons.Rounded.ChevronLeft, null, tint = t.tx3)
                        }
                    }
                }
            }
        }
    }

    choose?.let { p ->
        val ids = p.optJSONArray("course_ids")!!
        val names = p.optJSONArray("courses")
        AlertDialog(
            onDismissRequest = { choose = null }, containerColor = bt.card,
            title = { Text(p.optString("full_name"), fontWeight = FontWeight.ExtraBold) },
            text = {
                Column {
                    Text("اختر المادة لعرض سجله فيها", color = bt.tx3)
                    for (i in 0 until ids.length()) {
                        TextButton(
                            onClick = { choose = null; openStudent(ids.optInt(i), p.optInt("student_id"), p.optString("full_name")) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(names?.optString(i) ?: "مادة ${i + 1}") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choose = null }) { Text("إلغاء") } },
        )
    }
}
