package iq.edu.almaaqal.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** تغيير كلمة المرور الإجباري عند أول دخول (must_change = 1). */
@Composable
fun ChangePasswordScreen(onDone: () -> Unit, onLogout: (() -> Unit)? = null, onBack: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var old by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    fun save() {
        err = when {
            old.isEmpty() -> "أدخل كلمة المرور الحالية"
            next.length < 6 -> "كلمة المرور الجديدة قصيرة (6 أحرف على الأقل)"
            next != again -> "كلمتا المرور غير متطابقتين"
            else -> null
        }
        if (err != null) return
        busy = true
        scope.launch {
            try {
                Api.changePassword(old, next)
                onDone()
            } catch (e: ApiError) {
                toast(ctx, e.message ?: "تعذر التغيير")
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(bt.page).verticalScroll(rememberScrollState())) {
        BayanBand("تغيير كلمة المرور", onBack = onBack)
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("لأمان حسابك، اختر كلمة مرور جديدة قبل المتابعة.", color = bt.tx2, lineHeight = 22.sp)
            PasswordField(old, { old = it; err = null }, "كلمة المرور الحالية")
            PasswordField(next, { next = it; err = null }, "كلمة المرور الجديدة")
            PasswordField(again, { again = it; err = null }, "أعد كتابة كلمة المرور الجديدة", error = err)
            PrimaryButton("حفظ ومتابعة", Icons.Outlined.Check, busy) { save() }
            if (onLogout != null) SecondaryButton("تسجيل الخروج", onClick = onLogout)
        }
    }
}
