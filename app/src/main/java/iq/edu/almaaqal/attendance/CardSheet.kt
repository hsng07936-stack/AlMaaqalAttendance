package iq.edu.almaaqal.attendance

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardAlt
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * ورقة سفلية تنتظر بطاقة واحدة (askCard في card.dart). تُعيد ما قُرئ، أو null عند الإلغاء.
 * على المستدعي التأكد من [NfcHub.available] قبل عرضها.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardSheet(title: String, hint: String? = null, onDone: (CardRead?) -> Unit) {
    var problem by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val fired = remember { booleanArrayOf(false) }

    DisposableEffect(attempt) {
        NfcHub.listener = { r ->
            if (!fired[0]) {
                fired[0] = true
                onDone(r)
            }
        }
        onDispose { NfcHub.listener = null }
    }
    LaunchedEffect(attempt) {
        problem = null
        delay(40_000)
        problem = "لم تُقرَّب بطاقة. حاول مرة أخرى."
    }

    ModalBottomSheet(onDismissRequest = { onDone(null) }, containerColor = bt.card) {
        Column(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            NfcPulse(live = problem == null)
            Text(
                problem ?: "قرّب البطاقة من ظهر الهاتف وأبقِها ثابتة لحظة",
                textAlign = TextAlign.Center,
                color = if (problem == null) bt.tx2 else bt.err,
                lineHeight = 24.sp,
            )
            if (hint != null && problem == null) {
                Text(hint, textAlign = TextAlign.Center, color = bt.tx3, fontSize = 12.5.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(16.dp))
            if (problem != null) {
                PrimaryButton("إعادة المحاولة") { attempt++ }
                Spacer(Modifier.height(8.dp))
            }
            SecondaryButton("إلغاء") { onDone(null) }
        }
    }
}

/** الدوائر النابضة حول أيقونة NFC (NfcPulse). */
@Composable
fun NfcPulse(live: Boolean) {
    val t = bt
    val tr = rememberInfiniteTransition(label = "pulse")
    val v by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "v")
    val phase = if (live) v else 0f
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            if (live) {
                for (k in 0..1) {
                    val p = (phase + k * 0.5f) % 1f
                    drawCircle(
                        color = t.primary.copy(alpha = (1f - p) * 0.35f),
                        radius = 37.dp.toPx() + p * 38.dp.toPx(),
                        center = Offset(size.width / 2, size.height / 2),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }
        Box(Modifier.size(74.dp).background(t.plumBg, CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (live) Icons.Outlined.Nfc else Icons.Outlined.KeyboardAlt, null, tint = t.plumText, modifier = Modifier.size(36.dp))
        }
    }
}
