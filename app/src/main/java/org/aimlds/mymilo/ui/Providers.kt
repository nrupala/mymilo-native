package org.aimlds.mymilo.ui

/**
 * The provider catalogue (v0.11.0): the majors, as ready-made
 * source cards. Picking one is "ease of doing business" — the
 * card carries the address and a sensible starting model; the
 * user adds only their key. Every field stays editable in the
 * add dialog, because providers move.
 */
data class ProviderPreset(
    val kind: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val blurb: String,
)

val PROVIDER_PRESETS: List<ProviderPreset> = listOf(
    ProviderPreset(
        "openrouter", "OpenRouter",
        "https://openrouter.ai/api/v1", "deepseek/deepseek-chat",
        "One key, hundreds of models — free and paid.",
    ),
    ProviderPreset(
        "opencode", "OpenCode Zen",
        "https://opencode.ai/zen/v1", "big-pickle",
        "OpenCode's own models, including free ones.",
    ),
    ProviderPreset(
        "anthropic", "Anthropic",
        "https://api.anthropic.com/v1", "claude-sonnet-4-5",
        "Claude models, straight from Anthropic.",
    ),
    ProviderPreset(
        "openai", "OpenAI",
        "https://api.openai.com/v1", "gpt-5",
        "OpenAI's models, straight from OpenAI.",
    ),
    ProviderPreset(
        "codex", "Codex (OpenAI)",
        "https://api.openai.com/v1", "gpt-5-codex",
        "OpenAI's coding models.",
    ),
    ProviderPreset(
        "google", "Google Gemini",
        "https://generativelanguage.googleapis.com/v1beta/openai/",
        "gemini-2.5-pro",
        "Gemini models, straight from Google.",
    ),
    ProviderPreset(
        "xai", "xAI — Grok",
        "https://api.x.ai/v1", "grok-4",
        "Grok models from xAI.",
    ),
    ProviderPreset(
        "mistral", "Mistral",
        "https://api.mistral.ai/v1", "mistral-large-latest",
        "Mistral models, straight from Mistral AI.",
    ),
    ProviderPreset(
        "deepseek", "DeepSeek",
        "https://api.deepseek.com/v1", "deepseek-chat",
        "DeepSeek models, straight from DeepSeek.",
    ),
    ProviderPreset(
        "cloudflare", "Cloudflare Workers AI",
        "https://api.cloudflare.com/client/v4/accounts/" +
            "your-account-id/ai/v1",
        "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
        "Models on your own Cloudflare account. Replace " +
            "your-account-id in the address with your account ID.",
    ),
    ProviderPreset(
        "copilot", "GitHub Copilot",
        "https://api.githubcopilot.com", "gpt-4o",
        "Your Copilot subscription's models. Note: GitHub aims " +
            "Copilot keys at its own editors — it may decline " +
            "to answer here.",
    ),
)
