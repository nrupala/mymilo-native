package org.aimlds.mymilo.local

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import java.io.File

/**
 * Models that can live on the phone (v0.13.0). The catalogue
 * is small and curated — ungated GGUF releases only, with
 * exact byte sizes verified against the publisher, so the
 * picker can be honest about fit: it reads this phone's
 * memory and free space and says, per model, whether it
 * runs well, works slower, or is too big. That verdict is
 * the product: nobody should download 2 GB to find out.
 */
data class LocalModel(
    val id: String,
    val name: String,
    val maker: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    /** Rough resident-memory need once loaded, in MB. */
    val ramNeedMB: Int,
    val blurb: String,
)

object LocalModels {

    val CATALOG: List<LocalModel> = listOf(
        LocalModel(
            id = "qwen25-05b",
            name = "Qwen 2.5 · 0.5B",
            maker = "Alibaba",
            fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            url = "https://huggingface.co/Qwen/" +
                "Qwen2.5-0.5B-Instruct-GGUF/resolve/main/" +
                "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            sizeBytes = 491_400_032,
            ramNeedMB = 900,
            blurb = "The smallest. Quick answers, short " +
                "questions, summaries — modest smarts, " +
                "runs almost anywhere.",
        ),
        LocalModel(
            id = "llama32-1b",
            name = "Llama 3.2 · 1B",
            maker = "Meta",
            fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            url = "https://huggingface.co/bartowski/" +
                "Llama-3.2-1B-Instruct-GGUF/resolve/main/" +
                "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            sizeBytes = 807_694_464,
            ramNeedMB = 1250,
            blurb = "Light and capable for everyday chat " +
                "and drafting.",
        ),
        LocalModel(
            id = "qwen25-15b",
            name = "Qwen 2.5 · 1.5B",
            maker = "Alibaba",
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            url = "https://huggingface.co/Qwen/" +
                "Qwen2.5-1.5B-Instruct-GGUF/resolve/main/" +
                "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            sizeBytes = 1_117_320_736,
            ramNeedMB = 1600,
            blurb = "A step up in reasoning and writing, " +
                "still light on the phone.",
        ),
        LocalModel(
            id = "llama32-3b",
            name = "Llama 3.2 · 3B",
            maker = "Meta",
            fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            url = "https://huggingface.co/bartowski/" +
                "Llama-3.2-3B-Instruct-GGUF/resolve/main/" +
                "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            sizeBytes = 2_019_377_696,
            ramNeedMB = 2600,
            blurb = "The strongest Llama that fits a " +
                "phone comfortably. Needs a roomy phone.",
        ),
        LocalModel(
            id = "qwen25-3b",
            name = "Qwen 2.5 · 3B",
            maker = "Alibaba",
            fileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
            url = "https://huggingface.co/Qwen/" +
                "Qwen2.5-3B-Instruct-GGUF/resolve/main/" +
                "qwen2.5-3b-instruct-q4_k_m.gguf",
            sizeBytes = 2_104_932_768,
            ramNeedMB = 2700,
            blurb = "The strongest Qwen that fits a phone. " +
                "Needs a roomy phone.",
        ),
    )

    fun byId(id: String?): LocalModel? =
        CATALOG.firstOrNull { it.id == id }

    fun modelsDir(ctx: Context): File =
        File(ctx.filesDir, "models").apply { mkdirs() }

    fun fileFor(ctx: Context, model: LocalModel): File =
        File(modelsDir(ctx), model.fileName)

    fun isDownloaded(ctx: Context, model: LocalModel): Boolean {
        val f = fileFor(ctx, model)
        return f.exists() && f.length() == model.sizeBytes
    }

    /** This phone, measured — no permissions needed. */
    data class DeviceInfo(
        val ramMB: Int,
        val freeStorageMB: Long,
        val chip: String,
        val cores: Int,
    )

    fun probe(ctx: Context): DeviceInfo {
        val am = ctx.getSystemService(
            ActivityManager::class.java,
        )
        val mi = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        val stat = StatFs(ctx.filesDir.path)
        val chip = if (Build.VERSION.SDK_INT >= 31) {
            Build.SOC_MODEL.ifBlank { Build.HARDWARE }
        } else {
            Build.HARDWARE
        }
        return DeviceInfo(
            ramMB = (mi.totalMem / (1024 * 1024)).toInt(),
            freeStorageMB = stat.availableBytes / (1024 * 1024),
            chip = chip,
            cores = Runtime.getRuntime().availableProcessors(),
        )
    }

    enum class Verdict { RUNS_WELL, WORKS_SLOWER, TOO_BIG, NO_SPACE }

    fun verdict(model: LocalModel, device: DeviceInfo): Verdict {
        val sizeMB = model.sizeBytes / (1024 * 1024)
        if (device.freeStorageMB < sizeMB * 1.15) {
            return Verdict.NO_SPACE
        }
        return when {
            device.ramMB >= model.ramNeedMB * 3 / 2 ->
                Verdict.RUNS_WELL
            device.ramMB >= model.ramNeedMB ->
                Verdict.WORKS_SLOWER
            else -> Verdict.TOO_BIG
        }
    }

    fun verdictText(v: Verdict): String = when (v) {
        Verdict.RUNS_WELL -> "Runs well on your phone"
        Verdict.WORKS_SLOWER -> "Works — a little slower"
        Verdict.TOO_BIG -> "Too big for your phone's memory"
        Verdict.NO_SPACE -> "Not enough free space"
    }

    /** The largest model that runs well — the picker's pick. */
    fun bestFor(device: DeviceInfo): LocalModel? =
        CATALOG
            .filter { verdict(it, device) == Verdict.RUNS_WELL }
            .maxByOrNull { it.sizeBytes }

    fun sizeText(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) {
            String.format("%.1f GB", mb / 1024.0)
        } else {
            String.format("%.0f MB", mb)
        }
    }
}

/** Chat prompt formatting per model family. */
object LocalPrompt {

    private const val SYSTEM =
        "You are Milo, a helpful assistant running entirely " +
            "on this phone. Answer briefly and clearly."

    fun format(
        model: LocalModel,
        history: List<Pair<String, String>>,
    ): String {
        val sb = StringBuilder()
        if (model.id.startsWith("llama")) {
            sb.append("<|begin_of_text|>")
            sb.append(
                "<|start_header_id|>system<|end_header_id|>" +
                    "\n\n" + SYSTEM + "<|eot_id|>",
            )
            for ((role, content) in history) {
                val r = if (role == "assistant") {
                    "assistant"
                } else {
                    "user"
                }
                sb.append(
                    "<|start_header_id|>" + r +
                        "<|end_header_id|>\n\n" + content +
                        "<|eot_id|>",
                )
            }
            sb.append(
                "<|start_header_id|>assistant" +
                    "<|end_header_id|>\n\n",
            )
        } else {
            sb.append("<|im_start|>system\n")
            sb.append(SYSTEM).append("<|im_end|>\n")
            for ((role, content) in history) {
                val r = if (role == "assistant") {
                    "assistant"
                } else {
                    "user"
                }
                sb.append("<|im_start|>").append(r).append('\n')
                sb.append(content).append("<|im_end|>\n")
            }
            sb.append("<|im_start|>assistant\n")
        }
        return sb.toString()
    }
}
