/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.model;

import java.io.Serializable;

public class IPInterface implements Serializable {
    private static final long serialVersionUID = -1336961543426762018L;

    private String interfaceName;
    private String ipAddress;
    private String MACAddress;
    private String netMask;
    private String ip6Address;
    private String broadcastAddress;
    
    public String getIpAddress() {
        return ipAddress;
    }
    public String getBroadcastAddress() {
        return broadcastAddress;
    }
    public void setBroadcastAddress(String broadcastAddress) {
        this.broadcastAddress = broadcastAddress;
    }
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    public String getMACAddress() {
        return MACAddress;
    }
    public void setMACAddress(String mACAddress) {
        MACAddress = mACAddress;
    }
    public String getNetMask() {
        return netMask;
    }
    public void setNetMask(String netMask) {
        this.netMask = netMask;
    }
    public String getInterfaceName() {
        return interfaceName;
    }
    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }
    public String getIP6Address() {
        return ip6Address;
    }
    public void setIP6Address(String ip6Address) {
        this.ip6Address = ip6Address;
    }
    @Override
    public String toString() {
        return "IPInfo [interfaceName=" + interfaceName + ", ipAddress="
                + ipAddress + ", MACAddress=" + MACAddress + ", netMask="
                + netMask + ", ip6Address=" + ip6Address
                + ", broadcastAddress=" + broadcastAddress + "]";
    }

}
