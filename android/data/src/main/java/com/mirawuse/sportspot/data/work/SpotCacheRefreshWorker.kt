package com.mirawuse.sportspot.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.repository.SpotRefreshArea
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainFailure
import com.mirawuse.sportspot.domain.result.NetworkIssue
import com.mirawuse.sportspot.domain.usecase.spot.SearchNearbySpotsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@HiltWorker
class SpotCacheRefreshWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted parameters: WorkerParameters,
        private val search: SearchNearbySpotsUseCase,
    ) : CoroutineWorker(context, parameters) {
        override suspend fun doWork(): Result {
            val area = inputData.refreshArea() ?: return Result.failure(DomainError.Validation("").asWorkError())
            if (runAttemptCount >= MAX_ATTEMPTS) return Result.failure()
            return try {
                search.refreshStoredSearch(area)
                currentCoroutineContext().ensureActive()
                Result.success(area.asWorkData())
            } catch (failure: Exception) {
                if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
                currentCoroutineContext().ensureActive()
                val error =
                    if (failure is TimeoutCancellationException) {
                        DomainError.Network(NetworkIssue.TIMEOUT)
                    } else {
                        (failure as? DomainFailure)?.domainError ?: DomainError.Unexpected
                    }
                val retryable = error is DomainError.Network && error.issue in RETRY_ISSUES
                if (retryable && runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else Result.failure(error.asWorkError())
            }
        }

        private companion object {
            const val MAX_ATTEMPTS = 3
            val RETRY_ISSUES = setOf(NetworkIssue.OFFLINE, NetworkIssue.CONNECTION, NetworkIssue.TIMEOUT, NetworkIssue.SERVER)
        }
    }

internal fun SpotRefreshArea.asWorkData(): Data =
    workDataOf("latitude" to center.latitude, "longitude" to center.longitude, "radius" to radiusMeters)

internal fun Data.refreshArea(): SpotRefreshArea? =
    runCatching {
        val center = GeoPoint(getDouble("latitude", Double.NaN), getDouble("longitude", Double.NaN))
        SpotRefreshArea(center, getInt("radius", -1))
    }.getOrNull()

private fun DomainError.asWorkError(): Data =
    when (this) {
        is DomainError.Network -> workDataOf("error" to issue.name, "retryAt" to retryAtMillis)
        DomainError.Unauthorized -> workDataOf("error" to "unauthorized")
        DomainError.Forbidden -> workDataOf("error" to "forbidden")
        is DomainError.Validation -> workDataOf("error" to "invalid")
        DomainError.NotFound -> workDataOf("error" to "missing")
        else -> Data.EMPTY
    }

internal fun Data.workError(): DomainError {
    val code = getString("error")
    val issue = NetworkIssue.entries.firstOrNull { it.name == code }
    return when {
        issue != null -> DomainError.Network(issue, getLong("retryAt", 0L))
        code == "unauthorized" -> DomainError.Unauthorized
        code == "forbidden" -> DomainError.Forbidden
        code == "invalid" -> DomainError.Validation("")
        code == "missing" -> DomainError.NotFound
        else -> DomainError.Unexpected
    }
}
