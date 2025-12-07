package com.omarflex5.temp.omerflex.service.casting;

import android.net.nsd.NsdServiceInfo;
import android.net.wifi.p2p.WifiP2pDevice;

import java.net.InetAddress;

/**
 * Represents a discovered device on the network
 * This class encapsulates information about devices discovered through NSD or WiFi Direct
 */
public class Device {
    private String deviceName;
    private String deviceAddress;
    private InetAddress deviceIp;
    private int devicePort;
    private String serviceName;
    private String serviceType;
    private DeviceType deviceType;
    private long lastSeen;
    private boolean isConnected;
    
    public enum DeviceType {
        UNKNOWN,
        ANDROID_TV,
        ANDROID_PHONE,
        ANDROID_TABLET,
        OTHER
    }
    
    public Device() {
        this.lastSeen = System.currentTimeMillis();
        this.isConnected = false;
        this.deviceType = DeviceType.UNKNOWN;
    }
    
    public Device(NsdServiceInfo serviceInfo) {
        this();
        this.deviceName = serviceInfo.getServiceName();
        this.serviceName = serviceInfo.getServiceName();
        this.serviceType = serviceInfo.getServiceType();
        this.devicePort = serviceInfo.getPort();
        this.deviceIp = serviceInfo.getHost();
        this.deviceAddress = deviceIp != null ? deviceIp.getHostAddress() : null;
    }
    
    public Device(WifiP2pDevice wifiDevice) {
        this();
        this.deviceName = wifiDevice.deviceName;
        this.deviceAddress = wifiDevice.deviceAddress;
        this.deviceType = determineDeviceType(wifiDevice.deviceName);
    }
    
    private DeviceType determineDeviceType(String deviceName) {
        if (deviceName == null) return DeviceType.UNKNOWN;
        
        String lowerName = deviceName.toLowerCase();
        if (lowerName.contains("tv") || lowerName.contains("television") || lowerName.contains("smarttv")) {
            return DeviceType.ANDROID_TV;
        } else if (lowerName.contains("phone") || lowerName.contains("mobile")) {
            return DeviceType.ANDROID_PHONE;
        } else if (lowerName.contains("tablet")) {
            return DeviceType.ANDROID_TABLET;
        }
        return DeviceType.OTHER;
    }
    
    // Getters and Setters
    public String getDeviceName() {
        return deviceName;
    }
    
    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }
    
    public String getDeviceAddress() {
        return deviceAddress;
    }
    
    public void setDeviceAddress(String deviceAddress) {
        this.deviceAddress = deviceAddress;
    }
    
    public InetAddress getDeviceIp() {
        return deviceIp;
    }
    
    public void setDeviceIp(InetAddress deviceIp) {
        this.deviceIp = deviceIp;
        if (deviceIp != null) {
            this.deviceAddress = deviceIp.getHostAddress();
        }
    }
    
    public int getDevicePort() {
        return devicePort;
    }
    
    public void setDevicePort(int devicePort) {
        this.devicePort = devicePort;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
    
    public String getServiceType() {
        return serviceType;
    }
    
    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }
    
    public DeviceType getDeviceType() {
        return deviceType;
    }
    
    public void setDeviceType(DeviceType deviceType) {
        this.deviceType = deviceType;
    }
    
    public long getLastSeen() {
        return lastSeen;
    }
    
    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
    }
    
    public boolean isConnected() {
        return isConnected;
    }
    
    public void setConnected(boolean connected) {
        isConnected = connected;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Device device = (Device) obj;
        return deviceAddress != null && deviceAddress.equals(device.deviceAddress);
    }
    
    @Override
    public int hashCode() {
        return deviceAddress != null ? deviceAddress.hashCode() : 0;
    }
    
    @Override
    public String toString() {
        return "Device{" +
                "deviceName='" + deviceName + '\'' +
                ", deviceAddress='" + deviceAddress + '\'' +
                ", deviceIp=" + (deviceIp != null ? deviceIp.getHostAddress() : "null") +
                ", devicePort=" + devicePort +
                ", serviceName='" + serviceName + '\'' +
                ", serviceType='" + serviceType + '\'' +
                ", deviceType=" + deviceType +
                ", isConnected=" + isConnected +
                '}';
    }
}