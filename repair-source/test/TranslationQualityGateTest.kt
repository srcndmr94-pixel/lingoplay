import com.example.altyazi.TranslationQualityGate as G
fun main() {
    check(G.check("Were not that late.", "O kadar geç değil miydik?") == G.Problem.INVENTED_QUESTION)
    check(G.choose("Were not that late.", "O kadar geç değil miydik?", "O kadar da geç kalmadık.") == "O kadar da geç kalmadık.")
    check(G.choose("Were not that late.", "O kadar geç değil miydik?", "Geç mi kalmadık?") == "O kadar geç değil miydik?")
    check(G.check("I get it.", "Anlıyorum.") == null)
    check(G.check("What is this?", "Bu ne?") == null)
    check(G.check("This is my house.", "This is my house.") == G.Problem.UNTRANSLATED)
    check(G.check("We need this intro, Lu.", "Bu introya ihtiyacımız var, Lu.") == null)
    check(G.check("I don't want to go there.", "Oraya gitmek istiyorum.") == G.Problem.LOST_NEGATION)
    check(G.check("I don't want to go there.", "Oraya gitmek istemiyorum.") == null)
    check(G.check("We are not ready yet.", "Henüz hazırız.") == G.Problem.LOST_NEGATION)
    check(G.check("We are not ready yet.", "Henüz hazır değiliz.") == null)
    check(G.check("Are you ready?", "Hazır mısın?") == null)
    check(!G.mayRemember("We are not ready yet.", "Henüz hazırız."))
    check(G.check("And don't worry about Brian. Dude's retired.", "Brian'ı da boş ver. Adam emekli oldu.") == null)
    check(G.check("Don't worry about it, I have it under control.", "Boş ver, hallettim ben.") == null)
    println("15/15 translation gate tests PASS")
}
