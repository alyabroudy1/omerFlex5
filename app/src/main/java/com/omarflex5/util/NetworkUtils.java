package com.omarflex5.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

/**
 * Utility class for network connectivity checks.
 */
public class NetworkUtils {

    /**
     * Check if the device has an active internet connection.
     */
    public static boolean isNetworkAvailable(Context context) {
        if (context == null) {
            return false;
        }

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) {
                return false;
            }

            NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
            return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            // For older APIs
            android.net.NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.isConnected();
        }
    }

    /**
     * Get the local IP address of the device, prioritizing standard home network
     * ranges.
     */
    public static String getLocalIpAddress() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface
                    .getNetworkInterfaces();

            String bestIp = null;
            int bestScore = 0; // 0=none, 1=10.x, 2=172.x, 3=192.168.x

            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface networkInterface = interfaces.nextElement();
                String interfaceName = networkInterface.getName();

                // Skip unwanted interfaces
                if (interfaceName == null ||
                        interfaceName.startsWith("rmnet") ||
                        interfaceName.startsWith("tun") ||
                        interfaceName.startsWith("dummy") ||
                        interfaceName.startsWith("p2p") ||
                        interfaceName.startsWith("lo")) {
                    continue;
                }

                java.util.Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && address instanceof java.net.Inet4Address) {
                        String ip = address.getHostAddress();
                        int score = 1; // Default score for valid IP (e.g. 10.x.x.x)

                        if (ip.startsWith("192.168.")) {
                            score = 3;
                        } else if (ip.startsWith("172.")) {
                            score = 2;
                        }

                        // Bonus for wlan/eth to strictly prefer physical hardware interfaces
                        if (interfaceName.startsWith("wlan") || interfaceName.startsWith("eth")) {
                            score += 1;
                        }

                        if (score > bestScore) {
                            bestScore = score;
                            bestIp = ip;
                        }
                    }
                }
            }

            if (bestIp != null) {
                return bestIp;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "0.0.0.0";
    }
}
