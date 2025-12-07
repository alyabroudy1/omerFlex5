package com.omarflex5.temp.omerflex.entity.dto;

import java.util.HashMap;

public class IptvSegmentDTO {
    public String id;
    public String name;
    public String tvgName;
    public String groupTitle;
    public String tvgLogo;
    public String url;
    public String fileName;
    public String credentialUrl;
    public HashMap<String, String> httpHeaders = new HashMap<>();

    @Override
    public String toString() {
        return "IptvSegmentDTO{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", tvgName='" + tvgName + '\'' +
                ", groupTitle='" + groupTitle + '\'' +
                ", tvgLogo='" + tvgLogo + '\'' +
                ", url='" + url + '\'' +
                ", headers='" + httpHeaders.toString() + '\'' +
                ", fileName='" + fileName + '\'' +
                ", credentialUrl='" + credentialUrl + '\'' +
                '}';
    }
}
