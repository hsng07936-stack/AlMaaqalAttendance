package iq.edu.almaaqal.attendance

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

val docLabels = linkedMapOf(
    "photo" to "الصورة الشخصية", "id_front" to "الهوية (الوجه الأمامي)", "id_back" to "الهوية (الوجه الخلفي)",
    "residence" to "بطاقة السكن", "certificate" to "شهادة الإعدادية",
)
val fieldLabels = linkedMapOf(
    "phone" to "رقم الهاتف", "birth_date" to "تاريخ الميلاد", "address" to "العنوان", "guardian_phone" to "هاتف ولي الأمر",
)

/** يفك صورة بحجم لا يتجاوز maxDim بكسل في أي بعد، كي لا تمتلئ الذاكرة بصور الهوية الكبيرة. */
private fun decodeSampled(bytes: ByteArray, maxDim: Int): Bitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    var s = 1
    while (o.outWidth / (s * 2) >= maxDim || o.outHeight / (s * 2) >= maxDim) s *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
}

/** صورة مستمسك محمية بالدخول، فتُجلب بالرأس ثم تُفك (DocImage). */
@Composable
fun DocImage(id: Int, size: Dp = 56.dp, maxDim: Int = 320, modifier: Modifier = Modifier, fit: ContentScale = ContentScale.Crop) {
    val bmp by produceState<Bitmap?>(null, id) {
        value = try { withContext(Dispatchers.Default) { decodeSampled(Api.getBytes("/api/documents/$id/file"), maxDim) } } catch (_: Exception) { null }
    }
    Box(modifier.size(size).clip(RoundedCornerShape(6.dp)).background(bt.plumBg), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = fit) }
            ?: Icon(Icons.Outlined.InsertDriveFile, null, tint = bt.tx3)
    }
}

/** عرض مستمسك بحجم كبير مع تكبير بالإصبعين. */
@Composable
private fun DocViewer(id: Int, title: String, onClose: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("إغلاق", color = Color.White) }
            }
            Box(
                Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        off = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else off + pan
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                DocImage(
                    id, size = 2000.dp, maxDim = 2048, fit = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = off.x, translationY = off.y),
                )
            }
        }
    }
}

// =============================================================== ملف الطالب

/**
 * ملف الطالب: المعلومات الشخصية والمستمسكات. [mine] يتيح للطالب تعديل ملفه؛
 * والمدير والمشرف يرفعان ويتحققان.
 */
