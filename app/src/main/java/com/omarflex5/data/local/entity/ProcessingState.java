package com.omarflex5.data.local.entity;

/**
 * Processing state for media content.
 */
public enum ProcessingState {
    NEW, // Just discovered, not processed
    PROCESSING, // Currently being processed
    COMPLETE, // Fully processed and enriched
    ERROR // Processing failed
}
