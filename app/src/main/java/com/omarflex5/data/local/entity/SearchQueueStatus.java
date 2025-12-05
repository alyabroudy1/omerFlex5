package com.omarflex5.data.local.entity;

/**
 * Status of a search queue item.
 */
public enum SearchQueueStatus {
    PENDING, // Waiting to be processed
    IN_PROGRESS, // Currently being processed by WebView
    DONE, // Successfully processed
    FAILED // Processing failed
}
