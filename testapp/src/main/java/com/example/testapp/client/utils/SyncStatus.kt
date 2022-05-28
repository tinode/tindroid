package com.example.testapp.client.utils


/**
 * If the message has been sent to the servers.
 */
public enum class SyncStatus(public val status: Int) {
    /**
     * Status undefined/not set.
     */
    UNDEFINED(0),

    /**
     * When the message is not ready to be sent to the server.
     */
    DRAFT(10),

    /**
     * After the retry strategy we still failed to sync this.
     */
    QUEUED(20),

    /**
     * When sync is in the process of being sent to the server.
     */
    SENDING(30),

    /**
     * Send failed.
     */
    FAILED(40),

    /**
     * When message is received by the server.
     */
    SYNCED(50),

    /**
     * Object is hard-deleted.
     */
    DELETED_HARD(60),

    /**
     * Object is soft-deleted.
     */
    DELETED_SOFT(70),

    /**
     * The object is a deletion range marker synchronized with the server.
     */
    DELETED_SYNCED(80);

    public companion object {
        private val map = values().associateBy(SyncStatus::status)
        public fun fromInt(type: Int): SyncStatus? = map[type]
    }
}
