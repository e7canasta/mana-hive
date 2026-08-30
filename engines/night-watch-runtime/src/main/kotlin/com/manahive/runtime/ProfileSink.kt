package com.manahive.runtime

import com.manahive.profile.api.ResidentProfileDto

/**
 * Receives resident profile changes from any source.
 */
interface ProfileSink {
    fun onProfileChanged(profile: ResidentProfileDto)
}
