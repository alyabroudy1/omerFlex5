package com.omarflex5.data.local;

import androidx.room.TypeConverter;

import com.omarflex5.data.local.entity.MediaType;
import com.omarflex5.data.local.entity.ProcessingState;
import com.omarflex5.data.local.entity.SearchQueueStatus;

/**
 * Type converters for Room database.
 * Converts enums to/from strings for storage.
 */
public class Converters {

    // MediaType
    @TypeConverter
    public static MediaType toMediaType(String value) {
        return value == null ? null : MediaType.valueOf(value);
    }

    @TypeConverter
    public static String fromMediaType(MediaType type) {
        return type == null ? null : type.name();
    }

    // ProcessingState
    @TypeConverter
    public static ProcessingState toProcessingState(String value) {
        return value == null ? null : ProcessingState.valueOf(value);
    }

    @TypeConverter
    public static String fromProcessingState(ProcessingState state) {
        return state == null ? null : state.name();
    }

    // SearchQueueStatus
    @TypeConverter
    public static SearchQueueStatus toSearchQueueStatus(String value) {
        return value == null ? null : SearchQueueStatus.valueOf(value);
    }

    @TypeConverter
    public static String fromSearchQueueStatus(SearchQueueStatus status) {
        return status == null ? null : status.name();
    }
}
