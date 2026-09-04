package com.talkmitra.offlinevoice.communication.model

/**
 * State updates during transmission of an offline message.
 */
sealed interface DeliveryState {
    data object Queued : DeliveryState
    data class Transmitting(val tier: TransportTier) : DeliveryState
    data class Relayed(val hopCount: Int) : DeliveryState
    data class Delivered(val rttMs: Long) : DeliveryState
    data class Failed(val reason: String) : DeliveryState
}
