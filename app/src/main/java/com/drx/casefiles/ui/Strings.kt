package com.drx.casefiles.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.drx.casefiles.data.Lang
import java.util.Locale

/*
 * Drop-in replacement for androidx's stringResource inside the app: same call, but only the language chosen in
 * Settings is shown. Reading Lang.ar here makes every screen redraw when the language is switched.
 */

@Composable
fun stringResource(@StringRes id: Int): String {
    val arabic = Lang.ar
    return Lang.pickFor(LocalContext.current.resources.getString(id), arabic)
}

@Composable
fun stringResource(@StringRes id: Int, vararg args: Any): String {
    val arabic = Lang.ar
    val t = Lang.pickFor(LocalContext.current.resources.getString(id), arabic)
    return String.format(Locale.getDefault(), t, *args)
}
