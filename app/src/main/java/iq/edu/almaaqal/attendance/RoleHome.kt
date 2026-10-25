package iq.edu.almaaqal.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

fun roleName(role: String) = when (role) {
    "admin" -> "مدير النظام"
    "supervisor" -> "مشرف"
    "teacher" -> "تدريسي"
    else -> "طالب"
}

/**
 * الصفحة الرئيسية بحسب الدور: admin / supervisor+teacher / student.
 * هذه الشاشة مؤقتة في المرحلة الأولى؛ تُستبدل بشاشات AdminHome وStaffHome وStudentHome
 * (انظر خارطة الطريق في الرد).
 */
@Composable
fun RoleHome(onLogout: () -> Unit) {
    when (Api.role) {
        "student" -> { StudentHome(onLogout); return }
        "teacher", "supervisor" -> { StaffHome(onLogout); return }
        "admin" -> { AdminHome(onLogout); return }
    }
    val scope = rememberCoroutineScope()
    val u = Api.user
    Column(Modifier.fillMaxSize().background(bt.page)) {
        BayanBand(roleName(Api.role)) {
            Spacer(Modifier.height(4.dp))
            Text(u?.optString("name").orEmpty(), color = androidx.compose.ui.graphics.Color.White.copy(alpha = .85f), fontSize = 14.sp)
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(
                shape = RoundedCornerShape(6.dp),
                colors = CardDefaults.cardColors(containerColor = bt.card),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("تم الاتصال بالسيرفر", fontWeight = FontWeight.ExtraBold, color = bt.tx, fontSize = 17.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("الدور: ${roleName(Api.role)}", color = bt.tx2)
                    Text("العنوان: ${Api.base}", color = bt.tx3, fontSize = 12.sp)
                }
            }
            SecondaryButton("تسجيل الخروج", Icons.AutoMirrored.Outlined.Logout) {
                scope.launch { Api.logout(); onLogout() }
            }
        }
    }
}
