package com.omarflex5.temp.omerflex.server;

import com.omerflex.entity.Movie;

public class ServerFactory {
    public static AbstractServer createServer(String serverName) {
        switch (serverName) {
            case Movie.SERVER_MyCima:
                return new MyCimaServer();
            case Movie.SERVER_CimaNow:
                return new CimaNowServer();
            case Movie.SERVER_ARAB_SEED:
                return new ArabSeedServer();
            case Movie.SERVER_FASELHD:
                return new FaselHdServer();
            case Movie.SERVER_LAROZA:
                return new LarozaServer();
            case Movie.SERVER_AKWAM:
                return new AkwamServer();
            case Movie.SERVER_OLD_AKWAM:
                return new OldAkwamServer();
            case Movie.SERVER_IPTV:
                return new IptvServer();
            case Movie.SERVER_KOORA_LIVE:
                return new KooraLiveServer();
            case Movie.SERVER_OMAR:
                return null;
//            case "Imdb":
//                return new ImdbServer();
            default:
                throw new IllegalArgumentException("Fail generating server object in ServerFactory. Unknown server name: " + serverName );
        }
    }
}
