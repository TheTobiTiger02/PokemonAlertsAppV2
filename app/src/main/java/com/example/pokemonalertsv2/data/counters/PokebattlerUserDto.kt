package com.example.pokemonalertsv2.data.counters

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The signed-in Pokébattler account, from `GET /secure/user`.
 *
 * Only the id and a display name are needed: the id becomes the `attackers/users/{id}`
 * path segment, and the name is shown in settings so the user can see which account is
 * linked. Everything else in the payload is ignored.
 */
@Serializable
data class PokebattlerUserResponse(
    @SerialName("id") val id: String? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("trainerName") val trainerName: String? = null
) {
    /** Best available label for the account, or null when the payload names it nothing. */
    val displayName: String?
        get() = listOfNotNull(trainerName, username, email)
            .firstOrNull { it.isNotBlank() }
}
