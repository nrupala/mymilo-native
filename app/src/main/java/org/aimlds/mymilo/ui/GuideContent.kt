package org.aimlds.mymilo.ui

/** Static Guide content (v0.8.0) — mirrors the web /guide page.
 *  The skills catalogue itself is NOT here: it renders live from
 *  the synced SkillEntity rows so it can never drift. */

fun humanSkillName(name: String): String =
    name.split("-").joinToString(" ") { part ->
        part.replaceFirstChar { it.uppercase() }
    }

val CATEGORY_ORDER = listOf(
    "Money & markets",
    "Engineering & code",
    "Electrical & energy",
    "Thinking & decisions",
    "Mind & personal growth",
    "Everyday life",
    "Writing & documents",
    "Security & privacy",
    "How Milo works",
    "More skills",
)

val QUICK_START = listOf(
    "Connect your phone: on the Devices page of your MyMilo " +
        "website, create a token and paste it into the app's " +
        "Connect screen.",
    "Ask anything: type in the message box, or tap the " +
        "microphone and talk.",
    "Summon Milo anywhere: make Milo your assistant (drawer → " +
        "Assistant), then long-press Home or swipe up from a " +
        "bottom corner and speak.",
    "Run a skill on purpose: open Skills, pick one, and tell " +
        "it what to work on.",
    "Check the sources under each answer — Milo names what it " +
        "used: a skill, your memory, the web, live market data.",
)

data class UseCase(
    val title: String,
    val line: String,
    val prompt: String,
    /** When set, Try runs this skill directly (server v0.41.0). */
    val skillName: String? = null,
)

val USE_CASES = listOf(
    UseCase(
        "Start the day briefed",
        "Markets and what changed overnight, with sources.",
        "Brief me on the market today",
        "stock-analysis",
    ),
    UseCase(
        "Understand a stock",
        "A seven-step analyst pass — business, financials, " +
            "valuation, risks.",
        "Analyze AAPL stock for me, step by step",
        "stock-analysis",
    ),
    UseCase(
        "See your money clearly",
        "A household budget in plain terms, honest about " +
            "inflation.",
        "Help me with my household budget",
        "budget-engine",
    ),
    UseCase(
        "Fly for less",
        "What drives the fare on your route — and where the " +
            "real savings hide.",
        "Find me the cheapest way to fly Calgary to Mumbai in December",
        "travel-fares",
    ),
    UseCase(
        "Write it in your voice",
        "Emails, posts, and articles — direct, warm, economical.",
        "Write a polite email declining a meeting",
    ),
    UseCase(
        "Code that comes with proof",
        "Ask for code and its verification together.",
        "Write a function that validates Canadian postal codes, and verify it",
        "verified-code",
    ),
    UseCase(
        "A memory that works for you",
        "Tell Milo a fact once; it uses it when it matters.",
        "Remember that my passport renews in March",
    ),
    UseCase(
        "Quick answers, hands-free",
        "Summon the assistant, ask out loud — the exchange is " +
            "saved under “Assistant” in your chats.",
        "What is 15% of 240?",
    ),
)

val FAQS = listOf(
    "Where do Milo's answers come from?" to
        "From your own MyMilo server — everyday questions use " +
        "fast local models; harder ones take stronger routes; " +
        "fresh questions add live web search and market data. " +
        "Every answer lists its sources.",
    "What works without internet?" to
        "Calculations and unit conversions (instant, on this " +
        "phone), all your saved chats, and the skills list. " +
        "Questions that need the server are kept and answered " +
        "when you're back — Milo says so instead of failing " +
        "quietly.",
    "What does Milo remember about me?" to
        "Facts you share and the shape of past conversations, " +
        "kept on your server — never sold, never used for ads " +
        "(there are no ads). You can review and delete " +
        "remembered facts from your profile on the website.",
    "What exactly is a skill?" to
        "A playbook: a body of know-how — a method, a checklist, " +
        "deep expertise — that Milo applies to your request. " +
        "Open Skills to browse them all, read what each one " +
        "does, and run any of them on purpose.",
    "How do updates arrive?" to
        "The app checks quietly and tells you in the drawer " +
        "under Updates. Download, then Install — Android asks " +
        "for your tap on the installer; that's the platform's " +
        "rule for apps outside the Play Store.",
    "Who else can use my MyMilo?" to
        "Only accounts you allow and devices you connect. The " +
        "Devices page on the website shows every connected " +
        "phone — refresh its token or remove it there, any time.",
)
