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

/**
 * Yours first (v0.14.0) — the owner's own house leads the
 * list, by standing rule: Pipavia (the company) and Conduit
 * (its connector marketplace). Conduit is a connector, not a
 * chat model: it feeds live data to Milo's tasks, so it is
 * added as a source with a key, but never offered as a
 * brain.
 */
val YOUR_PRESETS: List<ProviderPreset> = listOf(
    ProviderPreset(
        "pipavia", "Pipavia",
        "https://pipavia.com", "",
        "Your company — the house behind Conduit and your " +
            "other products.",
    ),
    ProviderPreset(
        "conduit", "Conduit — Filings & Fundamentals",
        "https://conduit-filings-fundamentals." +
            "nrupalakolkar.workers.dev/mcp",
        "",
        "Your own connector marketplace, by Pipavia: 24 " +
            "tools of live SEC filings and fundamentals " +
            "data. A connector feeds data to Milo — it " +
            "isn't a chat brain.",
    ),
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
