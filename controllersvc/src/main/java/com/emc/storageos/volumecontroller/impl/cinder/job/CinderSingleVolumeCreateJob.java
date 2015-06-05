/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2014. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.cinder.job;

import java.net.URI;
import java.util.Map;

import com.emc.storageos.cinder.CinderEndPointInfo;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class CinderSingleVolumeCreateJob extends AbstractCinderVolumeCreateJob {

	private static final long serialVersionUID = -5238397100589536628L;

	/**
	 * @param jobId
	 * @param jobName
	 * @param storageSystem
	 * @param componentType
	 * @param ep
	 * @param taskCompleter
	 * @param storagePoolUri
	 */
	public CinderSingleVolumeCreateJob(String jobId, String jobName,
														URI storageSystem, String componentType,
														CinderEndPointInfo ep, TaskCompleter taskCompleter,
														URI storagePoolUri, Map<String, URI> volumeIds) 
	{
		super(jobId, String.format("CreateSingleVolume:VolumeName:%s", jobName), storageSystem, 
		        componentType, ep, taskCompleter, storagePoolUri, volumeIds);
	}

}
