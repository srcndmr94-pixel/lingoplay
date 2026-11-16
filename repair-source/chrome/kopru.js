// GameLingo PC programına çeviri: program 127.0.0.1:47330'da bekler; bu sayfa "iş var mı?" diye sorar,
// gelen oyun satırını Chrome'un yerleşik çeviri modeliyle çevirip sonucu geri yollar.
const ADRES = "http://127.0.0.1:47330";
const BASLIK = { "X-GameLingo": "1" };
let cevirmen = null;

const bekle = (ms) => new Promise((r) => setTimeout(r, ms));

function durum(bagli, not) {
  try { chrome.runtime.sendMessage({ glKopru: { bagli, not: not || "" } }); } catch (e) {}
}

async function cevirmenAl() {
  if (cevirmen) return cevirmen;
  if (typeof self.Translator === "undefined" || !self.Translator) {
    throw new Error("Bu Chrome'da yerleşik çeviri yok (Chrome 138 veya üstü gerekli)");
  }
  const u = await Translator.availability({ sourceLanguage: "en", targetLanguage: "tr" });
  if (u !== "available") {
    throw new Error("Chrome'un Türkçe çeviri modeli hazır değil: eklenti simgesine tıklayıp 'Türkçe çeviri modelini hazırla'ya basın");
  }
  cevirmen = await Translator.create({ sourceLanguage: "en", targetLanguage: "tr" });
  return cevirmen;
}

async function dongu() {
  let sonBagli = null;
  for (;;) {
    let yanit;
    try {
      yanit = await fetch(ADRES + "/is", { headers: BASLIK, cache: "no-store" });
    } catch (e) {
      // PC programı kapalı: sessizce bekle, sonra yeniden dene
      if (sonBagli !== false) { sonBagli = false; durum(false); }
      await bekle(2500);
      continue;
    }
    if (sonBagli !== true) { sonBagli = true; durum(true); }
    if (yanit.status === 204) continue;          // iş yok, hemen yeniden sor
    if (yanit.status !== 200) { await bekle(1000); continue; }
    let govde;
    try {
      const is_ = await yanit.json();
      try {
        const c = await cevirmenAl();
        govde = { id: is_.id, ceviri: await c.translate(is_.metin) };
      } catch (e) {
        cevirmen = null;
        govde = { id: is_.id, hata: String((e && e.message) || e) };
        durum(true, govde.hata);
      }
      await fetch(ADRES + "/sonuc", {
        method: "POST",
        headers: { ...BASLIK, "Content-Type": "application/json" },
        body: JSON.stringify(govde),
      });
    } catch (e) {
      await bekle(500);
    }
  }
}

dongu();
