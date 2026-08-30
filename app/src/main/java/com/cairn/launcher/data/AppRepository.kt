package com.cairn.launcher.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * The installed app list, plus the three orderings the drawer's top row toggles between.
 *
 * Recent and frequent both need PACKAGE_USAGE_STATS, which is a Settings toggle rather than a
 * runtime permission. Without it the queries return nothing, so both fall back to alphabetical
 * rather than failing.
 */
class AppRepository(private val context: Context) {

    private val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val usage =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private val scope = CoroutineScope(Dispatchers.Default)

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = reload()
        override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = reload()
        override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = reload()
    }

    fun start() {
        launcherApps.registerCallback(callback)
        reload()
    }

    fun stop() {
        runCatching { launcherApps.unregisterCallback(callback) }
    }

    private fun reload() {
        scope.launch {
            val me = Process.myUserHandle()
            val pm = context.packageManager
            val list = runCatching { launcherApps.getActivityList(null, me) }.getOrNull().orEmpty()
            _apps.value = list.mapNotNull { info ->
                runCatching {
                    val pkg = info.applicationInfo.packageName
                    AppInfo(
                        packageName = pkg,
                        className = info.componentName.className,
                        label = info.label?.toString().orEmpty().ifBlank { pkg },
                        icon = info.getIcon(0),
                        firstInstall = runCatching {
                            pm.getPackageInfo(pkg, 0).firstInstallTime
                        }.getOrDefault(0L)
                    )
                }.getOrNull()
            }.sortedBy { it.label.lowercase() }
        }
    }

    /** Most recently used first. Empty if usage access has not been granted. */
    fun recent(limit: Int = 8): List<AppInfo> {
        val stats = queryStats() ?: return emptyList()
        val byPackage = stats.associate { it.packageName to it.lastTimeUsed }
        return _apps.value
            .filter { byPackage.containsKey(it.packageName) }
            .sortedByDescending { byPackage[it.packageName] ?: 0L }
            .distinctBy { it.packageName }
            .take(limit)
    }

    /** Most foreground time over the last week. Empty if usage access has not been granted. */
    fun frequent(limit: Int = 8): List<AppInfo> {
        val stats = queryStats() ?: return emptyList()
        val byPackage = HashMap<String, Long>()
        stats.forEach { s ->
            byPackage[s.packageName] = (byPackage[s.packageName] ?: 0L) + s.totalTimeInForeground
        }
        return _apps.value
            .filter { (byPackage[it.packageName] ?: 0L) > 0L }
            .sortedByDescending { byPackage[it.packageName] ?: 0L }
            .distinctBy { it.packageName }
            .take(limit)
    }

    /** Installed most recently first, within the last 30 days. */
    fun newlyInstalled(limit: Int = 8): List<AppInfo> {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        return _apps.value
            .filter { it.firstInstall > cutoff }
            .sortedByDescending { it.firstInstall }
            .take(limit)
    }

    private fun queryStats(): List<android.app.usage.UsageStats>? {
        val now = System.currentTimeMillis()
        val from = now - TimeUnit.DAYS.toMillis(7)
        val result = runCatching {
            usage.queryUsageStats(UsageStatsManager.INTERVAL_BEST, from, now)
        }.getOrNull()
        return if (result.isNullOrEmpty()) null else result
    }

    /**
     * An app's own deep shortcuts. Only the default home app is allowed to ask, which is why
     * this returns nothing until Cairn is actually set as the launcher.
     */
    fun shortcuts(packageName: String): List<android.content.pm.ShortcutInfo> {
        val query = LauncherApps.ShortcutQuery()
            .setPackage(packageName)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        return runCatching {
            launcherApps.getShortcuts(query, Process.myUserHandle()).orEmpty()
        }.getOrDefault(emptyList())
    }

    fun startShortcut(info: android.content.pm.ShortcutInfo) {
        runCatching {
            launcherApps.startShortcut(info, null, null)
        }
    }

    fun launch(app: AppInfo, sourceBounds: android.graphics.Rect?) {
        runCatching {
            launcherApps.startMainActivity(
                android.content.ComponentName(app.packageName, app.className),
                Process.myUserHandle(),
                sourceBounds,
                null
            )
        }
    }
}
