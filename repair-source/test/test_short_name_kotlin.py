#!/usr/bin/env python3
"""Compile production AdDefteri + NameShield in actual kotlinc with a dictionary stub."""
from pathlib import Path
import subprocess, tempfile, sys
base = Path(__file__).resolve().parents[1]/'app/src/main/java/com/example/altyazi'
s = (base/'OfflineFix.kt').read_text()
start=s.index('object AdDefteri {')
end=s.index('/** Uygulama genelinde tek örnek. */',start)
code=r'''package com.example.altyazi
object Sozluk { const val hazir = true; fun icinde(w: String) = w in setOf("art","wait","hey","thanks","my","paul","pout","great","on","is","good","i","love","he") }
'''+s[start:end]+r'''
fun main() {
  AdDefteri.temizle()
  val untrusted = NameShield.shield("Art is valuable.")
  check(untrusted.slots.none { it.ad == "Art" }) { "İsim kanıtı yokken 'Art' isim sayıldı" }
  AdDefteri.ogren("Wait, Art.")
  check(AdDefteri.adMi("art")) { "Seslenme özel ad olarak öğrenilmedi" }
  val k = NameShield.shield("Wait, Art.")
  check(k.slots.any { it.ad == "Art" }) { "Üç harfli ad korunmadı: ${k.text}" }
  check(NameShield.restore(k.text, k.slots) == "Wait, Art.")
  val common = NameShield.shield("I love art.")
  check(common.slots.none { it.ad == "art" })
  AdDefteri.temizle()
  check(!AdDefteri.adMi("art"))
  println("OK: 6 gerçek Kotlin ad-koruma kontrolü")
}
'''
with tempfile.TemporaryDirectory() as td:
    p=Path(td)/'Main.kt';p.write_text(code)
    jar=Path(td)/'run.jar'
    r=subprocess.run(['kotlinc',str(p),str(base/'ProperNameSequence1039.kt'),'-include-runtime','-d',str(jar)],capture_output=True,text=True,timeout=65)
    if r.returncode: print(r.stdout+r.stderr);sys.exit(r.returncode)
    r=subprocess.run(['java','-jar',str(jar)],capture_output=True,text=True,timeout=15)
    print(r.stdout+r.stderr)
    sys.exit(r.returncode)
