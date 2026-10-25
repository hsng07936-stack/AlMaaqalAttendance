package iq.edu.almaaqal.attendance

import android.app.Activity
import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Handler
import android.os.Looper

/** كل ما تخبرنا به لمسة بطاقة واحدة (مثل CardRead في nfc.dart). */
data class CardRead(
    /** رقم البطاقة UID بحروف كبيرة، وهو ما يخزنه السيرفر في card_uid. */
    val uid: String,
    val techs: List<String> = emptyList(),
    val texts: List<String> = emptyList(),
) {
    /** رقم من 10 خانات مكتوب على البطاقة إن وُجد (الرقم المعرفي الجامعي). */
    val refNo: String?
        get() = texts.firstNotNullOfOrNull { Regex("(?<!\\d)\\d{10}(?!\\d)").find(it)?.value }
}

/**
 * قارئ البطاقات. MainActivity تشغّل وضع القارئ عند الاستئناف، وكل لمسة تصل إلى [listener]
 * (الذي تضبطه الشاشة المنتظرة للبطاقة). إن لم يوجد مستمع تُتجاهل اللمسة.
 */
object NfcHub {
    @Volatile var listener: ((CardRead) -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())

    fun available(ctx: Context): Boolean = try {
        NfcAdapter.getDefaultAdapter(ctx)?.isEnabled == true
    } catch (_: Exception) {
        false
    }

    fun enable(activity: Activity) {
        val a = NfcAdapter.getDefaultAdapter(activity) ?: return
        if (!a.isEnabled) return
        try {
            a.enableReaderMode(
                activity,
                { tag -> read(tag)?.let { r -> main.post { listener?.invoke(r) } } },
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                null,
            )
        } catch (_: Exception) {
        }
    }

    fun disable(activity: Activity) {
        try {
            NfcAdapter.getDefaultAdapter(activity)?.disableReaderMode(activity)
        } catch (_: Exception) {
        }
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02X".format(it) }

    private fun read(tag: Tag): CardRead? {
        val id = tag.id
        if (id == null || id.isEmpty()) return null
        val texts = try {
            ndefTexts(Ndef.get(tag)?.cachedNdefMessage)
        } catch (_: Exception) {
            emptyList()
        }
        return CardRead(hex(id), tag.techList.map { it.removePrefix("android.nfc.tech.") }, texts)
    }

    /** نصوص وروابط رسالة NDEF القابلة للقراءة. */
    fun ndefTexts(msg: NdefMessage?): List<String> {
        val out = ArrayList<String>()
        for (r in msg?.records.orEmpty()) {
            val p = r.payload ?: continue
            if (p.isEmpty()) continue
            try {
                val wellKnown = r.tnf == NdefRecord.TNF_WELL_KNOWN
                when {
                    wellKnown && r.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                        val lang = p[0].toInt() and 0x3F // طول رمز اللغة في بايت الحالة
                        out.add(String(p, 1 + lang, p.size - 1 - lang, Charsets.UTF_8))
                    }
                    wellKnown && r.type.contentEquals(NdefRecord.RTD_URI) ->
                        out.add(String(p, 1, p.size - 1, Charsets.UTF_8))
                    else -> out.add(String(p, Charsets.UTF_8))
                }
            } catch (_: Exception) {
            }
        }
        return out.filter { it.isNotBlank() }
    }
}
