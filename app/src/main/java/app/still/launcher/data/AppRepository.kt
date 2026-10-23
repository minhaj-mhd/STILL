package app.still.launcher.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** A launchable activity as reported by the system, before any user customization. */
data class InstalledApp(
    /** Stable identity across reboots: component plus the profile's serial number. */
    val key: String,
    val component: ComponentName,
    val user: UserHandle,
    val label: String,
    val isWorkProfile: Boolean,
    val canUninstall: Boolean,
)

class AppRepository(context: Context) {

    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)

    /** Every launchable app in every profile; re-emits when packages are installed, updated or removed. */
    val installedApps: Flow<List<InstalledApp>> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) {
                trySend(Unit)
            }

            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                trySend(Unit)
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {
                trySend(Unit)
            }

            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                trySend(Unit)
            }

            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                trySend(Unit)
            }
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        trySend(Unit)
        awaitClose { launcherApps.unregisterCallback(callback) }
    }
        .conflate()
        .map { loadInstalledApps() }
        .flowOn(Dispatchers.Default)

    fun launch(component: ComponentName, user: UserHandle): Boolean =
        try {
            launcherApps.startMainActivity(component, user, null, null)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }

    fun openAppInfo(component: ComponentName, user: UserHandle) {
        launcherApps.startAppDetailsActivity(component, user, null, null)
    }

    fun uninstall(packageName: String, user: UserHandle) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
            .putExtra(Intent.EXTRA_USER, user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    private fun loadInstalledApps(): List<InstalledApp> {
        val owner = Process.myUserHandle()
        return userManager.userProfiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            launcherApps.getActivityList(null, user)
                .filter { it.componentName.packageName != appContext.packageName }
                .map { info ->
                    InstalledApp(
                        key = "${info.componentName.flattenToShortString()}#$serial",
                        component = info.componentName,
                        user = user,
                        label = info.label.toString(),
                        isWorkProfile = user != owner,
                        canUninstall = (info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0,
                    )
                }
        }
    }
}
