package com.omarflex5.data.local.entity;

/**
 * Types of media content in the database.
 */
public enum MediaType {
    SERIES, // TV series (Group of Groups / Seasons)
    SEASON, // Specific Season (Group of Episodes)
    EPISODE, // Specific Episode (Playable Item)
    FILM // Standalone movie (Playable Item)
}
