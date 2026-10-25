package iq.edu.almaaqal.attendance

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AssignmentInd
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject

// =============================================================== تصدير Excel

/**
 * يطلب من السيرفر رابطًا لمرة واحدة ويفتحه في المتصفح، فينزّل ملف Excel:
 * مادة واحدة، أو كل مواد المستخدم، أو قائمة المستخدمين (للمدير)، أو نموذج الأسئلة.
 */
suspend fun exportExcel(ctx: Context, courseId: Int? = null, users: Boolean = false, template: Boolean = false) {
    try {
        val b = JSONObject()
        courseId?.let { b.put("course_id", it) }
        if (users) b.put("users", true)
        if (template) b.put("template", true)
        val url = (Api.post("/api/export-link", b) as? JSONObject)?.str("url") ?: throw ApiError("لم يصل رابط التصدير")
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Api.base + url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ApiError) {
        toast(ctx, e.message ?: "تعذّر التصدير")
    } catch (_: ActivityNotFoundException) {
        toast(ctx, "تعذّر فتح الرابط")
    }
}

// =============================================================== التحديث التلقائي

/** ما يقوله السيرفر عن آخر إصدار (UpdateInfo). */
data class UpdateInfo(val build: Int, val minBuild: Int, val version: String, val notes: String, val url: String) {
    fun newerThan(installed: Int) = build > installed && url.isNotEmpty()
    /** الإصدار المثبّت أقدم من أن يتكلم مع السيرفر: التحديث لا يُتجاوز. */
    fun forces(installed: Int) = newerThan(installed) && installed < minBuild
}

/** رقم البناء المثبّت (versionCode في build.gradle.kts). */
fun appBuild(ctx: Context): Int = try {
    PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0)).toInt()
} catch (_: Exception) {
    0
}

/** يسأل السيرفر عن آخر إصدار. يرجع null إذا لا جديد أو فشل الطلب (بلا اتصال، سيرفر قديم). */
suspend fun checkForUpdate(ctx: Context): UpdateInfo? = try {
    val j = Api.getObj("/api/app-version", fresh = true)
    UpdateInfo(j.optInt("build"), j.optInt("min_build"), j.optString("version"), j.optString("notes"), j.optString("url"))
        .takeIf { it.newerThan(appBuild(ctx)) }
} catch (_: Exception) {
    null
}

@Composable
fun UpdateDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val t = bt
    val force = info.forces(appBuild(ctx))
    AlertDialog(
        onDismissRequest = { if (!force) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !force, dismissOnClickOutside = !force),
        containerColor = t.card,
        icon = { Icon(Icons.Outlined.SystemUpdateAlt, null, tint = t.plumText) },
        title = { Text("يتوفر تحديث جديد", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text("الإصدار ${info.version}  (لديك $APP_VERSION)", color = t.tx3, fontSize = 13.sp)
                if (info.notes.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("ما الجديد", fontWeight = FontWeight.ExtraBold)
                    Text(info.notes, color = t.tx2, lineHeight = 24.sp)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (force) "هذا التحديث إلزامي لاستمرار عمل التطبيق." else "بعد التنزيل افتح الملف واضغط «تثبيت». بياناتك وحسابك لا يتأثران.",
                    color = if (force) t.err else t.tx3, fontSize = 12.5.sp, lineHeight = 22.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Api.base + info.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (_: Exception) { toast(ctx, "تعذّر فتح الرابط") }
            }) { Text("تنزيل التحديث") }
        },
        dismissButton = if (force) null else ({ TextButton(onClick = onDismiss) { Text("لاحقًا") } }),
    )
}

// =============================================================== تنبيه إكمال الملف (للطالب)

/** يظهر في رئيسية الطالب إذا نقصت معلوماته أو مستمسكاته، ويفتح ملفه. */
@Composable
fun ProfileNudge() {
    val id = Api.user?.optInt("id") ?: return
    var p by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(Unit) { p = try { Api.getObj("/api/students/$id/profile") } catch (_: Exception) { null } }
    val prof = p ?: return
    if (prof.optBoolean("complete")) return
    val missing = (prof.optJSONArray("missing_docs")?.length() ?: 0) + (prof.optJSONArray("missing_fields")?.length() ?: 0)
    val t = bt
    Panel(accent = t.gold, onClick = { Overlay.push { close -> StudentProfilePage(id, Api.user?.optString("full_name").orEmpty(), true, close) } }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.AssignmentInd)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("أكمل ملفك", fontWeight = FontWeight.ExtraBold)
                Text("ينقصك $missing من المعلومات والمستمسكات", color = t.tx3, fontSize = 12.5.sp)
            }
            Icon(Icons.Rounded.ChevronLeft, null, tint = t.tx3)
        }
    }
}
