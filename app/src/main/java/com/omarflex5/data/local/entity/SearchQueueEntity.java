package com.omarflex5.data.local.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Search queue for CF-protected servers.
 * 
 * When a search is initiated:
 * - Fast servers are queried immediately
 * - CF servers with expired cookies are queued here
 * - User clicks "Load More" to process queue via WebView
 */
@Entity(tableName = "search_queue", foreignKeys = @ForeignKey(entity = ServerEntity.class, parentColumns = "id", childColumns = "serverId", onDelete = ForeignKey.CASCADE), indices = {
        @Index(value = "serverId"),
        @Index(value = "status"),
        @Index(value = { "query", "serverId" }, unique = true)
})
public class SearchQueueEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    // Search query
    private String query;

    // Server to search
    private long serverId;

    // Status
    private SearchQueueStatus status;

    // Result count (after processing)
    private Integer resultCount;

    // Error message (if failed)
    private String errorMessage;

    // Timestamps
    private long createdAt;
    private Long processedAt;

    // ========== Getters and Setters ==========

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public long getServerId() {
        return serverId;
    }

    public void setServerId(long serverId) {
        this.serverId = serverId;
    }

    public SearchQueueStatus getStatus() {
        return status;
    }

    public void setStatus(SearchQueueStatus status) {
        this.status = status;
    }

    public Integer getResultCount() {
        return resultCount;
    }

    public void setResultCount(Integer resultCount) {
        this.resultCount = resultCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Long processedAt) {
        this.processedAt = processedAt;
    }
}
