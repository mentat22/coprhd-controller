/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.modelclient.model;

import java.util.List;

import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.IndexByKey;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.Relation;
import com.emc.storageos.db.client.model.RelationIndex;
import com.emc.storageos.db.client.model.StringSet;

/**
 * @author cgarber
 *
 */
@Cf("ClassA")
public class ClassAManyToMany extends DataObject {
    
    private StringSet bids;
    private List<ClassBManyToMany> binstances;

    @RelationIndex(cf = "RelationIndex", type = ClassBManyToMany.class)
    @IndexByKey
    @Name("bIds")
    public StringSet getBids() {
        return bids;
    }

    public void setBids(StringSet bids) {
        this.bids = bids;
    }

    @Relation(type = ClassBManyToMany.class, mappedBy = "bIds")
    @Name("bInstances")
    public List<ClassBManyToMany> getBinstances() {
        return binstances;
    }

    public void setBinstances(List<ClassBManyToMany> binstances) {
        this.binstances = binstances;
    }


}
