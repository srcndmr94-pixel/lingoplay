package com.example.altyazi

/**
 * OCR'ın düzenli olarak yaptığı hataları çeviriden ÖNCE düzeltir.
 *
 * Ekrandan okunan yazıda kesme işareti kaybolur ("dont"), kelimeler birbirine
 * yapışır ("youare", "foryou"), bazı harfler rakama dönüşür. Bunlar
 * düzeltilmezse hem telefon çevirisi hem yapay zekâ yanılır; üstelik yapay
 * zekâ yanıldığında boşa jeton da gider.
 *
 * Buradaki her kural, kullanıcının gerçek çalışma kaydındaki bir bozulmadan
 * çıktı. Kurallar bilerek temkinli: emin olmadığında metne dokunmuyor.
 */
object TextFix {

    private val contractions = listOf(
        "dont" to "don't", "doesnt" to "doesn't", "didnt" to "didn't",
        "cant" to "can't", "couldnt" to "couldn't", "wont" to "won't",
        "wouldnt" to "wouldn't", "shouldnt" to "shouldn't", "isnt" to "isn't",
        "arent" to "aren't", "wasnt" to "wasn't", "werent" to "weren't",
        "havent" to "haven't", "hasnt" to "hasn't", "hadnt" to "hadn't",
        "im" to "I'm", "ive" to "I've",
        "youre" to "you're", "youve" to "you've", "youll" to "you'll",
        "itll" to "it'll", "theyll" to "they'll", "thatll" to "that'll", "whatll" to "what'll",
        "shes" to "she's", "thats" to "that's",
        "theres" to "there's", "theyre" to "they're", "theyve" to "they've",
        "weve" to "we've",
        "whats" to "what's", "wheres" to "where's", "whos" to "who's"
    )

    // Bilerek YOK: "were", "its", "ill", "id", "lets", "hes" (hepsi gerçek İngilizce kelime;
    // "They were here" -> "They we're here" gibi bozuluyordu).

