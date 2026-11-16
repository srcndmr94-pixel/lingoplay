#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Dayanik.java sarmalayicisi — kalip dayanikliligi.

Iki calisma ani arizasini arar:
  1) BOS ESLESME + LAMBDA: normalize() icindeki "m.value.first()" bos
     eslesmede NoSuchElementException atar ve ceviri ortasinda COKER.
  2) YAVASLAMA/KILITLENME: bozuk uzun OCR satirinda geri izleme.
     Her kalip icin kalibin KENDI harflerinden saldirgan girdi uretilir.
"""
import os
import shutil
import subprocess
import sys
import tempfile

KOK = os.path.dirname(os.path.abspath(__file__))
KAYNAK = os.path.join(KOK, "..", "app", "src", "main", "java", "com", "example", "altyazi")
JAVA = os.path.join(KOK, "Dayanik.java")

if not os.path.exists(JAVA):
    print("BAŞARISIZ: eksik dosya " + JAVA)
    sys.exit(1)

if not shutil.which("javac") or not shutil.which("java"):
    print("ATLANDI: JDK yok (javac/java bulunamadı).")
    sys.exit(0)

gecici = tempfile.mkdtemp(prefix="dayanik")
try:
    d = subprocess.run(["javac", "-encoding", "UTF-8", "-d", gecici, JAVA],
                       capture_output=True, text=True)
    if d.returncode:
        print("BAŞARISIZ: Dayanik.java derlenmedi\n" + d.stderr)
        sys.exit(1)
    ortam = dict(os.environ, JAVA_TOOL_OPTIONS="")
    s = subprocess.run(["java", "-cp", gecici, "-Dfile.encoding=UTF-8",
                        "-Dstdout.encoding=UTF-8", "Dayanik", KAYNAK],
                       capture_output=True, text=True, env=ortam, timeout=600)
    print((s.stdout + s.stderr).strip())
    if s.returncode:
        sys.exit(1)
finally:
    shutil.rmtree(gecici, ignore_errors=True)
