package com.muddassir.squadgym.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.muddassir.squadgym.SquadGymApp
import com.muddassir.squadgym.core.error.ApiResult
import com.muddassir.squadgym.data.repository.MemberRepository
import com.muddassir.squadgym.data.session.SessionState
import java.util.concurrent.TimeUnit

/**
 * Background cache warm-up.
 *
 * Only *reads* are refreshed — attendance writes are never queued offline, so a
 * mark is either confirmed by the server or shown as unconfirmed (spec §4.C).
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? SquadGymApp ?: return Result.success()
        val container = app.container
        val session = container.sessionManager.state.value as? SessionState.SignedIn
            ?: return Result.success()

        return try {
            val results = buildList {
                if (session.user.isStaff) {
                    add(container.dashboardRepository.dashboard() is ApiResult.Success)
                    add(
                        container.memberRepository.list(
                            MemberRepository.ListFilter(pageSize = 50),
                        ) is ApiResult.Success,
                    )
                } else {
                    add(container.meRepository.home() is ApiResult.Success)
                }
                add(container.leaderboardRepository.board("week") is ApiResult.Success)
            }
            if (results.any { !it }) Result.retry() else Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

object BackgroundSync {
    private const val UNIQUE_NAME = "squadgym-cache-refresh"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
