#!/usr/bin/env python3
"""10.18: Üretim kodundaki Google yolunu ve ad-kalkanını Kotlin ile gerçekten çalıştırır.
Android SDK gerektirmeyen dar test; tam APK/cihaz testi DEĞİLDİR.
"""
from pathlib import Path
import subprocess, tempfile, sys, re
root = Path(__file__).resolve().parents[1]
src = (root/'app/src/main/java/com/example/altyazi/OfflineFix.kt').read_text()
name_start = src.index('object NameShield {')
name_end = src.index('/** Uygulama genelinde tek örnek. */', name_start)
name = src[name_start:name_end]
preparation = (root/'app/src/main/java/com/example/altyazi/SubtitlePreparation.kt').read_text()
google_prep = preparation[preparation.index('object GoogleSourcePreparation {'):]

method_start = src.index('    private suspend fun mlTranslate(text: String): String {')
method_end = src.index('    /**\n     * Virgülle ayrılmış TEK PARÇA hitaplar', method_start)
method = src[method_start:method_end]
assert 'val k = NameShield.shield(GoogleSourcePreparation.prepare(text))' in method
assert 'SlangNormalizer.normalize(Slang.normalize(GoogleSourcePreparation.prepare(text)))' in method
assert not re.search(r'val k = NameShield.shield\(Slang', method)
kotlin = r'''
package com.example.altyazi
import kotlin.coroutines.*
object Sozluk { const val hazir = true; fun icinde(w: String) = w != "mark" }
object AdDefteri { fun adMi(w: String) = false }
object ProperNameSequence1039 { fun protectedStarts(s: String): Set<Int> = emptySet() }
''' + name + google_prep + r'''
object Slang { fun normalize(s: String) = "SLANG:" + s }
object SlangNormalizer { fun normalize(s: String) = "NORM:" + s }
object ProfanityShield {
  class Shield(val text: String, val tails: List<String>) { val slots = emptyList<String>() }
  fun shield(s: String) = Shield(s, if (s.contains("fuck")) listOf("amına koyayım") else emptyList())
  fun addTails(s: String, tails: List<String>) = if (tails.isNotEmpty()) "$s, ${tails[0]}" else s
  fun restore(s: String, slots: List<String>, tr: java.util.Locale): String? = s
  fun unshield(s: String, slots: List<String>) = s
}
class TestEngine {
    val tr = java.util.Locale("tr")
    var gucluFn: (suspend (String) -> String?)? = null
    var fallbackCount = 0
    private suspend fun base(s: String): String { fallbackCount++; return "YEDEK:" + s }
    private fun fix(s: String) = s
''' + method.replace('    private suspend fun mlTranslate(', '    suspend fun mlTranslate(').replace('kotlinx.coroutines.CancellationException', 'java.util.concurrent.CancellationException') + r'''
}
fun <T> call(block: suspend () -> T): T {
  var result: Result<T>? = null
  block.startCoroutine(object : Continuation<T> {
    override val context = EmptyCoroutineContext
    override fun resumeWith(r: Result<T>) { result = r }
  })
  return checkNotNull(result).getOrThrow()
}
fun main() {
  val t = TestEngine()
  var seen = ""
  t.gucluFn = { source -> seen = source; "Doğru Türkçe" }
  check(call { t.mlTranslate("Where the fuck you at?") } == "Doğru Türkçe, amına koyayım")
  check(seen == "Where the fuck you at?") { "Google'a küfür/argo değiştirilerek gidiyor: $seen" }
  check(t.fallbackCount == 0)

  t.gucluFn = { source -> seen = source; "Qx1 bir süper kahraman." }
  check(call { t.mlTranslate("Mark is a superhero.") } == "Mark bir süper kahraman.")
  check(seen == "Qx1 is a superhero.") { "Özel ad korunmadı: $seen" }

  t.gucluFn = { source -> seen = source; "Ben de geliyorum" }
  check(call { t.mlTranslate("I'm coming, too.") } == "Ben de geliyorum")
  check(seen == "I'm coming as well.") { "too yanlış hazırlandı: $seen" }

  t.gucluFn = { null }
  check(call { t.mlTranslate("I'm coming, too.") } == "YEDEK:NORM:SLANG:I'm coming as well.")
  val backup = call { t.mlTranslate("This is fucked up!") }
  check(backup == "YEDEK:NORM:SLANG:This is fucked up!, amına koyayım") { "Yedek yol bozuldu: $backup" }

  t.gucluFn = { "İsim kayboldu" }
  val missing = call { t.mlTranslate("Mark is a superhero.") }
  check(missing.startsWith("YEDEK:")) { "Google özel adı kaybettiğinde yedek kullanılmadı: $missing" }

  println("Kotlin testleri geçti: ham Google girişi, küfür korunumu, özel ad geri koyma, iki yedek senaryosu.")
}
'''
with tempfile.TemporaryDirectory() as td:
    p = Path(td)/'GoogleBranch.kt'; p.write_text(kotlin)
    jar = Path(td)/'google-test.jar'
    # Extract a minimal engine to exercise the actual strong-engine path.
    # Compile the real quality gate rather than faking its semantic warning.
    sources = [str(p),
        str(root/'app/src/main/java/com/example/altyazi/MeaningSafety.kt'),
        str(root/'app/src/main/java/com/example/altyazi/TranslationQualityGate.kt')]
    c = subprocess.run(['kotlinc',*sources,'-include-runtime','-d',str(jar)],capture_output=True,text=True,timeout=80)
    if c.returncode:
        print(c.stdout+c.stderr);sys.exit(c.returncode)
    r = subprocess.run(['java','-jar',str(jar)],capture_output=True,text=True,timeout=20)
    print(r.stdout+r.stderr)
    sys.exit(r.returncode)
