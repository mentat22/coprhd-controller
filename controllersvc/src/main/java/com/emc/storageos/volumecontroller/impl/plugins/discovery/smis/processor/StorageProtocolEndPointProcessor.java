/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.cim.CIMInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.StoragePoolAssociationHelper;
import com.emc.storageos.volumecontroller.impl.StoragePortAssociationHelper;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.google.common.base.Joiner;

public class StorageProtocolEndPointProcessor extends StorageEndPointProcessor {
    private Logger _logger = LoggerFactory
            .getLogger(StorageProtocolEndPointProcessor.class);
    private DbClient _dbClient;
    private static final String NAME = "Name";
    private static final String SYSTEMNAME = "SystemName";
    private static final String COMMA_STR = ",";

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            @SuppressWarnings("unchecked")
            final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            CoordinatorClient coordinator = (CoordinatorClient) keyMap.get(Constants.COORDINATOR_CLIENT);
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            Map<URI,StoragePool> poolsToMatchWithVpool = (Map<URI, StoragePool>) keyMap.get(Constants.MODIFIED_STORAGEPOOLS);
            StorageSystem device = _dbClient.queryObject(StorageSystem.class, profile.getSystemId());
            List<StoragePort> newPorts = new ArrayList<StoragePort>();
            List<StoragePort> existingPorts = new ArrayList<StoragePort>();
            while (it.hasNext()) {
                CIMInstance endPointInstance = null;
                StoragePort port = null;
                try {
                    endPointInstance = it.next();
                    String portInstanceID = endPointInstance.getObjectPath()
                            .getKey(SYSTEMNAME).getValue().toString();
                    String iScsiPortName = getCIMPropertyValue(endPointInstance, NAME);
                    // Skip the iSCSI ports without name or without a valid name.
                    if (null == iScsiPortName || iScsiPortName.split(COMMA_STR)[0].length() <= 0) {
                       _logger.warn("Invalid port Name found for {} Skipping", portInstanceID);
                       continue;
                    }
                    port = checkEthernetStoragePortExistsInDB(
                            iScsiPortName.split(COMMA_STR)[0].toLowerCase(), _dbClient, device);
                    createEthernetStoragePort(keyMap, port, endPointInstance,
                            portInstanceID, coordinator, newPorts, existingPorts);
                    addPath(keyMap, operation.get_result(),
                            endPointInstance.getObjectPath());
                } catch (Exception e) {
                    _logger.warn("SCSI End Point Discovery failed for {}-->{}", "",
                            getMessage(e));
                }
            }
            
            @SuppressWarnings("unchecked")
            List<List<StoragePort>> portsUsedToRunNetworkConnectivity = (List<List<StoragePort>>) keyMap.get(Constants.STORAGE_PORTS);
            portsUsedToRunNetworkConnectivity.add(newPorts);
            
            //discovered ports used later to check for not visible ports
            List<StoragePort> discoveredPorts = (List<StoragePort>) keyMap.get(Constants.DISCOVERED_PORTS);
            discoveredPorts.addAll(newPorts);
            discoveredPorts.addAll(existingPorts);
           
            List<StoragePool> modifiedPools = StoragePoolAssociationHelper.getStoragePoolsFromPorts(_dbClient, newPorts, null);
            for (StoragePool pool : modifiedPools) {
                // pool matcher will be invoked on this pool
                if (!poolsToMatchWithVpool.containsKey(pool.getId())) {
                    poolsToMatchWithVpool.put(pool.getId(), pool);
                }
            }
            
            _logger.debug("# Pools used in invoking PoolMatcher during StorageProtoclEndPoint {}",Joiner.on("\t").join(poolsToMatchWithVpool.keySet()));
        } catch (Exception e) {
            _logger.error("SCSI End Point Discovery failed -->{}", getMessage(e));
        } finally {
        }
    }

    /**
     * create Ethernet Storage Port.
     * StoragePorts would have been created in SToragePorts Processor, but for ethernet those
     * will not get updated to DB, as to get SCSIAddress ,we need a different SMI Class ProtocolEndPoint
     * Algo :
     * 1. Check if StorageEthernet Port available in DB.
     * 2. If not, then get already created StoragePort, update SCSI Address and persist.
     * 3. If yes, then just update the properties alone.
     * 
     * @param keyMap
     * @param port
     * @param endPointInstance
     * @param portInstanceID
     * @throws IOException
     */
    private void createEthernetStoragePort(
            Map<String, Object> keyMap, StoragePort port, CIMInstance endPointInstance,
            String portInstanceID, CoordinatorClient coordinator, List<StoragePort> newPorts, 
            List<StoragePort> existingPorts) throws IOException {
        StoragePort portinMemory = (StoragePort) keyMap.get(portInstanceID);
        if (null == port) {
            // Name Property's value --> iqn.23.....,t,0x0001
            portinMemory.setPortNetworkId(endPointInstance.getObjectPath().getKey(NAME)
                    .getValue().toString().split(",")[0].toLowerCase());
            portinMemory.setPortEndPointID(endPointInstance.getObjectPath().getKey(NAME)
                    .getValue().toString());
            String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(_dbClient, portinMemory);
            portinMemory.setNativeGuid(portNativeGuid);
            portinMemory.setLabel(portNativeGuid);
            _logger.info("Creating port - {}:{}", portinMemory.getLabel(), portinMemory.getNativeGuid());
            _dbClient.createObject(portinMemory);
            newPorts.add(portinMemory);
        } else {
            port.setPortName(portinMemory.getPortName());
            port.setPortSpeed(portinMemory.getPortSpeed());
            port.setPortEndPointID(endPointInstance.getObjectPath().getKey(NAME)
                    .getValue().toString());
            port.setCompatibilityStatus(portinMemory.getCompatibilityStatus());
            port.setDiscoveryStatus(portinMemory.getDiscoveryStatus());
            port.setOperationalStatus(portinMemory.getOperationalStatus());
            _logger.info("Updating port - {} : {}", port.getLabel(), port.getNativeGuid());
            _dbClient.persistObject(port);
            existingPorts.add(port);
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        // TODO Auto-generated method stub
    }
}
