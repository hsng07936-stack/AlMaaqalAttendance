package iq.edu.almaaqal.attendance

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

// ---------------------------------------------------------------- JSON
fun JSONArray.objs(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONObject.str(k: String): String? = if (!has(k) || isNull(k)) null else optString(k)
fun JSONObject.dbl(k: String): Double? = if (!has(k) || isNull(k)) null else optDouble(k)
fun JSONObject.obj(k: String): JSONObject = optJSONObject(k) ?: JSONObject()

// ---------------------------------------------------------------- نصوص (منقولة من course.dart / shell.dart / text.dart)
val roleAr = mapOf("admin" to "مدير النظام", "supervisor" to "مشرف القسم", "teacher" to "تدريسي", "student" to "طالب")
val levelsAr = mapOf(1 to "الأولى", 2 to "الثانية", 3 to "الثالثة", 4 to "الرابعة")

fun shiftAr(s: String?) = when (s) { "morning" -> "صباحي"; "evening" -> "مسائي"; else -> "" }

fun pct(v: Double?): String = if (v == null) "—" else "${Math.round(v * 100)}%"

fun fmtNum(v: Double): String = if (v == Math.rint(v)) "${Math.round(v)}" else String.format(Locale.US, "%.1f", v)

private fun cal(t: Double) = Calendar.getInstance().apply { timeInMillis = Math.round(t * 1000) }
private fun p2(n: Int) = n.toString().padStart(2, '0')

fun fmtTime(t: Double?): String {
    if (t == null) return ""
    val c = cal(t)
    return "${c[Calendar.YEAR]}/${p2(c[Calendar.MONTH] + 1)}/${p2(c[Calendar.DAY_OF_MONTH])}  ${p2(c[Calendar.HOUR_OF_DAY])}:${p2(c[Calendar.MINUTE])}"
}

fun fmtClock(t: Double?): String {
    if (t == null) return ""
    val c = cal(t)
    return "${p2(c[Calendar.HOUR_OF_DAY])}:${p2(c[Calendar.MINUTE])}"
}

private val months = listOf("", "كانون الثاني", "شباط", "آذار", "نيسان", "أيار", "حزيران", "تموز", "آب", "أيلول", "تشرين الأول", "تشرين الثاني", "كانون الأول")

fun fmtDay(t: Double?): String {
    if (t == null) return ""
    val c = cal(t)
    return "${c[Calendar.DAY_OF_MONTH]} ${months[c[Calendar.MONTH] + 1]}"
}

fun greeting(): String {
    val h = Calendar.getInstance()[Calendar.HOUR_OF_DAY]
    return when {
        h < 5 -> "مساء الخير"
        h < 12 -> "صباح الخير"
        h < 18 -> "طاب يومك"
        else -> "مساء الخير"
    }
}

/** يربط أجزاء السطر بنقطة، ويعزل كل جزء بعلامات يونيكود كي لا يختلط العربي واللاتيني والأرقام (meta في text.dart). */
fun meta(vararg parts: Any?): String =
    parts.filter { it != null && it.toString().isNotBlank() }.joinToString("  ·  ") { "\u2068$it\u2069" }

fun userLine(): String {
    val u = Api.user ?: return ""
    val parts = ArrayList<String>()
    parts.add(roleAr[u.optString("role")] ?: "")
    if (u.has("level") && !u.isNull("level")) parts.add("المرحلة ${levelsAr[u.optInt("level")] ?: u.optInt("level")}")
    shiftAr(u.str("shift")).takeIf { it.isNotEmpty() }?.let { parts.add(it) }
    return parts.joinToString(" · ")
}
