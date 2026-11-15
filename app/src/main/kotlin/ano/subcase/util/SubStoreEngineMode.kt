package ano.subcase.util

import androidx.annotation.StringRes
import ano.subcase.R

/** Sub-Store 后端引擎实现。 */
enum class SubStoreEngineMode(
    @StringRes val labelRes: Int,
) {
    /** Kotlin/Ktor + WebView 模拟 Loon API。 */
    WEBVIEW(R.string.substore_engine_webview),

    /** Javet 内置 Node.js 运行时，执行官方 sub-store.bundle.js（默认）。 */
    NODE(R.string.substore_engine_node);

    companion object {
        fun fromStored(value: String?): SubStoreEngineMode =
            entries.firstOrNull { it.name == value } ?: NODE
    }
}