    /**
     * Bölme için kullanılan kelime havuzu.
     *
     * İki iş yapıyor: yapışmış kelimeleri ayırmak, ve ayrılmaması gereken
     * kelimeleri korumak. "another" burada olduğu için "an + other" diye
     * bölünmez; "youare" burada olmadığı için "you + are" diye bölünür.
     */
    private val WORDS: Set<String> = setOf(
        // en sık geçen işlev kelimeleri
        "a", "an", "and", "are", "as", "at", "be", "been", "but", "by",
        "can", "could", "did", "do", "does", "for", "from", "get", "got",
        "had", "has", "have", "he", "her", "him", "his", "how", "i", "if",
        "in", "is", "it", "its", "me", "my", "no", "not", "of", "on", "or",
        "our", "out", "she", "so", "that", "the", "their", "them", "then",
        "there", "they", "this", "to", "up", "us", "was", "we", "were",
        "what", "when", "where", "which", "who", "why", "will", "with",
        "would", "you", "your", "yours", "am", "all", "any", "ever", "just",
        "now", "only", "own", "same", "some", "than", "too", "very", "well",
        "back", "down", "off", "over", "under", "again", "away", "here",
        "near", "once", "more", "most", "much", "many", "few", "both",
        // sık fiiller
        "go", "going", "gone", "come", "coming", "came", "give", "given",
        "take", "taken", "took", "make", "made", "know", "known", "knew",
        "think", "thought", "want", "wanted", "need", "needed", "look",
        "looking", "looked", "see", "seen", "saw", "say", "said", "tell",
        "told", "ask", "asked", "find", "found", "keep", "kept", "let",
        "put", "run", "running", "ran", "stay", "stop", "wait", "help",
        "move", "turn", "hold", "open", "close", "play", "build", "built",
        "bring", "brought", "leave", "left", "send", "sent", "work", "live",
        "die", "died", "kill", "killed", "save", "saved", "watch", "listen",
        "talk", "call", "walk", "fight", "win", "lose", "lost", "break",
        "broke", "pay", "paid", "buy", "bought", "sell", "sold", "read",
        "write", "feel", "felt", "try", "tried", "use", "used", "start",
        "follow", "carry", "throw", "catch", "push", "pull", "cut", "shoot",
        // sık adlar ve sıfatlar
        "man", "men", "woman", "women", "boy", "girl", "child", "people",
        "friend", "enemy", "captain", "sir", "lad", "lass", "ship", "boat",
        "sea", "shore", "island", "shop", "store", "gold", "coin", "money",
        "door", "key", "room", "house", "home", "town", "city", "road",
        "way", "place", "time", "day", "night", "life", "death", "hand",
        "head", "eye", "eyes", "face", "heart", "blood", "water", "fire",
        "food", "drink", "gun", "sword", "knife", "ship's", "crew",
        "good", "bad", "big", "small", "old", "new", "long", "short",
        "hard", "easy", "fast", "slow", "right", "wrong", "true", "false",
        "dead", "alive", "safe", "ready", "sure", "last", "first", "next",
        "every", "thing", "things", "nothing", "something", "anything",
        "everything", "someone", "anyone", "everyone", "nobody", "body",
        // bölünmemesi gereken birleşik kelimeler
        "another", "without", "within", "into", "onto", "upon", "cannot",
        "because", "before", "after", "around", "about", "against",
        "between", "behind", "beyond", "below", "above", "always", "never",
        "maybe", "perhaps", "myself", "yourself", "himself", "herself",
        "themselves", "ourselves", "today", "tonight", "tomorrow",
        "yesterday", "somewhere", "anywhere", "everywhere", "nowhere",
        "however", "whatever", "whenever", "wherever", "whoever",
        "understand", "together", "already", "almost", "enough", "instead",
        "outside", "inside", "forward", "backward", "anymore", "someday",
        "anytime", "anyway", "forever", "therefore", "sometimes", "somehow",
        "somewhat", "meanwhile", "everybody", "nowhere", "throughout",
        // oyun ve denizci ağzı
        "ye", "aye", "nay", "oi", "lad's", "cap", "mate", "matey", "arr",
        "treasure", "loot", "quest", "mission", "level", "health", "ammo",
        "weapon", "armor", "shield", "potion", "boss", "enemy's",
        "obliged", "pleased", "grateful", "useful", "little", "emporium",
        // iki bilinen kelimeden oluşan ama BÖLÜNMEMESİ gereken gerçek kelimeler
        "anybody", "somebody", "become", "became", "becomes", "becoming",
        "forget", "forgot", "forgotten", "forgive", "forgiven", "forgo", "overall",
        "overcome", "overtake", "overtook", "overrun", "upstairs", "downstairs",
        "anyhow", "withhold", "outrun", "outside", "outlaw", "takeover", "handgun",
        "handout", "gunman", "gunmen", "gunfire", "gunshot", "gunshots", "headshot",
        "backup", "setup", "lookout", "hideout", "showdown", "breakdown", "shutdown",
        "countdown", "download", "upload", "payday", "doorway", "hallway", "hereby",
        "herein", "thereby", "whereby", "upset", "uphold", "nobody's", "moreover",
        "otherwise", "meantime", "himself", "itself", "yourselves", "worthwhile",
        "uptown", "downtown", "goodbye", "goodnight", "weekend", "airport",
        "background", "playground", "bodyguard", "boyfriend", "girlfriend",
        "whatsoever", "therapy", "theater", "theatre", "theme", "thermal",
        "manhattan", "mankind", "manhunt", "manpower", "wasteland", "outstanding",
        "forest", "format", "formal", "former", "fortune", "forty", "fortress",
        "heroes", "hero", "heroin", "heroine", "bodies", "toward", "towards",
        "himself", "notice", "nothing's", "carpet", "season", "reason", "treason",
    )

    private val BASTAKI_I = Regex("\\b[IlL]([a-z]{2,})\\b")

