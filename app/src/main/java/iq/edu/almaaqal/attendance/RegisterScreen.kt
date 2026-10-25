package iq.edu.almaaqal.attendance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * إنشاء حساب ذاتيًا (register.dart). الطالب يُتحقق منه بكشف الجامعة أو ببطاقته المربوطة،
 * والبطاقة تُربط بالحساب. التدريسي يرسل طلبًا ينتظر موافقة المدير.
 */
@Composable
fun RegisterScreen(card: CardRead?, onBack: () -> Unit, onSignedIn: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)

    var role by remember { mutableIntStateOf(0) } // 0 طالب، 1 تدريسي
    var name by remember { mutableStateOf("") }
    var ref by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var cardRead by remember { mutableStateOf<CardRead?>(null) }
    var cardName by remember { mutableStateOf<String?>(null) } // البطاقة مرتبطة بطالب في الكشف لم يفعّل حسابه
    var cardNote by remember { mutableStateOf<String?>(null) }
    var cardUsed by remember { mutableStateOf(false) } // البطاقة لحساب مفعّل
    var askCard by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val cardOnly = role == 0 && cardName != null

    fun useCard(c: CardRead) {
        cardRead = c; cardName = null; cardNote = null; cardUsed = false
        scope.launch {
            try {
                val r = Api.cardLookup(c.uid)
                val found = r.optBoolean("found")
                val activated = r.optBoolean("activated")
                if (found && !activated) {
                    cardName = r.optString("name"); cardNote = "تعرّفنا على بطاقتك"
                } else if (found) {
                    cardUsed = true
                    cardNote = "هذه البطاقة لحساب مفعّل. ارجع واستعمل «الدخول بتقريب البطاقة»."
                } else if (c.refNo != null) {
                    ref = c.refNo!!
                    cardNote = "وجدنا رقمك المعرفي على البطاقة. اكتب اسمك لإكمال التفعيل وربط البطاقة."
                } else {
                    cardNote = "بطاقتك غير مربوطة بعد. اكتب رقمك المعرفي واسمك، وسنفعّل حسابك ونربطها به."
                }
            } catch (e: ApiError) {
                toast(ctx, e.message ?: "تعذّر التعرف على البطاقة")
            }
        }
    }
    // بطاقة قُرئت في شاشة الدخول: لا نطلب اللمس مرتين
    LaunchedEffect(card) { if (card != null) useCard(card) }

    fun validate(): Boolean {
        val e = HashMap<String, String>()
        if (!cardOnly) {
            val words = name.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
            if (words < (if (role == 0) 3 else 2)) e["name"] = if (role == 0) "اكتب اسمك الثلاثي على الأقل" else "اكتب اسمك الكامل"
            if (role == 0 && !Regex("^\\d{10}$").matches(ref.trim())) e["ref"] = "الرقم المعرفي عشرة أرقام"
            if (role == 1) {
                if (!Regex("^[A-Za-z0-9._-]{3,}$").matches(user.trim())) e["user"] = "أحرف إنجليزية وأرقام، ثلاثة على الأقل"
                if (phone.trim().length < 7) e["phone"] = "أدخل رقم هاتف"
            }
        }
        if (pass.length < 8) e["pass"] = "ثمانية أحرف على الأقل"
        if (again != pass) e["again"] = "غير متطابقتين"
        errors = e
        return e.isEmpty()
    }

    fun go() {
        if (!validate()) return
        if (role == 0 && cardUsed) {
            toast(ctx, "هذه البطاقة لحساب مفعّل بالفعل"); return
        }
        val body = JSONObject().put("role", if (role == 0) "student" else "teacher").put("password", pass)
        if (role == 0 && cardOnly) {
            body.put("card_uid", cardRead!!.uid)
        } else if (role == 0) {
            body.put("full_name", name.trim()).put("ref_no", ref.trim())
            cardRead?.let { body.put("card_uid", it.uid) }
        } else {
            body.put("full_name", name.trim()).put("username", user.trim()).put("phone", phone.trim())
        }
        busy = true
        scope.launch {
            try {
                val pending = Api.register(body)
                if (pending) sent = true else onSignedIn()
            } catch (e: ApiError) {
                toast(ctx, e.message ?: "تعذّر إنشاء الحساب")
            } finally {
                busy = false
            }
        }
    }

    if (sent) {
        Column(Modifier.fillMaxSize().background(bt.page)) {
            BayanBand("تم إرسال طلبك", onBack = onBack)
            Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(92.dp).background(bt.okBg, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.HourglassTop, null, tint = bt.ok, modifier = Modifier.size(42.dp))
                }
                Spacer(Modifier.height(18.dp))
                Text("طلبك بانتظار موافقة المدير", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                Spacer(Modifier.height(8.dp))
                Text("بعد الموافقة ادخل باسم المستخدم وكلمة المرور اللذين اخترتهما.", textAlign = TextAlign.Center, color = bt.tx3, lineHeight = 26.sp)
                Spacer(Modifier.height(24.dp))
                SecondaryButton("رجوع إلى تسجيل الدخول", onClick = onBack)
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().background(bt.page)) {
        BayanBand("إنشاء حساب", onBack = onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 28.dp)
                .widthIn(max = 440.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("طالب", "تدريسي").forEachIndexed { i, l ->
                    FilterChip(selected = role == i, onClick = { role = i; errors = emptyMap() }, label = { Text(l) })
                }
            }
            if (role == 0) {
                // بطاقة التعبئة من البطاقة
                Card(
                    onClick = {
                        if (NfcHub.available(ctx)) askCard = true
                        else toast(ctx, "NFC غير متاح أو مغلق. شغّله من إعدادات الهاتف.")
                    },
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(containerColor = bt.card),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(46.dp).background(if (cardRead == null) bt.plumBg else bt.okBg, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(if (cardRead == null) Icons.Outlined.Nfc else Icons.Rounded.Check, null, tint = if (cardRead == null) bt.plumText else bt.ok)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cardName ?: if (cardRead == null) "تعبئة من البطاقة" else "تمت قراءة البطاقة", fontWeight = FontWeight.ExtraBold)
                            Text(cardNote ?: "قرّب بطاقة الطالب من ظهر الهاتف", color = bt.tx3, fontSize = 12.5.sp, lineHeight = 19.sp)
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().background(bt.plumBg, RoundedCornerShape(6.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Info, null, tint = bt.plumText)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (role == 0) {
                        if (cardOnly) "اختر كلمة مرور فقط. حسابك جاهز وبياناتك من البطاقة."
                        else "قرّب بطاقتك لتُملأ بياناتك، أو اكتب رقمك المعرفي واسمك كما في كشف الطلاب (يكفي الثلاثي)."
                    } else "يصل طلبك إلى مدير النظام، ويُفعَّل حسابك بعد موافقته.",
                    color = bt.plumText, lineHeight = 24.sp,
                )
            }
            if (!cardOnly) {
                BayanField(name, { name = it }, if (role == 0) "الاسم الثلاثي أو الرباعي" else "الاسم الكامل", leading = Icons.Outlined.Person, error = errors["name"])
                if (role == 0) {
                    BayanField(ref, { ref = it }, "الرقم المعرفي (10 أرقام)", leading = Icons.Outlined.Tag, keyboard = KeyboardType.Number, error = errors["ref"])
                } else {
                    BayanField(user, { user = it }, "اسم المستخدم (إنجليزي)", leading = Icons.Outlined.AlternateEmail, error = errors["user"])
                    BayanField(phone, { phone = it }, "رقم الهاتف", leading = Icons.Outlined.Phone, keyboard = KeyboardType.Phone, error = errors["phone"])
                }
            }
            PasswordField(pass, { pass = it }, "كلمة المرور (8 أحرف على الأقل)", error = errors["pass"])
            PasswordField(again, { again = it }, "أعد كتابة كلمة المرور", error = errors["again"])
            Spacer(Modifier.height(4.dp))
            PrimaryButton(if (role == 0) "تفعيل حسابي" else "إرسال الطلب", Icons.Rounded.Check, busy) { go() }
        }
    }

    if (askCard) {
        CardSheet("قرّب بطاقتك", "تُملأ بياناتك منها، وتُربط بحسابك") { c ->
            askCard = false
            if (c != null) useCard(c)
        }
    }
}
