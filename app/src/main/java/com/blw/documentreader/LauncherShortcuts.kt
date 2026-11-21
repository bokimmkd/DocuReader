package com.blw.documentreader

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon

/** Stable IDs also keep shortcuts pinned by the user working across updates. */
object LauncherShortcuts {
    enum class Destination(val id: String, val label: Int, val icon: Int) {
        Open("open_document", R.string.shortcut_open, R.drawable.ic_shortcut_open),
        Recent("recent", R.string.shortcut_recent, R.drawable.ic_shortcut_recent),
        Favorites("favorites", R.string.shortcut_favorites, R.drawable.ic_shortcut_favorites),
        Sign("sign_pdf", R.string.shortcut_sign, R.drawable.ic_shortcut_sign);

        val action get() = "com.blw.wreader.shortcut.$id"
    }

    fun destination(intent: Intent?): Destination? =
        Destination.entries.firstOrNull { it.action == intent?.action }

    fun intent(context: Context, destination: Destination) = Intent(context, MainActivity::class.java)
        .setAction(destination.action)
        // Reuse the live reader, so it can protect an unfinished edit/export.
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun publish(context: Context) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        // Launcher support/rate limiting must never prevent opening a document.
        runCatching {
            val shortcuts = Destination.entries.take(manager.maxShortcutCountPerActivity).mapIndexed { rank, destination ->
                ShortcutInfo.Builder(context, destination.id)
                    .setActivity(ComponentName(context, MainActivity::class.java))
                    .setShortLabel(context.getString(destination.label))
                    .setLongLabel(context.getString(destination.label))
                    .setIcon(Icon.createWithResource(context, destination.icon))
                    .setIntent(intent(context, destination))
                    .setRank(rank)
                    .build()
            }
            manager.setDynamicShortcuts(shortcuts)
        }
    }
}
