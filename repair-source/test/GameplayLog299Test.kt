package com.example.altyazi

fun main() {
    var passes = 0
    fun checkCase(label: String, value: Boolean) {
        check(value) { "FAILED: $label" }
        passes++
    }
    val repair = GameplayLogRepair299::repairOcr
    checkCase("time OCR", repair("Ata time this dangerous... they went out?") ==
        "At a time this dangerous... they went out?")
    checkCase("not 'Ata' proper name", repair("Ata went to the lake.") == "Ata went to the lake.")
    checkCase("fallen souls in Blight", repair("The souls of our fatten now retun to the Blight!") ==
        "The souls of our fallen now return to the Blight!")
    checkCase("real fatten untouched", repair("They fatten the cattle.") == "They fatten the cattle.")
    checkCase("real return untouched", repair("We will retun tomorrow") == "We will retun tomorrow")
    checkCase("Sha Chi", repair("Sha Chi unleased... It sends the Perfect Transcension fighters") ==
        "Sha-Chi unleashed... It sends the Perfect Transcension fighters")
    checkCase("Std combo", repair("Standand Combo") == "Standard Combo")
    checkCase("normal standard", repair("Standard Combo") == "Standard Combo")
    checkCase("unleashed after", repair("Can be unieasned otter any swing during a Standand Combo") ==
        "Can be unleashed after any swing during a Standard Combo")
    checkCase("unrelated otter", repair("An otter swam by.") == "An otter swam by.")
    checkCase("temper verb", repair("There to tem per this sword's soul.") ==
        "There to temper this sword's soul.")
    checkCase("broken IH", repair("IH can't explain it...") == "I can't explain it...")
    checkCase("letter abbreviation IH unmodified", repair("The IH engine is ready.") == "The IH engine is ready.")
    checkCase("joined I'mused", repair("I'mused to the wind.") == "I'm used to the wind.")
    checkCase("drop unusable char tail", GameplayLogRepair299.waitForCleanReading(
        "Press after dodging or while sprinting to charge thg"))
    checkCase("drop chopped ability", GameplayLogRepair299.waitForCleanReading(
        "Can be unleashed after any swing during a Standard"))
    checkCase("drop terrible OCR", GameplayLogRepair299.waitForCleanReading(
        "Can be unieasned otter any swing, Witti enogi Sla CHi, Jtfarkwith"))
    checkCase("keep clean menu sentence", !GameplayLogRepair299.waitForCleanReading(
        "These attacks convert HP into Recoverable HP and deal damage"))
    checkCase("protected gameplay sentence", GameplayLogRepair299.isGameplayInstruction(
        "These attacks convert HP into Recoverable HP and deal damage"))
    checkCase("skip controller text", !GameplayLogRepair299.isGameplayInstruction("O Move @ Zoom"))
    checkCase("keep valid speaker", GameplayLogRepair299.plausibleSpeakerName("Daoist Bi Di"))
    checkCase("keep valid other speaker", GameplayLogRepair299.plausibleSpeakerName("Archer Captain"))
    checkCase("reject OCR fake speaker", !GameplayLogRepair299.plausibleSpeakerName("WU Xra H"))
    checkCase("reject giant prefix", !GameplayLogRepair299.plausibleSpeakerName("Some Long Speaker Title With Five Words"))
    checkCase("repair Bronze Essence", GameplayLogRepair299.repairTranslation(
        "Bronze Essence there to temper this sword's soul", "Bronze ve Essence'ü bulabilirsin") ==
        "Bronze Essence'i bulabilirsin")
    checkCase("never change unrelated Bronze", GameplayLogRepair299.repairTranslation(
        "A bronze sword", "Bronze ve Essence'ü bulabilirsin") == "Bronze ve Essence'ü bulabilirsin")
    checkCase("repair Sha Chi", GameplayLogRepair299.repairTranslation(
        "Sha Chi unleashed", "Sha ve Chi'ü serbest bırak") == "Sha-Chi'yi serbest bırak")
    checkCase("repair named title", GameplayLogRepair299.repairTranslation(
        "Perfect Transcension fighters", "Perfect ve Transcension savaşçıları") == "Perfect Transcension savaşçıları")
    checkCase("soul temper menu", GameplayLogRepair299.verifiedUiLabel("Soul Temper Leave") == "Ruh Tavlama · Ayrıl")
    checkCase("soul temper with cost", GameplayLogRepair299.verifiedUiLabel("8 Soul Temper") == "8 Ruh Tavlama")
    checkCase("no menu override other sentence", GameplayLogRepair299.verifiedUiLabel("We must temper the soul") == null)
    checkCase("do not touch profanity", GameplayLogRepair299.repairTranslation("Yo, what the fuck?", "Bu ne lan?") == "Bu ne lan?")
    checkCase("healers OCR", repair("You're not like those other Transcended heaters...") ==
        "You're not like those other Transcended healers...")
    checkCase("unrelated heater unchanged", repair("Those heaters are new") == "Those heaters are new")
    checkCase("Xie Qiudong OCR", repair("Xie iudong 1gave her more time than she evershouldve hadl") ==
        "Xie Qiudong I gave her more time than she ever should've had!")
    checkCase("menu OCR", repair("Enpthe liew Meditine?") == "Equip the new Medicine?")
    checkCase("longed OCR", repair("pro longed sleep") == "prolonged sleep")
    checkCase("skip broken UI", GameplayLogRepair299.waitForCleanReading("Traverse Ungrade Equipnjenl"))
    checkCase("skip broken systems", GameplayLogRepair299.waitForCleanReading("sys Tems."))
    checkCase("skip broken runs dry", GameplayLogRepair299.waitForCleanReading("nevertr UIy runs dry."))
    checkCase("valid systems not skipped", !GameplayLogRepair299.waitForCleanReading("systems."))
    checkCase("menu upgrade", GameplayLogRepair299.verifiedUiLabel("Upgrade Medicine Leave") == "İlacı geliştir · Ayrıl")
    checkCase("menu equip", GameplayLogRepair299.verifiedUiLabel("Equip the new Medicine?") == "Yeni ilacı kuşanmak istiyor musun?")
    checkCase("menu preset", GameplayLogRepair299.verifiedUiLabel("Controller Preset 1") == "Kontrol düzeni 1")
    checkCase("protect Hollow Heart", GameplayLogRepair299.repairTranslation(
        "I refused even the Hollow Heart", "Hollow ve Heart’yi bile reddettim") ==
        "Hollow Heart'i bile reddettim")
    checkCase("unrelated Hollow untouched", GameplayLogRepair299.repairTranslation(
        "The room is hollow", "Hollow ve Heart’yi bile reddettim") == "Hollow ve Heart’yi bile reddettim")
    checkCase("protect Xie Huichun", GameplayLogRepair299.repairTranslation(
        "Xie Huichun devoted his life", "Xie ve Huichun hayatlarını adadı") ==
        "Xie Huichun hayatlarını adadı")
    checkCase("protect Meigu Lake", GameplayLogRepair299.repairTranslation(
        "Meigu Lake is dangerous", "Meigu ve Lake’in doğusunda") == "Meigu Lake'in doğusunda")
    checkCase("protect White Serpent House", GameplayLogRepair299.repairTranslation(
        "White Serpent House has stopped", "White, Serpent ve House ilaç vermeyi bıraktı") ==
        "White Serpent House ilaç vermeyi bıraktı")
    checkCase("protect Phantom Edge", GameplayLogRepair299.repairTranslation(
        "Phantom Edge grows", "Phantom ile Edge değerini artırır") == "Phantom Edge değerini artırır")
    checkCase("third stage", GameplayLogRepair299.repairTranslation(
        "Third Stage, patient fell asleep", "Third Stage: Hasta uyudu") == "Üçüncü evre: Hasta uyudu")
    checkCase("exact sacrifice", SourceAwareDialogueFix.correct(
        "Xie Qiudong Sacrifice a few to save the many. That is how you save the world!") ==
        "Xie Qiudong: Çoğunluğu kurtarmak için birkaç kişiyi feda etmek gerekir. Dünya böyle kurtulur!")
    checkCase("unrelated sacrifice", SourceAwareDialogueFix.correct("I sacrifice nothing") == null)
    checkCase("exact soul", SourceAwareDialogueFix.correct(
        "Soul Could I replace my poisoned heart.. with one altered by Transcension?")?.startsWith("Soul:") == true)
    checkCase("source locked Hollow Heart", SourceAwareDialogueFix.correct(
        "[Soul] I refused even the Hollow Heart... How could I ever accept something so monstrous...") ==
        "[Soul] Hollow Heart'i bile reddettim... Böyle korkunç bir şeyi nasıl kabul edebilirim?")
    checkCase("source locked healers", SourceAwareDialogueFix.correct(
        "You're not like those other Transcended healers... you still have your senses?")?.contains("şifacılar") == true)
    checkCase("source locked Xie family", SourceAwareDialogueFix.correct(
        "Xie Huichun devoted his life to healing, but failed to save the Transcended.")?.contains("Xie Huichun") == true)
    checkCase("Xie OCR to source dictionary", SourceAwareDialogueFix.correct(repair(
        "Xie iudong 1gave her more time than she evershouldve hadl"))?.startsWith("Xie Qiudong:") == true)
    // Verify that the actual shipped OCR stage invokes these repairs.
    checkCase("production OCR repair", SubtitlePreparation.repair("Ata time this dangerous...") == "At a time this dangerous...")
    checkCase("production confidence guard", !SubtitlePreparation.confidentFirstRead(
        "Press after dodging or while sprinting to charge thg"))
    println("PASS $passes APK-299 log regressions")
}
