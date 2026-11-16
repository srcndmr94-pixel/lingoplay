"""Sahte tkinter: pencere açmadan arayüz kodunu baştan sona çalıştırmak için.
Oluşturulan her düğme DUGMELER listesine girer; testler komutlarına basabilir."""
DUGMELER = []
PENCERELER = []
PANO = []


class _W:
    def __init__(self, *a, **k):
        self._k = dict(k)
        self._t = k.get("text", "")
        if "command" in k and k.get("command") is not None:
            DUGMELER.append(self)
        if type(self).__name__ in ("Tk", "Toplevel"):
            PENCERELER.append(self)

    def config(self, **k):
        self._k.update(k)
        self._t = k.get("text", self._t)
    configure = config

    def cget(self, k):
        return self._t if k == "text" else self._k.get(k, "")

    def winfo_width(self): return 100
    def winfo_screenwidth(self): return 1920
    def winfo_screenheight(self): return 1080

    def after(self, ms, fn=None, *a):
        # Hemen çalıştır; ama 1 sn ve üstü tekrarlayan yenilemeleri (kendini yeniden kuran) atla
        if fn and (not isinstance(ms, (int, float)) or ms < 1000):
            fn(*a)

    def invoke(self):
        c = self._k.get("command")
        return c() if c else None

    def __setitem__(self, k, v): self._k[k] = v
    def __getitem__(self, k): return self._k.get(k)

    def clipboard_clear(self):
        PANO.clear()

    def clipboard_append(self, m):
        PANO.append(m)

    def __getattr__(self, ad):
        if ad.startswith("create_"):
            return lambda *a, **k: 1
        return lambda *a, **k: None


class Tk(_W): pass
class Toplevel(_W): pass
class Label(_W): pass
class Frame(_W): pass
class Button(_W): pass
class Entry(_W): pass
class Canvas(_W): pass
class Text(_W): pass
class Scrollbar(_W): pass


class Scale(_W):
    def __init__(self, *a, **k):
        super().__init__(*a, **{x: y for x, y in k.items() if x != "command"})
        self._k["command"] = k.get("command")


class StringVar:
    def __init__(self, value=""): self.v = value
    def get(self): return self.v
    def set(self, v): self.v = v


class IntVar(StringVar):
    def __init__(self, value=0): self.v = int(value)
    def get(self): return int(self.v)
