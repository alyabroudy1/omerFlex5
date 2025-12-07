package com.omarflex5.temp.omerflex.server.config;

import com.omerflex.entity.Movie;
import com.omerflex.entity.ServerConfig;
import com.omerflex.server.IptvServer;
import com.omerflex.server.MyCimaServer;

import java.util.Calendar;
import java.util.Date;

public class DefaultServersConfig {
    public static void initializeDefaultServers(){

        ServerConfigRepository scm = ServerConfigRepository.getInstance();
        Calendar calendar = Calendar.getInstance();
        calendar.set(2020, Calendar.JANUARY, 1); // year, month (0-based), day
        Date date = calendar.getTime();

        //### mycima ###
        ServerConfig mycimaConfig = new ServerConfig();
        mycimaConfig.setName(Movie.SERVER_MyCima);
        mycimaConfig.setUrl("https://cima.wecima.show");
        mycimaConfig.setReferer("https://cima.wecima.show/");
        mycimaConfig.setActive(true);
        mycimaConfig.setLabel("ماي سيما");
        mycimaConfig.setCreatedAt(date);

        scm.updateConfig(mycimaConfig);

//        //         ### cimaNow ###
        ServerConfig cimaNowConfig = new ServerConfig();
        cimaNowConfig.setName(Movie.SERVER_CimaNow);
        cimaNowConfig.setActive(false);
        cimaNowConfig.setUrl("https://cimanow.cc");
        cimaNowConfig.setReferer("https://cimanow.cc/");
        cimaNowConfig.setLabel("سيماناو");
        cimaNowConfig.setCreatedAt(date);

        scm.updateConfig(cimaNowConfig);
//
//
//        //### arabseed ###
        ServerConfig arabseedConfig = new ServerConfig();
        arabseedConfig.setName(Movie.SERVER_ARAB_SEED);
        arabseedConfig.setUrl("https://arabseed.show");
        arabseedConfig.setReferer("https://arabseed.show/");
        arabseedConfig.setActive(true);
        arabseedConfig.setLabel("عرب سيد");
        arabseedConfig.setCreatedAt(date);

        scm.updateConfig(arabseedConfig);
//
        //        ### fasel ###
        ServerConfig faselConfig = new ServerConfig();
        faselConfig.setActive(true);
        faselConfig.setName(Movie.SERVER_FASELHD);
        faselConfig.setUrl("https://www.faselhds.center");
        faselConfig.setReferer("https://www.faselhds.center/");
        faselConfig.setLabel("فاصل");
        faselConfig.setCreatedAt(date);

        scm.updateConfig(faselConfig);
//
//        //         ### laroza ###
        ServerConfig larozaConfig = new ServerConfig();
        larozaConfig.setName(Movie.SERVER_LAROZA);
        larozaConfig.setActive(true);
        larozaConfig.setUrl("https://www.laroza.now");
        larozaConfig.setReferer("https://www.laroza.now/");
        larozaConfig.setLabel("لاروزا");
        larozaConfig.setCreatedAt(date);

        scm.updateConfig(larozaConfig);
//
//         ### akwam ###
        ServerConfig akwamConfig = new ServerConfig();
        akwamConfig.setName(Movie.SERVER_AKWAM);
        akwamConfig.setActive(true);
        akwamConfig.setUrl("https://ak.sv");
        akwamConfig.setReferer("https://ak.sv/");
        akwamConfig.setCreatedAt(date);
        akwamConfig.setLabel("أكوام");

        scm.updateConfig(akwamConfig);

        //         ### kooraLive ###
        ServerConfig kooraLiveConfig = new ServerConfig();
        kooraLiveConfig.setName(Movie.SERVER_KOORA_LIVE);
        kooraLiveConfig.setActive(true);
        kooraLiveConfig.setUrl("https://www.koraa-live.com");
        kooraLiveConfig.setReferer("https://www.koraa-live.com/");
        kooraLiveConfig.setCreatedAt(date);
        kooraLiveConfig.setLabel("كورة");

        scm.updateConfig(kooraLiveConfig);
//
//        //### old_Akwam ###
        ServerConfig oldAkwamConfig = new ServerConfig();
        oldAkwamConfig.setName(Movie.SERVER_OLD_AKWAM);
        oldAkwamConfig.setActive(true);
        oldAkwamConfig.setUrl("https://www.ak.sv");
        oldAkwamConfig.setReferer("https://www.ak.sv/");
        oldAkwamConfig.setCreatedAt(date);
        oldAkwamConfig.setLabel("اكوام القديم");

        scm.updateConfig(oldAkwamConfig);
//
//        //### iptv ###
        ServerConfig iptvConfig = new ServerConfig();
        iptvConfig.setName(Movie.SERVER_IPTV);
        iptvConfig.setUrl(IptvServer.MAIN_IPTV_PLAYLIST_URL);
//        iptvConfig.setUrl("https://drive.google.com/drive/folders/1lHoE-WD43FGr9kHAYoo-11HrPHgUOQMa?usp=sharing");
        iptvConfig.setReferer("https://drive.google.com/");
        iptvConfig.setActive(true);
        iptvConfig.setLabel("قنوات");
        iptvConfig.setCreatedAt(date);

        scm.updateConfig(iptvConfig);
//
//        // ### omar ###
//        ServerConfig omarConfig = new ServerConfig();
//        omarConfig.setName(Movie.SERVER_OMAR);
//        omarConfig.setActive(true);
//        omarConfig.setUrl("http://194.164.53.40/movie");
//        omarConfig.setReferer("http://194.164.53.40/");
//        omarConfig.setCreatedAt(date);
//
//        scm.updateConfig(omarConfig);
//
//
//        // ### Imdb ###
//        ServerConfig imdbConfig = new ServerConfig();
//        imdbConfig.setName(Movie.SERVER_IMDB);
//        imdbConfig.setActive(true);
//        imdbConfig.setUrl("https://api.themoviedb.org/3/");
//        imdbConfig.setReferer("https://api.themoviedb.org/3/");
//        imdbConfig.setCreatedAt(date);
//
//        scm.updateConfig(imdbConfig);

    }
}