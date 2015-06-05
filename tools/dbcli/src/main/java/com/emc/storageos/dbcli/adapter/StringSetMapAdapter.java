/**
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
package com.emc.storageos.dbcli.adapter;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;

import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;

@XmlRootElement
public class StringSetMapAdapter extends
     XmlAdapter<StringSetMapAdapter.AdaptedMap, StringSetMap> {

    public static class AdaptedMap {
        public List<Entry> entry = new ArrayList<Entry>();
    }
    
    public static class Entry {
        public String key;
        public StringSet value;
    }
    
    @Override
    public StringSetMap unmarshal(AdaptedMap adaptedMap)
         throws Exception {
        StringSetMap map = new StringSetMap();
        for (Entry entry : adaptedMap.entry) {
            map.put(entry.key, entry.value);
        }
        return map;
    }
    
    @Override
    public AdaptedMap marshal(StringSetMap map) throws Exception {
        AdaptedMap adaptedMap = new AdaptedMap();
        for (Map.Entry<String, AbstractChangeTrackingSet<String>> mapEntry : map.entrySet()) {
            Entry entry = new Entry();
            entry.key = mapEntry.getKey();
            entry.value = (StringSet)mapEntry.getValue();
            adaptedMap.entry.add(entry);
        }
        return adaptedMap;
    }

}
