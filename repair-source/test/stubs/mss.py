class _S:
    monitors=[{"left":0,"top":0,"width":1920,"height":1080},
              {"left":0,"top":0,"width":1920,"height":1080}]
    def __enter__(self): return self
    def __exit__(self,*a): pass
    def grab(self, bolge): raise RuntimeError("test")
def mss(): return _S()
