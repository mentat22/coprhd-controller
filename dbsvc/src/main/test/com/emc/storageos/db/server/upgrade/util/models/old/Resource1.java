/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.server.upgrade.util.models.old;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.StringMap;

@Cf("Resource1")
public class Resource1 extends DataObject {
    private StringMap res3Map;

    @Name("res3")
    public StringMap getRes3Map() {
        return res3Map;
    }

    public void setRes3Map(StringMap res3Map) {
        this.res3Map = res3Map;
        setChanged("res3");
    }

}
