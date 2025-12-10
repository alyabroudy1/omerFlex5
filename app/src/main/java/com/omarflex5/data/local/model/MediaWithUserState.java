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
}