    // ---------------------------------------------- "I" rakam 1 okunuyor
    //
    // OCR büyük I'yı tek başına 1 (bir) okuyor: "1 don't know", "1'm sorry".
    // Tek harfli olduğu için sözlüklü onarıma girmiyor (orada 2+ harf şartı var),
    // bu yüzden hem taslağa hem yapay zekâya "1" olarak gidiyordu.
    //
    // Gerçek sayıyı bozmamak için her kural AYRI ve dar: rakamın ardından
    // İngilizce bir kısaltma, olumsuz fiil ya da bilinen bir fiil gelmeli.
    // Çıplak "1 bottle", "Chapter 1", "1 of 3" gibi gerçek sayımlara dokunulmaz.

    /** "1'm", "1've", "1'll", "1'd", "1're" -> "I'm"... Rakam kısaltma almaz. */
    private val BIR_KESME = Regex("(?<![\\p{L}\\p{N}])1['’](m|ve|ll|d|re)(?![\\p{L}])")

    /** "1'am" / "I'am" -> "I am". İngilizcede "X'am" diye kelime yok. */
    private val BIR_AM = Regex("(?<![\\p{L}\\p{N}])[I1]['’]am(?![\\p{L}])")

    /** "1 don't", "1 can't", "1 didn't" -> "I ...". Rakamın ardından olumsuz fiil gelmez. */
    private val BIR_OLUMSUZ = Regex("(?<![\\p{L}\\p{N}])1(?=\\s+\\p{Ll}+n['’]?t(?![\\p{L}]))")

    /** "1- I don't..." kekeleme. Tireden sonra BOŞLUK şart: "1-hour" bozulmasın. */
    private val BIR_KEKEME = Regex("(?<![\\p{L}\\p{N}])1(?=[-–—]\\s)")

    /** "1 wonder", "1 am", "1 know": ardındaki kelime fiil listesindeyse "I". */
    private val BIR_FIIL = Regex("(?<![\\p{L}\\p{N}])1(\\s+)(\\p{Ll}[\\p{L}'’]*)(?![\\p{L}])")

    /** "Ll be" / "Il go" -> "I'll ...". Ardından yalın fiil gelmeli. */
    private val IL_FIIL = Regex(
        "\\b[IL]l\\b(?=\\s+(?:be|go|get|take|tell|see|do|have|let|make|try|call|come|" +
            "give|keep|need|put|say|show|stay|stop|talk|think|wait|walk|work|bring|" +
            "find|help|leave|meet|send|sit|start|turn)\\b)"
    )

    /**
     * "I" ile başlayan kalıpta sık geçen kelimeler. BİLEREK ayrı bir liste:
     * WORDS'e eklenirlerse aşağıdaki genel ayırıcı gerçek kelimeleri bölmeye
     * başlıyor ("mayor" -> "may or").
     *
     * Listeye "and", "or", "in", "is" gibi kelimeler ALINMAZ: "land" -> "I and",
     * "list" -> "I ist" olurdu. "ran" da yok: "Iran" ülke adı bozulmasın.
     */
    private val I_FIILLERI: Set<String> = setOf(
        "wonder", "may", "absolve", "pity", "swear", "suppose", "guess", "reckon",
        "promise", "believe", "remember", "forget", "hope", "wish", "mean", "need",
        "want", "know", "think", "said", "say", "saw", "see", "feel", "felt",
        "hate", "love", "like", "miss", "owe", "agree", "admit", "doubt", "expect",
        "intend", "prefer", "regret", "trust", "understand", "appreciate", "assume",
        "imagine", "realize", "suggest", "insist", "refuse", "decide", "heard",
        "hear", "found", "find", "told", "tell", "gave", "give", "took", "take",
        "came", "come", "went", "got", "get", "had", "have", "was", "am",
        "will", "would", "should", "could", "can", "must", "did", "do",
        "never", "always", "just", "really", "still", "only",
        "thought", "brought", "kept", "left", "made", "make", "put", "run", "sent",
        // Altyazıda sık geçen ama listede olmayanlar. Hepsi 3+ harf ve
        // I/l/L + kendisi gerçek bir İngilizce kelime etmiyor ("lbet", "ltry"
        // diye kelime yok), o yüzden üstteki yapışma kuralını bozmuyorlar.
        "gonna", "gotta", "wanna", "bet", "care", "dare", "deserve", "ought",
        "used", "tried", "try", "knew", "forgot", "swore", "wanted", "needed",
        "seen", "been", "figured", "noticed", "lost", "lose", "told",
    )

