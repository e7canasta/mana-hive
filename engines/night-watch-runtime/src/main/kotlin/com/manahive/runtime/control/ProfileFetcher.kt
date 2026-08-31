package com.manahive.runtime.control

import com.manahive.kernel.ResidentId
import com.manahive.profile.api.ResidentProfileDto

/**
 * Port outbound — fetches resident profile from hub.
 * Fowler: Gateway / Anti-Corruption Layer.
 * Adapter: HubProfileFetcherAdapter (HTTP GET /api/profiles/{id})
 */
interface ProfileFetcher {
    fun fetch(residentId: ResidentId): ResidentProfileDto
}
