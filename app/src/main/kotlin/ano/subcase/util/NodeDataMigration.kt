package ano.subcase.util

import android.content.Context
import ano.subcase.caseApp
import ano.subcase.engine.bridge.PersistentStoreBridge
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File

/**
 * Sub-Store 两种引擎的数据格式不同：
 * - WebView 引擎：全部数据在一个 SharedPreferences 文件里，主数据位于 `sub-store` 键；
 * - Node 引擎：`filesDir/data/sub-store.json` + `filesDir/data/root.json`。
 *
 * 切换引擎时做一次性迁移，避免用户数据丢失。
 */
object NodeDataMigration {
    private const val CACHE_KEY = "sub-store"
    private const val CACHE_FILE = "sub-store.json"
    private const val ROOT_FILE = "root.json"

    fun prepare(engine: SubStoreEngineMode) {
        val previous = ConfigStore.lastDataEngine
        if (previous == engine) return
        Timber.i("迁移 Sub-Store 数据所有权：%s -> %s", previous, engine)
        when (engine) {
            SubStoreEngineMode.NODE -> importToFiles()
            SubStoreEngineMode.WEBVIEW -> exportToKv()
        }
        ConfigStore.lastDataEngine = engine
    }

    private fun dataDir(): File = File(caseApp.filesDir, "data").apply { mkdirs() }

    private fun kv() = caseApp.getSharedPreferences(
        PersistentStoreBridge.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    /** WebView KV -> Node 数据文件。 */
    private fun importToFiles() {
        val prefs = kv()
        val dir = dataDir()
        val cache = prefs.getString(CACHE_KEY, null)
        val cacheFile = File(dir, CACHE_FILE)
        if (cache.isNullOrBlank() && cacheFile.exists()) {
            // 没有可导入的 KV 时不覆盖已有 Node 数据（例如降级安装后的首次启动）。
            Timber.w("KV 中没有 Sub-Store 数据，保留现有 Node 数据文件")
            return
        }
        val cacheText = cache?.takeIf { it.isNotBlank() } ?: "{}"
        runCatching { JSONObject(cacheText) }.getOrElse { error ->
            throw IllegalStateException("WebView 数据不是有效 JSON，无法迁移到 Node 引擎", error)
        }
        cacheFile.writeText(cacheText)

        val root = JSONObject()
        prefs.all.forEach { (key, value) ->
            if (key == CACHE_KEY) return@forEach
            when (value) {
                is String -> root.put(key, parseStoredValue(value))
                null -> Unit
                else -> root.put(key, value)
            }
        }
        File(dir, ROOT_FILE).writeText(root.toString())
        Timber.i("已从 KV 导入 Node 数据：%d 个 root 键", root.length())
    }

    /** Node 数据文件 -> WebView KV。 */
    private fun exportToKv() {
        val dir = dataDir()
        val cacheFile = File(dir, CACHE_FILE)
        val rootFile = File(dir, ROOT_FILE)
        val cache = cacheFile.takeIf { it.isFile }
            ?.readText()
            ?.takeIf { it.isNotBlank() }
        val root = rootFile.takeIf { it.isFile }?.let { file ->
            runCatching { JSONObject(file.readText()) }.getOrElse { error ->
                throw IllegalStateException("Node 数据 root.json 解析失败", error)
            }
        }

        val rootEmpty = root == null || !root.keys().hasNext()
        if ((cache == null || cache == "{}") && rootEmpty) {
            // 避免 Node 引擎尚未写入数据时清空 WebView 中已有配置。
            Timber.w("Node 数据为空，保留现有 KV")
            return
        }

        val editor = kv().edit().clear()
        if (cache != null) editor.putString(CACHE_KEY, cache)
        if (root != null) {
            val rootObject = root
            rootObject.keys().forEach { key ->
                if (key == CACHE_KEY) return@forEach
                val value = rootObject.opt(key)
                if (value != null && value != JSONObject.NULL) {
                    editor.putString(key, storageString(value))
                }
            }
        }
        check(editor.commit()) { "写入 Sub-Store KV 失败" }
        Timber.i("已把 Node 数据导出到 KV")
    }

    /** Loon KV 只能存字符串：JSON 容器还原成对象，其余保持字符串。 */
    private fun parseStoredValue(value: String): Any {
        val trimmed = value.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val parsed = runCatching {
                if (trimmed.startsWith("{")) JSONObject(trimmed) else JSONArray(trimmed)
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return value
    }

    private fun storageString(value: Any): String = when (value) {
        is JSONObject, is JSONArray -> value.toString()
        else -> value.toString()
    }
}
