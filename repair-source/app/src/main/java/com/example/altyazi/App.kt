package com.example.altyazi

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        // İngilizce sözlük (OCR onarımı için) arka planda yüklensin: çeviri
        // başladığında hazır olsun. Yüklenene kadar onarım metne dokunmaz.
        Sozluk.yukle(this)
    }
}
