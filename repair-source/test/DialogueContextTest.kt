package com.example.altyazi

fun main() {
    val c = DialogueContextRepair()
    check(c.repair("Pout's great.") == "Pout's great.")
    check(c.repair("So, tell me about this Paul guy.") == "So, tell me about this Paul guy.")
    c.remember("So, tell me about this Paul guy.")
    check(c.repair("Pout's great.") == "Paul's great.")
    repeat(60) { check(c.repair("Pout's great.") == "Paul's great.") }
    check(c.repair("The Pout is on the table.") == "The Pout is on the table.")
    check(c.repair("She is pouting.") == "She is pouting.")
    c.reset()
    check(c.repair("Pout's great.") == "Pout's great.")
    check(c.repair("I moved in with Paul.") == "I moved in with Paul.")
    c.remember("I moved in with Paul.")
    repeat(41) { c.remember("Another line $it.") }
    check(c.repair("Pout's great.") == "Pout's great.")
    check(GoogleSourcePreparation.prepare("I'm coming, too.") == "I'm coming as well.")
    check(GoogleSourcePreparation.prepare("I love you, too.") == "I love you as well.")
    check(GoogleSourcePreparation.prepare("It's too late.") == "It's too late.")
    check(GoogleSourcePreparation.prepare("Too many people.") == "Too many people.")
    check(GoogleSourcePreparation.prepare("I came here, too much.") == "I came here, too much.")
    check(Phrases.lookup("I'm coming, too.") == "Ben de geliyorum")
    check(Phrases.lookup("I love you, too.") == "Ben de seni seviyorum")
    check(Phrases.lookup("It's too late.") == "Çok geç")
    check(Phrases.lookup("I like art.") == null)
    println("OK: 16 bağımsız bağlam, Google hazırlığı ve tam eşleşme kontrolü")
}
