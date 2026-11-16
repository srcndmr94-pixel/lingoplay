// Test: Chrome'un çeviri modeli yerine sahte çevirmen (içerik betiğinin dünyasında)
(() => {
  const mod = new URLSearchParams(location.search).get("mod") || "hazir";
  let indirildi = false, sayac = 0, tiklandi = 0;
  // Test aracı her komutu "kullanıcı hareketi" sayıyor; gerçek tıklamayı ayrıca izle
  window.addEventListener("click", () => { tiklandi = Date.now(); }, true);
  const yaz = () => document.documentElement.setAttribute("data-gl-cagri", String(sayac));
  if (mod === "yok") { self.Translator = null; return; }
  self.Translator = {
    availability: async () => (mod === "indir" && !indirildi ? "downloadable" : "available"),
    create: async (o) => {
      if (mod === "indir" && !indirildi) {
        if (Date.now() - tiklandi > 3000) { const e = new Error("Requires user activation"); e.name = "NotAllowedError"; throw e; }
        if (o.monitor) o.monitor({ addEventListener: (n, f) => f({ loaded: 0.5 }) });
        indirildi = true;
      }
      return { translate: async (t) => { sayac++; yaz(); await new Promise((r) => setTimeout(r, 60)); return "TR: " + t; } };
    },
  };
  yaz();
})();
