import com.example.altyazi.MeaningSafety
import com.example.altyazi.TranslationQualityGate as Gate

fun main() {
    val bad = listOf(
        Triple("Leave no one alive.", "Kimseyi canlı bırak.", Gate.Problem.REVERSED_COMMAND),
        Triple("Leave no one alive!", "Hiç kimseyi sağ bırakın!", Gate.Problem.REVERSED_COMMAND),
        Triple("Spare no one.", "Kimseyi affet.", Gate.Problem.REVERSED_COMMAND),
        Triple("Don't kill them!", "Onları öldür!", Gate.Problem.LOST_PROHIBITION),
        Triple("Don't kill them!", "Onları öldürmek!", Gate.Problem.LOST_PROHIBITION),
        Triple("Never leave him behind!", "Onu geride bırak!", Gate.Problem.LOST_PROHIBITION),
        Triple("Keep going!", "Devam etmek!", Gate.Problem.IMPERATIVE_AS_INFINITIVE),
        Triple("Stop running!", "Koşmayı bırakmak!", Gate.Problem.IMPERATIVE_AS_INFINITIVE),
    )
    val good = listOf(
        "Leave no one alive." to "Kimseyi sağ bırakmayın!",
        "Leave no one alive." to "Kimseyi sağ bırakma.",
        "Spare no one." to "Kimseyi affetmeyin.",
        "Don't kill them!" to "Onları öldürmeyin!",
        "Don't move!" to "Kıpırdama!",
        "Don't worry about it." to "Boş ver.",
        "Never mind." to "Boş ver.",
        "Never leave him behind!" to "Onu asla geride bırakma!",
        "Keep going!" to "Devam et!",
        "Stop running!" to "Koşmayı bırak!",
        "I don't know if I'd call Earth home." to "Dünya'ya evim diyebilir miyim bilmiyorum.",
        "Try not to kill them so they can help." to "Yardım edebilmeleri için onları öldürmemeye çalış.",
        "No sign of more Viltrumite forces." to "Başka Viltrumite askerlerinden iz yok.",
        "Wait!" to "Bekle!",
        "Where is the Betrayer?" to "Hain nerede?",
        "The Coalition's gonna be happy you're on their side." to "Koalisyon onlardan yana olmana sevinecek.",
        "[Nolan] Don't move!" to "[Nolan] Kıpırdama!",
        "Don't panic!" to "Sakin ol!",
    )
    bad.forEach { (en,tr,problem) ->
        check(Gate.check(en,tr) == problem) { "Missed $en -> $tr : ${Gate.check(en,tr)}" }
        check(Gate.mustReject(Gate.check(en,tr)) == (problem != Gate.Problem.LOST_PROHIBITION))
        check(!Gate.mayRemember(en,tr))
    }
    good.forEach { (en,tr) ->
        check(!Gate.mustReject(Gate.check(en,tr))) { "False positive $en -> $tr : ${Gate.check(en,tr)}" }
    }
    check(MeaningSafety.risk("Leave no one alive.", "Kimseyi sağ bırakmayın!") == null)
    println("MeaningSafety1033: ${bad.size * 3 + good.size + 1}/${bad.size * 3 + good.size + 1} checks PASS")
}
