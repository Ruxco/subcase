package ano.subcase.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import ano.subcase.caseApp
import ano.subcase.service.SubStoreServiceController
import ano.subcase.util.ConfigStore
import ano.subcase.util.SubStoreEngineMode

class MainViewModel : ViewModel() {

    var allowLan by mutableStateOf(false)
    var useNodeEngine by mutableStateOf(false)

    init {
        allowLan = ConfigStore.isAllowLan
        useNodeEngine = ConfigStore.engineMode == SubStoreEngineMode.NODE
    }

    /** 切换后端引擎；调用方负责停止正在运行的服务。 */
    fun setEngineMode(node: Boolean) {
        ConfigStore.engineMode =
            if (node) SubStoreEngineMode.NODE else SubStoreEngineMode.WEBVIEW
        useNodeEngine = node
    }

    fun startService() {
        SubStoreServiceController.start(caseApp)
    }

    fun stopService() {
        SubStoreServiceController.stop(caseApp)
    }
}
