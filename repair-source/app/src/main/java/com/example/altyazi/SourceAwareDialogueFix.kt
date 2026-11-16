package com.example.altyazi

/** Kaynak İngilizceyle birebir doğrulanan replikler; diğerlerine dokunmaz. */
object SourceAwareDialogueFix {
    private fun key(s: String): String = s.trim()
        .replace('’', '\'')
        .replace(Regex("\\s+"), " ")
        .trimEnd('.', '!', '?', ' ')
        .lowercase(java.util.Locale.ROOT)

    private val known = mapOf(
        // APK-299 ikinci cihaz kaydında görülen anlamı ters çeviren / ad
        // bozan satırlar. Sadece birebir İngilizce kaynağa uygulanır.
        "xie qiudong sacrifice a few to save the many. that is how you save the world" to
            "Xie Qiudong: Çoğunluğu kurtarmak için birkaç kişiyi feda etmek gerekir. Dünya böyle kurtulur!",
        "soul could i replace my poisoned heart.. with one altered by transcension" to
            "Soul: Zehirlenmiş kalbimin yerine Transcension'la değiştirilmiş bir kalp koyabilir miyim?",
        "[soul] i refused even the hollow heart... how could i ever accept something so monstrous" to
            "[Soul] Hollow Heart'i bile reddettim... Böyle korkunç bir şeyi nasıl kabul edebilirim?",
        "xie qiudong i gave her more time than she ever should've had" to
            "Xie Qiudong: Ona hak ettiğinden çok daha fazla zaman tanıdım.",
        "you're not like those other transcended healers... you still have your senses" to
            "Sen diğer Transcended şifacılar gibi değilsin... Hâlâ aklın başında mı?",
        "xie huichun devoted his life to healing, but failed to save the transcended" to
            "Xie Huichun hayatını insanları iyileştirmeye adadı ama Transcended olanları kurtaramadı.",
        "we're not that late" to "O kadar da geç kalmadık.",
        "were not that late" to "O kadar da geç kalmadık.",
        "brian's doing us a favor, you know? we need this intro, lu" to
            "Brian bize iyilik yapıyor, biliyorsun. Lu, bizi tanıştırmasına ihtiyacımız var.",
        "you know, we're in some deep fucking shit" to "Biliyorsun, fena hâlde boka battık.",
        "look, he could sell us out. or put a bullet through our heads" to
            "Bak, bizi ele verebilir. Ya da kafamıza kurşun sıkabilir.",
        "what you think i needed a lift for" to "Sence beni neden arabayla almanı istedim?",
        "yeah, go. like, right fucking now" to "Evet, git. Hem de hemen, amına koyayım.",
        "he wants it on the quiet. he only wants one of you" to
            "Bu işin gizli kalmasını istiyor. Sadece birinizi istiyor.",
        "nice. on my way" to "Güzel. Yoldayım.",
        "alright, so how you wanna play it" to "Peki, nasıl hareket edeceğiz?",
        "this is so fucked" to "Bu iş fena sıçtı.",
        "and i fucking jumped" to "Ben de atladım, amına koyayım.",
        // 16 Kasım Pixel 9 Pro kaydı, yalnız birebir gözlenen cümleler:
        // "Phase One/Two" bu sahnede karakter kod adı; sahne geçişlerinde
        // genel "birinci aşama" kuralı koymuyoruz.
        "[phase two] you're phase one" to "[Phase Two] Sen Phase One'sın...",
        "..phase two," to "...Phase Two,",
        "phase three," to "Phase Three,",
        "[phase] four," to "Phase Four,",
        "[phase] five" to "Phase Five.",
        // Kopuk OCR noktalama ve model uydurma ayrıntılarına karşı,
        // cümlenin kendisi dışında olay/eylem eklemiyoruz.
        "long enough to learn to talk" to "Konuşmayı öğrenecek kadar uzun süre.",
        "they've never been out of their tanks" to "Tanklarından hiç çıkmadılar.",
        // 10.42.5: DeepL can be fluent yet soften the actual spoken tone or
        // omit crucial meaning. Only exact observed dialogue is pinned.
        "dude could be a cop for all we know" to
            "Bu adam polis bile olabilir, nereden bilelim?",
        "yeah, yeah. you gotta know when to get off, jason" to
            "Evet, evet. Ne zaman çekileceğini bilmelisin, Jason.",
        "our planetary defense network is back up" to
            "Gezegen savunma ağımız yeniden devrede.",
        "enjoy your dinner, guys. i'll be back for dessert" to
            "Afiyet olsun millet. Tatlıya geri geleceğim.",
        "he's not a cop, alright? he's just cautious" to
            "Polis değil, tamam mı? Sadece temkinli davranıyor.",
        "then why don't you go see oliver" to
            "O zaman neden gidip Oliver'ı görmüyorsun?",
        "i got big shoes to fill" to "Üstlendiğim sorumluluk çok büyük.",
        "you guys are the best" to "Siz bir tanesiniz.",
        // 10.30: reported real gameplay mistranslations, source-locked to avoid
        // corrupting unrelated dialogue. These are regression anchors, not an MT engine.
        "you got a point" to "Sadede gelecek misin?",
        "they had bale mazes and ferris wheel" to "Saman balyalarından labirentler ve dönme dolap vardı.",
        "yeah, let's take in the view" to "Evet, manzaranın tadını çıkaralım.",
        "everybody, hands where we can see them" to "Herkes ellerini görebileceğimiz yere kaldırsın!",
        "look what the law chased in" to "Bakın, polis kimleri getirmiş.",
        "the locals are feisty" to "Buranın insanları bayağı dişli!",
        "cracked his head open. died that night" to "Kafatası yarıldı. O gece öldü.",
        "i never get to smoke these in front of lori" to "Lori'nin yanında bunları hiç içemiyorum.",
        "and they had this beautiful carousel" to "Bir de harika bir atlıkarıncaları vardı.",
        "one night we snuck in and a buddy of mine, he got it working" to
            "Bir gece gizlice içeri girdik. Bir arkadaşım da onu çalıştırdı.",
        "well, brian was quite the stud back in the day" to "Brian eskiden tam bir çapkındı.",
        "what did i fucking say" to "Ben sana ne dedim, amına koyayım?",
        "i get that too" to "Onu da anlıyorum.",
        "get that too" to "Onu da anlıyorum.",
        "well look who's back" to "Bak sen kim gelmiş!",
        "then he cranked the speed up so fast we thought we was flying" to
            "Sonra hızı öyle artırdı ki uçuyoruz sandık.",
        // 10.31: source-locked regression cases from the user's latest log.
        "uh, yeah, zoe, this is not gonna work for me" to "Zoe, böyle olmaz.",
        "okay now we're talking" to "İşte şimdi oldu!",
        "before those two nerds tear us apart" to "Şu iki inek bizi paramparça etmeden önce.",
        "it also will \"suck\" now" to "Bu sefer de berbat olacak.",
        "i need you on lookout" to "Gözcülük yapmanı istiyorum.",
        "yeah, yeah, i know you're all upset. shoo" to "Tamam, tamam, kızgınsınız, biliyorum. Hadi, kışt!",
        "and super pissed off" to "Üstelik çok öfkeli.",
        "you take the left, i'll take the right" to "Sen soldan git, ben sağdan gideceğim.",
        "she can't lay more if we kill her" to "Onu öldürürsek bir daha yumurtlayamaz.",
        "shouldn't we, like, cook it or something" to "Bunu pişirsek falan daha iyi olmaz mı?",
        "a picky eater, you know" to "Yemek seçerdi, bilirsin.",
        "we're here. [explosions]" to "Geldik. [patlamalar]",
        "and so is the coalition" to "Koalisyon da burada.",
        "yeah. you better run. or fly" to "Evet, kaçsan iyi olur. Ya da uçsan.",
        "it's a good point, and i still appreciate it" to "Haklısın, yine de sağ ol.",
        "you guys" to "Siz..."
    )
    fun correct(source: String): String? = known[key(source)]
}
