import com.example.altyazi.SpokenEnglish204 as S
import com.example.altyazi.TranslationQualityGate as Q

fun main() {
    var ok = 0
    fun v(check: Boolean, why: String) { require(check) { why }; ok++ }
    val source = "Yo. Yall need something?"
    v(S.forEngine(source) == "hey. you need something?", "normalize Yall for model only")
    v(S.forEngine("Nah. Whatchu want, bruh?") == "Nah. what do you want, bro?", "normalize casual grammar")
    v(S.forEngine("Aight, y'all tryna smoke? Fo sho.") == "alright, you trying to smoke? for sure.", "normalize dialect words")
    v(S.forEngine("Raymond? Boobie sent us.") == "Raymond? Boobie sent us.", "do not tamper with names")
    v(S.forEngine("You're not gonna do it.") == "You're not gonna do it.", "keep normal grammatical tokens")
    v(!S.isSpokenGrammar("Boobie") && !S.isSpokenGrammar("Raymond"), "proper names still allowed")
    v(S.isSpokenGrammar("Yall") && S.isSpokenGrammar("Whatchu"), "colloquial grammar is not a proper name")
    v(Q.check("Yo. Yall need something?", "Hey. Yall, bir şey mi lazım?") == Q.Problem.UNTRANSLATED_SLANG, "untranslated dialect detected")
    v(Q.mustReject(Q.Problem.UNTRANSLATED_SLANG), "never show untranslated dialect in overlay")
    v(Q.check("Yo. Yall need something?", "Hey. Bir şey mi lazım?") == null, "normal Turkish accepted")
    val bad = "Sanki, etrafta dolaşıp biraz bok sallamak zorunda kalabilir, dediğimi bil."
    val good = "Yani, biraz ortalığı karıştırıp bir şeyler halletmem gerekebilir, anladın mı?"
    val english = "Like, might have to fuck around and shake some shit, know I'm saying?"
    v(Q.check(english, good) == Q.Problem.LOST_PROFANITY, "natural MT receives style-only warning")
    v(Q.choose(english, good, bad) == good, "do not replace natural MT with literal Firefox")
    v(Q.choose("Our planetary defense network is back up.", "Gezegensel savunma ağımız yedeklendi.", "Gezegen savunma ağımız yeniden devrede.") == "Gezegen savunma ağımız yeniden devrede.", "other measured defects can be repaired")
    v(Q.check("Whatchu want?", "Whatchu ne istiyorsun?") == Q.Problem.UNTRANSLATED_SLANG, "whatchu never carried into Turkish")
    v(Q.check("Aight, aight.", "Aight, tamam.") == Q.Problem.UNTRANSLATED_SLANG, "aight never carried into Turkish")
    v(Q.check("What the fuck y'all doing in here?", "Burada neler yapıyorum?") == Q.Problem.PERSON_REVERSAL, "old subject check still works")
    println("PASS $ok/$ok quality 204 scenarios")
}
