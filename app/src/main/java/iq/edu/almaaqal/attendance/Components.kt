package iq.edu.almaaqal.attendance

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------- Panel

/** البطاقة الأساسية (Panel): حدود رفيعة، وشريط لون جانبي اختياري عند طرف السطر (accent). */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(14.dp),
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    color: Color? = null,
    bottomGap: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    val m = modifier.fillMaxWidth().padding(bottom = bottomGap)
    val inner: @Composable () -> Unit = {
        Box {
            Column(Modifier.fillMaxWidth().padding(padding), content = content)
            if (accent != null) Box(Modifier.align(Alignment.CenterEnd).width(4.dp).fillMaxHeight().background(accent))
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = m, shape = shape, color = color ?: bt.card, border = BorderStroke(1.dp, bt.line), content = inner)
    } else {
        Surface(modifier = m, shape = shape, color = color ?: bt.card, border = BorderStroke(1.dp, bt.line), content = inner)
    }
}

@Composable
fun SectionHeader(title: String, count: Int? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = bt.tx)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                "$count", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = bt.plumText,
                modifier = Modifier.background(bt.plumBg, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (action != null) {
            Row(Modifier.clickable { onAction?.invoke() }, verticalAlignment = Alignment.CenterVertically) {
                Text(action, color = bt.plumText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Icon(Icons.Rounded.ChevronLeft, null, Modifier.size(18.dp), tint = bt.plumText)
            }
        }
    }
}

