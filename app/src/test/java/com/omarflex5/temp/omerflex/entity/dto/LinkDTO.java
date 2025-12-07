package com.omarflex5.temp.omerflex.entity.dto;

public class LinkDTO {
    public int id;
    public String title;
    public String url;
    public String state;
    public boolean splittable;
    public ServerDTO server;
    public String authority;

    @Override
    public String toString() {
        return "LinkDTO{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", url='" + url + '\'' +
                ", state='" + state + '\'' +
                ", splittable=" + splittable +
                ", server=" + server +
                ", authority='" + authority + '\'' +
                '}';
    }
}
