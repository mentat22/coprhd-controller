/*
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

public class VNXDataMover extends VNXBaseClass {

    private String _name;
    private int    _id;
    private String _role;

    public void setName(String name) {
        _name = name;
    }
    public String getName(){
        return _name;
    }
    public void setId(int id){
        _id = id;
    }
    public int getId() {
        return _id;
    }

    public VNXDataMover() {}

    public VNXDataMover(String name, int id, String role){
        _name = name;
        _id   = id;
        _role = role;
    }

    public void setRole(String role) {
        _role = role;
    }

    public String getRole() {
        return _role;
    }

    @Override
    public String toString() {
     
        return new StringBuilder().append("name : ").append(_name).append("id : ").append(_id).append("role : ").append(_role).toString();
        
    }
    
    public static String getAllDataMovers(){
        String xml = requestHeader +
                "\t<Query>\n" +
                "\t<MoverQueryParams>\n" +
                "\t<AspectSelection movers=\"true\"/>\n" +
                "\t</MoverQueryParams>\n" +
                "\t</Query>\n" +
                requestFooter;
        return xml;
    }

}