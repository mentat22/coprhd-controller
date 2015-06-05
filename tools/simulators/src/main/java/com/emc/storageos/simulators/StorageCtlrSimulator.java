/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.simulators;


/**
 * API for generic storage controller simulator
 */
public interface StorageCtlrSimulator {
    /**
     * Starts simulator service
     *
     * @throws Exception
     */
    public void start() throws Exception;

    /**
     * Stops simulator service
     *
     * @throws Exception
     */
    public void stop() throws Exception;
}
