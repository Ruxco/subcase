package ano.subcase.engine.node

import android.os.Looper
import ano.subcase.engine.SubStoreEngine
import com.caoccao.javet.enums.V8AwaitMode
import com.caoccao.javet.interception.logging.JavetStandardConsoleInterceptor
import com.caoccao.javet.interop.NodeRuntime
import com.caoccao.javet.interop.V8Host
import com.caoccao.javet.interop.options.NodeRuntimeOptions
import org.json.JSONObject
import timber.log.Timber
import java.io.File

/**
 * 使用 Javet 内置的 Node.js 运行时执行官方 `sub-store.bundle.js`。
 *
 * 运行时由 Node 自己监听前端与后端两个端口；[bundleFile] 更新后需要重启引擎生效。
 */
class NodeEngine(
    private val bundleFile: File,
    private val frontendDir: File,
    private val dataDir: File,
    private val host: String,
    private val lanIp: String,
    private val frontendPort: Int,
    private val backendPort: Int,
    private val onFailure: (NodeEngine, Throwable) -> Unit,
) : SubStoreEngine {

    private val lock = Any()
    private var runtime: NodeRuntime? = null
    private var worker: Thread? = null
    private var running = false

    override fun startServer() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "NodeEngine 必须在主线程启动" }
        synchronized(lock) {
            check(!running && worker == null) { "Node 引擎已启动" }
            require(bundleFile.isFile) { "Node 后端脚本缺失：${bundleFile.absolutePath}" }
            frontendDir.mkdirs()
            dataDir.mkdirs()

            val node = try {
                createRuntime()
            } catch (error: Throwable) {
                throw IllegalStateException(
                    "Node 运行时初始化失败：${error.message ?: error.javaClass.simpleName}",
                    error,
                )
            }

            try {
                node.getExecutor(environmentScript()).executeVoid()
            } catch (error: Throwable) {
                runCatching { node.close() }
                throw IllegalStateException(
                    "Node 环境变量初始化失败：${error.message ?: error.javaClass.simpleName}",
                    error,
                )
            }

            runtime = node
            running = true
            worker = Thread({ runNode(node) }, "SubCaseNode").apply {
                isDaemon = false
                start()
            }
        }
    }

    override fun stopServer() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "NodeEngine 必须在主线程停止" }
        var node: NodeRuntime? = null
        var thread: Thread? = null
        synchronized(lock) {
            if (!running && worker == null) return
            running = false
            node = runtime
            thread = worker
        }
        node?.let { current ->
            runCatching { current.terminateExecution() }
                .onFailure { Timber.w(it, "终止 Node 运行时失败") }
        }
        thread?.let { current ->
            if (current !== Thread.currentThread()) {
                runCatching { current.join(STOP_TIMEOUT_MS) }
                    .onFailure { Timber.w(it, "等待 Node 运行时退出失败") }
                if (current.isAlive) {
                    // 避免残留线程阻塞下一次启动；旧运行时已被 terminateExecution 终止。
                    synchronized(lock) {
                        if (worker === current) worker = null
                        if (runtime === node) runtime = null
                    }
                    Timber.w("Node 运行时未在 %d ms 内退出", STOP_TIMEOUT_MS)
                }
            }
        }
    }

    private fun runNode(node: NodeRuntime) {
        var failure: Throwable? = null
        try {
            node.getExecutor(bundleFile).executeVoid()
            while (isRunning()) {
                node.await(V8AwaitMode.RunNoWait)
            }
        } catch (error: Throwable) {
            if (isRunning()) failure = error else Timber.d(error, "Node 运行时已按请求停止")
        } finally {
            synchronized(lock) {
                running = false
                if (runtime === node) runtime = null
                if (worker === Thread.currentThread()) worker = null
            }
            runCatching { node.isStopping = true }.onFailure { Timber.w(it) }
            runCatching { node.close() }.onFailure { Timber.w(it, "关闭 Node 运行时失败") }
            failure?.let { error ->
                Timber.e(error, "Node 后端运行失败")
                onFailure(this, error)
            }
        }
    }

    private fun isRunning(): Boolean = synchronized(lock) { running }

    private fun createRuntime(): NodeRuntime {
        val options = NodeRuntimeOptions().apply {
            // 与旧版实现保持一致：允许脚本读写数据目录。
            setConsoleArguments(arrayOf("--allow-fs-read", "--allow-fs-write"))
        }
        val node: NodeRuntime = V8Host.getNodeInstance().createV8Runtime(options)
        JavetStandardConsoleInterceptor(node).register(node.globalObject)
        node.allowEval(true)
        return node
    }

    /** 把 Sub-Store 依赖的环境变量写入 Node 的 process.env。 */
    private fun environmentScript(): String = buildString {
        append("(function () {\n")
        fun setEnv(key: String, value: Any) {
            append("  process.env.").append(key).append(" = ")
                .append(JSONObject.quote(value.toString())).append(";\n")
        }
        setEnv("SUB_STORE_FRONTEND_HOST", host)
        setEnv("SUB_STORE_FRONTEND_PORT", frontendPort)
        setEnv("SUB_STORE_FRONTEND_PATH", frontendDir.absolutePath)
        setEnv("SUB_STORE_BACKEND_API_HOST", host)
        setEnv("SUB_STORE_BACKEND_API_PORT", backendPort)
        setEnv("SUB_STORE_DATA_BASE_PATH", dataDir.absolutePath)
        // Node 版 Sub-Store 要求设置该变量，脚本操作/脚本过滤/修改响应才会执行。
        setEnv("SUB_STORE_FRONTEND_BACKEND_PATH", "/")
        // 前端静态页在 frontendPort，API 在 backendPort，属于跨域请求。
        setEnv("SUB_STORE_CORS_ALLOWED_ORIGINS", corsOrigins())
        append("})();")
    }

    private fun corsOrigins(): String = buildList {
        add("http://127.0.0.1:$frontendPort")
        add("http://localhost:$frontendPort")
        if (lanIp.isNotBlank()) add("http://$lanIp:$frontendPort")
    }.joinToString(",")

    private companion object {
        const val STOP_TIMEOUT_MS = 3_000L
    }
}
