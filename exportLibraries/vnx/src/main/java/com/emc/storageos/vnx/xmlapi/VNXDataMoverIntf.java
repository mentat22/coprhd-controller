/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnx.xmlapi;

public class VNXDataMoverIntf extends VNXBaseClass {
    
    private String _name;
    private String _ipAddress;
    private String _dataMoverId;

    public VNXDataMoverIntf(String name, String ipAddr, String id) {
        _name        = name;
        _ipAddress   = ipAddr;
        _dataMoverId = id;
    }

    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    public String getIpAddress() {
        return _ipAddress;
    }

    public void setIpAddress(String ipAddr) {
        _ipAddress = ipAddr;
    }

    public String getDataMoverId() {
        return _dataMoverId;
    }

    public void setDataMoverId(String id) {
        _dataMoverId = id;
    }

    public static String discoverDataMovers(){
        String xml = requestHeader +
                "\t<Query>\n" +
                "\t<MoverQueryParams>\n" +
                "\t<AspectSelection movers=\"true\" moverInterfaces=\"true\"/>\n" +
                "\t</MoverQueryParams>\n" +
                "\t</Query>\n" +
                requestFooter;
        return xml;
    }
    @Override
    public String toString() {
     
        return new StringBuilder().append("name : ").append(_name).append("ipAddress : ").append(_ipAddress).append("dataMoverId ").append(_dataMoverId).toString();
        
    }
}
