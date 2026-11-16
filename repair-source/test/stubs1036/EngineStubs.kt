package com.example.altyazi
import android.content.Context
class Prefs(context: Context) { val googleCeviri = true }
object CaptureService { var running = false }
object MeaningSafety { fun risk(source: String, text: String): String? = null }
class FastTranslate {
  var lastError: String? = null
  suspend fun prepare(): Boolean = true
  suspend fun translate(s: String): String = "ML Kit sample: $s"
  fun close() {}
}
object GoogleCeviri { suspend fun cevir(s: String, kullanHafiza: Boolean): String = "Google sample: $s" }
class LlmTranslate(context: Context) {
  val backendName = "GPU · E4B"
  val lastError: String? = null
  fun hasModel(): Boolean = true
  suspend fun prepare(): Boolean = true
  suspend fun translate(line: String, previous: List<String>, contentType: String, title: String, discardIfStale: Boolean, stillWanted: () -> Boolean): String? = "E4B sample: $line"
}
