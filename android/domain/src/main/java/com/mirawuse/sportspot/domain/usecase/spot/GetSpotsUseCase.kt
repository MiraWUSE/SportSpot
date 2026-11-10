
package com.mirawuse.sportspot.domain.usecase.spot

import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.repository.SpotRepository

class GetSpotsUseCase(
    private val repository: SpotRepository,
) {
    suspend operator fun invoke(): List<Spot> = repository.getSpots()
}
