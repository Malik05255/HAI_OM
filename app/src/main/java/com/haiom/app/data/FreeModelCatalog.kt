package com.haiom.app.data

import com.haiom.app.model.AuthMode
import com.haiom.app.model.FreeCodingModel

/**
 * Hard allowlist: only models documented by the upstream free-provider registry as
 * keyless/free are eligible. Unknown or premium model IDs are rejected by policy.
 * The numeric priority is a routing preference, not a benchmark percentage.
 */
object FreeModelCatalog {
    const val POLLINATIONS_ENDPOINT = "https://gen.pollinations.ai/v1/chat/completions"

    val models: List<FreeCodingModel> = listOf(
        FreeCodingModel("qwen-coder", "Qwen Coder", "Pollinations", POLLINATIONS_ENDPOINT, 100, AuthMode.OPTIONAL_FREE_KEY, "مخصص للبرمجة"),
        FreeCodingModel("deepseek", "DeepSeek", "Pollinations", POLLINATIONS_ENDPOINT, 96, AuthMode.OPTIONAL_FREE_KEY, "استدلال وإصلاح أخطاء"),
        FreeCodingModel("openai-large", "OpenAI Large", "Pollinations", POLLINATIONS_ENDPOINT, 94, AuthMode.OPTIONAL_FREE_KEY, "مهام برمجية كبيرة"),
        FreeCodingModel("grok", "Grok", "Pollinations", POLLINATIONS_ENDPOINT, 90, AuthMode.OPTIONAL_FREE_KEY, "بديل قوي"),
        FreeCodingModel("mistral", "Mistral", "Pollinations", POLLINATIONS_ENDPOINT, 84, AuthMode.OPTIONAL_FREE_KEY, "سريع وخفيف"),
        FreeCodingModel("openai", "OpenAI", "Pollinations", POLLINATIONS_ENDPOINT, 82, AuthMode.OPTIONAL_FREE_KEY, "بديل عام"),
        FreeCodingModel("openai-fast", "OpenAI Fast", "Pollinations", POLLINATIONS_ENDPOINT, 76, AuthMode.OPTIONAL_FREE_KEY, "للمهام السريعة"),
        FreeCodingModel("gemini-flash-lite-3.1", "Gemini Flash Lite 3.1", "Pollinations", POLLINATIONS_ENDPOINT, 72, AuthMode.OPTIONAL_FREE_KEY, "بديل اقتصادي")
    ).sortedByDescending { it.codingPriority }

    private val allowedIds = models.mapTo(hashSetOf()) { it.id }
    private val allowedHosts = setOf("gen.pollinations.ai")

    fun isAllowed(model: FreeCodingModel): Boolean =
        model.id in allowedIds && runCatching { java.net.URI(model.endpoint).host in allowedHosts }.getOrDefault(false)

    fun requireAllowed(model: FreeCodingModel) {
        require(isAllowed(model)) { "Paid or untrusted model/provider rejected: ${model.provider}/${model.id}" }
    }
}
