package com.example.altyazi

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Uygulama çökerse sebebini telefonun içine yazar.
 * Böylece bilgisayar ve logcat olmadan hatayı görebiliyoruz.
 */
object CrashLog {

    private const val FILE_NAME = "son_hata.txt"

    private fun appVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: Throwable) { "?" }

    /**
     * APK'nın kurulma zamanı. Her yeni kurulumda değişir; sürüm adı aynı kalsa
     * bile değişir. Eski hatanın ne zaman silineceğini buna bakarak karar
     * veriyoruz: sürüm adına bakmak yetmiyordu, çünkü sürüm adı güncellemeler
     * arasında çoğu zaman aynı kalıyor ve bir kez düşen hata kaydı sonsuza
     * kadar gösteriliyordu.
     */
    private fun installStamp(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
    } catch (_: Throwable) { "?" }

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                write(app, error, thread.name)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Çökmeye yol açmayan ama işi bozan hatalar için. */
    fun note(context: Context, where: String, error: Throwable) {
        try {
            write(context.applicationContext, error, where)
        } catch (_: Throwable) {
        }
    }

    private fun write(context: Context, error: Throwable, where: String) {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        val stamp = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val text = buildString {
            append("Tarih: ").append(stamp).append('\n')
            append("Sürüm: ").append(appVersion(context)).append('\n')
            append("Kurulum: ").append(installStamp(context)).append('\n')
            append("Yer: ").append(where).append('\n')
            append("Android: ").append(android.os.Build.VERSION.SDK_INT)
            append(" / ").append(android.os.Build.MODEL).append('\n')
            append('\n')
            append(sw.toString())
        }
        File(context.filesDir, FILE_NAME).writeText(text.take(8000))
    }

    fun read(context: Context): String? {
        val f = File(context.applicationContext.filesDir, FILE_NAME)
        if (!f.exists()) return null
        return try {
            val text = f.readText().ifBlank { null } ?: return null
            // Önceki KURULUMDAN kalan hata (güncellemeyle düzelmiş olabilir) gösterilmez, silinir.
            //
            // Eskiden yalnızca sürüm ADINA bakılıyordu. Sürüm adı güncellemeler
            // arasında çoğu zaman aynı kaldığı için bir kez düşen hata kaydı
            // hiç silinmiyor, her açılışta aynı eski hata gösteriliyordu.
            // Kurulum zamanı her yüklemede değiştiği için bu sorun kalmıyor.
            val kurulum = Regex("(?m)^Kurulum: (.+)$").find(text)?.groupValues?.get(1)?.trim()
            val ver = Regex("(?m)^Sürüm: (.+)$").find(text)?.groupValues?.get(1)?.trim()
            if (kurulum == null || kurulum != installStamp(context) || ver != appVersion(context)) {
                f.delete()
                return null
            }
            text
        } catch (_: Throwable) {
            null
        }
    }

    fun clear(context: Context) {
        try {
            File(context.applicationContext.filesDir, FILE_NAME).delete()
        } catch (_: Throwable) {
        }
    }
}
