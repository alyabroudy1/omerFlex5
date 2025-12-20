package com.omarflex5.data.local.model;

import androidx.room.Embedded;
import androidx.room.Relation;

import com.omarflex5.data.local.entity.MediaEntity;
import com.omarflex5.data.local.entity.UserMediaStateEntity;

public class MediaWithUserState {
    @Embedded
    public MediaEntity media;

    @Relation(parentColumn = "id", entityColumn = "mediaId")
    public UserMediaStateEntity userState;

    @Relation(parentColumn = "primaryServerId", entityColumn = "id")
    public com.omarflex5.data.local.entity.ServerEntity server;

    @Relation(parentColumn = "id", entityColumn = "mediaId")
    public java.util.List<com.omarflex5.data.local.entity.MediaSourceEntity> sources;
}
