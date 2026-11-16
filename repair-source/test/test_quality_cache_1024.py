"""Real OfflineFix methods extracted verbatim and compiled with Kotlin stubs.
Tests Google-selected fallback doesn't stick in cache after a 429.
Not an Android APK test.
"""
from pathlib import Path
import subprocess, tempfile, sys
root = Path(__file__).resolve().parents[1]
src = (root / 'app/src/main/java/com/example/altyazi/OfflineFix.kt').read_text()
def method(prefix):
    start=src.index(prefix)
    brace=src.index('{',start)
    depth=0
    for i in range(brace,len(src)):
        if src[i]=='{': depth+=1
        if src[i]=='}':
            depth-=1
            if depth==0: return src[start:i+1]
    raise ValueError(prefix)
run=method('private suspend fun cachedRun(text: String)')
single=method('private suspend fun cached(sentence: String)')
# Original functions, no mock replacement of the cache policy.
code='''package com.example.altyazi
import kotlin.coroutines.*
class Fake {
  val cache = LinkedHashMap<String,String>()
  var gucluFn: (suspend (String) -> String?)? = null
  var calls=0
  private suspend fun mlTranslate(s: String): String { calls++; return "answer-$calls" }
  private suspend fun translateSentence(s: String): String = mlTranslate(s)
''' + run + '\n' + single + '''
  suspend fun run(s: String) = cachedRun(s)
  suspend fun one(s: String) = cached(s)
}
fun <T> call(block: suspend () -> T): T {
  var out: Result<T>? = null
  block.startCoroutine(object : Continuation<T> {
    override val context = EmptyCoroutineContext
    override fun resumeWith(result: Result<T>) { out = result }
  })
  return checkNotNull(out).getOrThrow()
}
fun main() {
 val v=Fake()
 // Non-Google path still caches successful offline translations.
 check(call { v.run("A") } == "answer-1")
 check(call { v.run("A") } == "answer-1")
 check(v.calls==1)
 v.cache.clear()
 v.gucluFn = { null } // Google configured but blocked / timeout
 check(call { v.run("B") } == "answer-2")
 check(call { v.run("B") } == "answer-3")
 check(call { v.one("C") } == "answer-4")
 check(call { v.one("C") } == "answer-5")
 check(v.cache.isEmpty())
 println("OK: real cachedRun/cached Kotlin methods: fallback not persisted, offline cache intact")
}
'''
with tempfile.TemporaryDirectory() as td:
    kt=Path(td)/'CacheSmoke.kt'; kt.write_text(code)
    jar=Path(td)/'smoke.jar'
    cmd=['kotlinc',str(kt),'-include-runtime','-d',str(jar)]
    r=subprocess.run(cmd,text=True,capture_output=True,timeout=60)
    if r.returncode: print(r.stderr); sys.exit(r.returncode)
    r=subprocess.run(['java','-jar',str(jar)],text=True,capture_output=True,timeout=15)
    print(r.stdout+r.stderr)
    sys.exit(r.returncode)
