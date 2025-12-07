package com.omarflex5.temp.omerflex.entity.dto;

import androidx.annotation.NonNull;

public class GoogleFile {
    public String name;
    public String id;
    public String link;

    @NonNull
    @Override
    public String toString() {
        return "name: "+name + ", id: "+id+", link:"+link;
    }
}
