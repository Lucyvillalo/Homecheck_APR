package com.casacheck.app.data.remote

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

internal suspend fun <T> Task<T>.awaitOnline(): T = withTimeout(30_000) { await() }
