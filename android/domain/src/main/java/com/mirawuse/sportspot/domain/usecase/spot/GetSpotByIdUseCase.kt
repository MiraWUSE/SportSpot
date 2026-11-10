package com.mirawuse.sportspot.domain.usecase.spot

import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.SpotRepository

class GetSpotByIdUseCase(
    private val repository: SpotRepository,
) {
    suspend operator fun invoke(id: SpotId): Spot? = repository.getSpotById(id)
}