    fun clean(raw: String): String {
        var text = raw

        // Satır sonu tireleri ve üç nokta varyasyonları
        text = text.replace("…", "...")
        text = text.replace(Regex("\\s*\\.\\s*\\.\\s*\\."), "...")

        // Birden fazla boşluk
        text = text.replace(Regex("\\s+"), " ").trim()

        // Baştaki ve sondaki tırnak/tire kalıntıları
        // '|' de atılıyor: OCR altyazı kutusunun kenar çizgisini ya da imleci
        // boru işareti okuyor. Kayıttan: "|Aight, aight." -> "| Tamam, tamam."
        text = text.trim('-', '—', '«', '»', '"', '|', '¦').trim()

        // Nokta iki kelimeyi yapıştırmış: "as.Repayment" -> "as. Repayment"
        text = text.replace(Regex("(?<=[a-z])\\.(?=[A-Z])"), ". ")

        // "they'I kill" -> "they'll kill" ("ll" I okunmuş); yoksa aşağıdaki ayırma "they I" yapıyordu
        text = text.replace(Regex("\\b(they|you|we|he|she|it|i|that|there|who)['’][Il1](?![\\p{L}'])", RegexOption.IGNORE_CASE), "$1'll")

        // Kesme işareti "t" okunmuş: "Wetve" -> "We've", "theytre" -> "they're".
        // Kayıttan: "Wetve one more Riddle to solve." -> "bir tane daha bilmece ıslatın".
        // Yalnız bilinen zamir + kısaltma ikilisinde uygulanıyor, o yüzden gerçek
        // kelimeleri ("software", "network") bozmuyor.
        text = text.replace(Regex("\\b(I|i|We|we|You|you|They|they)t(ve|ll|re|d|m)\\b"), "$1'$2")
        text = text.replace(Regex("\\b(He|he|She|she|It|it|That|that|There|there|Who|who)t(s|ll|d)\\b"), "$1'$2")
        // Olumsuzda aynı hata: "dontt" -> "don't", "cantt" -> "can't", "isntt" -> "isn't".
        // İngilizcede "ntt" ile biten kelime yok, o yüzden geniş kural güvenli.
        text = text.replace(Regex("\\b(\\p{L}+n)tt\\b", RegexOption.IGNORE_CASE), "$1't")

        // Büyük I + kesme işareti tek harf okunmuş: "Pm sorry" -> "I'm sorry",
        // "Pve seen it" -> "I've seen it". Kayıttan: "Pm sorry to say."
        // Küçük/büyük harfe duyarlı: "PM" (saat) ve "FM" bozulmasın diye ek küçük harf şart.
        text = text.replace(Regex("\\b[PF]['’]?(m|ve|ll|d)\\b"), "I'$1")

        // Büyük I rakam 1 okunmuş: "1'm sorry" -> "I'm sorry", "1 don't" -> "I don't".
        // Kullanıcının bildirdiği hata. Kurallar yukarıda, her biri dar.
        text = BIR_KESME.replace(text, "I'$1")
        text = BIR_AM.replace(text, "I am")
        text = BIR_OLUMSUZ.replace(text, "I")
        text = BIR_KEKEME.replace(text, "I")
        // "1 can of soup", "1 run of bad luck": "can/run" fiil listesinde ama
        // ardından "of" gelirse bu gerçek bir sayımdır, dokunulmaz.
        val fiilKaynak = text
        text = BIR_FIIL.replace(text) { m ->
            val kelime = m.groupValues[2].trim('\'', '’').lowercase()
            val sonra = fiilKaynak.substring(minOf(m.range.last + 1, fiilKaynak.length))
            if (kelime in I_FIILLERI && !sonra.startsWith(" of ") && sonra != " of") {
                "I" + m.groupValues[1] + m.groupValues[2]
            } else {
                m.value
            }
        }

        // "I'll" kesme işaretini yitirip "Ll" / "Il" okunuyor.
        // Kayıttan: "Ll be trying to walk off a wicked hangover".
        //
        // DİKKAT: "Il" aynı zamanda "It"in bozuk okunuşu olabiliyor
        // ("Il is what it is" -> "It is what it is"). Ayırt etmek için
        // ardından YALIN BİR FİİL gelmesini şart koşuyoruz: "I'll be",
        // "I'll go"... "is/was/has" gelirse dokunmuyoruz, harf düzeltmesi
        // onu "It" yapıyor.
        text = text.replace(IL_FIIL, "I'll")

        // Baştaki "I" sonraki kelimeye yapışmış ya da l/L okunmuş:
        //   "Iwonder" -> "I wonder", "Imay" -> "I may",
        //   "labsolve" -> "I absolve", "Lpity" -> "I pity"
        // Hepsi kullanıcının kaydından. Aşağıdaki genel ayırıcı bunları kaçırıyor
        // çünkü sol parçanın en az 2 harf olmasını şart koşuyor; burada 1 harf.
        text = BASTAKI_I.replace(text) { m ->
            if (m.groupValues[1].lowercase() in I_FIILLERI) "I " + m.groupValues[1] else m.value
        }

        // Yapışmış kelimeleri ayır: "youare" -> "you are"
        text = kelimeleriAyir(text)

        // Kaybolan kesme işaretlerini geri koy (sadece tam kelime eşleşmesinde)
        for ((wrong, right) in contractions) {
            text = text.replace(Regex("\\b$wrong\\b", RegexOption.IGNORE_CASE), right)
        }

        return text.replace(Regex("\\s+"), " ").trim()
    }

