package android.os
object SystemClock { fun elapsedRealtime(): Long = System.nanoTime()/1_000_000L }
object Build { val MODEL = "Fake Pixel"; object VERSION { val RELEASE = "17" } }
