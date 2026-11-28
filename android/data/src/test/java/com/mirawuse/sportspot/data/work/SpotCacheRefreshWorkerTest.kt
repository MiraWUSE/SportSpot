package com.mirawuse.sportspot.data.work

import android.content.Context
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.SpotRefreshArea
import com.mirawuse.sportspot.domain.repository.SpotRefreshPhase
import com.mirawuse.sportspot.domain.repository.SpotRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainOperationException
import com.mirawuse.sportspot.domain.result.NetworkIssue
import com.mirawuse.sportspot.domain.usecase.spot.SearchNearbySpotsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SpotCacheRefreshWorkerTest {
    private val context = RuntimeEnvironment.getApplication()
    private val area = SpotRefreshArea(GeoPoint(56.3269, 44.0059), 2000)

    @Test
    fun validInputCallsOnlyStrictRefreshAndReturnsTheSavedArea() =
        runBlocking {
            val requested = mutableListOf<SpotRefreshArea>()
            val repository = fakeRepository { requested += it }
            assertEquals(Result.success(area.asWorkData()), buildRefreshWorker(context, repository, area.asWorkData()).doWork())
            val invalid = listOf(Data.EMPTY, workDataOf("latitude" to 91.0, "longitude" to 0.0, "radius" to 1000))
            for (input in invalid) {
                assertTrue(buildRefreshWorker(context, repository, input).doWork() is Result.Failure)
            }
            assertEquals(listOf(area), requested)
        }

    @Test
    fun transientFailuresHaveTwoRetriesAndRateLimitedOrUnauthorizedEndImmediately() =
        runBlocking {
            val errors = listOf(NetworkIssue.OFFLINE, NetworkIssue.TIMEOUT, NetworkIssue.CONNECTION, NetworkIssue.SERVER)
            for (issue in errors) {
                val error = DomainError.Network(issue)
                val repository = fakeRepository { throw DomainOperationException(error) }
                for (attempt in 0..1) {
                    assertEquals(
                        Result.retry(),
                        buildRefreshWorker(context, repository, area.asWorkData(), attempt).doWork(),
                    )
                }
                val failure = buildRefreshWorker(context, repository, area.asWorkData(), 2).doWork() as Result.Failure
                assertEquals(error, failure.outputData.workError())
            }
            val terminal = listOf(DomainError.Network(NetworkIssue.RATE_LIMITED, 20_000L), DomainError.Unauthorized, DomainError.Forbidden)
            for (error in terminal) {
                val repository = fakeRepository { throw DomainOperationException(error) }
                val failure = buildRefreshWorker(context, repository, area.asWorkData()).doWork() as Result.Failure
                assertEquals(error, failure.outputData.workError())
            }
        }

    @Test
    fun cancellationPropagatesWithoutReturningWorkSuccess() =
        runBlocking {
            val cancelled = CancellationException()
            val repository = fakeRepository { throw cancelled }
            val failure = runCatching { buildRefreshWorker(context, repository, area.asWorkData()).doWork() }.exceptionOrNull()
            assertSame(cancelled, failure)
        }

    @Test
    fun queuedWorkRequiresNetworkAndIsKeptReplacedCancelledAndRestored() =
        runBlocking {
            withTimeout(8_000) {
                WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
                val scheduler = WorkManagerSpotRefresh(context)
                scheduler.enqueue(area)
                val queued = requireNotNull(scheduler.status.first())
                assertEquals(SpotRefreshPhase.QUEUED, queued.phase)
                val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WorkManagerSpotRefresh.NAME)
                assertEquals(
                    NetworkType.CONNECTED,
                    infos
                        .first()
                        .single()
                        .constraints.requiredNetworkType,
                )

                scheduler.enqueue(area)
                assertEquals(queued, scheduler.status.first())
                assertEquals(1, infos.first().size)
                val otherArea = area.copy(radiusMeters = 500)
                scheduler.enqueue(otherArea)
                val updated = requireNotNull(scheduler.status.first())
                assertNotEquals(queued.id, updated.id)
                assertEquals(SpotRefreshPhase.QUEUED, updated.phase)
                assertEquals(updated, WorkManagerSpotRefresh(context).status.first())

                scheduler.cancel()
                assertEquals(SpotRefreshPhase.CANCELLED, scheduler.status.first()?.phase)
                scheduler.enqueue(otherArea)
                assertEquals(SpotRefreshPhase.QUEUED, scheduler.status.first()?.phase)
                scheduler.cancel()
            }
        }

    private fun fakeRepository(refresh: suspend (SpotRefreshArea) -> Unit): SpotRepository =
        object : SpotRepository {
            override suspend fun getSpots(): List<Spot> = error("Unexpected saved spots read")

            override suspend fun getSpotById(id: SpotId): Spot? = error("Unexpected details read")

            override suspend fun searchNearby(
                center: GeoPoint,
                radiusMeters: Int,
                sportType: SportType?,
            ): List<Spot> = error("The worker must not use a fallback search")

            override suspend fun refreshStoredSearch(area: SpotRefreshArea) = refresh(area)
        }
}

internal fun buildRefreshWorker(
    context: Context,
    repository: SpotRepository,
    input: Data,
    attempt: Int = 0,
): SpotCacheRefreshWorker {
    val factory =
        object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? =
                if (workerClassName == SpotCacheRefreshWorker::class.java.name) {
                    SpotCacheRefreshWorker(appContext, workerParameters, SearchNearbySpotsUseCase(repository))
                } else {
                    null
                }
        }
    return TestListenableWorkerBuilder<SpotCacheRefreshWorker>(context)
        .setWorkerFactory(factory)
        .setInputData(input)
        .setRunAttemptCount(attempt)
        .build()
}
