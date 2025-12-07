package com.omarflex5.temp.omerflex.entity.dto;

import java.util.List;

public class MovieDTO {

    //"type": "search",
    //    "title": "ss",
    //    "result": [
    //      {
    //        "tvgName": "ssc news ᴴᴰ",
    //        "tvgLogo": "https://airmax.boats:443/images/5ff58cb582115da542b99eccca1fe452.png",
    //        "groupTitle": "SSC SPORT",
    //        "fileName": "317.ts",
    //        "type": "Iptv_channel",
    //        "id": 25,
    //        "title": "ssc news ᴴᴰ",
    //        "description": null,
    //        "cardImage": null,
    //        "backgroundImage": null,
    //        "rate": null,
    //        "playedTime": null,
    //        "totalTime": null,
    //        "createdAt": "2024-10-06T13:59:30+00:00",
    //        "updatedAt": "2024-10-06T13:59:30+00:00",
    //        "categories": [],
    //        "url": "https://airmax.boats:443/airmaxtv1122/airmaxtv2211/317.ts||user-agent=airmaxtv"
    //      },



    public String tvgName;
    public String tvgLogo;
    public String groupTitle;
    public String credentialUrl;
    public String fileName;
    public String url;
    public String type;
    public int id;
    public String title;
    public String description;
    public String cardImage;
    public String backgroundImage;
    public String rate;
    public int playedTime;
    public int totalTime;

    public String createdAt;

    public String updatedAt;
    public LinkDTO link;

    //    public List<SourceDTO> sources;
    public List<CategoryDTO> categories;
    //  public Collection categories;


    @Override
    public String toString() {
        return "MovieDTO{" +
                "tvgName='" + tvgName + '\'' +
                ", tvgLogo='" + tvgLogo + '\'' +
                ", groupTitle='" + groupTitle + '\'' +
                ", fileName='" + fileName + '\'' +
                ", url='" + url + '\'' +
                ", credentialUrl='" + credentialUrl + '\'' +
                ", type='" + type + '\'' +
                ", id=" + id +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", cardImage='" + cardImage + '\'' +
                ", backgroundImage='" + backgroundImage + '\'' +
                ", rate='" + rate + '\'' +
                ", playedTime=" + playedTime +
                ", totalTime=" + totalTime +
                ", createdAt='" + createdAt + '\'' +
                ", updatedAt='" + updatedAt + '\'' +
                ", link=" + link +
                ", categories=" + categories +
                '}';
    }
}
