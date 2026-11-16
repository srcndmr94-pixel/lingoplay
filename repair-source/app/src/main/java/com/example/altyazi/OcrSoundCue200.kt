package com.example.altyazi

/** Genel kapalı altyazı ses etiketlerinde OCR kaynaklı kırık parantezleri onarır.
 *  Diyalog metni değiştirilmez; yalnızca ses-etiketi olduğundan emin olunan parçalar.
 */
object OcrSoundCue200 {
    private val verb = "(?:shouts|shouting|grunts|grunting|groans|groaning|snarls|snarling|pants|panting|screams|screaming)"
    private val tail = Regex("""(\s[-–]\s)([\p{Lu}][\p{L}'’.\-]*(?:\s+[\p{Lu}][\p{L}'’.\-]*){0,2})\s+($verb)\]""")
    private val missingClose = Regex("""\[([^\[\]\r\n]{1,65}?\s$verb)l(?=\s*$)""", RegexOption.IGNORE_CASE)
    private val leadingOpen = Regex("""^[Il|]\s+(shouting|grunting|groaning|screaming)(?=\s*[-–]\s*\[)""", RegexOption.IGNORE_CASE)
    fun repair(source: String): String {
        var s = tail.replace(source) { m -> m.groupValues[1] + "[" + m.groupValues[2] + " " + m.groupValues[3] + "]" }
        s = missingClose.replace(s) { m -> "[" + m.groupValues[1] + "]" }
        s = leadingOpen.replace(s) { m -> "[" + m.groupValues[1] + "]" }
        return s
    }
}
