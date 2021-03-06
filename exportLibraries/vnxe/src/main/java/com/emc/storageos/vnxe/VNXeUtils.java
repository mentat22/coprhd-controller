/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnxe;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VNXeUtils {
	private static Logger _logger = LoggerFactory.getLogger(VNXeUtils.class);
    private static final String HOST_NAME_PATTERN = 
        "^(?![0-9]+$)(?:([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])$";

 // Regular Expression to match an IPv4 IP Address.
    private static final String IPV4_PATTERN = 
            "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
            "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
            "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
            "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";    
    // Regular Expression to match an IPv6 IP Address.
    private static final String IPV6_PATTERN = 
            "^([0-9a-fA-F]{1,4}|0)(\\:([0-9a-fA-F]{1,4}|0)){7}$";  
    
    private static int KBYTES = 1024;
    
	public static boolean isHostType(String hostName) {
        return hostName.matches(HOST_NAME_PATTERN);
	}
	
	public static boolean isIPV4Type(String ip) {
		return ip.matches(IPV4_PATTERN);
	}
	
	public static boolean isIPV6Type(String ip) {
		return ip.matches(IPV6_PATTERN);
	}
	
	public static String getHostIp(String hostName) throws UnknownHostException{
		_logger.info("Host name: " + hostName);
		String ipAddr = "";
		
	    InetAddress inetAddr = InetAddress.getByName(hostName);             
	    byte[] addr = inetAddr.getAddress();
	
	    // Convert to dot representation
	    ipAddr = "";
	    for (int i = 0; i < addr.length; i++) {
	        if (i > 0) {
	            ipAddr += ".";
	        }
	        ipAddr += addr[i] & 0xFF;
	    }
	         
	    _logger.info("IP Address: " + ipAddr);
	         
	    return ipAddr;
	     
		
	}
	
	public static String buildNfsShareName(String id, String path) {
	    path = path.replace('/', '.'); 
	    String name = String.format("%1$s.%2$s", id, path);
	    return name;
	}
	  
	public static String convertCIDRToNetmask(int cidr) {
	    
	    int mask = 0xffffffff << (32 - cidr);
	   
	    int value = mask;
	    byte[] bytes = new byte[]{ 
	            (byte)(value >>> 24), (byte)(value >> 16 & 0xff), (byte)(value >> 8 & 0xff), (byte)(value & 0xff) };

	    InetAddress netAddr;
        try {
            netAddr = InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            return null;
        }
	    System.out.println("Mask=" + netAddr.getHostAddress());
	    return netAddr.getHostAddress();
	}
	
	public static long convertDoubleSizeToViPRLong(double size) {
	    double kSize = size/KBYTES;
	    return Double.valueOf(kSize).longValue();
	    
	}
}
