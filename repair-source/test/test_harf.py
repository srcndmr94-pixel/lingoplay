"""PC: düşük çözünürlükte karışan harfler ("thal" -> "that") sözlükle düzeltiliyor; isimler ve doğru kelimeler bozulmuyor."""
import importlib.util, os, sys
PC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "pc")
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "stubs")); sys.path.insert(0, PC)
spec = importlib.util.spec_from_file_location("lingoplay", os.path.join(PC, "lingoplay_pc.py"))
lp = importlib.util.module_from_spec(spec); spec.loader.exec_module(lp)
ornek = {   # kullanıcının 5.8 kaydından
    "Yo, is Raymand araund?": "Yo, is Raymand around?",
    "Yo, we're here lo pick up. For Boobie.": "Yo, we're here to pick up. For Boobie.",
    "Aight, hald up.": "Aight, hold up.",
    "Yo, Ray! lhese lwo say lhey're for Hoobie.": "Yo, Ray! these two say they're for Hoobie.",
    "I already lold thal motherfucker whal the fuck going on, man.": "I already told that motherfucker what the fuck going on, man.",
    "He seriously senl y'all over here right now?": "He seriously sent y'all over here right now?",
    "Look, we could go back emply handed.": "Look, we could go back empty handed.",
    "My parlner's off the grid righl now, you Ieel me?": "My partner's off the grid right now, you feel me?",
    "al this poinl I really don'l give a fuck.": "al this point I really don't give a fuck.",
    "so jusl let themtake whalever they wanl, akay?": "so just let them take whatever they want, okay?",
    "Raymond, lislen very clasely, cabran.": "Raymond, listen very closely, cabran.",
    "Hey, ya, Ray! Il's lwelve! 1hey looled up and everylhing!": "Hey, ya, Ray! It's twelve! They tooled up and everything!",
    "Police! Dan'l fucking move!": "Police! Don't fucking move!",
    "Il is what it is, my friend.": "It is what it is, my friend.",
    "Our friends an the lask force": "Our friends an the task force",
}
sorun = [f"{k!r} -> {lp.metni_onar(k)!r} (beklenen {v!r})" for k, v in ornek.items() if lp.metni_onar(k) != v]
for ok in ["Raymond, listen to me.", "I love Della and Lola.", "Bill told Molly.", "Tell Lily I said hi.", "Is Lola here?",
           "I feel it.", "Ian is here. Ian left.", "Ilsa, wait!", "Nah, never heard of him.", "FBI! Open up!", "Holy shit!",
           "Come on, come on!", "They say they're paying you a visit", "Look at the lake.", "Ali told me."]:
    if lp.metni_onar(ok) != ok: sorun.append(f"bozuldu: {ok!r} -> {lp.metni_onar(ok)!r}")
import numpy as np
siyah = np.zeros((90, 600, 3), dtype=np.uint8)
yazili = siyah.copy(); yazili[30:60, 100:400] = 255
if lp.altyazi_rengi_var_mi(siyah): sorun.append("siyah bölgede yazı var sandı")
if not lp.altyazi_rengi_var_mi(yazili): sorun.append("beyaz yazıyı görmedi")
sari = siyah.copy(); sari[30:60, 100:400] = (40, 220, 250)   # BGR sarı
if not lp.altyazi_rengi_var_mi(sari): sorun.append("sarı yazıyı görmedi")
if sorun: print("BAŞARISIZ:\n  " + "\n  ".join(sorun)); sys.exit(1)
print("Harf düzeltme testi geçti.")
