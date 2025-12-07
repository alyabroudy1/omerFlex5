package com.omarflex5.temp.omerflex.providers;


import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class DlnaCaster {
    private static final String TAG = "DlnaCaster";

    public interface CastListener {
        void onCastSuccess();
        void onCastError(String error);
    }

    public static void castToDevice(String deviceLocation, String videoUrl, String title, CastListener listener) {
        new Thread(() -> {
            try {
                Log.d(TAG, "Starting DLNA cast to: " + deviceLocation);
                Log.d(TAG, "Video URL: " + videoUrl);
                Log.d(TAG, "Title: " + title);

                // First, get device description to find AVTransport service
                String controlURL = getAvTransportControlURL(deviceLocation);
                if (controlURL == null) {
                    listener.onCastError("Device doesn't support media casting");
                    return;
                }

                // Send SetAVTransportURI command
                boolean success = sendSetAvTransportUri(controlURL, videoUrl, title);
                if (success) {
                    // Send Play command
                    success = sendPlayCommand(controlURL);
                }

                if (success) {
                    Log.d(TAG, "DLNA cast started successfully");
                    listener.onCastSuccess();
                } else {
                    listener.onCastError("Failed to start playback on device");
                }

            } catch (Exception e) {
                Log.e(TAG, "DLNA casting error", e);
                listener.onCastError("Casting failed: " + e.getMessage());
            }
        }).start();
    }

    private static String getAvTransportControlURL(String deviceLocation) {
        try {
            URL url = new URL(deviceLocation);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            // Parse the XML response to find AVTransport service
            String xml = response.toString();
            Log.d(TAG, "Device description XML: " + xml.substring(0, Math.min(500, xml.length())));

            // Look for AVTransport service and control URL
            // This is a simplified parser - you might need to adjust based on your device
            if (xml.contains("AVTransport") && xml.contains("controlURL")) {
                int controlIndex = xml.indexOf("controlURL");
                if (controlIndex != -1) {
                    int start = xml.indexOf(">", controlIndex) + 1;
                    int end = xml.indexOf("<", start);
                    String controlPath = xml.substring(start, end);

                    // Build full control URL
                    URL deviceUrl = new URL(deviceLocation);
                    String baseUrl = deviceUrl.getProtocol() + "://" + deviceUrl.getHost() + ":" + deviceUrl.getPort();
                    return baseUrl + controlPath;
                }
            }

            return null;

        } catch (Exception e) {
            Log.e(TAG, "Error getting device description", e);
            return null;
        }
    }

    private static boolean sendSetAvTransportUri(String controlURL, String videoUrl, String title) {
        try {
            String soapAction = "urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI";
            String soapBody =
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                            "<s:Body>" +
                            "<u:SetAVTransportURI xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                            "<InstanceID>0</InstanceID>" +
                            "<CurrentURI>" + escapeXml(videoUrl) + "</CurrentURI>" +
                            "<CurrentURIMetaData>&lt;DIDL-Lite xmlns=&quot;urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/&quot; xmlns:dc=&quot;http://purl.org/dc/elements/1.1/&quot; xmlns:upnp=&quot;urn:schemas-upnp-org:metadata-1-0/upnp/&quot;&gt;&lt;item id=&quot;1&quot; parentID=&quot;-1&quot; restricted=&quot;0&quot;&gt;&lt;dc:title&gt;" + escapeXml(title) + "&lt;/dc:title&gt;&lt;upnp:class&gt;object.item.videoItem&lt;/upnp:class&gt;&lt;res protocolInfo=&quot;http-get:*:video/*:*&quot;&gt;" + escapeXml(videoUrl) + "&lt;/res&gt;&lt;/item&gt;&lt;/DIDL-Lite&gt;</CurrentURIMetaData>" +
                            "</u:SetAVTransportURI>" +
                            "</s:Body>" +
                            "</s:Envelope>";

            return sendSoapRequest(controlURL, soapAction, soapBody);

        } catch (Exception e) {
            Log.e(TAG, "Error sending SetAVTransportURI", e);
            return false;
        }
    }

    private static boolean sendPlayCommand(String controlURL) {
        try {
            String soapAction = "urn:schemas-upnp-org:service:AVTransport:1#Play";
            String soapBody =
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                            "<s:Body>" +
                            "<u:Play xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                            "<InstanceID>0</InstanceID>" +
                            "<Speed>1</Speed>" +
                            "</u:Play>" +
                            "</s:Body>" +
                            "</s:Envelope>";

            return sendSoapRequest(controlURL, soapAction, soapBody);

        } catch (Exception e) {
            Log.e(TAG, "Error sending Play command", e);
            return false;
        }
    }

    private static boolean sendSoapRequest(String controlURL, String soapAction, String soapBody) {
        try {
            URL url = new URL(controlURL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
            connection.setRequestProperty("SOAPAction", soapAction);
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            OutputStream os = connection.getOutputStream();
            os.write(soapBody.getBytes());
            os.flush();
            os.close();

            int responseCode = connection.getResponseCode();
            Log.d(TAG, "SOAP response code: " + responseCode);

            return responseCode == 200;

        } catch (Exception e) {
            Log.e(TAG, "Error sending SOAP request", e);
            return false;
        }
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}