// ---------------------------------------------------------------- إحصاءات

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    icon: ImageVector? = null,
    valueColor: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    Panel(modifier, PaddingValues(16.dp, 14.dp), onClick, bottomGap = 0.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = bt.tx3, fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (icon != null) Icon(icon, null, Modifier.size(19.dp), tint = bt.tx3)
        }
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 34.sp, color = valueColor ?: bt.tx, maxLines = 1)
        if (caption != null) Text(caption, color = bt.tx2, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** شبكة من عمودين (على الهاتف). */
@Composable
fun StatGrid(vararg tiles: @Composable (Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.toList().chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { t -> t(Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun Ring(value: Double, modifier: Modifier = Modifier, size: Dp = 56.dp, stroke: Dp = 6.dp, color: Color? = null, content: @Composable () -> Unit = {}) {
    val target = value.coerceIn(0.0, 1.0).toFloat()
    val v by animateFloatAsState(target, tween(700, easing = CubicBezierEasing(.2f, .7f, .2f, 1f)), label = "ring")
    val track = bt.bar
    val fill = color ?: bt.primary
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val inset = sw / 2
            val sz = Size(this.size.width - sw, this.size.height - sw)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(sw, cap = StrokeCap.Round))
            if (v > 0f) drawArc(fill, -90f, 360f * v, false, Offset(inset, inset), sz, style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}

// ---------------------------------------------------------------- وسوم وأيقونات

enum class Kind { Ok, Warn, Err, Plum, Mute }

@Composable
fun Diamond(size: Dp = 6.dp, color: Color) {
    Box(Modifier.size(size).rotate(45f).background(color))
}

@Composable
fun Tag(text: String, kind: Kind = Kind.Plum, dot: Boolean = true) {
    val t = bt
    val (bg, fg) = when (kind) {
        Kind.Ok -> t.okBg to t.ok
        Kind.Warn -> t.warnBg to t.warn
        Kind.Err -> t.errBg to t.err
        Kind.Plum -> t.plumBg to t.plumText
        Kind.Mute -> t.card2 to t.tx2
    }
    Row(Modifier.background(bg, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (dot) {
            Diamond(5.dp, fg)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun IconTile(icon: ImageVector, size: Dp = 44.dp) {
    Box(Modifier.size(size).background(bt.plumBg, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.52f), tint = bt.plumText)
    }
}

@Composable
fun Avatar(name: String, size: Dp = 44.dp, onBand: Boolean = false) {
    val t = bt
    val words = name.replace(Regex("^(د\\.|م\\.|أ\\.|م\\.م\\.)\\s*"), "").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        .map { if (it.length > 3 && it.startsWith("ال")) it.substring(2) else it }
    val initials = when {
        words.isEmpty() -> "؟"
        words.size == 1 -> words[0].take(1)
        else -> words[0].take(1) + words[1].take(1)
    }
    val bg = if (onBand) Color.White.copy(alpha = .14f) else t.plumBg
    val fg = if (onBand) Color.White else t.plumText
    Box(
        Modifier.size(size).background(bg, CircleShape).then(
            if (onBand) Modifier.androidBorder() else Modifier,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = fg, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * .34f).sp)
    }
}

private fun Modifier.androidBorder() = this.then(
    Modifier.border(1.2.dp, Color.White.copy(alpha = .35f), CircleShape),
)

@Composable
fun Empty(text: String, sub: String? = null, icon: ImageVector = Icons.Outlined.Inbox, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(84.dp).background(bt.plumBg, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(38.dp), tint = bt.plumText)
        }
        Spacer(Modifier.height(16.dp))
        Text(text, textAlign = TextAlign.Center, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = bt.tx)
        if (sub != null) Text(sub, textAlign = TextAlign.Center, color = bt.tx3, modifier = Modifier.padding(top = 6.dp))
    }
}

// ---------------------------------------------------------------- التحميل (Load)

private sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Failed(val msg: String) : LoadState<Nothing>
    data class Done<T>(val data: T) : LoadState<T>
}

@Composable
private fun Skeleton() {
    val tr = rememberInfiniteTransition(label = "sk")
    val a by tr.animateFloat(.35f, .8f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(4) { Box(Modifier.fillMaxWidth().height(if (it == 0) 110.dp else 76.dp).alpha(a).background(bt.card2, RoundedCornerShape(6.dp))) }
    }
}

/**
 * يجلب بيانات من السيرفر: هيكل تحميل، ثم المحتوى مع سحب للتحديث، أو رسالة خطأ مع «إعادة المحاولة».
 * المحتوى يجب أن يكون قابلًا للتمرير (verticalScroll أو LazyColumn).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Load(fetch: suspend () -> T, content: @Composable (data: T, reload: () -> Unit) -> Unit) {
    var key by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<LoadState<T>>(LoadState.Loading) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(key) {
        try {
            state = LoadState.Done(fetch())
        } catch (e: ApiError) {
            state = LoadState.Failed(e.message ?: "خطأ")
        } catch (e: Exception) {
            state = LoadState.Failed("تعذّر تحميل البيانات")
        }
        refreshing = false
    }
    val reload: () -> Unit = { Api.forgetCache(); refreshing = true; key++ }

    when (val s = state) {
        is LoadState.Loading -> Skeleton()
        is LoadState.Failed -> Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.CloudOff, null, Modifier.size(44.dp), tint = bt.tx3)
            Spacer(Modifier.height(12.dp))
            Text(s.msg, textAlign = TextAlign.Center, color = bt.tx)
            Spacer(Modifier.height(16.dp))
            PrimaryButton("إعادة المحاولة") { state = LoadState.Loading; reload() }
        }
        is LoadState.Done -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = { reload() }, modifier = Modifier.fillMaxSize()) {
            content(s.data, reload)
        }
    }
}

/** عمود قابل للتمرير بالهوامش المعتادة لصفحات التبويبات. */
@Composable
fun ScrollPage(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp), content = content)
}

@Composable
fun Dimmed(text: String, modifier: Modifier = Modifier) {
    Text(text, color = bt.tx3, fontSize = 12.5.sp, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
}


/** شريط أزرار الفلترة الأفقي (Chips). */
@Composable
fun Chips(labels: List<String>, index: Int, onTap: (Int) -> Unit, modifier: Modifier = Modifier, counts: List<Int?>? = null, hPad: androidx.compose.ui.unit.Dp = 16.dp) {
    val t = bt
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = hPad),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { i, l ->
            val on = i == index
            val shape = RoundedCornerShape(6.dp)
            Row(
                Modifier.height(44.dp).clip(shape)
                    .background(if (on) t.plumBg else t.card)
                    .border(1.dp, if (on) t.primary.copy(alpha = .35f) else t.line, shape)
                    .clickable { onTap(i) }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(l, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = if (on) t.plumText else t.tx2)
                counts?.getOrNull(i)?.let {
                    Spacer(Modifier.width(6.dp))
                    Text("$it", fontSize = 12.sp, color = if (on) t.plumText else t.tx3, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

val statusAr = mapOf("present" to "حاضر", "absent" to "غائب", "excused" to "مجاز", "late" to "متأخر")
fun statusKind(s: String) = if (s == "present") Kind.Ok else if (s == "absent") Kind.Err else Kind.Warn

@Composable
fun kindColor(k: Kind): Color {
    val t = bt
    return when (k) { Kind.Ok -> t.ok; Kind.Warn -> t.gold; Kind.Err -> t.err; Kind.Plum -> t.plum; Kind.Mute -> t.line2 }
}
