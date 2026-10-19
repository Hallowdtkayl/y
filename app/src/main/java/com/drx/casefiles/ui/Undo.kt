package com.drx.casefiles.ui

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.mutableStateListOf
import com.drx.casefiles.R
import com.drx.casefiles.data.Lang
import com.drx.casefiles.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Delete without asking. The item disappears at once (its key sits in [hidden], and lists skip hidden keys),
 * a "Deleted - Undo" bar shows for a few seconds, and only then is it really deleted. Undo brings it back.
 * If the app is closed during that moment nothing was deleted yet. Keys: "v12" visit, "m34" image.
 */
object Undo {
    val host = SnackbarHostState()
    val hidden = mutableStateListOf<String>()

    fun isHidden(key: String): Boolean = hidden.contains(key)

    fun run(repo: Repo, ctx: Context, keys: List<String>, onUndo: () -> Unit = {}, commit: suspend () -> Unit) {
        hidden.addAll(keys)
        repo.appScope.launch(Dispatchers.Main) {
            var undone = false
            try {
                val r = host.showSnackbar(
                    message = Lang.get(ctx, R.string.deleted_msg),
                    actionLabel = Lang.get(ctx, R.string.undo),
                    duration = SnackbarDuration.Short
                )
                undone = r == SnackbarResult.ActionPerformed
            } finally {
                if (undone) {
                    hidden.removeAll(keys)
                    onUndo()
                } else {
                    repo.appScope.launch {
                        try {
                            commit()
                        } finally {
                            hidden.removeAll(keys)
                        }
                    }
                }
            }
        }
    }
}
