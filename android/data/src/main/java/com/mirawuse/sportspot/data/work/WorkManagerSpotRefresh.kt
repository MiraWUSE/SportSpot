package com.mirawuse.sportspot.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import com.mirawuse.sportspot.domain.repository.BackgroundSpotRefresh
import com.mirawuse.sportspot.domain.repository.SpotRefreshArea
import com.mirawuse.sportspot.domain.repository.SpotRefreshPhase
import com.mirawuse.sportspot.domain.repository.SpotRefreshStatus
import com.mirawuse.sportspot.domain.result.DomainError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class WorkManagerSpotRefresh
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : BackgroundSpotRefresh {
        private val manager by lazy { WorkManager.getInstance(context) }
        private val queue = Mutex()

        override val status
            get() = manager.getWorkInfosForUniqueWorkFlow(NAME).map { it.latest()?.toStatus() }

        override suspend fun enqueue(area: SpotRefreshArea) {
            queue.withLock {
                val previous = manager.getWorkInfosForUniqueWorkFlow(NAME).first().latest()
                val active = previous?.state?.isFinished == false
                val areaTag = area.tag()
                if (previous != null && active && areaTag in previous.tags) return
                val failure = previous?.takeIf { it.state == WorkInfo.State.FAILED }?.outputData?.workError()
                val now = System.currentTimeMillis()
                if (failure is DomainError.Network && failure.retryAtMillis > now) return

                val order = maxOf(now, (previous?.order() ?: 0L) + 1)
                val request =
                    OneTimeWorkRequestBuilder<SpotCacheRefreshWorker>()
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                        .setInputData(area.asWorkData())
                        .addTag(areaTag)
                        .addTag(ORDER_TAG + order)
                        .build()
                manager.enqueueUniqueWork(NAME, if (active) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request).await()
            }
        }

        override suspend fun cancel() {
            queue.withLock { manager.cancelUniqueWork(NAME).await() }
        }

        private fun List<WorkInfo>.latest(): WorkInfo? = maxByOrNull { it.order() }

        private fun WorkInfo.order(): Long = tags.firstOrNull { it.startsWith(ORDER_TAG) }?.removePrefix(ORDER_TAG)?.toLongOrNull() ?: 0L

        private fun WorkInfo.toStatus(): SpotRefreshStatus =
            when (state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> SpotRefreshStatus(id.toString(), SpotRefreshPhase.QUEUED)
                WorkInfo.State.RUNNING -> SpotRefreshStatus(id.toString(), SpotRefreshPhase.RUNNING)
                WorkInfo.State.SUCCEEDED -> SpotRefreshStatus(id.toString(), SpotRefreshPhase.SUCCEEDED, area = outputData.refreshArea())
                WorkInfo.State.FAILED -> SpotRefreshStatus(id.toString(), SpotRefreshPhase.FAILED, error = outputData.workError())
                WorkInfo.State.CANCELLED -> SpotRefreshStatus(id.toString(), SpotRefreshPhase.CANCELLED)
            }

        private fun SpotRefreshArea.tag(): String {
            // Tags are visible to diagnostics. Keep the coordinates in input data.
            val bytes = "${center.latitude},${center.longitude},$radiusMeters".encodeToByteArray()
            return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        }

        companion object {
            const val NAME = "spot-cache-refresh-once"
            private const val ORDER_TAG = "spot-refresh-order:"
        }
    }
