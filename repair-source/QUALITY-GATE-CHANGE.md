# GameLingo translation quality gate — experimental source patch

The existing 10.24 pipeline now runs a source-aware structural check on the fast Google/Firefox translation output. If a declarative English subtitle becomes a Turkish question, or the output is unchanged/contains multiple English function words, the ready Firefox offline engine is asked for an alternative. The alternative is only selected if it passes the same checks. No API keys, token usage, or per-line memorized translations.

Limits: This is a conservative structural guard, NOT a new language model and NOT a general fluency fix. If Firefox is unavailable or also wrong, the original output remains. It does not reliably detect wrong meanings of polysemous words (e.g. intro), idioms, or profanity loss. There is no device APK test and no Android build validation in this environment. Version remains 10.24 to avoid misrepresenting a tested release.

Verified: 7/7 standalone Kotlin gate assertions pass. Four existing Python script checks pass (Google, quality cache, context guard, slang). The repository's test scripts are not pytest modules; invoking pytest on them causes a SystemExit during collection, so they were executed directly.
