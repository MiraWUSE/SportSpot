package com.mirawuse.sportspot.domain.repository

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.result.DomainError
import kotlinx.coroutines.flow.Flow

data class SpotRefreshArea(
    val center: GeoPoint,
    val radiusMeters: Int,
) {
    init {
        require(radiusMeters in 100..3000)
    }
}

enum class SpotRefreshPhase { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }

data class SpotRefreshStatus(
    val id: String,
    val phase: SpotRefreshPhase,
    val area: SpotRefreshArea? = null,
    val error: DomainError? = null,
) {
    val active: Boolean get() = phase == SpotRefreshPhase.QUEUED || phase == SpotRefreshPhase.RUNNING
}

interface BackgroundSpotRefresh {
    val status: Flow<SpotRefreshStatus?>

    suspend fun enqueue(area: SpotRefreshArea)

    suspend fun cancel()
}
