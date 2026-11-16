package com.example.altyazi
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.nio.file.Files

fun main() = runBlocking {
  val dir = Files.createTempDirectory("gl1036").toFile()
  val ctx = Context(dir)
  val reportsDuring = mutableListOf<String>()
  val final = EngineBenchmark1035.run(ctx) { state ->
      val report = EngineBenchmark1035.lastReport(ctx)
      check(report != null) { "Missing report during '$state'" }
      reportsDuring += report
  }
  check(final.contains("DURUM: TAMAMLANDI")) { final.take(700) }
  check(final.contains("ML Kit: 11/11")) { final.takeLast(500) }
  check(final.contains("Google: 11/11")) { final.takeLast(500) }
  check(final.contains("GPU · E4B: 11/11")) { final.takeLast(500) }
  check(reportsDuring.first().contains("DURUM: Test başladı"))
  check(!EngineBenchmark1035.running)
  println("OK: initial report, 33 translations, incremental report and completion")

  try {
    EngineBenchmark1035.run(ctx) { state ->
      if (state.contains("Google: 2/11")) throw CancellationException("simulated user cancellation")
    }
    error("should throw")
  } catch (_: CancellationException) {}
  check(!EngineBenchmark1035.running)
  check(EngineBenchmark1035.lastReport(ctx)?.contains("İPTAL EDİLDİ") == true)
  println("OK: interrupted test retains partial report")

  CaptureService.running = true
  val blocked = EngineBenchmark1035.run(ctx) { }
  check(blocked.contains("DURDURULDU"))
  check(!EngineBenchmark1035.running)
  CaptureService.running = false
  println("OK: active live capture blocks benchmark")
}
