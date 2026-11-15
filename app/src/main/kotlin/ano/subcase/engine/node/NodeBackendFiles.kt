package ano.subcase.engine.node

import android.content.Context
import ano.subcase.util.ConfigStore
import java.io.File

/**
 * 管理 Node 后端的 `sub-store.bundle.js`：APK 内置一份作为离线兜底，
 * 运行时由 SubStore 更新流程下载到 `filesDir/backend-node/<version>/`。
 */
object NodeBackendFiles {
    const val BUNDLE_NAME = "sub-store.bundle.js"
    const val ASSET_PATH = "backend-node/$BUNDLE_NAME"

    /** 与 assets/backend-node/sub-store.bundle.js 首行注释保持一致。 */
    const val BUNDLED_VERSION = "2.44.8"

    /** 官方 bundle 约 2.4~3.4MB，低于该值视为下载/复制不完整。 */
    private const val MIN_BUNDLE_BYTES = 1_000_000L

    private val safeVersion = Regex("[A-Za-z0-9._-]+")

    fun directory(context: Context): File = File(context.filesDir, "backend-node")

    fun versionDir(context: Context, version: String = ConfigStore.localNodeBackendVersion): File =
        File(directory(context), version)

    fun bundleFile(context: Context, version: String = ConfigStore.localNodeBackendVersion): File =
        File(versionDir(context, version), BUNDLE_NAME)

    /** 确保当前配置版本的 bundle 可用；缺失时释放 APK 内置版本。 */
    fun ensureInstalled(context: Context): File {
        val configuredVersion = ConfigStore.localNodeBackendVersion
        if (isSafeVersion(configuredVersion)) {
            val configured = bundleFile(context, configuredVersion)
            if (hasBundle(configured.parentFile!!)) return configured
        }

        val targetDir = versionDir(context, BUNDLED_VERSION).apply { mkdirs() }
        val target = File(targetDir, BUNDLE_NAME)
        if (!hasBundle(targetDir)) {
            context.assets.open(ASSET_PATH).use { input ->
                target.outputStream().use(input::copyTo)
            }
        }
        check(hasBundle(targetDir)) { "APK 内置 Node 后端脚本不完整" }
        ConfigStore.localNodeBackendVersion = BUNDLED_VERSION
        if (ConfigStore.lastNodeBackendInstalledAt <= 0L) {
            ConfigStore.lastNodeBackendInstalledAt = System.currentTimeMillis()
        }
        return target
    }

    fun hasBundle(directory: File): Boolean =
        File(directory, BUNDLE_NAME).let { it.isFile && it.length() >= MIN_BUNDLE_BYTES }

    fun isSafeVersion(version: String): Boolean =
        version.isNotBlank() && !version.contains("..") && safeVersion.matches(version)

    /** 校验下载结果，避免把 GitHub HTML 错误页当成后端脚本。 */
    fun validate(bundle: File): Result<Unit> = runCatching {
        check(bundle.isFile) { "Node 后端脚本不存在" }
        check(bundle.length() >= MIN_BUNDLE_BYTES) { "Node 后端脚本大小异常：${bundle.length()}" }
        val text = bundle.readText()
        check(text.contains("SUB_STORE_BACKEND_API_PORT")) { "Node 后端脚本内容异常" }
    }

    /** 删除旧版本目录，只保留 [keepVersion]。 */
    fun deleteOtherVersions(context: Context, keepVersion: String) {
        versionDir(context).listFiles()
            ?.filter { it.isDirectory && it.name != keepVersion && hasBundle(it) }
            ?.forEach(File::deleteRecursively)
    }
}
