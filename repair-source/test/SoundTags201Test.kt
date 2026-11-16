package com.example.altyazi
fun main() {
    val cases = listOf(
        "[exclaims]" to "[sesleniyor]",
        "[clangs]" to "[metal çınlaması]",
        "[tires squeal]" to "[lastikler gıcırdıyor]",
        "[both groaning]" to "[ikisi de inliyor]",
        "[footsteps thudding]" to "[tok ayak sesleri]",
        "[fading groan]" to "[giderek azalan inleme]",
        "[brakes screeching]" to "[frenler gıcırdıyor]",
        "[horn honking]" to "[korna çalıyor]",
        "[engine revving]" to "[motor devirleniyor]",
        "[pained groaning]" to "[acılı inliyor]"
    )
    cases.forEach { (en, tr) -> check(SoundTags.translate(en) == tr) { "$en -> ${SoundTags.translate(en)} expected $tr" } }
    println("SOUND TAGS 201: ${cases.size}/${cases.size} PASS")
}
