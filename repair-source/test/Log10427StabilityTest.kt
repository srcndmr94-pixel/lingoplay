import com.example.altyazi.SubtitleReadGuard299
import com.example.altyazi.GameplayLogRepair299

fun main() {
    var count = 0
    fun checkCase(condition: Boolean, name: String) { check(condition) { name }; count++ }
    val g = SubtitleReadGuard299
    checkCase(g.smallCorrection("How... how could this be? Its gone... it's not here...", "How... how could this be? lts gone... it's not here..."), "first-frame OCR typo identified")
    checkCase(!g.newMeaningForDispatch("How... how could this be? Its gone... it's not here...", "How... how could this be? lts gone... it's not here..."), "single-letter oscillation not a new subtitle")
    checkCase(!g.newMeaningForDispatch("Fine! Go on then! And don't come back!", "Fine! Go on then! And dont come back!"), "apostrophe-only change not a new subtitle")
    checkCase(g.newMeaningForDispatch("I will go now", "I will not go now"), "inserted negation genuinely changes meaning")
    checkCase(g.newMeaningForDispatch("I will not go now", "I will go now"), "removed negation genuinely changes meaning")
    checkCase(g.newMeaningForDispatch("She knew the answer.", "She knew the answer. She never told me."), "new clause is allowed")
    checkCase(!g.newMeaningForDispatch("I can do it", "I can do it..."), "punctuation only is ignored")

    fun ocr(input: String) = GameplayLogRepair299.repairOcr(input)
    checkCase(ocr("The Thread Binding Sectwould hide their base") == "The Thread Binding Sect would hide their base", "sectword glued")
    checkCase(ocr("These...could these be the devicesthey use?") == "These...could these be the devices they use?", "devices they glued")
    checkCase(ocr("Go! TIl hold them off!") == "Go! I'll hold them off!", "I will hold OCR")
    checkCase(ocr("Something's coming! And its not alonel") == "Something's coming! And its not alone!", "alone punctuation OCR")
    checkCase(ocr("Junior Brothero.. I still couldn't find it...") == "Junior Brother.. I still couldn't find it...", "speaker title glitch")
    checkCase(ocr("this mart I age will fall through") == "this marriage will fall through", "marriage OCR")
    checkCase(ocr("Xiu'sa good girl") == "Xiu's a good girl", "Xiu's a glued")
    checkCase(ocr("and dontcome back!") == "and don't come back!", "dontcome glued")
    checkCase(ocr("I Idontneedto beg you!") == "I I don't need to beg you!", "dontneedto glued")
    checkCase(ocr("Go! Till the end.") == "Go! Till the end.", "do not hallucinate I'll")
    checkCase(ocr("Sectwould like to speak") == "Sectwould like to speak", "do not change unrelated word")
    checkCase(ocr("Keen Mandate Disciple I never imagined this") == "[Keen Mandate Disciple] I never imagined this", "speaker role Keen separate")
    checkCase(ocr("Worldly Mandate Disciple Hmph, I'd bet") == "[Worldly Mandate Disciple] Hmph, I'd bet", "speaker role Worldly separate")
    checkCase(ocr("Lone Mandate Disciple Thread-Binding... and the faceless") == "[Lone Mandate Disciple] Thread-Binding... and the faceless", "speaker role Lone separate")
    checkCase(ocr("The Keen Mandate Disciple I knew") == "The Keen Mandate Disciple I knew", "do not split ordinary mention")

    fun translate(src: String, target: String) = GameplayLogRepair299.repairTranslation(src, target)
    checkCase(translate("Chang Hong and Lei Dang have faced death", "Chang, Hong ile Lei, Dang ölümle yüzleşti") == "Chang Hong ve Lei Dang ölümle yüzleşti", "two proper names stay whole")
    checkCase(translate("Heart-Carving Art", "Heart-Carving ve Art için") == "Heart-Carving Art için", "technique term")
    checkCase(translate("Mirage Estate", "Mirage ve Estate'ye") == "Mirage Estate'ye", "estate term")
    checkCase(translate("Unrelated text", "Mirage ve Estate'ye") == "Mirage ve Estate'ye", "do not rewrite unrelated texts")
    checkCase(translate("Senior Brother! Junior Brother!", "Senior, Brother! Junior, Brother!") == "Senior Brother! Junior Brother!", "role labels remain paired")
    println("Log10427StabilityTest: $count/$count PASS")
}
