package iq.edu.almaaqal.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.withTimeoutOrNull

/** يبدّل بين الدخول وتغيير كلمة المرور وصفحة الدور. الخروج يعيد إلى هنا. */
@Composable
fun AppRoot() {
    var ready by remember { mutableStateOf(false) }
    var epoch by remember { mutableIntStateOf(0) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var registerOpen by remember { mutableStateOf(false) }
    var registerCard by remember { mutableStateOf<CardRead?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        withTimeoutOrNull(10_000) { Api.resume() } // يبقى مسجّلًا بين مرات التشغيل
        ready = true
    }

    // محاولات الامتحان التي لم تُرفع من القاعة ترفع تلقائيًا كلما فُتح التطبيق وكان فيه اتصال
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { if (Api.token != null) ExamSync.flush() } }
    val ctx = LocalContext.current
    // بعد الدخول: تشغيل خدمة إشعارات الهاتف وتحديث العدّادات كل 30 ثانية
    LaunchedEffect(epoch, ready) {
        Overlay.clear()
        if (ready && Api.token != null) {
            Push.start(ctx)
            update = checkForUpdate(ctx)
            while (true) { Inbox.refresh(); delay(30_000) }
        } else Inbox.reset()
    }

    BayanTheme {
        if (!ready) {
            Centered {
                Text("الحضور الجامعي", color = bt.tx, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(androidx.compose.ui.Modifier.height(10.dp))
                Text("جارٍ التشغيل…", color = bt.goldText, fontSize = 14.sp)
            }
        } else {
            val signedOut = Api.token == null
            // epoch يُجبر إعادة التركيب بعد الدخول/الخروج/تغيير كلمة المرور
            key(epoch) {
                when {
                    signedOut && registerOpen -> RegisterScreen(
                        card = registerCard,
                        onBack = { registerOpen = false; registerCard = null },
                        onSignedIn = { registerOpen = false; registerCard = null; epoch++ },
                    )
                    signedOut -> LoginScreen(
                        onDone = { epoch++ },
                        onRegister = { c -> registerCard = c; registerOpen = true },
                    )
                    Api.user?.optInt("must_change") == 1 ->
                        ChangePasswordScreen(onDone = { epoch++ }, onLogout = { scope.launch { Api.logout(); epoch++ } })
                    else -> Box(Modifier.fillMaxSize()) {
                        RoleHome { epoch++ }
                        BioOffer()
                        update?.let { u -> UpdateDialog(u) { update = null } }
                        // صفحات فوق كل شيء: محادثة، إشعارات، تبليغ جديد
                        Overlay.stack.lastOrNull()?.let { page ->
                            key(Overlay.stack.size) {
                                Surface(Modifier.fillMaxSize().pointerInput(Unit) {}, color = bt.page) { page { Overlay.pop() } }
                            }
                        }
                    }
                }
            }
        }
    }
}
