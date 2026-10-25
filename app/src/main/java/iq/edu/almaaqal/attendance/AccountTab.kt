package iq.edu.almaaqal.attendance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = bt.tx3)
        Spacer(Modifier.width(12.dp))
        Text(label, color = bt.tx3)
        Spacer(Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(2f, fill = false))
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, 38.dp)
        Spacer(Modifier.width(12.dp))
        Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronLeft, null, tint = bt.tx3)
    }
}

/** تبويب «حسابي»: بيانات المستخدم، ربط البطاقة، البصمة، كلمة المرور، السيرفر، الخروج. */
@Composable
fun AccountTab(onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = bt
    var tick by remember { mutableIntStateOf(0) } // لإعادة الرسم بعد تغيّر بيانات المستخدم
    var showPw by remember { mutableStateOf(false) }
    var askCard by remember { mutableStateOf<String?>(null) } // "link" أو "inspect"
    var inspected by remember { mutableStateOf<CardRead?>(null) }
    var serverDialog by remember { mutableStateOf(false) }
    var diag by remember { mutableStateOf(false) }
    var whyNoBio by remember { mutableStateOf(false) }
    val bioAvailable = remember { Bio.available(ctx) }
    var bioOn by remember { mutableStateOf(Bio.enabled()) }

    if (showPw) {
        BackHandler { showPw = false }
        ChangePasswordScreen(onDone = { showPw = false; toast(ctx, "تم تغيير كلمة المرور") }, onBack = { showPw = false })
        return
    }

    if (tick < 0) return // يقرأ tick فقط ليعيد الرسم عند تغيّره
    val u = Api.user ?: return
    val isStudent = u.optString("role") == "student"
    val hasCard = u.str("card_uid") != null

    fun needNfc(then: () -> Unit) {
        if (NfcHub.available(ctx)) then() else toast(ctx, "NFC غير متاح أو مغلق. شغّله من إعدادات الهاتف.")
    }

    fun toggleBio() {
        val act = ctx.findActivity() ?: return
        scope.launch {
            try {
                if (bioOn) {
                    Bio.disable(); toast(ctx, "تم إيقاف الدخول بالبصمة أو الوجه")
                } else {
                    val ok = Bio.enable(act)
                    toast(ctx, if (ok) "تم تفعيل الدخول بالبصمة أو الوجه" else "لم يتم التفعيل")
                }
            } catch (e: ApiError) {
                toast(ctx, e.message ?: "تعذّر التنفيذ")
            }
            bioOn = Bio.enabled()
        }
    }

    Column(Modifier.fillMaxSize()) {
        BayanBand("حسابي")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
            Panel(padding = PaddingValues(20.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(u.optString("full_name"), 78.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(u.optString("full_name"), textAlign = TextAlign.Center, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(8.dp))
                    Tag(roleAr[u.optString("role")] ?: "")
                }
            }
            Panel(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                val rows = ArrayList<@Composable () -> Unit>()
                rows.add { InfoRow(Icons.Outlined.Badge, "اسم المستخدم", u.optString("username")) }
                u.str("ref_no")?.let { r -> rows.add { InfoRow(Icons.Outlined.Tag, "الرقم المعرفي", r) } }
                if (u.has("level") && !u.isNull("level")) rows.add {
                    InfoRow(Icons.Outlined.Stairs, "المرحلة", "المرحلة ${levelsAr[u.optInt("level")] ?: u.optInt("level")}")
                }
                shiftAr(u.str("shift")).takeIf { it.isNotEmpty() }?.let { s -> rows.add { InfoRow(Icons.Outlined.Schedule, "الدوام", s) } }
                if (isStudent) rows.add { InfoRow(Icons.Outlined.Nfc, "بطاقة NFC", if (hasCard) "مربوطة بحسابك" else "غير مربوطة") }
                rows.forEachIndexed { i, r ->
                    r()
                    if (i < rows.lastIndex) HorizontalDivider(color = t.line)
                }
            }
            Panel(padding = PaddingValues(0.dp)) {
                if (isStudent) {
                    ActionRow(Icons.Outlined.AssignmentInd, "ملفي ومستمسكاتي") {
                        Overlay.push { close -> StudentProfilePage(u.optInt("id"), u.optString("full_name"), true, close) }
                    }
                    HorizontalDivider(color = t.line)
                }
                if (isStudent && !hasCard) {
                    ActionRow(Icons.Outlined.Nfc, "ربط بطاقتي") { needNfc { askCard = "link" } }
                    HorizontalDivider(color = t.line)
                }
                ActionRow(
                    Icons.Outlined.Fingerprint,
                    if (bioAvailable) (if (bioOn) "إيقاف الدخول بالبصمة أو الوجه" else "تفعيل الدخول بالبصمة أو الوجه")
                    else "الدخول بالبصمة أو الوجه (غير متاح، اضغط لمعرفة السبب)",
                ) { if (bioAvailable) toggleBio() else whyNoBio = true }
                HorizontalDivider(color = t.line)
                ActionRow(Icons.Outlined.BatteryChargingFull, "السماح بالعمل في الخلفية (للإشعارات)") { Push.batterySettings(ctx) }
                HorizontalDivider(color = t.line)
                ActionRow(Icons.Outlined.Contactless, "فحص بطاقة") { needNfc { askCard = "inspect" } }
                HorizontalDivider(color = t.line)
                ActionRow(Icons.Outlined.BugReport, "معلومات التشخيص") { diag = true }
                HorizontalDivider(color = t.line)
                ActionRow(Icons.Outlined.LockReset, "تغيير كلمة المرور") { showPw = true }
                HorizontalDivider(color = t.line)
                ActionRow(Icons.Outlined.Dns, "إعدادات السيرفر") { serverDialog = true }
            }
            OutlinedButton(
                onClick = { scope.launch { Api.logout(); onLogout() } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, t.err.copy(alpha = .4f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = t.err),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("تسجيل الخروج", fontWeight = FontWeight.Bold)
            }
        }
    }

    // ---- النوافذ
    askCard?.let { mode ->
        CardSheet(
            if (mode == "link") "ربط بطاقتك" else "فحص بطاقة",
            if (mode == "link") "تُربط بحسابك مرة واحدة، وبعدها تدخل بتقريبها" else "يعرض ما تحمله البطاقة ولا يغيّر فيها شيئًا",
        ) { c ->
            askCard = null
            if (c != null) {
                if (mode == "link") scope.launch {
                    try { Api.linkMyCard(c.uid); toast(ctx, "تم ربط بطاقتك"); tick++ } catch (e: ApiError) { toast(ctx, e.message ?: "تعذّر الربط") }
                } else inspected = c
            }
        }
    }
    inspected?.let { r ->
        AlertDialog(
            onDismissRequest = { inspected = null }, containerColor = t.card,
            title = { Text("محتوى البطاقة", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Dimmed("رقم البطاقة (UID)")
                    Text(r.uid, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Spacer(Modifier.height(14.dp))
                    Dimmed("نوع البطاقة")
                    Text(if (r.techs.isEmpty()) "—" else r.techs.joinToString("، "))
                    Spacer(Modifier.height(14.dp))
                    Dimmed("نصوص مكتوبة على البطاقة")
                    if (r.texts.isEmpty()) Text("لا شيء. البطاقة تحمل رقمها فقط، فسيُربط بالطالب عند التفعيل.", color = t.tx2, lineHeight = 24.sp)
                    else r.texts.forEach { Text(it) }
                    r.refNo?.let { Spacer(Modifier.height(10.dp)); Tag("وُجد رقم معرفي: $it", Kind.Ok) }
                }
            },
            confirmButton = { TextButton(onClick = { inspected = null }) { Text("تم") } },
        )
    }
    if (whyNoBio) {
        AlertDialog(
            onDismissRequest = { whyNoBio = false }, containerColor = t.card,
            title = { Text("البصمة والوجه", fontWeight = FontWeight.ExtraBold) },
            text = { Text("هذا الهاتف لا يدعم القفل الحيوي القوي، أو لا توجد بصمة أو وجه مسجَّل فيه.\n\nسجّل بصمة أو وجهًا من إعدادات الهاتف (الأمان) ثم ارجع.", lineHeight = 26.sp) },
            confirmButton = { TextButton(onClick = { whyNoBio = false }) { Text("تم") } },
        )
    }
    if (diag) {
        AlertDialog(
            onDismissRequest = { diag = false }, containerColor = t.card,
            title = { Text("معلومات التشخيص", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column {
                    Text("الإصدار $APP_VERSION  (بناء ${appBuild(ctx)})", fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    Text("السيرفر: ${Api.base}", color = t.tx2, fontSize = 12.5.sp)
                }
            },
            confirmButton = { TextButton(onClick = { diag = false }) { Text("تم") } },
        )
    }
    if (serverDialog) {
        AskFieldsDialog(
            title = "عنوان السيرفر",
            fields = listOf("base" to "الافتراضي: ${Api.DEFAULT_BASE}"),
            initial = mapOf("base" to Api.base),
            onDismiss = { serverDialog = false },
        ) { r ->
            serverDialog = false
            r["base"]?.takeIf { it.isNotBlank() }?.let { Api.setBase(it); toast(ctx, "تم حفظ العنوان. سيُستعمل بعد تسجيل الخروج.") }
        }
    }
}
