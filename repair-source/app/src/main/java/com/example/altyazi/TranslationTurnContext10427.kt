package com.example.altyazi

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Context belongs to one translation job; never leak it across overlapping subtitles. */
class TranslationTurnContext10427(val previousEnglish: String) :
    AbstractCoroutineContextElement(TranslationTurnContext10427) {
    companion object Key : CoroutineContext.Key<TranslationTurnContext10427>
}
