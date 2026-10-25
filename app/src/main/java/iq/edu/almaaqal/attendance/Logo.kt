package iq.edu.almaaqal.attendance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * شعار جامعة المعقل (رسم برمجي): حلقة ذهبية مزدوجة وواجهة مبنى كلاسيكي بأعمدة.
 * لاستخدام الشعار الرسمي: ضع الصورة في res/drawable باسم almaaqal_logo.png
 * واستبدل محتوى هذه الدالة بـ Image(painterResource(R.drawable.almaaqal_logo), null, modifier).
 */
@Composable
fun AlMaaqalLogo(modifier: Modifier = Modifier.size(96.dp)) {
    val navy = Color(0xFF12294B)
    val gold = Color(0xFFD3A94F)
    val ivory = Color(0xFFFFFDF9)
    Canvas(modifier) {
        val w = size.width
        val c = Offset(w / 2f, size.height / 2f)
        // الحلقتان
        drawCircle(ivory, radius = w * 0.50f, center = c)
        drawCircle(gold, radius = w * 0.47f, center = c, style = Stroke(w * 0.03f))
        drawCircle(navy, radius = w * 0.42f, center = c)
        drawCircle(gold, radius = w * 0.39f, center = c, style = Stroke(w * 0.012f))
        // المبنى: جملون + أعمدة + درجات
        val roof = Path().apply {
            moveTo(w * 0.50f, w * 0.22f); lineTo(w * 0.76f, w * 0.38f); lineTo(w * 0.24f, w * 0.38f); close()
        }
        drawPath(roof, gold)
        drawRect(gold, Offset(w * 0.26f, w * 0.40f), Size(w * 0.48f, w * 0.035f))
        listOf(0.30f, 0.41f, 0.52f, 0.63f).forEach { x ->
            drawRect(ivory, Offset(w * x, w * 0.46f), Size(w * 0.07f, w * 0.20f))
        }
        drawRect(gold, Offset(w * 0.26f, w * 0.68f), Size(w * 0.48f, w * 0.03f))
        drawRect(gold, Offset(w * 0.22f, w * 0.72f), Size(w * 0.56f, w * 0.03f))
    }
}
