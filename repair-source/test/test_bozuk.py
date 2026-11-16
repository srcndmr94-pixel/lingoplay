"""PC: bozuk okuma kapısı (telefondakiyle aynı kurallar) + dar bölge genişletme + OCR düzeltmeleri."""
import importlib.util, os, sys
PC_YOLU = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "pc", "lingoplay_pc.py")
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "stubs"))
spec = importlib.util.spec_from_file_location("lingoplay", PC_YOLU)
lp = importlib.util.module_from_spec(spec); spec.loader.exec_module(lp)
sorun = []
for b in ["And we thankKMI. CorTIgan Yor Volunteering", "sCUrfs) For vo.untteri?hg", "ontiou00 Touooon", "Ihoooing",
          "vqIILQnpyi", "Fi!st, we must Slop"]:
    if not lp.bozuk_mu(lp.metni_onar(b)): sorun.append(f"bozuk geçti: {b}")
for ok in ["And we thank Mr. Corrigan for volunteering", "First, we must stop", "Ooh, ahh.", "Hmmm.", "Call 911 now!",
           "Meet me at 5pm.", "FBI! Open up!", "U.S.A. all the way.", "My iPhone is dead.", "MacKenzie, wait!",
           "They say they're paying you a visit", "Pick up.", "Yep.", "IAM CAN SANSURSUZ", "The 2nd floor."]:
    if lp.bozuk_mu(lp.metni_onar(ok)): sorun.append(f"temiz engellendi: {ok}")
if lp.metni_onar("Thcy say thcy're paying") != "They say they're paying": sorun.append(lp.metni_onar("Thcy say thcy're paying"))
if lp.bolge_yuzdeleri({"ust_yuzde": 88, "alt_yuzde": 93}) != (86, 94): sorun.append(f"dar bölge: {lp.bolge_yuzdeleri({'ust_yuzde': 88, 'alt_yuzde': 93})}")
if lp.bolge_yuzdeleri({"ust_yuzde": 74, "alt_yuzde": 99}) != (74, 99): sorun.append("geniş bölge değişti")
if lp.bolge_yuzdeleri({"ust_yuzde": 97, "alt_yuzde": 99}) != (92, 100): sorun.append(f"alt kenar: {lp.bolge_yuzdeleri({'ust_yuzde': 97, 'alt_yuzde': 99})}")
if sorun: print("BAŞARISIZ:", sorun); sys.exit(1)
print("Bozuk okuma / bölge testi geçti.")
