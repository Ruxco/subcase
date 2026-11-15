package ano.subcase.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import ano.subcase.caseApp
import ano.subcase.util.PreferencesKeys.ALLOW_LAN
import ano.subcase.util.PreferencesKeys.APP_IS_FIRST_OPEN
import ano.subcase.util.PreferencesKeys.BACKEND_LOCAL_VER
import ano.subcase.util.PreferencesKeys.ENGINE_MODE
import ano.subcase.util.PreferencesKeys.FRONTEND_LOCAL_VER
import ano.subcase.util.PreferencesKeys.LAST_DATA_ENGINE
import ano.subcase.util.PreferencesKeys.LAST_NODE_BACKEND_INSTALLED_AT
import ano.subcase.util.PreferencesKeys.LEGACY_NODE_MIGRATED
import ano.subcase.util.PreferencesKeys.NODE_BACKEND_LOCAL_VER

object PreferencesKeys {
    const val HOME_PANEL_VERTICAL_FRACTION = "home_panel_vertical_fraction"
    const val NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"

    const val APP_IS_FIRST_OPEN = "app_first_open"

    const val ALLOW_LAN = "allow_lan"

    const val BACKEND_LOCAL_VER = "backend_local_ver"
    const val FRONTEND_LOCAL_VER = "frontend_local_ver"

    const val ENGINE_MODE = "substore_engine_mode"
    const val NODE_BACKEND_LOCAL_VER = "node_backend_local_ver"
    const val LAST_NODE_BACKEND_INSTALLED_AT = "last_node_backend_installed_at"
    const val LAST_DATA_ENGINE = "last_data_engine"

    const val SUBSTORE_UPDATE_POLICY = "substore_update_policy"
    const val LAST_FRONTEND_INSTALLED_AT = "last_frontend_installed_at"
    const val LAST_BACKEND_INSTALLED_AT = "last_backend_installed_at"
    const val LAST_SUBSTORE_REMOTE_VERSION_CHECK_AT = "last_substore_remote_version_check_at"

    const val LEGACY_NODE_MIGRATED = "legacy_node_migrated"
}

object ConfigStore {
    val homePanelVerticalFraction: Float?
        get() = getInstance()
            .getFloat(PreferencesKeys.HOME_PANEL_VERTICAL_FRACTION, Float.NaN)
            .takeIf { it.isFinite() }
            ?.coerceIn(0f, 1f)

    /** Call from an IO dispatcher; commit reports disk write failures. */
    fun saveHomePanelVerticalFraction(fraction: Float) {
        require(fraction.isFinite() && fraction in 0f..1f)
        check(getInstance().edit()
            .putFloat(PreferencesKeys.HOME_PANEL_VERTICAL_FRACTION, fraction)
            .commit()) { "Failed to save home panel position" }
    }

    private var prefs: SharedPreferences? = null

    fun getInstance(): SharedPreferences {
        if (prefs == null) {
            prefs = caseApp.getSharedPreferences("settings", Context.MODE_PRIVATE)
        }
        return prefs!!
    }

    var notificationPermissionRequested: Boolean
        get() = getInstance().getBoolean(PreferencesKeys.NOTIFICATION_PERMISSION_REQUESTED, false)
        set(value) {
            getInstance().edit { putBoolean(PreferencesKeys.NOTIFICATION_PERMISSION_REQUESTED, value) }
        }

    var isFirstOpen: Boolean
        get() = getInstance().getBoolean(APP_IS_FIRST_OPEN, true)
        set(value) {
            getInstance().edit { putBoolean(APP_IS_FIRST_OPEN, value) }
        }

    var isAllowLan: Boolean
        get() = getInstance().getBoolean(ALLOW_LAN, false)
        set(value) {
            getInstance().edit { putBoolean(ALLOW_LAN, value) }
        }

    var localFrontendVersion: String
        get() = getInstance().getString(FRONTEND_LOCAL_VER, "")!!
        set(value) {
            getInstance().edit { putString(FRONTEND_LOCAL_VER, value) }
        }

    var localBackendVersion: String
        get() = getInstance().getString(BACKEND_LOCAL_VER, "")!!
        set(value) {
            getInstance().edit { putString(BACKEND_LOCAL_VER, value) }
        }

    var engineMode: SubStoreEngineMode
        get() = SubStoreEngineMode.fromStored(
            getInstance().getString(ENGINE_MODE, null),
        )
        set(value) {
            getInstance().edit { putString(ENGINE_MODE, value.name) }
        }

    var localNodeBackendVersion: String
        get() = getInstance().getString(NODE_BACKEND_LOCAL_VER, "")!!
        set(value) {
            getInstance().edit { putString(NODE_BACKEND_LOCAL_VER, value) }
        }

    var lastNodeBackendInstalledAt: Long
        get() = getInstance().getLong(LAST_NODE_BACKEND_INSTALLED_AT, 0L)
        set(value) {
            getInstance().edit { putLong(LAST_NODE_BACKEND_INSTALLED_AT, value) }
        }

    /** 上一次服务启动时数据所在位置；用于在两种引擎之间做一次性迁移。 */
    var lastDataEngine: SubStoreEngineMode
        get() = getInstance().getString(LAST_DATA_ENGINE, null)
            ?.let { stored -> SubStoreEngineMode.entries.firstOrNull { it.name == stored } }
            ?: SubStoreEngineMode.WEBVIEW
        set(value) {
            getInstance().edit { putString(LAST_DATA_ENGINE, value.name) }
        }

    var subStoreUpdatePolicy: SubStoreUpdatePolicy
        get() = SubStoreUpdatePolicy.fromStored(
            getInstance().getString(PreferencesKeys.SUBSTORE_UPDATE_POLICY, null),
        )
        set(value) {
            getInstance().edit {
                putString(PreferencesKeys.SUBSTORE_UPDATE_POLICY, value.name)
            }
        }

    var lastFrontendInstalledAt: Long
        get() = getInstance().getLong(PreferencesKeys.LAST_FRONTEND_INSTALLED_AT, 0L)
        set(value) {
            getInstance().edit { putLong(PreferencesKeys.LAST_FRONTEND_INSTALLED_AT, value) }
        }

    var lastBackendInstalledAt: Long
        get() = getInstance().getLong(PreferencesKeys.LAST_BACKEND_INSTALLED_AT, 0L)
        set(value) {
            getInstance().edit { putLong(PreferencesKeys.LAST_BACKEND_INSTALLED_AT, value) }
        }

    var lastSubStoreRemoteVersionCheckAt: Long
        get() = getInstance().getLong(PreferencesKeys.LAST_SUBSTORE_REMOTE_VERSION_CHECK_AT, 0L)
        set(value) {
            getInstance().edit {
                putLong(PreferencesKeys.LAST_SUBSTORE_REMOTE_VERSION_CHECK_AT, value)
            }
        }

    var legacyNodeMigrated: Boolean
        get() = getInstance().getBoolean(LEGACY_NODE_MIGRATED, false)
        set(value) {
            check(getInstance().edit().putBoolean(LEGACY_NODE_MIGRATED, value).commit())
        }
}
