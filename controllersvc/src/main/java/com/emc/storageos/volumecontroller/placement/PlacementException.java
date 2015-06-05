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
package com.emc.storageos.volumecontroller.placement;

import com.emc.storageos.exceptions.FatalClientControllerException;
import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * PlacementException is very special, as it subclasses
 * FatalClientControllerException (a fatal client) exception.
 * This is thrown in code like the BlockStorageScheduler where
 * the code is called both from the apisvc and the controllersvc.
 * This is done in order to validate placement operations 
 * in the apisvc before
 * initiating the asynchronous controller operations.
 * @author watson
 */
public class PlacementException extends FatalClientControllerException{
    
    private static final long serialVersionUID = -6826356337754883087L;
    
    public static final PlacementExceptions exceptions = 
            ExceptionMessagesProxy.create(PlacementExceptions.class);

    protected PlacementException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(code, cause, detailBase, detailKey, detailParams);
    }
}
