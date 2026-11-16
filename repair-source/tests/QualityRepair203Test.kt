import com.example.altyazi.TranslationQualityGate as Q
import com.example.altyazi.StableCaptionGate199
import com.example.altyazi.ControlledCaptionUpgrade203

fun main() {
    var pass = 0
    fun verify(condition: Boolean, description: String) {
        check(condition) { description }
        pass++
    }
    val s = "Be cool, don't... you know."
    verify(Q.check(s, "Sakin olun, sakın... bilirsiniz işte.") == null,
        "sakın correctly preserves the negative sense")
    verify(Q.check(s, "Sakin ol, yapma... bilirsin.") == null,
        "do not reject the OPUS expression of a prohibition")
    verify(Q.check(s, "Sakin olun, biliyorsunuz işte.") == Q.Problem.LOST_NEGATION,
        "still detect genuinely lost negation")
    val who = "What the fuck y'all doing in here?"
    verify(Q.check(who, "Burada neler yapıyorum?") == Q.Problem.PERSON_REVERSAL,
        "detect clear 2nd-person to 1st-person reversal")
    verify(Q.mustReject(Q.check(who, "Burada neler yapıyorum?")),
        "block first-person fallback")
    verify(Q.check(who, "Siz burada ne yapıyorsunuz?") == Q.Problem.LOST_PROFANITY,
        "clear Google result has only a profanity warning")
    verify(!Q.mustReject(Q.Problem.LOST_PROFANITY),
        "profanity warning is not a forced rejection")
    verify(Q.acceptableStrong(Q.Problem.LOST_PROFANITY),
        "strong engines can retain grammatical output with a profanity warning")
    verify(!Q.acceptableStrong(Q.Problem.PERSON_REVERSAL),
        "structural person reversal is never an acceptable strong result")
    verify(Q.check("I've always been there for you.", "Her zaman senin için oradaydım.") == null,
        "do not flag existing first-person dialogue")
    verify(Q.check("We told you I am here.", "Ben buradayım dedik.") != Q.Problem.PERSON_REVERSAL,
        "mixed-person English is excluded from simple polarity detector")
    verify(Q.check("What are you doing?", "Ne yapıyorsun?") == null,
        "normal second-person translation still passes")
    verify(!ControlledCaptionUpgrade203.clearlyBetter(null, null),
        "different good translations do not trigger an upgrade")
    verify(ControlledCaptionUpgrade203.clearlyBetter(Q.Problem.LOST_NEGATION, null),
        "known loss fixed by strong engine may upgrade")
    verify(!ControlledCaptionUpgrade203.clearlyBetter(Q.Problem.LOST_NEGATION, Q.Problem.LOST_PROFANITY),
        "replacement must have no quality issue")
    var currentId = 100L
    val gate = StableCaptionGate199()
    fun claim(id: Long, t: Long, local: Boolean, issue: Q.Problem? = null,
              visible: Boolean = true) = gate.decide203(id, { it == currentId }, t, local, visible, issue)
    verify(claim(100, 1000, true, Q.Problem.LOST_PROFANITY) == StableCaptionGate199.Decision.FIRST,
        "local draft is first displayed")
    verify(claim(100, 1400, false, null) == StableCaptionGate199.Decision.UPGRADE,
        "clean strong result may replace flawed local draft once")
    verify(claim(100, 1500, false, null) == StableCaptionGate199.Decision.LOCKED,
        "never upgrade twice")
    currentId = 101
    verify(claim(100, 1600, false, null) == StableCaptionGate199.Decision.LOCKED,
        "old run cannot display")
    verify(claim(101, 2000, false, null) == StableCaptionGate199.Decision.FIRST,
        "normal strong result is displayed")
    verify(claim(101, 2200, false, null) == StableCaptionGate199.Decision.LOCKED,
        "normal strong result stays stable")
    currentId = 102
    verify(claim(102, 3000, true, Q.Problem.LOST_PROFANITY) == StableCaptionGate199.Decision.FIRST,
        "new provisional result")
    verify(claim(102, 4500, false, null) == StableCaptionGate199.Decision.LOCKED,
        "late answer outside 1200ms cannot redraw")
    currentId = 103
    verify(claim(103, 5000, true, Q.Problem.MISSING_DETAIL) == StableCaptionGate199.Decision.FIRST,
        "another local preview")
    verify(claim(103, 5200, false, null, visible=false) == StableCaptionGate199.Decision.LOCKED,
        "cannot redraw vanished caption")
    currentId = 104
    verify(claim(104, 5000, true, null) == StableCaptionGate199.Decision.FIRST,
        "accurate local preview")
    verify(claim(104, 5200, false, null) == StableCaptionGate199.Decision.LOCKED,
        "style change alone is insufficient")
    println("PASS $pass/$pass quality/upgrade regression tests")
}
