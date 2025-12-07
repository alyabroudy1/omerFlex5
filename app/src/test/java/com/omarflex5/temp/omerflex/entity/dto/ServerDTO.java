package com.omarflex5.temp.omerflex.entity.dto;

public class ServerDTO {
    public String name;
    public String authority;

    @Override
    public String toString() {
        return "ServerDTO{" +
                "name='" + name + '\'' +
                ", webAddress='" + authority + '\'' +
                '}';
    }
}
