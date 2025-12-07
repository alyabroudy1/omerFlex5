package com.omarflex5.cast.dlna;

import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Handles sending UPnP/DLNA commands to a device (SetAVTransportURI, Play,
 * Pause, Stop).
 */
public class DlnaCaster {
    private static final String TAG = "DlnaCaster";

    public interface CastListener {
        void onCastSuccess();

        void onCastError(String error);
    }

    public static void castToDevice(String deviceLocation, String videoUrl, String title, CastListener listener) {
        new Thread(() -> {
            try {
                Log.d(TAG, "Casting to: " + deviceLocation);

                // 1. Get AVTransport control URL
                String controlURL = getAvTransportControlURL(deviceLocation);
                if (controlURL == null) {
                    if (listener != null)
                        listener.onCastError("Device does not support AVTransport");
                    return;
                }

                // 2. SetAVTransportURI
                if (!sendSetAvTransportUri(controlURL, videoUrl, title)) {
                    if (listener != null)
                        listener.onCastError("Failed to set video URI");
                    return;
                }

                // 3. Play
                if (!sendPlayCommand(controlURL)) {
                    if (listener != null)
                        listener.onCastError("Failed to start playback");
                    return;
                }

                if (listener != null)
                    listener.onCastSuccess();

            } catch (Exception e) {
                Log.e(TAG, "Cast exception", e);
                if (listener != null)
                    listener.onCastError(e.getMessage());
            }
        }).start();
    }

    public static void stopCast(String deviceLocation) {
        new Thread(() -> {
            String controlURL = getAvTransportControlURL(deviceLocation);
            if (controlURL != null)
                sendStopCommand(controlURL);
        }).start();
    }

    private static String getAvTransportControlURL(String deviceLocation) {
        try {
            URL url = new URL(deviceLocation);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
                response.append(line);
            reader.close();

            String xml = response.toString();

            // Simple string parsing to avoid XML dependency overhead for now
            if (xml.contains("AVTransport") && xml.contains("controlURL")) {
                // Find service block containing AVTransport
                int serviceStart = xml.indexOf("AVTransport");
                int controlUrlStart = xml.indexOf("controlURL", serviceStart); // Look forward from service definition
                if (controlUrlStart != -1) {
                    int valStart = xml.indexOf(">", controlUrlStart) + 1;
                    int valEnd = xml.indexOf("<", valStart);
                    String path = xml.substring(valStart, valEnd);

                    // Handle relative or absolute URLs
                    if (path.startsWith("http"))
                        return path;

                    URL devUrl = new URL(deviceLocation);
                    if (!path.startsWith("/"))
                        path = "/" + path;
                    return devUrl.getProtocol() + "://" + devUrl.getHost() + ":" + devUrl.getPort() + path;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean sendSetAvTransportUri(String controlURL, String videoUrl, String title) {
        String mimeType = "video/mp4";
        String protocolInfo = "http-get:*:video/mp4:*";

        if (videoUrl.contains(".m3u8")) {
            mimeType = "application/vnd.apple.mpegurl";
            protocolInfo = "http-get:*:application/vnd.apple.mpegurl:*";
        }

        String soapBody = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
                +
                "<s:Body>" +
                "<u:SetAVTransportURI xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                "<InstanceID>0</InstanceID>" +
                "<CurrentURI>" + escapeXml(videoUrl) + "</CurrentURI>" +
                "<CurrentURIMetaData>&lt;DIDL-Lite xmlns=&quot;urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/&quot; xmlns:dc=&quot;http://purl.org/dc/elements/1.1/&quot; xmlns:upnp=&quot;urn:schemas-upnp-org:metadata-1-0/upnp/&quot;&gt;&lt;item id=&quot;1&quot; parentID=&quot;-1&quot; restricted=&quot;0&quot;&gt;&lt;dc:title&gt;"
                + escapeXml(title)
                + "&lt;/dc:title&gt;&lt;upnp:class&gt;object.item.videoItem&lt;/upnp:class&gt;&lt;res protocolInfo=&quot;"
                + protocolInfo + "&quot;&gt;"
                + escapeXml(videoUrl) + "&lt;/res&gt;&lt;/item&gt;&lt;/DIDL-Lite&gt;</CurrentURIMetaData>" +
                "</u:SetAVTransportURI>" +
                "</s:Body>" +
                "</s:Envelope>";
        return sendSoapRequest(controlURL, "urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI", soapBody);
    }

    private static boolean sendPlayCommand(String controlURL) {
        String soapBody = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
                +
                "<s:Body>" +
                "<u:Play xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                "<InstanceID>0</InstanceID>" +
                "<Speed>1</Speed>" +
                "</u:Play>" +
                "</s:Body>" +
                "</s:Envelope>";
        return sendSoapRequest(controlURL, "urn:schemas-upnp-org:service:AVTransport:1#Play", soapBody);
    }

    private static boolean sendStopCommand(String controlURL) {
        String soapBody = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
                +
                "<s:Body>" +
                "<u:Stop xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                "<InstanceID>0</InstanceID>" +
                "</u:Stop>" +
                "</s:Body>" +
                "</s:Envelope>";
        return sendSoapRequest(controlURL, "urn:schemas-upnp-org:service:AVTransport:1#Stop", soapBody);
    }

    private static boolean sendSoapRequest(String controlURL, String soapAction, String soapBody) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(controlURL).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
            conn.setRequestProperty("SOAPAction", soapAction);
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);

            OutputStream os = conn.getOutputStream();
            os.write(soapBody.getBytes());
            os.close();

            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            Log.e(TAG, "SOAP Error: " + soapAction, e);
            return false;
        }
    }

    private static String escapeXml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'",
                "&apos;");
    }
}
