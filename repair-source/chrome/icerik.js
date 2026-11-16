// GameLingo Tarayıcı — sayfadaki video altyazısını metin olarak alır (ekran okuma yok),
// Chrome'un yerleşik çeviri modeliyle (Translator API, internetsiz) Türkçeye çevirir,
// çeviriyi İngilizce altyazının hemen altında koyu bir bantta gösterir.
(() => {
  "use strict";
  if (window.top !== window && !document.querySelector("video")) return; // reklam çerçevelerine girme

  const VARSAYILAN = { acik: true, boyut: 100, ingilizceGizle: false };
  let ayar = { ...VARSAYILAN };

  // Siteye göre altyazı yeri. Site adına değil sayfadaki öğelere bakılır.
  const KAYNAKLAR = [
    { ad: "YouTube", satir: ".caption-window .caption-visual-line", parca: ".ytp-caption-segment",
      pencere: ".caption-window", oynatici: "#movie_player, .html5-video-player" },
    { ad: "Netflix", satir: ".player-timedtext-text-container", parca: "span",
      pencere: ".player-timedtext-text-container", oynatici: ".watch-video--player-view, .watch-video, .nf-player-container" },
    { ad: "Prime Video", satir: ".atvwebplayersdk-captions-text", parca: null,
      pencere: ".atvwebplayersdk-captions-overlay", oynatici: ".webPlayerSDKContainer, .atvwebplayersdk-overlays-container" },
  ];

  // ---------------------------------------------------------------- çeviri
  const onbellek = new Map();
  let cevirmen = null;
  let durum = "baslamadi"; // baslamadi | hazirlaniyor | hazir | indirilmeli | desteklenmiyor | hata
  let durumMetni = "";

  function durumYaz(d, metin) {
    durum = d; durumMetni = metin || "";
    document.documentElement.setAttribute("data-gl-durum", d);
    try { chrome.storage.local.set({ glDurum: { d, metin: durumMetni, zaman: Date.now() } }); } catch (e) {}
  }

  async function hazirla(kullaniciTikladi) {
    if (cevirmen) return cevirmen;
    if (typeof self.Translator === "undefined" || !self.Translator) {
      durumYaz("desteklenmiyor", "Bu tarayıcıda yerleşik çeviri yok. Chrome 138 veya daha yenisi gerekli.");
      return null;
    }
    try {
      const uygun = await Translator.availability({ sourceLanguage: "en", targetLanguage: "tr" });
      if (uygun === "unavailable") {
        durumYaz("desteklenmiyor", "Chrome bu bilgisayarda İngilizce→Türkçe çeviriyi desteklemiyor.");
        return null;
      }
      durumYaz("hazirlaniyor", uygun === "available" ? "Çeviri açılıyor..." : "Türkçe çeviri modeli indiriliyor...");
      cevirmen = await Translator.create({
        sourceLanguage: "en", targetLanguage: "tr",
        monitor(m) {
          m.addEventListener("downloadprogress", (e) => {
            const y = Math.round((e.loaded || 0) * 100);
            durumYaz("hazirlaniyor", `Türkçe çeviri modeli indiriliyor %${y}`);
            bilgiGoster(`Türkçe çeviri modeli indiriliyor %${y} (yalnızca ilk sefer)`);
          });
        },
      });
      durumYaz("hazir", "Çeviri hazır ✓");
      return cevirmen;
    } catch (e) {
      cevirmen = null;
      if (!kullaniciTikladi && (e && (e.name === "NotAllowedError" || /activation|gesture/i.test(e.message || "")))) {
        durumYaz("indirilmeli", "Çeviri modeli için bir kez tıklamak gerekiyor.");
      } else {
        durumYaz("hata", `Çeviri açılamadı: ${(e && e.message) || e}`);
      }
      return null;
    }
  }

  function temizle(metin) {
    return metin
      .replace(/[♪♫]/g, "")
      .replace(/^\s*(>>|-)\s*/g, "")
      .replace(/\s*\n\s*/g, " ")
      .replace(/\s+/g, " ")
      .trim();
  }

  // Aynı anda tek çeviri; beklerken yeni satır gelirse eskisi atlanır, hep en yenisi çevrilir.
  let calisiyor = false, bekleyen = null;
  async function cevir(kaynak, bitince) {
    const anahtar = kaynak.toLowerCase();
    if (onbellek.has(anahtar)) { bitince(kaynak, onbellek.get(anahtar)); return; }
    bekleyen = { kaynak, bitince };
    if (calisiyor) return;
    calisiyor = true;
    try {
      while (bekleyen) {
        const is = bekleyen; bekleyen = null;
        const c = await hazirla(false);
        if (!c) break;
        let sonuc;
        try {
          sonuc = await c.translate(is.kaynak);
        } catch (e) {
          cevirmen = null; // bozulduysa bir dahakine yeniden açılır
          durumYaz("hata", `Çeviri hatası: ${(e && e.message) || e}`);
          continue;
        }
        sonuc = (sonuc || "").trim();
        onbellek.set(is.kaynak.toLowerCase(), sonuc);
        if (onbellek.size > 800) onbellek.delete(onbellek.keys().next().value);
        is.bitince(is.kaynak, sonuc);
      }
    } finally {
      calisiyor = false;
    }
  }

  // ---------------------------------------------------------------- altyazıyı okuma
  function gorunur(el) {
    if (!el) return false;
    const r = el.getBoundingClientRect();
    if (r.width === 0 || r.height === 0) return false;
    const s = getComputedStyle(el);
    return s.visibility !== "hidden" && s.display !== "none";
  }

  function dokumandanOku() {
    for (const k of KAYNAKLAR) {
      const satirlar = [...document.querySelectorAll(k.satir)];
      if (!satirlar.length) continue;
      const metin = satirlar
        .map((s) => (k.parca ? [...s.querySelectorAll(k.parca)].map((p) => p.textContent).join("") : s.textContent))
        .map((t) => t.trim()).filter(Boolean).join("\n");
      if (!metin) continue;
      const pencereler = [...document.querySelectorAll(k.pencere)].filter(
        (p) => gorunur(p) || p.classList.contains("gl-ingilizce-gizli"));
      return { kaynak: k, metin, pencereler, oynatici: document.querySelector(k.oynatici) };
    }
    return null;
  }

  // Diğer siteler: videonun kendi altyazı izi (WebVTT)
  function izdenOku() {
    for (const v of document.querySelectorAll("video")) {
      for (const iz of v.textTracks || []) {
        if (iz.mode === "disabled" || !(iz.kind === "subtitles" || iz.kind === "captions")) continue;
        const etkin = iz.activeCues ? [...iz.activeCues] : [];
        const metin = etkin.map((c) => (c.text || "").replace(/<[^>]+>/g, "")).join("\n").trim();
        if (metin) return { kaynak: { ad: "video izi" }, metin, pencereler: [], oynatici: null, video: v };
      }
    }
    return null;
  }

  // ---------------------------------------------------------------- gösterme
  let kutu = null;
  function kutuyuHazirla(kap, sabit) {
    if (!kutu) {
      kutu = document.createElement("div");
      kutu.id = "gl-ceviri";
      kutu.addEventListener("click", async () => {
        if (!kutu.classList.contains("gl-dugme")) return;
        kutu.classList.remove("gl-dugme");
        bilgiGoster("Türkçe çeviri modeli hazırlanıyor...");
        const c = await hazirla(true);
        if (c) { sonGosterilen = ""; sonKaynak = ""; bilgiGizle(); }
        else bilgiGoster(durumMetni || "Çeviri açılamadı");
      });
    }
    if (kutu.parentElement !== kap) kap.appendChild(kutu);
    kutu.classList.toggle("gl-sabit", !!sabit);
    return kutu;
  }

  let bilgiZaman = 0;
  function bilgiGoster(metin, dugme) {
    const hedef = dokumandanOku() || izdenOku();
    const kap = (hedef && hedef.oynatici) || document.fullscreenElement || document.body;
    const k = kutuyuHazirla(kap, kap === document.body);
    k.textContent = metin;
    k.classList.add("gl-gorunur");
    k.classList.toggle("gl-dugme", !!dugme);
    k.classList.toggle("gl-bilgi", !dugme);
    k.style.top = ""; k.style.bottom = "12%";
    bilgiZaman = Date.now();
  }
  function bilgiGizle() {
    if (kutu) kutu.classList.remove("gl-bilgi", "gl-dugme", "gl-gorunur");
  }

  function yerlestir(hedef) {
    const kap = hedef.oynatici || document.fullscreenElement || document.body;
    const sabit = kap === document.body;
    const k = kutuyuHazirla(kap, sabit);
    const kr = sabit ? { top: 0, left: 0, width: innerWidth, height: innerHeight } : kap.getBoundingClientRect();
    // yazı boyu oynatıcıya göre (tam ekranda büyür)
    const yazi = Math.max(14, Math.min(42, kr.height * 0.042)) * (ayar.boyut / 100);
    k.style.fontSize = `${yazi.toFixed(1)}px`;
    k.style.maxWidth = `${Math.round(kr.width * 0.88)}px`;

    if (!hedef.pencereler.length && hedef.video) {
      const vr = hedef.video.getBoundingClientRect();
      k.style.left = `${vr.left + vr.width / 2 - (sabit ? 0 : kr.left)}px`;
      k.style.bottom = ""; k.style.top = `${vr.bottom - (sabit ? 0 : kr.top) - k.offsetHeight - vr.height * 0.04}px`;
      return;
    }
    if (!hedef.pencereler.length) { k.style.left = "50%"; k.style.top = ""; k.style.bottom = "12%"; return; }

    let ust = Infinity, alt = -Infinity, sol = Infinity, sag = -Infinity;
    for (const p of hedef.pencereler) {
      const r = p.getBoundingClientRect();
      ust = Math.min(ust, r.top); alt = Math.max(alt, r.bottom); sol = Math.min(sol, r.left); sag = Math.max(sag, r.right);
    }
    const ortaX = (sol + sag) / 2 - (sabit ? 0 : kr.left);
    k.style.left = `${ortaX}px`;
    k.style.bottom = "";
    if (ayar.ingilizceGizle) {
      // İngilizce gizliyse çeviri onun yerine geçer
      k.style.top = `${ust - (sabit ? 0 : kr.top)}px`;
      return;
    }
    const bosluk = 4;
    let y = alt - (sabit ? 0 : kr.top) + bosluk;            // İngilizcenin hemen altı
    if (y + k.offsetHeight > kr.height - 2) {
      y = ust - (sabit ? 0 : kr.top) - k.offsetHeight - bosluk; // sığmazsa üstü
    }
    k.style.top = `${Math.max(0, y)}px`;
  }

  // ---------------------------------------------------------------- ana döngü
  let sonKaynak = "", sonGosterilen = "", gizliPencereler = [];

  function ingilizceyiAyarla(hedef) {
    for (const p of gizliPencereler) if (!hedef || !hedef.pencereler.includes(p)) p.classList.remove("gl-ingilizce-gizli");
    gizliPencereler = [];
    if (ayar.acik && ayar.ingilizceGizle && hedef) {
      for (const p of hedef.pencereler) { p.classList.add("gl-ingilizce-gizli"); gizliPencereler.push(p); }
    }
  }

  function tara() {
    if (!ayar.acik) { if (kutu) kutu.classList.remove("gl-gorunur"); ingilizceyiAyarla(null); return; }
    const hedef = dokumandanOku() || izdenOku();
    ingilizceyiAyarla(hedef);
    if (kutu && (kutu.classList.contains("gl-dugme") || kutu.classList.contains("gl-bilgi"))) {
      if (kutu.classList.contains("gl-dugme") || Date.now() - bilgiZaman < 4000) return;
      bilgiGizle();
    }
    if (!hedef) {
      if (kutu && kutu.classList.contains("gl-gorunur")) kutu.classList.remove("gl-gorunur");
      sonKaynak = ""; sonGosterilen = "";
      return;
    }
    // Model ilk sefer tıklama istiyorsa ya da tarayıcı desteklemiyorsa: altyazı varken bunu göster
    if (durum === "indirilmeli") { bilgiGoster("▶ Türkçe çeviri için buraya bir kez tıklayın", true); return; }
    if (durum === "desteklenmiyor") {
      if (Date.now() - bilgiZaman > 30000) bilgiGoster(durumMetni);
      return;
    }
    const kaynak = temizle(hedef.metin);
    if (!kaynak) return;
    if (kaynak !== sonKaynak) {
      sonKaynak = kaynak;
      cevir(kaynak, (k, tr) => {
        if (k !== sonKaynak || !tr) return; // geç kaldı: ekranda artık başka satır var
        sonGosterilen = tr;
        const h = dokumandanOku() || izdenOku();
        if (!h) return;
        const el = kutuyuHazirla(h.oynatici || document.fullscreenElement || document.body,
          !(h.oynatici || document.fullscreenElement));
        el.textContent = tr;
        el.classList.remove("gl-bilgi", "gl-dugme");
        el.classList.add("gl-gorunur");
        yerlestir(h);
      });
    } else if (sonGosterilen && kutu && kutu.classList.contains("gl-gorunur")) {
      yerlestir(hedef); // oynatıcı büyüdü/küçüldü, altyazı kaydı
    }
  }

  // ---------------------------------------------------------------- ayarlar ve başlatma
  function ayarlariUygula(yeni) {
    ayar = { ...VARSAYILAN, ...(yeni || {}) };
    sonKaynak = ""; // yeniden yerleşsin
  }
  try {
    chrome.storage.sync.get(VARSAYILAN, ayarlariUygula);
    chrome.storage.onChanged.addListener((d, alan) => {
      if (alan !== "sync") return;
      const yeni = { ...ayar };
      for (const [k, v] of Object.entries(d)) yeni[k] = v.newValue;
      ayarlariUygula(yeni);
    });
  } catch (e) { /* test sayfası */ }

  document.addEventListener("fullscreenchange", () => { sonKaynak = ""; });
  setInterval(tara, 120);

})();
