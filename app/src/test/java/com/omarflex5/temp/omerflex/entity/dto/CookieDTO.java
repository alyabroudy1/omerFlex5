package com.omarflex5.temp.omerflex.entity.dto;

import java.util.Date;

public class CookieDTO {

    public String name;
    public String referer;
    public String headers;
    public String cookie;
    public Date date;

    @Override
    public String toString() {
        return "CookieDTO{" +
                "name='" + name + '\'' +
                ", referer='" + referer + '\'' +
                ", headers='" + headers + '\'' +
                ", cookie='" + cookie + '\'' +
                ", date=" + date +
                '}';
    }
}
