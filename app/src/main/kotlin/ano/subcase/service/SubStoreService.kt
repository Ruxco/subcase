package ano.subcase.service

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import ano.subcase.engine.CaseEngine
import ano.subcase.engine.SubStoreEngine
import ano.subcase.engine.node.NodeBackendFiles
import ano.subcase.engine.node.NodeEngine
import ano.subcase.GlobalStatus
import ano.subcase.util.ConfigStore
import ano.subcase.util.NetworkUtil
import ano.subcase.util.NodeDataMigration
import ano.subcase.util.NotificationUtil
import ano.subcase.util.ServerListenConfig
import ano.subcase.util.SubStore
import ano.subcase.util.SubStoreEngineMode
import ano.subcase.util.currentServerConfig
import ano.subcase.util.runSubStoreUpdateLoop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

class SubStoreService : Service() {

    companion object {
        var caseEngine: SubStoreEngine? = null
            private set

        private const val NODE_READY_TIMEOUT_MS = 15_000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val updateLoopScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ownedEngine: SubStoreEngine? = null
    private var notificationStarted = false
    private var updateLoopJob: Job? = null
    private var readinessJob: Job? = null

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (caseEngine != null) return START_STICKY
            SubStoreServiceController.store.starting()
            notificationStarted = true
            NotificationUtil.startNotification(this)
            val config = currentServerConfig()
            val mode = ConfigStore.engineMode
            Timber.i("启动 Sub-Store 服务，引擎：%s", mode)

            // 切换引擎时先把数据搬到新引擎能读取的位置，失败则不启动服务。
            NodeDataMigration.prepare(mode)

            val engine = createEngine(mode, config)
            ownedEngine = engine
            caseEngine = engine
            engine.startServer()
            startUpdateLoop()
            if (mode == SubStoreEngineMode.NODE) {
                // Node 的 HTTP 监听是异步的；等端口真正可连接后再标记 Running。
                waitForNodeReady(engine, config)
            } else {
                SubStoreServiceController.store.running()
            }
            return START_STICKY
        } catch (error: Exception) {
            handleFailure(error)
            stopSelf(startId)
            return START_NOT_STICKY
        }
    }

    private fun createEngine(
        mode: SubStoreEngineMode,
        config: ServerListenConfig,
    ): SubStoreEngine = when (mode) {
        SubStoreEngineMode.WEBVIEW -> CaseEngine(
            context = this,
            backendPort = config.backend.port,
            frontendPort = config.frontend.port,
            host = config.frontend.host,
            onFailure = { source, error ->
                mainHandler.post {
                    // 仅当前 Service 所持有的实例可以触发整组停止。
                    if (ownedEngine === source && caseEngine === source) {
                        handleFailure(error)
                        stopSelf()
                    }
                }
            },
        )

        SubStoreEngineMode.NODE -> {
            requirePortsAvailable(config)
            val bundle = NodeBackendFiles.ensureInstalled(this)
            // ensureInstalled 可能刚释放内置版本，同步设置页使用的 Compose 状态。
            SubStore.localNodeBackendVersion = ConfigStore.localNodeBackendVersion
            SubStore.lastNodeBackendInstalledAt = ConfigStore.lastNodeBackendInstalledAt
            NodeEngine(
                bundleFile = bundle,
                frontendDir = File(filesDir, "frontend"),
                dataDir = File(filesDir, "data"),
                host = config.frontend.host,
                lanIp = GlobalStatus.lanIP.value,
                frontendPort = config.frontend.port,
                backendPort = config.backend.port,
                onFailure = { source, error ->
                    mainHandler.post {
                        if (ownedEngine === source && caseEngine === source) {
                            handleFailure(error)
                            stopSelf()
                        }
                    }
                },
            )
        }
    }

    /** Node 的 http server 启动失败未必能传回 Java 层，启动前先自行检查端口。 */
    private fun requirePortsAvailable(config: ServerListenConfig) {
        if (!NetworkUtil.isPortAvailable(config.frontend.host, config.frontend.port)) {
            throw IllegalStateException("端口 ${config.frontend.port} 已被占用")
        }
        if (!NetworkUtil.isPortAvailable(config.backend.host, config.backend.port)) {
            throw IllegalStateException("端口 ${config.backend.port} 已被占用")
        }
    }

    /** Node 端口就绪后才把服务标记为 Running，避免前端 WebView 抢先加载到 ERR_CONNECTION_REFUSED。 */
    private fun waitForNodeReady(engine: SubStoreEngine, config: ServerListenConfig) {
        stopReadiness()
        readinessJob = updateLoopScope.launch {
            val deadline = System.currentTimeMillis() + NODE_READY_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                if (caseEngine !== engine) return@launch
                if (NetworkUtil.isPortOpen(config.frontend.port)) {
                    mainHandler.post {
                        if (ownedEngine === engine && caseEngine === engine) {
                            SubStoreServiceController.store.running()
                        }
                    }
                    return@launch
                }
                delay(150)
            }
            mainHandler.post {
                if (ownedEngine === engine && caseEngine === engine) {
                    handleFailure(IllegalStateException("Node 后端启动超时"))
                    stopSelf()
                }
            }
        }
    }

    private fun stopReadiness() {
        readinessJob?.cancel()
        readinessJob = null
    }

    private fun handleFailure(error: Throwable) {
        val failure = if (SubStoreServiceController.state.value.phase == ServicePhase.Starting)
            ServiceFailure.Startup else ServiceFailure.Runtime
        val cleanupError = releaseResources()
        SubStoreServiceController.store.stopped(failure)
        if (cleanupError != null && cleanupError !== error) error.addSuppressed(cleanupError)
        SubStoreServiceController.reportFailure(this, error)
    }

    private fun releaseResources(): Throwable? {
        val engine = ownedEngine
        ownedEngine = null // 先失效回调，再执行可能耗时的清理。
        var failure: Throwable? = null
        fun cleanup(action: () -> Unit) {
            try {
                action()
            } catch (error: Throwable) {
                val previous = failure
                if (previous == null) failure = error
                else if (previous !== error) previous.addSuppressed(error)
            }
        }
        try {
            cleanup { engine?.stopServer() }
        } finally {
            stopReadiness()
            stopUpdateLoop()
            if (caseEngine === engine) {
                caseEngine = null
                SubStoreServiceController.store.stopped()
            }
            if (notificationStarted) {
                notificationStarted = false
                cleanup { stopForeground(STOP_FOREGROUND_REMOVE) }
                cleanup { NotificationUtil.stopNotification() }
            }
        }
        return failure
    }

    override fun onDestroy() {
        try {
            releaseResources()?.let { error ->
                SubStoreServiceController.store.stopped(ServiceFailure.Shutdown)
                val message = "停止 Sub-Store 服务失败：${error.message ?: error.javaClass.simpleName}"
                SubStoreServiceController.reportFailure(this, IllegalStateException(message, error))
            }
        } finally {
            stopReadiness()
            stopUpdateLoop()
            updateLoopScope.cancel()
            mainHandler.removeCallbacksAndMessages(null)
            super.onDestroy()
        }
    }

    /** 服务运行后启动 SubStore 周期检查；已有活跃任务则不重复启动。 */
    private fun startUpdateLoop() {
        if (updateLoopJob?.isActive == true) return
        updateLoopJob = updateLoopScope.launch {
            runSubStoreUpdateLoop()
        }
    }

    /** 取消周期检查并清空 Job，避免服务停止后仍后台拉版本。 */
    private fun stopUpdateLoop() {
        updateLoopJob?.cancel()
        updateLoopJob = null
    }
}
