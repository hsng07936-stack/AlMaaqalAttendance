package iq.edu.almaaqal.attendance

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

const val APP_VERSION = "1.0.0"

private var bioAsked = false // مرة واحدة لكل تشغيل

@Composable
fun LoginScreen(onDone: () -> Unit, onRegister: (CardRead?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var keep by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var userErr by remember { mutableStateOf<String?>(null) }
    var passErr by remember { mutableStateOf<String?>(null) }
    var serverDialog by remember { mutableStateOf(false) }
    var askCard by remember { mutableStateOf(false) }
    var bioOn by remember { mutableStateOf(Bio.enabled() && Bio.available(ctx)) }

    // يملأ ما كُتب في المرة السابقة ليصبح الدخول لمسة واحدة
    LaunchedEffect(Unit) {
        Session.savedLogin()?.let { (n, p) ->
            if (user.isEmpty() && pass.isEmpty()) { user = n; pass = p }
        }
    }

    fun bioLogin() {
        val act = ctx.findActivity() ?: return
        busy = true
        scope.launch {
            try {
                if (Bio.unlock(act)) onDone()
            } catch (e: ApiError) {
                toast(ctx, e.message ?: "تعذّر الدخول بالبصمة")
                bioOn = false
            } finally {
                busy = false
            }
        }
    }

    // يعرض البصمة تلقائيًا مرة واحدة في كل تشغيل للتطبيق، لا بعد كل خروج
    LaunchedEffect(Unit) {
        if (bioOn && !bioAsked) {
            bioAsked = true
            bioLogin()
        }
    }

    fun cardLogin(c: CardRead) {
        busy = true
        scope.launch {
            try {
                Api.cardLogin(c.uid)
                onDone()
            } catch (e: ApiError) {
                if (e.code == 409 || e.code == 404) {
                    // غير مفعّل أو غير مربوطة: نكمل عبر إنشاء الحساب
                    toast(ctx, if (e.code == 409) "فعّل حسابك أولًا، وسنكمل من بطاقتك" else "بطاقتك غير مربوطة بعد. أنشئ حسابك وسنربطها")
                    onRegister(c)
                } else toast(ctx, e.message ?: "تعذّر الدخول بالبطاقة")
            } finally {
                busy = false
            }
        }
    }

    fun go() {
        userErr = if (user.isBlank()) "أدخل اسم المستخدم" else null
        passErr = if (pass.isEmpty()) "أدخل كلمة المرور" else null
        if (userErr != null || passErr != null) return
        busy = true
        scope.launch {
            try {
                Api.login(user.trim(), pass)
                Session.saveLogin(user.trim(), pass, keep)
                onDone()
            } catch (e: ApiError) {
                toast(ctx, e.message ?: "تعذر الدخول")
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(bt.page).verticalScroll(rememberScrollState())) {
        BayanBand("الحضور الجامعي") {
            Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AlMaaqalLogo(Modifier.size(96.dp))
                Spacer(Modifier.height(14.dp))
                Text("جامعة المعقل", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text("كلية الهندسة · هندسة الأمن السيبراني", color = Color.White.copy(alpha = .75f), fontSize = 12.5.sp)
            }
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp).widthIn(max = 440.dp)) {
            Text("تسجيل الدخول", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = bt.tx)
            Spacer(Modifier.height(4.dp))
            Text(
                "الطالب يدخل برقمه المعرفي، والتدريسي باسم المستخدم الذي أعطاه إياه المدير.",
                color = bt.tx3, lineHeight = 22.sp,
            )
            Spacer(Modifier.height(22.dp))
            if (bioOn) {
                PrimaryButton("الدخول بالبصمة أو الوجه", Icons.Outlined.Fingerprint, busy) { bioLogin() }
                Spacer(Modifier.height(14.dp))
                Text("أو بكلمة المرور", color = bt.tx3, fontSize = 12.5.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(14.dp))
            }
            BayanField(user, { user = it; userErr = null }, "اسم المستخدم / الرقم المعرفي", leading = Icons.Outlined.Person, error = userErr)
            Spacer(Modifier.height(14.dp))
            PasswordField(pass, { pass = it; passErr = null }, "كلمة المرور", error = passErr)
            Row(
                Modifier.fillMaxWidth().clickable { keep = !keep }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(keep, { keep = it })
                Text("تذكرني على هذا الجهاز", color = bt.tx2)
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton("تسجيل الدخول", Icons.AutoMirrored.Rounded.Login, busy) { go() }
            Spacer(Modifier.height(10.dp))
            SecondaryButton("الدخول بتقريب البطاقة", Icons.Outlined.Nfc, enabled = !busy) {
                if (NfcHub.available(ctx)) askCard = true
                else toast(ctx, "NFC غير متاح أو مغلق. شغّله من إعدادات الهاتف.")
            }
            Spacer(Modifier.height(8.dp))
            SecondaryButton("إنشاء حساب جديد", Icons.Outlined.PersonAdd) { onRegister(null) }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { serverDialog = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Outlined.Dns, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("إعدادات السيرفر")
            }
            Text("الإصدار $APP_VERSION", color = bt.tx3, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }

    if (askCard) {
        CardSheet("الدخول ببطاقتك", "للطلاب فقط. التدريسي والمدير يدخلان بكلمة المرور.") { c ->
            askCard = false
            if (c != null) cardLogin(c)
        }
    }

    if (serverDialog) {
        AskFieldsDialog(
            title = "عنوان السيرفر",
            fields = listOf("base" to "الافتراضي: ${Api.DEFAULT_BASE}"),
            initial = mapOf("base" to Api.base),
            onDismiss = { serverDialog = false },
        ) { r ->
            serverDialog = false
            r["base"]?.takeIf { it.isNotBlank() }?.let {
                Api.setBase(it)
                toast(ctx, "تم حفظ العنوان")
            }
        }
    }
}
