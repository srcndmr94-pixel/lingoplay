// Arka plan: GameLingo PC programına çeviri sağlayan görünmez sayfayı (kopru.html) açık tutar.
const KOPRU = "kopru.html";

async function kopruyuAc() {
  try {
    if (await chrome.offscreen.hasDocument()) return;
    await chrome.offscreen.createDocument({
      url: KOPRU,
      reasons: ["WORKERS"],
      justification: "GameLingo PC programının oyun altyazılarını Chrome'un yerleşik çeviri modeliyle çevirmek",
    });
  } catch (e) {
    if (!String(e && e.message).includes("single offscreen")) console.warn("GameLingo köprü:", e);
  }
}

chrome.runtime.onStartup.addListener(kopruyuAc);
chrome.runtime.onInstalled.addListener(kopruyuAc);
chrome.alarms.create("gl-kopru", { periodInMinutes: 1 });
chrome.alarms.onAlarm.addListener((a) => { if (a.name === "gl-kopru") kopruyuAc(); });

// Görünmez sayfada yalnızca chrome.runtime var; bağlantı durumunu buradan kaydederiz (açılır pencere gösterir).
chrome.runtime.onMessage.addListener((mesaj) => {
  if (mesaj && mesaj.glKopru) chrome.storage.local.set({ glKopru: { ...mesaj.glKopru, zaman: Date.now() } });
});

kopruyuAc();
