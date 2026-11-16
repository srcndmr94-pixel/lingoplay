package com.example.altyazi
fun main() {
    val pairs = listOf(
        Triple("Adam and Betsy Wilkins are here.", "Adam, Betsy ve Wilkins burada.", "Adam ve Betsy Wilkins burada."),
        Triple("Nora and Elise Chambers had arrived.", "Nora, Elise ve Chambers gelmişti.", "Nora ve Elise Chambers gelmişti."),
        Triple("Adam and Betsy Wilkins are here.", "Adam ve Betsy Wilkins burada.", "Adam ve Betsy Wilkins burada."),
        Triple("Adam and Betsy Wilkins are here.", "Betsy Wilkins burada.", "Betsy Wilkins burada."),
        Triple("Nora and Elise Chambers had arrived.", "Nora, Elise ve farklı biri geldi.", "Nora, Elise ve farklı biri geldi.")
    )
    pairs.forEach { (src, candidate, expected) ->
        val actual = ProperNameStructure10427.repair(src,candidate)
        check(actual == expected) { "$src -> $actual expected $expected" }
    }
    println("NAME STRUCTURE: ${pairs.size}/${pairs.size} PASS")
}