@Composable
fun StudentProfilePage(studentId: Int, name: String, mine: Boolean = false, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val manage = Api.role == "admin" || Api.role == "supervisor"
    val canUpload = mine || manage
    var k by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var pickKind by remember { mutableStateOf("") }
    var viewing by remember { mutableStateOf<Pair<Int, String>?>(null) }
    BackHandler(onBack = onBack)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = guard(ctx, "تم الرفع") {
                val (fname, bytes) = withContext(Dispatchers.IO) {
                    var n = "photo.jpg"
                    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (i >= 0 && c.moveToFirst()) n = c.getString(i)
                    }
                    n to (ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0))
                }
                Api.upload(if (mine) "/api/me/documents" else "/api/students/$studentId/documents", fname, bytes, mapOf("kind" to pickKind))
            }
            if (ok) { Api.forgetCache(); k++ }
        }
    }

    Column(Modifier.fillMaxSize().background(bt.page)) {
        BayanBand(if (mine) "ملفي" else name, subtitle = if (mine) "معلوماتك ومستمسكاتك" else "ملف الطالب", onBack = onBack)
        key(k) {
            Load(fetch = { Api.getObj("/api/students/$studentId/profile", fresh = true) }) { p, _ ->
                val t = bt
                val values = remember(p) { mutableStateMapOf<String, String>().apply { fieldLabels.keys.forEach { f -> put(f, p.str(f) ?: "") } } }
                val missingDocs = p.optJSONArray("missing_docs")?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList()
                val missingFields = p.optJSONArray("missing_fields")?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList()
                val total = docLabels.size + fieldLabels.size
                val done = total - missingDocs.size - missingFields.size
                val complete = p.optBoolean("complete")
                val byKind = (p.optJSONArray("documents")?.objs() ?: emptyList()).associateBy { it.optString("kind") }
                ScrollPage {
                    Panel(padding = PaddingValues(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val pid = if (p.isNull("photo_id") || !p.has("photo_id")) null else p.optInt("photo_id")
                            if (pid != null) Box(Modifier.clickable { viewing = pid to "الصورة الشخصية" }) { DocImage(pid, 72.dp) }
                            else Avatar(p.optString("full_name"), 72.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.optString("full_name"), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                Text(
                                    meta(p.str("ref_no"), p.str("level")?.let { "المرحلة $it" }, shiftAr(p.str("shift")).ifEmpty { null }),
                                    color = t.tx3, fontSize = 12.5.sp,
                                )
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { done.toFloat() / total }, color = if (complete) t.ok else t.gold, trackColor = t.bar,
                                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(6.dp)),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(if (complete) "الملف مكتمل" else "اكتمل $done من $total", color = if (complete) t.ok else t.tx2, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (!mine && Api.role == "admin") {
                        Spacer(Modifier.height(4.dp))
                        SecondaryButton("مراسلة الطالب", Icons.Outlined.ChatBubbleOutline) { openThread(0, studentId, p.optString("full_name")) }
                    }
                    SectionHeader("المعلومات الشخصية")
                    Panel(padding = PaddingValues(14.dp)) {
                        fieldLabels.forEach { (key, label) ->
                            if (mine) {
                                BayanField(
                                    values[key].orEmpty(), { values[key] = it }, label,
                                    keyboard = if (key.contains("phone")) KeyboardType.Phone else KeyboardType.Text,
                                )
                                Spacer(Modifier.height(10.dp))
                            } else {
                                val v = p.str(key).orEmpty()
                                Row(Modifier.padding(bottom = 10.dp)) {
                                    Text(label, color = t.tx3)
                                    Spacer(Modifier.weight(1f))
                                    Text(if (v.isEmpty()) "ناقص" else v, fontWeight = FontWeight.Bold, color = if (v.isEmpty()) t.err else t.tx)
                                }
                            }
                        }
                        if (mine) PrimaryButton("حفظ المعلومات", Icons.Outlined.Check, saving) {
                            saving = true
                            scope.launch {
                                val body = JSONObject().also { o -> values.forEach { (f, v) -> o.put(f, v.trim()) } }
                                val ok = guard(ctx, "حُفظت معلوماتك") { Api.patch("/api/me/profile", body) }
                                saving = false
                                if (ok) { Api.forgetCache(); k++ }
                            }
                        }
                    }
                    if (canUpload) {
                        SectionHeader("المستمسكات")
                        docLabels.forEach { (kind, label) ->
                            val d = byKind[kind]
                            val verified = d?.optInt("verified") == 1
                            Panel(accent = if (d == null) t.err else null, padding = PaddingValues(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (d != null) Box(Modifier.clickable { viewing = d.optInt("id") to label }) { DocImage(d.optInt("id"), 52.dp) }
                                    else IconTile(Icons.Outlined.AddPhotoAlternate, 52.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(label, fontWeight = FontWeight.Bold)
                                        if (d == null) Text("ناقص", color = t.err, fontSize = 12.5.sp)
                                        else Text(if (verified) "تم التحقق" else "بانتظار التحقق", color = if (verified) t.ok else t.tx3, fontSize = 12.5.sp)
                                    }
                                    if (manage && d != null) IconButton(onClick = {
                                        scope.launch {
                                            if (guard(ctx) { Api.post("/api/documents/${d.optInt("id")}/verify", JSONObject().put("verified", !verified)) }) { Api.forgetCache(); k++ }
                                        }
                                    }) { Icon(if (verified) Icons.Outlined.Verified else Icons.Outlined.NewReleases, if (verified) "إلغاء التحقق" else "تحقق", tint = if (verified) t.ok else t.tx3) }
                                    TextButton(onClick = { pickKind = kind; picker.launch(arrayOf("image/*")) }) { Text(if (d == null) "رفع" else "استبدال") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    viewing?.let { (id, title) -> DocViewer(id, title) { viewing = null } }
}

// =============================================================== نواقص الملفات (المدير)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MissingProfilesPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var k by remember { mutableIntStateOf(0) }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(bt.page)) {
        BayanBand("نواقص ملفات الطلاب", subtitle = "المعلومات والمستمسكات", onBack = onBack)
        key(k) {
            Load(fetch = { Api.getList("/api/profiles/missing", fresh = true).objs() }) { rows, _ ->
                val t = bt
                if (rows.isEmpty()) {
                    ScrollPage { Empty("كل الملفات مكتملة", icon = Icons.Outlined.Verified, modifier = Modifier.height(320.dp)) }
                    return@Load
                }
                ScrollPage {
                    PrimaryButton("تذكير كل الناقصين (${rows.size})", Icons.Outlined.NotificationsActive) {
                        scope.launch {
                            if (guard(ctx, "أُرسل التذكير") { Api.post("/api/profiles/request", JSONObject().put("student_ids", org.json.JSONArray())) }) { Api.forgetCache(); k++ }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    rows.forEach { p ->
                        Panel(
                            padding = PaddingValues(12.dp), bottomGap = 8.dp,
                            onClick = { Overlay.push { close -> StudentProfilePage(p.optInt("id"), p.optString("full_name"), false, close) } },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(p.optString("full_name"), 38.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(p.optString("full_name"), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(meta(p.str("level")?.let { "م$it" }, shiftAr(p.str("shift")).ifEmpty { null }), color = t.tx3, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                p.optJSONArray("missing_fields")?.let { a -> for (i in 0 until a.length()) Tag(fieldLabels[a.optString(i)] ?: a.optString(i), Kind.Warn, dot = false) }
                                p.optJSONArray("missing_docs")?.let { a -> for (i in 0 until a.length()) Tag(docLabels[a.optString(i)] ?: a.optString(i), Kind.Err, dot = false) }
                            }
                        }
                    }
                }
            }
        }
    }
}
