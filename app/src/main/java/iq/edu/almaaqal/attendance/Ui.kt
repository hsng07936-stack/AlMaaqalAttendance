package iq.edu.almaaqal.attendance

import androidx.compose.ui.draw.drawBehind

import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.FragmentActivity
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

/** يجد FragmentActivity من أي Context (مطلوب لـ BiometricPrompt). */
fun Context.findActivity(): FragmentActivity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

/** الشريط الكحلي العلوي بالعنوان (BayanBand): لون مسطّح بخط ذهبي سفلي، عنوان فرعي، زر رجوع، ومحتوى جانبي/سفلي. */
@Composable
fun BayanBand(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    bottom: @Composable (ColumnScope.() -> Unit)? = null,
) {
    val t = bt
    Column(
        modifier
            .fillMaxWidth()
            .background(t.band)
            .drawBehind {
                // خط ذهبي سفلي على الطراز الكلاسيكي
                val h = 3.dp.toPx()
                drawRect(t.gold, androidx.compose.ui.geometry.Offset(0f, size.height - h), androidx.compose.ui.geometry.Size(size.width, h))
            }
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Diamond(6.dp, t.gold)
                }
                if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = .72f), fontSize = 12.5.sp, maxLines = 1)
            }
            trailing?.invoke()
        }
        bottom?.invoke(this)
    }
}

/** الزر الرئيسي (كحلي). */
@Composable
fun PrimaryButton(text: String, icon: ImageVector? = null, busy: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bt.primary, contentColor = Color.White),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        else {
            if (icon != null) {
                Icon(icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
    }
}

@Composable
fun SecondaryButton(text: String, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, bt.line2),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = bt.tx),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BayanField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    error: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        leadingIcon = leading?.let { { Icon(it, null) } },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Outlined.Lock, null) },
        trailingIcon = {
            IconButton(onClick = { show = !show }) {
                Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null)
            }
        },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

/** نافذة إدخال حقول نصية (مثل askFields). تُعيد القيم عند «حفظ». */
@Composable
fun AskFieldsDialog(
    title: String,
    fields: List<Pair<String, String>>, // المفتاح، الوصف
    initial: Map<String, String> = emptyMap(),
    onDismiss: () -> Unit,
    onOk: (Map<String, String>) -> Unit,
) {
    val values = remember { mutableStateMapOf<String, String>().apply { putAll(initial) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = bt.card,
        title = { Text(title, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                fields.forEach { (k, hint) -> BayanField(values[k].orEmpty(), { values[k] = it }, hint) }
            }
        },
        confirmButton = { TextButton(onClick = { onOk(values.toMap()) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
fun Centered(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(bt.page),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

/** ينفّذ طلبًا ويعرض الخطأ أو رسالة النجاح. يرجع true عند النجاح (guard في ui.dart). */
suspend fun guard(ctx: Context, ok: String? = null, f: suspend () -> Unit): Boolean = try {
    f()
    ok?.let { toast(ctx, it) }
    true
} catch (e: ApiError) {
    toast(ctx, e.message ?: "تعذّر التنفيذ")
    false
}

/** قائمة منسدلة بعنوان صغير؛ options أزواج (قيمة، نص). */
@Composable
fun DropField(
    label: String,
    value: String?,
    options: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    allowClear: Boolean = false,
    onChange: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val shown = options.firstOrNull { it.first == value }?.second
    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, bt.line2),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = bt.tx),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 11.sp, color = bt.tx3)
                Text(shown ?: "—", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            }
            Icon(androidx.compose.material.icons.Icons.Rounded.ArrowDropDown, null)
        }
        DropdownMenu(open, { open = false }) {
            if (allowClear) DropdownMenuItem(text = { Text("الكل", color = bt.tx3) }, onClick = { onChange(null); open = false })
            options.forEach { (k, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { onChange(k); open = false }) }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, action: String = "حذف", danger: Boolean = true, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = bt.card,
        title = { Text(title, fontWeight = FontWeight.ExtraBold) },
        text = { Text(body, lineHeight = 26.sp) },
        confirmButton = { TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = if (danger) bt.err else bt.primary)) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

/** ورقة سفلية بقائمة إجراءات (أيقونة، نص، إجراء). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(title: String, actions: List<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit>>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = bt.card) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
            actions.forEach { (icon, label, act) ->
                Row(
                    Modifier.fillMaxWidth().clickable { onDismiss(); act() }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconTile(icon, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(label, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
