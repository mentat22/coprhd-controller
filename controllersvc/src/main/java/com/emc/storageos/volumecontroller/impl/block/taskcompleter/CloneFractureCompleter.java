/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class CloneFractureCompleter extends VolumeTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(CloneFractureCompleter.class);
    
    public CloneFractureCompleter(URI cloneId, String opId) {
        super(Volume.class, cloneId, opId);
    }

    public CloneFractureCompleter(List<URI> clones, String opId) {
        super(Volume.class, clones, opId);
    }
    
    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) {
        _log.info("START FullCopyFractureCompleter");
        try {
            
            for (URI clone : getIds()) {
                switch (status) {
                case error:
                    dbClient.error(Volume.class, clone, getOpId(), coded);
                    break;
                default:
                    dbClient.ready(Volume.class, clone, getOpId());
                }
            }
            super.complete(dbClient, status, coded);
            
        } catch (Exception e) {
            _log.error("Failed to update status for fracture volume clone", e);
        }
    }

}
