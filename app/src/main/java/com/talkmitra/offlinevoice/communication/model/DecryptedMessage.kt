package com.talkmitra.offlinevoice.communication.model

import com.talkmitra.offlinevoice.text.MessagePriority
import com.talkmitra.offlinevoice.text.MessageType

/**
 * End-to-end decrypted payload ready for TTS playback and UI presentation.
 */
data class DecryptedMessage(
    val messageId: String,
    val sender: PeerIdentity?,
    val text: String,
    val languageCode: String,
    val timestampMs: Long,
    val transportTier: TransportTier,
    val hopCount: Int,
    val messageType: MessageType = MessageType.NORMAL,
    val priority: MessagePriority = MessagePriority.NORMAL
)
