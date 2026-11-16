# ICU regex (Android'in java.util.regex motoru) - ctypes ile
import ctypes, ctypes.util, glob
_p = sorted(glob.glob("/usr/lib/x86_64-linux-gnu/libicui18n.so.*"))[0]
_v = _p.split(".so.")[1].split(".")[0]
L = ctypes.CDLL(_p)
def f(ad): return getattr(L, "%s_%s" % (ad, _v))
class UParseError(ctypes.Structure):
    _fields_ = [("line", ctypes.c_int32), ("offset", ctypes.c_int32),
                ("pre", ctypes.c_uint16 * 16), ("post", ctypes.c_uint16 * 16)]
_open = f("uregex_open"); _open.restype = ctypes.c_void_p
_open.argtypes = [ctypes.c_char_p, ctypes.c_int32, ctypes.c_uint32, ctypes.POINTER(UParseError), ctypes.POINTER(ctypes.c_int)]
_close = f("uregex_close"); _close.argtypes = [ctypes.c_void_p]
_set = f("uregex_setText"); _set.argtypes = [ctypes.c_void_p, ctypes.c_char_p, ctypes.c_int32, ctypes.POINTER(ctypes.c_int)]
_find = f("uregex_find"); _find.restype = ctypes.c_int8
_find.argtypes = [ctypes.c_void_p, ctypes.c_int32, ctypes.POINTER(ctypes.c_int)]
_start = f("uregex_start"); _start.restype = ctypes.c_int32; _start.argtypes = [ctypes.c_void_p, ctypes.c_int32, ctypes.POINTER(ctypes.c_int)]
_end = f("uregex_end"); _end.restype = ctypes.c_int32; _end.argtypes = [ctypes.c_void_p, ctypes.c_int32, ctypes.POINTER(ctypes.c_int)]
_err = ctypes.CDLL(sorted(glob.glob("/usr/lib/x86_64-linux-gnu/libicuuc.so.*"))[0])
_name = getattr(_err, "u_errorName_%s" % _v); _name.restype = ctypes.c_char_p; _name.argtypes = [ctypes.c_int]
def u16(s):
    b = s.encode("utf-16-le"); return b, len(b)//2
def derle(pat):
    """(tutamak, hata) doner"""
    b, n = u16(pat); st = ctypes.c_int(0); pe = UParseError()
    h = _open(b, n, 0, ctypes.byref(pe), ctypes.byref(st))
    if st.value > 0: return None, "%s @%d" % (_name(st.value).decode(), pe.offset)
    return h, None
def esle(pat, metin):
    """(eslesti, eslesen, hata)"""
    h, e = derle(pat)
    if e: return False, None, e
    b, n = u16(metin); st = ctypes.c_int(0)
    keep = ctypes.create_string_buffer(b + b"\0\0")
    _set(h, keep, n, ctypes.byref(st))
    ok = _find(h, 0, ctypes.byref(st))
    r = None
    if ok:
        a = _start(h, 0, ctypes.byref(st)); z = _end(h, 0, ctypes.byref(st))
        r = b[a*2:z*2].decode("utf-16-le")
    _close(h)
    return bool(ok), r, None
_repl = f("uregex_replaceAll"); _repl.restype = ctypes.c_int32
_repl.argtypes = [ctypes.c_void_p, ctypes.c_char_p, ctypes.c_int32, ctypes.c_char_p, ctypes.c_int32, ctypes.POINTER(ctypes.c_int)]
def degistir(pat, metin, yerine):
    """ICU replaceAll ("$1" grup başvurusu Java/Kotlin ile aynı). (sonuc, hata)"""
    h, e = derle(pat)
    if e: return None, e
    b, n = u16(metin); st = ctypes.c_int(0)
    keep = ctypes.create_string_buffer(b + b"\0\0")
    _set(h, keep, n, ctypes.byref(st))
    rb, rn = u16(yerine); rkeep = ctypes.create_string_buffer(rb + b"\0\0")
    kap = n * 2 + 64
    out = ctypes.create_string_buffer(kap * 2)
    st = ctypes.c_int(0)
    m = _repl(h, rkeep, rn, out, kap, ctypes.byref(st))
    _close(h)
    if st.value > 0: return None, _name(st.value).decode()
    return out.raw[:m*2].decode("utf-16-le"), None
if __name__ == "__main__":
    print("ICU surumu:", _v)
    for p, t in [("(?i)\\bçok\\b", "Çok güzel"), ("(?iU)\\bçok\\b", "Çok güzel"),
                 ("(?U)abc", "abc"), ("(?u)abc", "abc"), ("(?i)\\bgöt\\b", "götürdü"),
                 ("(?i)\\w{2,}m[ae]z", "ölmez"), ("(?i)^(lan|be)\\b", "Beğendim"),
                 ("(?i)\\bsik", "sıkıntı yok"), ("(?i)\\bs(?-i:[iİ])k", "siktir")]:
        print("%-28s %-14s -> %s" % (p, t, esle(p, t)))
