package com.example.altyazi

import android.content.Context

/**
 * Kalıcı çeviri hafızası.
 *
 * Bir kez çevrilen replik telefona yazılır. Aynı replik tekrar geçtiğinde
 * ne internet ne de jeton gerekir, anında ekrana gelir. Oyunu ikinci kez
 * oynarken veya aynı diziyi izlerken uygulama kendi kendine hızlanır.
 */
class TranslationStore(context: Context, name: String = QUICK) {

    // QUICK: hızlı (ML Kit) çeviriler. AI: telefondaki yapay zekânın çevirileri.
    // Ayrı tutulur: yapay zekâ açıkken kaba çeviri hafızası hiç kullanılmaz.
    private val sp = context.applicationContext
        .getSharedPreferences(name, Context.MODE_PRIVATE)

    // 10.8: her kaydın son kullanılma günü. Hafıza dolunca en eski kayıtlar
    // silinir; eskiden 5000'i geçince HAFIZANIN TAMAMI siliniyordu.
    private val zaman = context.applicationContext
        .getSharedPreferences(name + "_zaman", Context.MODE_PRIVATE)

    /** Kayıt sayısı; her yazışta bütün hafızayı okumamak için bellekte tutulur. */
    @Volatile private var adet = -1

    fun get(english: String): String? {
        val key = key(english)
        if (key.isEmpty()) return null
        val deger = sp.getString(key, null) ?: return null
        // Sık geçen replik eski sayılıp silinmesin: günde en çok bir kez tazelenir.
        val bugun = gun()
        if (zaman.getInt(key, 0) != bugun) zaman.edit().putInt(key, bugun).apply()
        return deger
    }

    fun put(english: String, turkish: String) {
        val key = key(english)
        if (key.isEmpty() || turkish.isBlank()) return
        if (adet < 0) adet = sp.all.size
        val yeni = !sp.contains(key)
        sp.edit().putString(key, turkish).apply()
        zaman.edit().putInt(key, gun()).apply()
        if (yeni) adet++
        if (adet > MAX_ENTRIES) budaEnEski()
    }

    /** En uzun süredir kullanılmayan BUDAMA kaydı siler; gerisi kalır. */
    private fun budaEnEski() {
        val gunler = zaman.all
        val sirali = sp.all.keys.sortedBy { (gunler[it] as? Int) ?: 0 }
        val silinecek = sirali.take(BUDAMA)
        val e = sp.edit()
        val z = zaman.edit()
        for (k in silinecek) { e.remove(k); z.remove(k) }
        e.apply()
        z.apply()
        adet = sirali.size - silinecek.size
    }

    private fun gun(): Int = (System.currentTimeMillis() / 86_400_000L).toInt()

    fun count(): Int = sp.all.size

    fun clear() {
        sp.edit().clear().apply()
        zaman.edit().clear().apply()
        adet = 0
    }

    /**
     * Anahtarına bakıp [bozuk] diyen kayıtları siler, silinen sayısını döner.
     * Bozuk bir OCR okumasının çevirisi hafızaya girdiyse kalıcı hasar veriyor:
     * aynı bozuk okuma her geldiğinde yanlış çeviri anında geri servis ediliyor.
     */
    fun purge(bozuk: (String) -> Boolean): Int {
        val silinecek = sp.all.keys.filter(bozuk)
        if (silinecek.isEmpty()) return 0
        val e = sp.edit()
        val z = zaman.edit()
        for (k in silinecek) { e.remove(k); z.remove(k) }
        e.apply()
        z.apply()
        adet = -1
        return silinecek.size
    }

    /** Tek bir kaydı siler (bozuk çeviri hafızada kalıcı hasar vermesin). */
    fun forget(english: String) {
        val k = key(english)
        if (sp.contains(k)) adet = -1
        sp.edit().remove(k).apply()
        zaman.edit().remove(k).apply()
    }

    private fun key(text: String): String =
        text.lowercase().filter { it.isLetterOrDigit() || it == ' ' }.trim().take(200)

    companion object {
        const val MAX_ENTRIES = 5000
        /**
         * 10.12: hızlı çeviri hafızasının sürümü. Çeviri kuralları değişince
         * artırılır; telefondaki eski (artık yanlış bilinen) taslaklar bir kez
         * silinir. 13 Kasım 10.11 kaydında düzeltilen satır, hafızadaki eski
         * çevirisiyle 7 ms'de yine yanlış çıktı. Yapay zekâ hafızasına dokunulmaz.
         */
        const val HIZLI_SURUM = 8 // invalidate cached captions from pre-204 quality policy
        /** Dolunca silinen en eski kayıt sayısı (%10). */
        const val BUDAMA = 500
        const val QUICK = "ceviri_hafizasi_v2"
        const val AI = "ceviri_hafizasi_yz"

        /** Her iki hafızayı da siler. */
        fun clearAll(context: Context) {
            TranslationStore(context, QUICK).clear()
            TranslationStore(context, AI).clear()
        }
    }
}
