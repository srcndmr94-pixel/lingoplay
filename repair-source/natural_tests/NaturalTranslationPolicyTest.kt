import com.example.altyazi.NaturalTranslationPolicy10427 as Policy

fun main() {
    data class Case(val balanced: Boolean, val engine: Boolean, val input: String, val expected: Boolean)
    val cases = listOf(
        Case(true,true,"What's happening?",true),
        Case(true,true,"They've never been out of their tanks.",true),
        Case(true,true,"Yo, what the fuck?",true),
        Case(true,true,"[Eve] What's happening?",true),
        Case(true,true,"[gasps]",false),
        Case(true,true,"",false),
        Case(false,true,"What happened?",false),
        Case(true,false,"What happened?",false),
        Case(true,true,"I guess they're coming.",true),
        Case(true,true,"A completely unfamiliar sentence.",true)
    )
    for (c in cases) {
        check(Policy.modelFirst(c.balanced,c.engine,c.input)==c.expected) { "model-first: ${c.input}" }
    }
    check(Policy.dependsOnContext("They've never been outside."))
    check(Policy.dependsOnContext("Here we go."))
    check(!Policy.dependsOnContext("Everybody put your hands up!"))
    check(!Policy.dependsOnContext("The ship has arrived."))
    check(!Policy.dependsOnContext("Nobody was speaking."))
    println("15/15 NATURAL POLICY PASS")
}
