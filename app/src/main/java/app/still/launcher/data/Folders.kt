package app.still.launcher.data

import kotlinx.serialization.Serializable

/** A named folder on home holding apps by [InstalledApp.key], in the order they were added. */
@Serializable
data class AppFolder(val id: String, val name: String, val apps: List<String> = emptyList())

/** How folders look on home, above the favourites. */
enum class FolderStyle { STRIP, TABBED, PAPER_STACK, ORBIT_DOTS }

/** How an open folder shows its apps. */
enum class FolderOpening { SPOTLIGHT, DROP, BLOOM, CASCADE, SHELF, ORBIT }

/** How apps appear on home and in folders. */
enum class AppDisplay { TEXT, ICON_AND_NAME, ICONS_ONLY }

/** [folders] with [appKey] moved into the folder [folderId], or out of every folder when it's null. An app is in one folder at most. */
fun moveToFolder(folders: List<AppFolder>, appKey: String, folderId: String?): List<AppFolder> = folders.map { folder ->
    val without = folder.apps - appKey
    folder.copy(apps = if (folder.id == folderId) without + appKey else without)
}