    /** Metindeki her parçayı tek tek ayırmayı dener. */
    private fun kelimeleriAyir(text: String): String =
        text.split(" ").joinToString(" ") { parca -> parcayiAyir(parca) }

    private fun parcayiAyir(parca: String): String {
        // Kesme işareti yanlış yere düşmüş: "Shop'would" -> "Shop would"
        if (parca.contains('\'')) {
            val yarim = parca.split('\'')
            if (yarim.size == 2 && bilinen(yarim[0]) && bilinen(yarim[1])) {
                return yarim[0] + " " + yarim[1]
            }
        }

        // İçinde hiç harf yoksa ("-", "...", "•" gibi) dokunacak bir şey yok.
        // Bu kontrol olmadan baş ve son aynı karakterleri sayıyor, geriye
        // kalan aralık negatife düşüyor ve substring çöküyordu.
        if (parca.none { it.isLetter() }) return parca

        val bas = parca.takeWhile { !it.isLetter() }
        val son = parca.takeLastWhile { !it.isLetter() }
        val govde = parca.substring(bas.length, parca.length - son.length)

        if (govde.length < 5) return parca
        if (bilinen(govde)) return parca

        val bolunmus = bol(govde) ?: return parca
        return bas + bolunmus + son
    }

    /**
     * Gövdeyi ikiye ayırmayı dener. Temkinli: ilk parça mutlaka bilinen bir
     * kelime olmalı, ikinci parça da ya bilinen olmalı ya da en az 4 harf.
     * En uzun ilk parçayı tercih ediyoruz ki "foryou" -> "for you" olsun,
     * "f oryou" olmasın.
     */
    private fun bol(govde: String): String? {
        for (i in govde.length - 2 downTo 2) {
            val sol = govde.substring(0, i)
            val sag = govde.substring(i)
            if (!bilinen(sol)) continue
            // Sol parca en az 3 harf olmali: yoksa "notice" -> "no tice"
            // gibi gercek kelimeler bolunur.
            // İki parça da bilinen kelime olmalı. Eskiden "sağ parça 4+ harf"
            // yetiyordu; bu "therapy" -> "the rapy", "playing" -> "play ing",
            // "outstanding" -> "out standing" gibi gerçek kelimeleri bölüyordu.
            if (bilinen(sag)) {
                return "$sol $sag"
            }
        }
        return null
    }

    private fun bilinen(kelime: String): Boolean =
        kelime.isNotEmpty() && kelime.lowercase() in WORDS
}
