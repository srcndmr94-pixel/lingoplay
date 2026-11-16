// Açılır pencere: ayarlar (chrome.storage.sync) ve çeviri modelini önceden indirme.
const VARSAYILAN = { acik: true, boyut: 100, ingilizceGizle: false };
const $ = (id) => document.getElementById(id);

function durumGoster(metin, tur) {
  const d = $("durum");
  d.textContent = metin;
  d.className = tur || "";
}

chrome.storage.sync.get(VARSAYILAN, (a) => {
  $("acik").checked = a.acik;
  $("ingilizceGizle").checked = a.ingilizceGizle;
  $("boyut").value = a.boyut;
  $("boyutDeger").textContent = `%${a.boyut}`;
});
$("acik").addEventListener("change", (e) => chrome.storage.sync.set({ acik: e.target.checked }));
$("ingilizceGizle").addEventListener("change", (e) => chrome.storage.sync.set({ ingilizceGizle: e.target.checked }));
$("boyut").addEventListener("input", (e) => {
  $("boyutDeger").textContent = `%${e.target.value}`;
  chrome.storage.sync.set({ boyut: Number(e.target.value) });
});

async function modelDurumu() {
  if (!("Translator" in self)) {
    durumGoster("Bu Chrome'da yerleşik çeviri yok. Chrome'u güncelleyin (138 veya üstü).", "kotu");
    $("hazirla").disabled = true;
    return;
  }
  try {
    const u = await Translator.availability({ sourceLanguage: "en", targetLanguage: "tr" });
    if (u === "available") { durumGoster("Türkçe çeviri modeli hazır ✓", "iyi"); $("hazirla").disabled = true; }
    else if (u === "unavailable") { durumGoster("Chrome bu bilgisayarda İngilizce→Türkçe çeviriyi desteklemiyor.", "kotu"); $("hazirla").disabled = true; }
    else durumGoster("Türkçe çeviri modeli henüz indirilmedi. Aşağıdaki düğmeye basın (bir kez).");
  } catch (e) {
    durumGoster(`Durum alınamadı: ${e.message || e}`, "kotu");
  }
}

$("hazirla").addEventListener("click", async () => {
  $("hazirla").disabled = true;
  durumGoster("Türkçe çeviri modeli indiriliyor...");
  try {
    const t = await Translator.create({
      sourceLanguage: "en", targetLanguage: "tr",
      monitor(m) {
        m.addEventListener("downloadprogress", (e) =>
          durumGoster(`Türkçe çeviri modeli indiriliyor %${Math.round((e.loaded || 0) * 100)}`));
      },
    });
    const ornek = await t.translate("Where are you going?");
    durumGoster(`Hazır ✓ Örnek: "${ornek}"`, "iyi");
  } catch (e) {
    durumGoster(`İndirilemedi: ${e.message || e}`, "kotu");
    $("hazirla").disabled = false;
  }
});

function pcDurumu() {
  chrome.storage.local.get({ glKopru: null }, ({ glKopru }) => {
    const el = $("pc");
    if (glKopru && glKopru.bagli && Date.now() - glKopru.zaman < 10 * 60 * 1000) {
      el.textContent = glKopru.not ? `Oyun çevirisi: PC programı bağlı, ama: ${glKopru.not}` : "Oyun çevirisi: PC programı bağlı ✓";
      el.className = glKopru.not ? "kotu" : "iyi";
    } else {
      el.textContent = "Oyun çevirisi: PC programı açık değil (PC'de 'Chrome çevirisi' seçilince bağlanır).";
      el.className = "";
    }
  });
}

modelDurumu();
pcDurumu();
setInterval(pcDurumu, 1500);
