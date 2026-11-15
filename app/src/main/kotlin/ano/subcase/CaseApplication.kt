package ano.subcase

import android.app.Application
import ano.subcase.engine.node.NodeBackendFiles
import ano.subcase.util.AppUtil
import ano.subcase.util.ConfigStore
import ano.subcase.util.LegacyNodeMigration
import ano.subcase.util.SubStore
import ano.subcase.util.SubStoreEngineMode
import timber.log.Timber

lateinit var caseApp: CaseApplication

class CaseApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Timber.plant(Timber.DebugTree())
        Timber.d("TimberInitializer is initialized.")
        caseApp = this

        LegacyNodeMigration.migrate(this)

        // 必须在 SubStore 状态首次读取前放好 Node bundle，保证设置页版本号正确。
        if (ConfigStore.engineMode == SubStoreEngineMode.NODE) {
            NodeBackendFiles.ensureInstalled(this)
        }

        if (ConfigStore.isFirstOpen) {
            AppUtil.initFirstOpen()
            ConfigStore.isFirstOpen = false
        }

        SubStore.ensureInstallTimestamps()
    }
}
