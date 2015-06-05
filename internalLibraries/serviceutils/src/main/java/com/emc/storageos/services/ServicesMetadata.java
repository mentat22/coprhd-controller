/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.services;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Class that holds list of services that are available for control node and extra node.
 * Provides static methods to retrieve these services.
 */
@Component
public class ServicesMetadata implements InitializingBean {

    private static Map<String, ServiceMetadata> _serviceMetadataMap = null;
    private static Map<String, RoleMetadata> _roleMetadataMap = null;
    private static Map<String, List<String>> _roleServiceIndex = null;

    public void setServiceMetadataMap(LinkedHashMap<String,
            ServiceMetadata> serviceMetadataMap) {
        if (_serviceMetadataMap == null) {
            _serviceMetadataMap = ImmutableMap.copyOf(serviceMetadataMap);
        }
    }

    public static Map<String, ServiceMetadata> getServiceMetadataMap() {
        if (_serviceMetadataMap == null) {
            throw new IllegalStateException("Services metadata doesn't exist");
        }
        return _serviceMetadataMap;
    }
    
    public void setRoleMetadataMap(LinkedHashMap<String,
            RoleMetadata> roleMetadataMap) {
        if (_roleMetadataMap == null) {
            _roleMetadataMap = ImmutableMap.copyOf(roleMetadataMap);
        }
    }

    public static Map<String, RoleMetadata> getRoleMetadataMap() {
        if (_roleMetadataMap == null) {
            throw new IllegalStateException("Role metadata doesn't exist");
        }
        return _roleMetadataMap;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // initialize the role-to-service index
        Splitter splitter = Splitter.on(' ').trimResults().omitEmptyStrings();
        Map<String, List<String>> index = new LinkedHashMap<String, List<String>>();
        for (String roleName : _roleMetadataMap.keySet()) {
            ImmutableList.Builder<String> listBuilder = new ImmutableList.Builder<>();
            for (ServiceMetadata serviceMetadata : _serviceMetadataMap.values()) {
                Set<String> roles = Sets.newHashSet(splitter.split(serviceMetadata.getRoles()));
                if(roles.contains(roleName)) {
                    listBuilder.add(serviceMetadata.getName());
                }
            }
            index.put(roleName, listBuilder.build());
        }
        _roleServiceIndex = ImmutableMap.copyOf(index);
    }
    
    /**
     * Returns list of service names that are available for control node
     */
    public static List<String> getControlNodeServiceNames() {
        if (_serviceMetadataMap == null) {
            throw new IllegalStateException("Service Metadata does not exist");
        }

        List<String> controlServices = new ArrayList<String>();
        for (ServiceMetadata serviceMetadata : _serviceMetadataMap.values()) {
            if (serviceMetadata.isControlNodeService()) {
                controlServices.add(serviceMetadata.getName());
            }
        }
        return controlServices;
    }

    /**
     * Returns list of service names that are available for extra node
     */
    public static List<String> getExtraNodeServiceNames() {
        if (_serviceMetadataMap == null) {
            throw new IllegalStateException("Service Metadata does not exist");
        }

        List<String> extraNodeServices = new ArrayList<String>();
        for (ServiceMetadata serviceMetadata : _serviceMetadataMap.values()) {
            if (serviceMetadata.isExtraNodeService()) {
                extraNodeServices.add(serviceMetadata.getName());
            }
        }
        return extraNodeServices;
    }
    
    public static List<String> getRoleServiceNames(String... role) {
        return getRoleServiceNames(Arrays.asList(role));
    }
    
    public static List<String> getRoleServiceNames(Iterable<String> roles) {
        if (_roleServiceIndex == null) {
            throw new IllegalStateException("Role Index does not exist");
        }
        HashSet<String> names = new HashSet<>();
        for(String role: roles) {
            names.addAll(_roleServiceIndex.get(role));
        }
        return ImmutableList.copyOf(names);
    }
}
