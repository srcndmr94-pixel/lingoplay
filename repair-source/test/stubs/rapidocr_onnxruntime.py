import numpy as np
KONTROL = {}
class RapidOCR:
    def __call__(self, img):
        KONTROL["ndim"]=img.ndim
        KONTROL["kanal"]=img.shape[2] if img.ndim==3 else None
        KONTROL["contiguous"]=img.flags["C_CONTIGUOUS"]
        KONTROL["dtype"]=str(img.dtype)
        kutu=[(10,10),(300,10),(300,40),(10,40)]
        return [(kutu,"Hold on",0.95)], None
