"""LingoPlay doğrulama paketi. Testler bağımsız betiklerdir; unittest discover kullanmayın.

STATİK DENETİMLER DE BURADA. Önceden kotlin_denetle.py, kotlin_cagri.py ve
kaynak_denetle.py elle çalıştırılıyordu; yani atlanabiliyorlardı. Kotlin
derleyicisi bu ortamda olmadığı için bu üç betik derleyicinin yerini tutan
tek şey — atlanmamaları gerekiyor.
"""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parent
TESTS = [
    "test_512.py", "test_ad_eki.py", "test_arayuz.py", "test_bitisik.py", "test_bozuk.py",
    "test_dongu.py", "test_ekran.py", "test_filtre.py", "test_harf.py", "test_hata.py",
    "test_hata_eslesme.py", "test_hata_kaydi.py", "test_hiz.py", "test_isim_hud.py", "test_metin.py",
    "test_model_alani.py", "test_pc.py", "test_pc_ocr.py", "test_ses_etiketi.py", "test_sozluk.py", "test_taslak_akisi.py",
    "test_telefon_kopru.py", "test_turkce.py", "test_yerel_ceviri.py", "test_winocr.py",
    "test_denetleyici.py", "test_cagri_denetimi.py", "test_argo.py", "test_cop_okuma.py", "test_yigin.py", "test_yz_denetim.py", "test_yz_tetik.py",
    "test_bosluk_taramasi.py", "test_yol_kapsamasi.py", "test_tezgah.py", "test_katman.py", "test_talimat.py", "test_kurtarma.py", "test_etiket_sirasi.py", "test_kutup.py", "test_kalip.py", "test_kufur_siniri.py", "test_ayna.py", "test_unicode.py", "test_cift_kayit.py", "test_sablon.py", "test_dayanik.py", "test_tarama.py", "test_yalniz_dogru.py", "test_dusunme.py", "test_bicim.py", "test_konusan.py", "test_etiket_onar.py", "test_firefox.py", "test_google.py", "test_hafiza_budama.py", "test_deyim_google.py", "test_context_guard_1019.py",
    # Derleyicinin yerini tutan statik denetimler: en sona, çünkü hepsi
    # kaynağın tamamını okuyor ve en pahalı olanlar bunlar.
    "kotlin_denetle.py", "kotlin_cagri.py", "kaynak_denetle.py",
]

for name in TESTS:
    path = ROOT / name
    print(f"\n{'=' * 12} {name} {'=' * 12}", flush=True)
    try:
        result = subprocess.run([sys.executable, str(path)], cwd=ROOT.parent, timeout=90)
    except subprocess.TimeoutExpired:
        print(f"BAŞARISIZ: {name} 90 saniyede tamamlanmadı", flush=True)
        sys.exit(1)
    if result.returncode:
        print(f"BAŞARISIZ: {name} (çıkış kodu {result.returncode})", flush=True)
        sys.exit(result.returncode)
print(f"\nTÜM TESTLER GEÇTİ ({len(TESTS)} betik).")
