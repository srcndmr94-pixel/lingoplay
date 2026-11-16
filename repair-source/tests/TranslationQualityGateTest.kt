import com.example.altyazi.TranslationQualityGate as G
fun main() {
    check(G.check("Were not that late.", "O kadar geç değil miydik?") == G.Problem.INVENTED_QUESTION)
    check(G.check("Were not that late.", "O kadar da geç kalmadık.") == null)
    check(G.check("What happened?", "Ne oldu?") == null)
    check(G.check("Nice. On my way.", "Nice. On my way.") == G.Problem.UNTRANSLATED)
    check(G.check("I need this intro.", "Bu introya ihtiyacım var.") == null) // semantic errors need model review
    check(G.check("The road is long.", "the road with you") == G.Problem.UNTRANSLATED)
    check(G.check("He said: go.", "O dedi: Git?") == null) // cautious on embedded speech
    check(G.choose("Were not that late.", "O kadar geç değil miydik?", "O kadar da geç kalmadık.") == "O kadar da geç kalmadık.")
    check(G.choose("Were not that late.", "O kadar geç değil miydik?", null) == "O kadar geç değil miydik?")
    check(!G.mayRemember("Were not that late.", "O kadar geç değil miydik?"))
    check(G.mayRemember("Were not that late.", "O kadar da geç kalmadık."))
    println("11/11 checks passed")
}
