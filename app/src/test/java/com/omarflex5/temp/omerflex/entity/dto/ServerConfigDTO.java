package com.omarflex5.temp.omerflex.entity.dto;

public class ServerConfigDTO {

    public String name;
    public String label;
    public  String url;
    public  String referer;
    public boolean isActive;
    public String description;
    public String date;

    @Override
    public String toString() {
        return "ServerConfig{" +
                "name='" + name + '\'' +
                ", label='" + label + '\'' +
                ", url='" + url + '\'' +
                ", referer='" + referer + '\'' +
                ", isActive=" + isActive +
                ", description='" + description + '\'' +
                ", date='" + date + '\'' +
                '}';
    }
}
