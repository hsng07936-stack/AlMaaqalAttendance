package iq.edu.almaaqal.attendance

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch

/** يعرض تفعيل البصمة مرة واحدة بعد دخول المستخدم؛ لا يعطّل الدخول أبدًا (bio_offer.dart). */
@Composable
fun BioOffer() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!Session.bioAsked && Bio.available(ctx) && !Bio.enabled()) {
            Session.bioAsked = true
            show = true
        }
    }
    if (!show) return
    AlertDialog(
        onDismissRequest = { show = false },
        containerColor = bt.card,
        title = { Text("الدخول بالبصمة أو الوجه؟", fontWeight = FontWeight.ExtraBold) },
        text = { Text("في المرات القادمة تفتح التطبيق ببصمتك أو وجهك دون كتابة كلمة المرور. يمكنك إيقافه متى شئت من «حسابي».") },
        confirmButton = {
            TextButton(onClick = {
                show = false
                scope.launch {
                    val ok = try {
                        ctx.findActivity()?.let { Bio.enable(it) } ?: false
                    } catch (e: ApiError) {
                        toast(ctx, e.message ?: "تعذّر التفعيل"); return@launch
                    }
                    toast(ctx, if (ok) "تم تفعيل الدخول بالبصمة أو الوجه" else "لم يتم التفعيل")
                }
            }) { Text("تفعيل") }
        },
        dismissButton = { TextButton(onClick = { show = false }) { Text("ليس الآن") } },
    )
}
