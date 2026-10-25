package iq.edu.almaaqal.attendance

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity

/** FragmentActivity لأن BiometricPrompt يحتاجها. وضع قارئ NFC يعمل ما دام التطبيق مفتوحًا. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Api.init(this)
        ExamStore.init(this)
        setContent { AppRoot() }
    }

    override fun onResume() {
        super.onResume()
        NfcHub.enable(this) // أي بطاقة تُقرَّب تصل إلى الشاشة المنتظرة دون أن يفتح النظام تطبيقًا آخر
    }

    override fun onPause() {
        super.onPause()
        NfcHub.disable(this)
    }
}
