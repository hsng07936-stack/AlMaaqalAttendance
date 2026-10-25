package iq.edu.almaaqal.attendance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

class ShellTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val badge: Boolean = false, // يعرض عدد المحادثات غير المقروءة
    val content: @Composable () -> Unit,
)

/** يتيح لبطاقة في تبويب أن تنقل الشريط السفلي إلى تبويب آخر (ShellScope). */
val LocalShellGo = staticCompositionLocalOf<(Int) -> Unit> { {} }

/** هيكل الشريط السفلي. التبويب المختار يُحفظ عند المستدعي كي لا يضيع عند فتح صفحة فرعية. */
@Composable
fun AppShell(tabs: List<ShellTab>, selected: Int, onSelect: (Int) -> Unit) {
    CompositionLocalProvider(LocalShellGo provides onSelect) {
        Scaffold(
            containerColor = bt.page,
            contentWindowInsets = WindowInsets(0, 0, 0, 0), // الشريط العلوي يتكفل بحافة الحالة بنفسه
            bottomBar = {
                NavigationBar(containerColor = bt.card) {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = selected == i,
                            onClick = { onSelect(i) },
                            icon = {
                                val ic = @Composable { androidx.compose.material3.Icon(if (selected == i) t.selectedIcon else t.icon, null) }
                                if (t.badge && Inbox.threads > 0) BadgedBox(badge = { Badge { Text("${Inbox.threads}") } }) { ic() } else ic()
                            },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = bt.plumText, selectedTextColor = bt.plumText,
                                unselectedIconColor = bt.tx3, unselectedTextColor = bt.tx3,
                                indicatorColor = bt.plumBg,
                            ),
                        )
                    }
                }
            },
        ) { pad -> Box(Modifier.padding(pad)) { tabs[selected].content() } }
    }
}
