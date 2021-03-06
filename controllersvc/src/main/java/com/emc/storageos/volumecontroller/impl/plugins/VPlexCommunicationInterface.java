/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sun.net.util.IPAddressUtil;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StoragePort.PortType;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageProvider.ConnectionStatus;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeCharacterstics;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.db.client.util.iSCSIUtility;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.StorageSystemViewObject;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.PartitionManager;
import com.emc.storageos.plugins.metering.vplex.VPlexCollectionException;
import com.emc.storageos.recoverpoint.utils.WwnUtils;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.util.VersionChecker;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.StoragePoolAssociationHelper;
import com.emc.storageos.volumecontroller.impl.StoragePortAssociationHelper;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.emc.storageos.vplex.api.VPlexApiClient;
import com.emc.storageos.vplex.api.VPlexApiConstants;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.vplex.api.VPlexApiFactory;
import com.emc.storageos.vplex.api.VPlexClusterInfo;
import com.emc.storageos.vplex.api.VPlexConsistencyGroupInfo;
import com.emc.storageos.vplex.api.VPlexDirectorInfo;
import com.emc.storageos.vplex.api.VPlexPortInfo;
import com.emc.storageos.vplex.api.VPlexPortInfo.PortRole;
import com.emc.storageos.vplex.api.VPlexPortInfo.SpeedUnits;
import com.emc.storageos.vplex.api.VPlexStorageViewInfo;
import com.emc.storageos.vplex.api.VPlexTargetInfo;
import com.emc.storageos.vplex.api.VPlexVirtualVolumeInfo;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

/**
 * Discovery framework plug-in class for discovering VPlex storage systems.
 */
public class VPlexCommunicationInterface extends ExtendedCommunicationInterfaceImpl {
    
    // string constants
    private final String ISCSI_PATTERN = "^(iqn|IQN|eui).*$";
    protected static int BATCH_SIZE = Constants.DEFAULT_PARTITION_SIZE;
    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final String LOCAL = "local";
    
    // WWN for offline ports
    public static final String OFFLINE_PORT_WWN = "00:00:00:00:00:00:00:00";
    
    // Cluster assembly id delimiter for a VPLEX metro.
    private static final String ASSEMBY_DELIM = ":";
    
    // Logger reference.
    private static Logger s_logger = LoggerFactory.getLogger(VPlexCommunicationInterface.class);    
    
    // Reference to the VPlex API factory allows us to get a VPlex API client
    // and execute requests to the VPlex storage system.
    private VPlexApiFactory _apiFactory;

    // PartitionManager used for batch database persistence.
    private PartitionManager _partitionManager;

    /**
     * Public constructor for Spring bean creation.
     */
    public VPlexCommunicationInterface() {
    }
    
    /**
     * Setter for the VPlex API factory for Spring bean configuration.
     *
     * @param apiFactory A reference to the VPlex API factory.
     */
    public void setVPlexApiFactory(VPlexApiFactory apiFactory) {
        _apiFactory = apiFactory;
    }

    /**
     * Setter for the PartitionManager for batch database persistence.
     * 
     * @param partitionManager
     */
    public void setPartitionManager(PartitionManager partitionManager) {
        _partitionManager = partitionManager;
    }

    /**
     * Implementation for scan for VPlex storage systems.
     * 
     * @param accessProfile
     * 
     * @throws BaseCollectionException
     */
    @Override
    public void scan(AccessProfile accessProfile) throws BaseCollectionException {
        URI mgmntServerURI = accessProfile.getSystemId();
        StorageProvider mgmntServer = null;
        String scanStatusMessage = "Unknown Status";
        try {
            // Get the storage provider representing a VPLEX management server.
            mgmntServer = _dbClient.queryObject(StorageProvider.class, mgmntServerURI);

            // Get the Http client for getting information about the VPLEX
            // cluster(s) managed by the VPLEX management server.
            VPlexApiClient client = getVPlexAPIClient(accessProfile);
            s_logger.debug("Got handle to VPlex API client");
            
            // Verify the connectivity to the VPLEX management server.
            verifyConnectivity(client, mgmntServer);
            
            // Verify the VPLEX system firmware version is supported.
            verifyMinimumSupportedFirmwareVersion(client, mgmntServer);
            
            // Determine the VPLEX system managed by this management server.
            Map<String, StorageSystemViewObject> scanCache = accessProfile.getCache();
            scanManagedSystems(client, mgmntServer, scanCache);
            scanStatusMessage = String.format("Scan job completed successfully for " +
                "VPLEX management server: %s", mgmntServerURI.toString());
        } catch (Exception e) {
            VPlexCollectionException vce = VPlexCollectionException.exceptions
                .failedScan(mgmntServer.getIPAddress(), e.getLocalizedMessage());
            scanStatusMessage = vce.getLocalizedMessage();
            throw vce;
        } finally {
            if (mgmntServer != null) {
                try {
                    mgmntServer.setLastScanStatusMessage(scanStatusMessage);
                    _dbClient.persistObject(mgmntServer);
                } catch (Exception e) {
                    s_logger.error("Error persisting scan status message for management server {}",
                        mgmntServerURI.toString(), e);
                }
            }            
        }
    }
    
    /**
     * Verifies the connectivity of the passed management server.
     * 
     * @param client The VPlex API client.
     * @param mgmntServer A reference to the VPlex management server.
     * 
     * @throws VPlexApiException When management server cannot be accessed.
     */
    private void verifyConnectivity(VPlexApiClient client, StorageProvider mgmntServer)
        throws VPlexApiException {
        try {
            client.verifyConnectivity();
            mgmntServer.setConnectionStatus(ConnectionStatus.CONNECTED.name());
        } catch (Exception e) {
            mgmntServer.setConnectionStatus(ConnectionStatus.NOTCONNECTED.name());
            throw e;
        } finally {
            try {
                _dbClient.persistObject(mgmntServer);
            } catch (Exception e) {
                s_logger.error("Error persisting connection status for management server {}",
                    mgmntServer.getId(), e);
            }
        }
    }
    
    /**
     * Verifies the firmware version of the VPLEX management server is supported,
     * otherwise aborts the scan.
     * 
     * @param client The VPlex API client.
     * @param mgmntServer A reference to the VPlex management server.
     * 
     * @throws VPlexCollectionException When an error occurs discovering the
     *         firmware version or the firmware version is less than the minimum
     *         supported version.
     */
    private void verifyMinimumSupportedFirmwareVersion(VPlexApiClient client,
        StorageProvider mgmntServer) throws VPlexCollectionException {
        try {
            String fwVersion = client.getManagementSoftwareVersion();
            mgmntServer.setVersionString(fwVersion);
            String minFWVersion = VersionChecker.getMinimumSupportedVersion(Type
                .valueOf(mgmntServer.getInterfaceType()));
            s_logger.info("Verifying VPLEX management server version: Minimum Supported Version {} - " +
                "Discovered Version {}", minFWVersion, fwVersion);
            if (VersionChecker.verifyVersionDetails(minFWVersion, fwVersion) < 0) {
                setStorageProviderCompatibilityStatus(mgmntServer, CompatibilityStatus.INCOMPATIBLE);
                throw VPlexCollectionException.exceptions
                    .unsupportedManagementServerVersion(fwVersion,
                        mgmntServer.getIPAddress(), minFWVersion);
            } else {
                setStorageProviderCompatibilityStatus(mgmntServer, CompatibilityStatus.COMPATIBLE);
            }
        } catch (VPlexCollectionException vce) {
        	s_logger.error("Error verifying management server version {}:", mgmntServer.getIPAddress(), vce);
            throw vce;
        } catch (Exception e) {
        	s_logger.error("Error verifying management server version {}:", mgmntServer.getIPAddress(), e);
            throw VPlexCollectionException.exceptions
                .failedVerifyingManagementServerVersion(mgmntServer.getIPAddress(), e);
        }
    }
    
    /**
     * Sets the compatibility status on a VPLEX StorageProvider and all its underlying
     * StorageSystems and StoragePorts.
     * 
     * @param provider the StorageProvider
     * @param status the CompatibilityStatus to set (COMPATIBLE or INCOMPATIBLE)
     */
    private void setStorageProviderCompatibilityStatus( StorageProvider provider, CompatibilityStatus status ) {
        
        if (provider == null) {
            s_logger.warn("The requested StorageProvider was null.");
            return;
        }
        
        if (status == null) {
            s_logger.warn("No updated CompatibilityStatus was provided for StorageProvider {}.", provider.getLabel());
            return;
        }
        
        s_logger.info("Setting compatibility status on Storage Provider {} to {}", provider.getLabel(), status.toString());
        provider.setCompatibilityStatus(status.toString());
        StringSet storageSystemURIStrs = provider.getStorageSystems();
        if (storageSystemURIStrs != null) {
            for (String storageSystemURIStr : storageSystemURIStrs) {
                
                // update storage system compatibility status
                StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class,
                    URI.create(storageSystemURIStr));
                if (storageSystem != null) {
                    s_logger.info("- Setting compatibility status on Storage System {} to {}", 
                            storageSystem.getLabel(), status.toString());
                    storageSystem.setCompatibilityStatus(status.toString());
                    
                    // update port compatibility status
                    URIQueryResultList storagePortURIs = new URIQueryResultList();
                    _dbClient.queryByConstraint(
                            ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(storageSystem.getId()),
                            storagePortURIs);
                    Iterator<URI> storagePortIter = storagePortURIs.iterator();
                    while (storagePortIter.hasNext()) {
                        StoragePort port = _dbClient.queryObject(StoragePort.class,storagePortIter.next());
                        if (port != null) {
                            s_logger.info("-- Setting compatibility status on Storage Port {} to {}", 
                                    port.getLabel(), status.toString());
                            port.setCompatibilityStatus(status.name());
                            _dbClient.persistObject(port);
                        }
                    }
                    
                    _dbClient.persistObject(storageSystem);
                }
            }
        }
        _dbClient.persistObject(provider);

    }
    
    /**
     * First gets the VPLEX cluster(s) that are manageable by the VPLEX management
     * server. For a VPLEX local configuration there will be one cluster. For
     * a VPLEX Metro configuration there will be two clusters. We then create a
     * StorageSystemViewObject to represent the managed clusters as a storage system
     * to be managed by this management server.
     * 
     * @param client The VPlex API client.
     * @param mgmntServer A reference to the VPlex management server.
     * @param scanCache A map holding previously found systems during a scan.
     * 
     * @throws VPlexCollectionException When an error occurs getting the VPLEX
     *         information.
     */
    private void scanManagedSystems(VPlexApiClient client, StorageProvider mgmntServer,
        Map<String, StorageSystemViewObject> scanCache) throws VPlexCollectionException {
        try {
            // Get the cluster info.
            List<VPlexClusterInfo> clusterInfoList = client.getClusterInfo();
            
            // Get the cluster assembly identifiers and form the
            // system serial number based on these identifiers.
            StringBuilder systemSerialNumber = new StringBuilder();
            List<String> clusterAssembyIds = new ArrayList<String>();
            for (VPlexClusterInfo clusterInfo: clusterInfoList) {
                String assemblyId = clusterInfo.getTopLevelAssembly();
                if (VPlexApiConstants.NULL_ATT_VAL.equals(assemblyId)) {
                    throw VPlexCollectionException.exceptions
                        .failedScanningManagedSystemsNullAssemblyId(
                            mgmntServer.getIPAddress(), clusterInfo.getName());
                }
                clusterAssembyIds.add(assemblyId);
                if (systemSerialNumber.length() != 0) {
                    systemSerialNumber.append(ASSEMBY_DELIM);
                }
                systemSerialNumber.append(assemblyId);
            }
            
            // Get the native GUID for the system using the constructed 
            // serial number.
            String systemNativeGUID = NativeGUIDGenerator.generateNativeGuid(
                mgmntServer.getInterfaceType(), systemSerialNumber.toString());
            s_logger.info("Scanned VPLEX system {}", systemNativeGUID);        
             
            // Determine if the VPLEX system was already scanned by another
            // VPLEX management server by checking the scan cache. If not, 
            // then we create a StorageSystemViewObject to represent this
            // VPLEX system and add it to the scan cache.
            StorageSystemViewObject systemViewObj = null;
            if (scanCache.containsKey(systemNativeGUID)) {
                s_logger.info("VPLEX system {} was previously found.",
                    systemNativeGUID);
                systemViewObj = scanCache.get(systemNativeGUID);
            } else {
                s_logger.info("Found new VPLEX system {}, adding to scan cache.",
                    systemNativeGUID);
                systemViewObj = new StorageSystemViewObject();
            }
            systemViewObj.setDeviceType(mgmntServer.getInterfaceType());
            systemViewObj.addprovider(mgmntServer.getId().toString());
            systemViewObj.setProperty(StorageSystemViewObject.SERIAL_NUMBER,
                                      systemSerialNumber.toString());
            systemViewObj.setProperty(StorageSystemViewObject.STORAGE_NAME, systemNativeGUID);
            scanCache.put(systemNativeGUID, systemViewObj);
        } catch (Exception e) {
        	s_logger.error("Error scanning managed systems for {}:", mgmntServer.getIPAddress(), e);
            throw VPlexCollectionException.exceptions.failedScanningManagedSystems(
                mgmntServer.getIPAddress(), e);
        }
    }

    /**
     * Implementation for discovery of VPLEX storage systems.
     * 
     * @param accessProfile providing context for this discovery session
     * 
     * @throws BaseCollectionException
     */
    @Override
    public void discover(AccessProfile accessProfile) throws BaseCollectionException {
        
        long start = new Date().getTime();
        s_logger.info("initiating discovery of VPLEX system {}", accessProfile.getProfileName());
        if ((null != accessProfile.getnamespace())
                && (accessProfile.getnamespace()
                .equals(StorageSystem.Discovery_Namespaces.UNMANAGED_VOLUMES
                        .toString()))) {
            try {
                VPlexApiClient client = getVPlexAPIClient(accessProfile);
                
                long unManagedStart = new Date().getTime();
                Map<String, VPlexVirtualVolumeInfo> vvolMap = client.getVirtualVolumes(true);
                Map <String, Set<UnManagedExportMask>> volumeToExportMasksMap = new HashMap<String, Set<UnManagedExportMask>>();
                long unmanagedElapsed = new Date().getTime() - unManagedStart;
                s_logger.info("TIMER: discovering deep vplex unmanaged volumes took {} ms", unmanagedElapsed);
                
                unManagedStart = new Date().getTime();
                discoverUnmanagedStorageViews(accessProfile, client, vvolMap, volumeToExportMasksMap);
                unmanagedElapsed = new Date().getTime() - unManagedStart;
                s_logger.info("TIMER: discovering vplex unmanaged storage views took {} ms", unmanagedElapsed);
                
                unManagedStart = new Date().getTime();
                discoverUnmanagedVolumes(accessProfile, client, vvolMap, volumeToExportMasksMap);
                unmanagedElapsed = new Date().getTime() - unManagedStart;
                
                s_logger.info("TIMER: discovering vplex unmanaged volumes took {} ms", unmanagedElapsed);
            } catch (URISyntaxException ex) {
                s_logger.error(ex.getLocalizedMessage());
                throw VPlexCollectionException.exceptions.vplexUnmanagedVolumeDiscoveryFailed(
                        accessProfile.getSystemId().toString(), ex.getLocalizedMessage());
            }
        } else {
            discoverAll(accessProfile);
        }
        
        long elapsed = new Date().getTime() - start;
        s_logger.info("TIMER: vplex storage system discovery took {} ms", elapsed);
    }
    
    /**
     * Implementation for discovering assembly ID (serial number) to cluster id (0 or 1)
     * mapping used in placement algorithms such as RP and VPLEX. 
     * 
     * @param accessProfile providing context for this discovery session
     * @param client a reference to the VPLEX API client
     */
    private void discoverClusterIdentification(AccessProfile accessProfile,
    		VPlexApiClient client) {
    	StorageSystem vplex = null;
    	try {
    		URI vplexUri = accessProfile.getSystemId();
    		vplex = _dbClient.queryObject(StorageSystem.class, vplexUri);
    		if (null == vplex) {
    			s_logger.error("No VPLEX Device was found in ViPR for URI: " + vplexUri);
    			s_logger.error("Cluster Identification discovery cannot continue.");
    			return;
    		}

    		if (vplex.getVplexAssemblyIdtoClusterId() != null && !vplex.getVplexAssemblyIdtoClusterId().isEmpty()) {
    			// We've already retrieved this information during registration (scan), so there's no reason
    			// to retrieve it again.  (This information is not expected to change)
    			return;
    		}

    		// Get the cluster information
    		List<VPlexClusterInfo> clusterInfoList = client.getClusterInfo();

    		// Get the cluster assembly identifiers and form the
    		// system serial number based on these identifiers.
    		StringMap assemblyIdToClusterId = new StringMap();
    		for (VPlexClusterInfo clusterInfo: clusterInfoList) {
    			String assemblyId = clusterInfo.getTopLevelAssembly();
    			if (VPlexApiConstants.NULL_ATT_VAL.equals(assemblyId)) {
    				throw VPlexCollectionException.exceptions
    				.failedScanningManagedSystemsNullAssemblyId(
    						vplex.getIpAddress(), clusterInfo.getName());
    			}
    			assemblyIdToClusterId.put(assemblyId, clusterInfo.getClusterId());
    		}

    		// Store the vplex assembly ID -> cluster ID mapping
    		if (vplex.getVplexAssemblyIdtoClusterId() == null) {
    			vplex.setVplexAssemblyIdtoClusterId(assemblyIdToClusterId);
    		} else {
    			vplex.getVplexAssemblyIdtoClusterId().putAll(assemblyIdToClusterId);
    		}
    		_dbClient.persistObject(vplex);
    	} catch (Exception e) {
    		if (vplex != null) {
    			s_logger.error("Error discovering cluster identification for the VPLEX storage system {}:", vplex.getIpAddress(), e);
    			throw VPlexCollectionException.exceptions.failedPortsDiscovery(
    				vplex.getId().toString(), e.getLocalizedMessage(), e);
    		}
			s_logger.error("Error discovering cluster identification for the VPLEX storage system", e);
			throw VPlexCollectionException.exceptions.failedPortsDiscovery(
				"None", e.getLocalizedMessage(), e);

    	}	
    }

	/**
     * Implementation for discovering unmanaged virtual volumes in a VPLEX storage system.
     * 
     * @param accessProfile providing context for this discovery session
     * @param client a reference to the VPLEX API client
     * @param vvolMap map of virtual volume names to virtual volume info objects
     * @param volumeToExportMasksMap map of volumes to a set of associated UnManagedExportMasks
     * @throws BaseCollectionException
     */
    private void discoverUnmanagedVolumes(AccessProfile accessProfile, VPlexApiClient client, 
            Map<String, VPlexVirtualVolumeInfo> allVirtualVolumes, 
            Map <String, Set<UnManagedExportMask>> volumeToExportMasksMap) throws BaseCollectionException {

        String statusMessage = "Starting discovery of Unmanaged VPLEX Volumes.";
        s_logger.info(statusMessage + " Access Profile Details :  IpAddress : "
                + "PortNumber : {}, namespace : {}",
                accessProfile.getIpAddress() + accessProfile.getPortNumber(),
                accessProfile.getnamespace());

        URI vplexUri = accessProfile.getSystemId();
        StorageSystem vplex = _dbClient.queryObject(StorageSystem.class, vplexUri);
        if (null == vplex) {
            s_logger.error("No VPLEX Device was found in ViPR for URI: " + vplexUri);
            s_logger.error("Unmanaged VPLEX Volume discovery cannot continue.");
            return;
        }
        
        Set<URI> allUnmanagedVolumes = new HashSet<URI>();
        List<UnManagedVolume> newUnmanagedVolumes = new ArrayList<UnManagedVolume>();
        List<UnManagedVolume> knownUnmanagedVolumes = new ArrayList<UnManagedVolume>();
        List<UnManagedExportMask> unmanagedExportMasksToUpdate = new ArrayList<UnManagedExportMask>();
        
        try {
            
            // set batch size for persisting unmanaged volumes
            int batchSize = Constants.DEFAULT_PARTITION_SIZE;
            Map<String,String> props =  (Map<String, String>) accessProfile.getProps();
            if (null != props && null != props.get(Constants.METERING_RECORDS_PARTITION_SIZE)) {
                batchSize = Integer.parseInt(props.get(Constants.METERING_RECORDS_PARTITION_SIZE));
            }

            Map<String, String> volumesToCgs = new HashMap<String, String>();
            List<VPlexConsistencyGroupInfo> cgs = client.getConsistencyGroups();
            s_logger.info("Found {} Consistency Groups.", cgs.size());
            for (VPlexConsistencyGroupInfo cg : cgs) {
                for (String volumeName : cg.getVirtualVolumes()) {
                    volumesToCgs.put(volumeName, cg.getName());
                }
            }
            s_logger.info("Volume to Consistency Group Map is: " + volumesToCgs.toString());
            
            Map<String, String> clusterIdToNameMap = client.getClusterIdToNameMap();
            Map<String, String> varrayToClusterIdMap = new HashMap<String, String>();
            
            if (null != allVirtualVolumes) {
                for (String name : allVirtualVolumes.keySet()) {
                    s_logger.info("Looking at Virtual Volume {}", name);
                    VPlexVirtualVolumeInfo info = allVirtualVolumes.get(name);
                    
                    //    UnManagedVolume discover does a pretty expensive
                    // iterative call into the VPLEX API to get extended details
                    // on every volume in each cluster. First it gets all the
                    // volume names/paths (the inexpensive "lite" call), then
                    // iterates through them getting the details to populate the
                    // VPlexVirtualVolumeInfo objects with extended details
                    // needed for unmanaged volume discovery.
                    //    In my testing, I ran into situations where this took so
                    // long that by the time it got to some arbitrary volume to
                    // populate with more details, that volume had been deleted
                    // by some other process and the VPLEX API threw a 404 Not
                    // Found. ...which then caused the whole unmanaged volume
                    // discovery process to fail.
                    //    So, there is a very rare chance that processing could get
                    // to this point and the name would would be in the key set,
                    // but the info object would be null... basically if it got
                    // to here null, it would mean a 404 happened earlier.
                    if (null == info) {
                        continue;
                    }
                    
                    Volume managedVolume = findVirtualVolumeManagedByVipr(info);
                    if (null == managedVolume) {
                        s_logger.info("Virtual Volume {} is not managed by ViPR", name);
                        
                        UnManagedVolume unmanagedVolume = findUnmanagedVolumeKnownToVipr(info);
                        
                        if (null != unmanagedVolume) {
                            // just refresh / update the existing unmanaged volume
                            s_logger.info("Unmanaged Volume {} is already known to ViPR", name);
                            
                            updateUnmanagedVolume(info, vplex, unmanagedVolume, volumesToCgs, clusterIdToNameMap, varrayToClusterIdMap);
                            knownUnmanagedVolumes.add(unmanagedVolume);
                        } else {
                            // set up new unmanaged vplex volume
                            s_logger.info("Unmanaged Volume {} is not known to ViPR", name);
                            
                            unmanagedVolume = createUnmanagedVolume(info, vplex, volumesToCgs, clusterIdToNameMap, varrayToClusterIdMap);
                            newUnmanagedVolumes.add(unmanagedVolume);
                        }
                        
                        Set<UnManagedExportMask> uems = volumeToExportMasksMap.get(unmanagedVolume.getNativeGuid());
                        if (uems != null) {
                            s_logger.info("{} UnManagedExportMasks found in the map for volume {}", uems.size(), unmanagedVolume.getNativeGuid());
                            for (UnManagedExportMask uem : uems) {
                                s_logger.info("   adding UnManagedExportMask {} to UnManagedVolume", uem.getMaskingViewPath());
                                unmanagedVolume.getUnmanagedExportMasks().add(uem.getId().toString());
                                uem.getUnmanagedVolumeUris().add(unmanagedVolume.getId().toString());
                                unmanagedExportMasksToUpdate.add(uem);
                                
                                // add the known initiators, too
                                for (String initUri : uem.getKnownInitiatorUris()) {
                                    s_logger.info("   adding known Initiator URI {} to UnManagedVolume", initUri);
                                    unmanagedVolume.getInitiatorUris().add(initUri);
                                    Initiator init = _dbClient.queryObject(Initiator.class, URI.create(initUri));
                                    unmanagedVolume.getInitiatorNetworkIds().add(init.getInitiatorPort());
                                }
                                
                                // log this info for debugging
                                for (String path : uem.getUnmanagedInitiatorNetworkIds()) {
                                    s_logger.info("   UnManagedExportMask has this initiator unknown to ViPR: {}", path);
                                }
                            }
                            

                            persistUnManagedExportMasks(null, unmanagedExportMasksToUpdate, false);
                        }
                        
                        persistUnManagedVolumes(newUnmanagedVolumes, knownUnmanagedVolumes, false);
                        allUnmanagedVolumes.add(unmanagedVolume.getId());
                        
                    } else {
                        s_logger.info("Virtual Volume {} is already managed by "
                                    + "ViPR as Volume URI {}", name, managedVolume.getId());
                    }
                }
            } else {
                s_logger.warn("No virtual volumes were found on VPLEX.");
            }

            persistUnManagedVolumes(newUnmanagedVolumes, knownUnmanagedVolumes, true);
            persistUnManagedExportMasks(null, unmanagedExportMasksToUpdate, true);
            cleanUpOrphanedVolumes(vplex.getId(), allUnmanagedVolumes);
            
        } catch (Exception ex) {
            s_logger.error("An error occurred during VPLEX unmanaged volume discovery", ex);
            ex.printStackTrace();
            String vplexLabel = vplexUri.toString();
            if (null != vplex) {
                vplexLabel = vplex.getLabel();
            }
            throw VPlexCollectionException.exceptions.vplexUnmanagedVolumeDiscoveryFailed(
                    vplexLabel, ex.toString());
        } finally {
            if (null != vplex) {
                try {
                    vplex.setLastDiscoveryStatusMessage(statusMessage);
                    _dbClient.persistObject(vplex);
                } catch (Exception ex) {
                    s_logger.error("Error while saving VPLEX discovery status message: {} - Exception: {}", 
                                    statusMessage, ex.getLocalizedMessage());
                }
            }
        }
    }
    
    /**
     * Determines if the given VPLEX volume information represents a
     * virtual volume that is already managed by ViPR.
     *  
     * @param info a VPlexVirtualVolumeInfo descriptor
     * @return a Volume object if a match is found in the ViPR database 
     */
    private Volume findVirtualVolumeManagedByVipr(VPlexVirtualVolumeInfo info) {
        if (info != null) {
            s_logger.info("Determining if Virtual Volume {} is managed by ViPR", info.getName());
            String volumeNativeGuid = info.getPath();
            s_logger.info("...checking ViPR's Volume table for volume native guid {}", volumeNativeGuid);
            
            URIQueryResultList result = new URIQueryResultList();
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getVolumeNativeIdConstraint(volumeNativeGuid), result);
            if (result.iterator().hasNext()) {
                return _dbClient.queryObject(Volume.class, result.iterator().next());
            }
        }
        
        return null;
    }

    /**
     * Determines if the given VPLEX volume information represents an
     * unmanaged virtual volume that is already known to ViPR, and
     * returns the UnManagedVolume object if it is found.
     * 
     * @param info a VPlexVirtualVolumeInfo descriptor
     * @return an UnManagedVolume object if found, otherwise null
     */
    private UnManagedVolume findUnmanagedVolumeKnownToVipr(VPlexVirtualVolumeInfo info) {
        
        s_logger.info("Determining if Unmanaged Volume {} is known to ViPR", info.getName());
        String volumeNativeGuid = info.getPath();
        s_logger.info("...checking ViPR's UnManagedVolume table for volume native guid {}", volumeNativeGuid);
        
        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVolumeInfoNativeIdConstraint(volumeNativeGuid), result);
        if (result.iterator().hasNext()) {
            return _dbClient.queryObject(UnManagedVolume.class, result.iterator().next());
        }

        return null;
    }

    /**
     * Updates an existing UnManagedVolume with the latest info from 
     * the VPLEX virtual volume.
     * 
     * @param info a VPlexVirtualVolumeInfo descriptor
     * @param vplex the VPLEX storage system managing the volume
     * @param volume the existing UnManagedVolume
     * @param volumesToCgs a Map of volume labels to consistency group names 
     */
    private void updateUnmanagedVolume(VPlexVirtualVolumeInfo info, 
            StorageSystem vplex, UnManagedVolume volume, 
            Map<String, String> volumesToCgs, 
            Map<String, String> clusterIdToNameMap,
            Map<String, String> varrayToClusterIdMap) {
        
        s_logger.info("Updating UnManagedVolume {} with latest from VPLEX volume {}", 
                        volume.getLabel(), info.getName());
        
        volume.setStorageSystemUri(vplex.getId());
        volume.setNativeGuid(info.getPath());
        volume.setLabel(info.getName());
        
        volume.getUnmanagedExportMasks().clear();
        volume.getInitiatorUris().clear();
        volume.getInitiatorNetworkIds().clear();
        
        // set volume characteristics and volume information
        Map<String, StringSet> unManagedVolumeInformation = new HashMap<String, StringSet>();
        StringMap unManagedVolumeCharacteristics = new StringMap();

        // check if volume is exported
        String isExported = info.isExported() ? TRUE : FALSE;
        unManagedVolumeCharacteristics.put(
                SupportedVolumeCharacterstics.IS_VOLUME_EXPORTED.toString(), isExported);


        // Set up default MAXIMUM_IO_BANDWIDTH and MAXIMUM_IOPS 
        StringSet bwValues = new StringSet();
        bwValues.add("0");

        if (unManagedVolumeInformation.get(SupportedVolumeInformation.EMC_MAXIMUM_IO_BANDWIDTH.toString()) == null) {
            unManagedVolumeInformation.put(SupportedVolumeInformation.EMC_MAXIMUM_IO_BANDWIDTH.toString(), bwValues);
        } else {
            unManagedVolumeInformation.get(SupportedVolumeInformation.EMC_MAXIMUM_IO_BANDWIDTH.toString()).replace(
                    bwValues);
        }

        StringSet iopsVal = new StringSet();
        iopsVal.add("0");

        if (unManagedVolumeInformation.get(SupportedVolumeInformation.EMC_MAXIMUM_IOPS.toString()) == null) {
            unManagedVolumeInformation.put(SupportedVolumeInformation.EMC_MAXIMUM_IOPS.toString(), iopsVal);
        } else {
            unManagedVolumeInformation.get(SupportedVolumeInformation.EMC_MAXIMUM_IOPS.toString()).replace(iopsVal);
        }
        
        // check if volume is part of a consistency group, and set the name if so
        if (volumesToCgs.containsKey(info.getName())) {
            unManagedVolumeCharacteristics.put(
                    SupportedVolumeCharacterstics.IS_VOLUME_ADDED_TO_CONSISTENCYGROUP.toString(), TRUE);
            StringSet set = new StringSet();
            set.add(volumesToCgs.get(info.getName()));
            unManagedVolumeInformation.put(
                    SupportedVolumeInformation.VPLEX_CONSISTENCY_GROUP_NAME.toString(), set);
        } else {
            unManagedVolumeCharacteristics.put(
                    SupportedVolumeCharacterstics.IS_VOLUME_ADDED_TO_CONSISTENCYGROUP.toString(), FALSE);
        }

        // set an is-ingestable flag, used later by the ingest process
        String isVolumeIngestable = isVolumeIngestable(info) ? TRUE : FALSE;
        unManagedVolumeCharacteristics.put(
                SupportedVolumeCharacterstics.IS_INGESTABLE.toString(), isVolumeIngestable);

        // set system type
        StringSet systemTypes = new StringSet();
        systemTypes.add(vplex.getSystemType());
        unManagedVolumeInformation.put(SupportedVolumeInformation.SYSTEM_TYPE.toString(), systemTypes);

        // set volume capacity
        StringSet provCapacity = new StringSet();  
        provCapacity.add(String.valueOf(info.getCapacityBytes()));
        unManagedVolumeInformation.put(SupportedVolumeInformation.PROVISIONED_CAPACITY.toString(),
                provCapacity);
        unManagedVolumeInformation.put(SupportedVolumeInformation.ALLOCATED_CAPACITY.toString(),
                provCapacity);
        
        // set vplex virtual volume properties
        unManagedVolumeCharacteristics.put(SupportedVolumeCharacterstics.IS_VPLEX_VOLUME.toString(), TRUE);
        StringSet locality = new StringSet();
        locality.add(info.getLocality());
        unManagedVolumeInformation.put(SupportedVolumeInformation.VPLEX_LOCALITY.toString(), locality);
        StringSet supportingDevice = new StringSet();
        supportingDevice.add(info.getSupportingDevice());
        unManagedVolumeInformation.put(
                SupportedVolumeInformation.VPLEX_SUPPORTING_DEVICE_NAME.toString(), supportingDevice);
        StringSet volumeClusters = new StringSet();
        volumeClusters.addAll(info.getClusters());
        unManagedVolumeInformation.put(SupportedVolumeInformation.VPLEX_CLUSTER_IDS.toString(),
            volumeClusters);            

        // set supported vpool list
        StringSet matchedVPools = new StringSet();  
        String highAvailability = info.getLocality().equals(LOCAL) ? 
                VirtualPool.HighAvailabilityType.vplex_local.name() : 
                    VirtualPool.HighAvailabilityType.vplex_distributed.name();
        List<URI> allVpoolUris = _dbClient.queryByType(VirtualPool.class, true);
        List<VirtualPool> allVpools = _dbClient.queryObject(VirtualPool.class, allVpoolUris);
        s_logger.info("finding valid virtual pools for UnManagedVolume {}", volume.getLabel());
        
        for (VirtualPool vpool : allVpools) {

            // VPool must specify the correct VPLEX HA.
            if ((vpool.getHighAvailability() == null) ||
                (!vpool.getHighAvailability().equals(highAvailability))) {
                s_logger.info("   virtual pool {} is not valid because "
                        + "its high availability setting does not match the unmanaged volume",
                        vpool.getLabel());
                continue;
            }
            
            // CTRL-12225 we shouldn't ingest to vpools that have recoverpoint enabled
            if (VirtualPool.vPoolSpecifiesRPVPlex(vpool)) {
                s_logger.info("   virtual pool {} is not valid because it is RecoverPoint enabled",
                        vpool.getLabel());
                continue;
            }
            
            // If the volume is in a CG, the vpool must specify multi-volume consistency.
            Boolean mvConsistency = vpool.getMultivolumeConsistency();
            if ((TRUE.equals(unManagedVolumeCharacteristics.get(
                SupportedVolumeCharacterstics.IS_VOLUME_ADDED_TO_CONSISTENCYGROUP.toString()))) &&
                ((mvConsistency == null) || (mvConsistency == Boolean.FALSE))) {
                s_logger.info("   virtual pool {} is not valid because it does not have the "
                        + "multi-volume consistency flag set, and the unmanaged volume is in a consistency group",
                        vpool.getLabel());
                continue;
            }
            
            // VPool must be assigned to a varray corresponding to volumes clusters.
            StringSet varraysForVpool = vpool.getVirtualArrays();
            for (String varrayId : varraysForVpool) {
                String varrayClusterId = varrayToClusterIdMap.get(varrayId);
                if (null == varrayClusterId) {
                    varrayClusterId = ConnectivityUtil.getVplexClusterForVarray(URI.create(varrayId), vplex.getId(), _dbClient);
                    varrayToClusterIdMap.put(varrayId, varrayClusterId);
                }
                
                if (!ConnectivityUtil.CLUSTER_UNKNOWN.equals(varrayClusterId)) {
                    String varrayClusterName = clusterIdToNameMap.get(varrayClusterId);
                    if (volumeClusters.contains(varrayClusterName)) {
                        matchedVPools.add(vpool.getId().toString());
                        break;
                    }
                }
            }
        }
        
        if (unManagedVolumeInformation
                .containsKey(SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString())) {

            if (null != matchedVPools && matchedVPools.size() == 0) {
                // replace with empty string set doesn't work, hence added explicit code to remove all
                unManagedVolumeInformation.get(
                        SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString()).clear();
                s_logger.info("No matching VPOOLS found for unmanaged volume " + volume.getLabel());
            } else {
                // replace with new StringSet
                unManagedVolumeInformation.get(
                        SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString()).replace( matchedVPools);
                s_logger.info("Replaced Pools :"+Joiner.on("\t").join( unManagedVolumeInformation.get(
                        SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString())));
            }
        } else {
            unManagedVolumeInformation.put(
                    SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString(), matchedVPools);
            s_logger.info("Matching VPOOLS found for unmanaged volume " + volume.getLabel() 
                    + " are " + matchedVPools.toString());
        }
        
        // add this info to the unmanaged volume object
        volume.setVolumeCharacterstics(unManagedVolumeCharacteristics);
        volume.addVolumeInformation(unManagedVolumeInformation);
    }

    /**
     * Creates a new UnManagedVolume with the info from 
     * the VPLEX virtual volume.
     * 
     * @param info a VPlexVirtualVolumeInfo descriptor
     * @param vplex the VPLEX storage system managing the volume
     * @param volumesToCgs a Map of volume labels to consistency group names 
     */
    private UnManagedVolume createUnmanagedVolume(VPlexVirtualVolumeInfo info, 
            StorageSystem vplex, Map<String, String> volumesToCgs, Map<String, String> clusterIdToNameMap, 
            Map<String, String> varrayToClusterIdMap) {

        s_logger.info("Creating new UnManagedVolume from VPLEX volume {}", 
                info.getName());
        
        UnManagedVolume volume = new UnManagedVolume();
        volume.setId(URIUtil.createId(UnManagedVolume.class));
        
        updateUnmanagedVolume(info, vplex, volume, volumesToCgs, clusterIdToNameMap, varrayToClusterIdMap);
        
        return volume;
    }
    
    /**
     * Used to determine the value of the IS_INGESTABLE flag.
     * 
     * @param info a VPlexVirtualVolumeInfo descriptor
     * @return true if the virtual volume is ingestable by ViPR
     */
    private boolean isVolumeIngestable(VPlexVirtualVolumeInfo info) {

        // currently no checks are needed during the ingest phase
        
        return true;
    }
    
    /**
     * Handles persisting UnManagedVolumes in batches.
     * 
     * @param unManagedVolumesToCreate UnManagedVolumes to be created
     * @param unManagedVolumesToUpdate UnManagedVolumes to be updated
     * @param flush if true, persistence with be forced
     */
    private void persistUnManagedVolumes(List<UnManagedVolume> unManagedVolumesToCreate, 
            List<UnManagedVolume> unManagedVolumesToUpdate, boolean flush) {
        if (null != unManagedVolumesToCreate) {
            if (flush || (unManagedVolumesToCreate.size() > BATCH_SIZE)) {
                _partitionManager.insertInBatches(unManagedVolumesToCreate,
                        BATCH_SIZE, _dbClient, UNMANAGED_VOLUME);
                unManagedVolumesToCreate.clear();
            }
        }
        if (null != unManagedVolumesToUpdate) {
            if (flush || (unManagedVolumesToUpdate.size() > BATCH_SIZE)) {
                _partitionManager.updateInBatches(unManagedVolumesToUpdate,
                        BATCH_SIZE, _dbClient, UNMANAGED_VOLUME);
                unManagedVolumesToUpdate.clear();
            }
        }
    }

    /**
     * This method cleans up UnManaged Volumes in DB, which had been deleted manually from the Array
     * 
     * 1. Get All UnManagedVolumes from DB for the given VPLEX device
     * 2. Store URIs of unmanaged volumes returned from the Provider
     * 3. If unmanaged volume is found only in DB, then set unmanaged volume to inactive.
     * 
     * @param vplexUri the URI for loading the VPLEX device
     * @param allUnmanagedVolumes a list of URI for all the newly discovered unmanaged volumes
     * @throws IOException
     */
    private void cleanUpOrphanedVolumes(URI vplexUri, Set<URI> allUnmanagedVolumes) {
        
        URIQueryResultList results = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getStorageSystemUnManagedVolumeConstraint(vplexUri), results);

        Set<URI> unManagedVolumesInDBSet = new HashSet<URI>();
        while (results.iterator().hasNext()) {
            // why does getting stuff from the database have to be so painful?
            unManagedVolumesInDBSet.add(results.iterator().next());
        }
        SetView<URI> onlyAvailableinDB = Sets.difference(unManagedVolumesInDBSet, allUnmanagedVolumes);

        if (onlyAvailableinDB != null && onlyAvailableinDB.size() > 0) {
            s_logger.info("UnManagedVolumes to be Removed : " + Joiner.on("\t").join(onlyAvailableinDB));
            List<UnManagedVolume> volumesToBeDeleted = new ArrayList<UnManagedVolume>();
            Iterator<UnManagedVolume> unManagedVolumes =  _dbClient.queryIterativeObjects(UnManagedVolume.class, 
                    new ArrayList<URI>(onlyAvailableinDB));

            while (unManagedVolumes.hasNext()) {
                UnManagedVolume volume = unManagedVolumes.next();
                if (null == volume || volume.getInactive()) {
                    continue;
                }

                s_logger.info("Setting UnManagedVolume {} inactive",volume.getId());
                volume.setStorageSystemUri(NullColumnValueGetter.getNullURI());                
                volume.setInactive(true);
                volumesToBeDeleted.add(volume);
            }
            
            if (volumesToBeDeleted.size() > 0 ) {
                _partitionManager.updateAndReIndexInBatches(volumesToBeDeleted, 
                        Constants.DEFAULT_PARTITION_SIZE, _dbClient, UNMANAGED_VOLUME);
            }
        }
    }
    
    private UnManagedExportMask getUnManagedExportMaskFromDb (VPlexStorageViewInfo storageView ) {
        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getUnManagedExportMaskPathConstraint(storageView.getPath()), result);
        UnManagedExportMask uem = null;
        Iterator<URI> it = result.iterator();
        while (it.hasNext()) {
            s_logger.info("found an existing unmanaged export mask for storage view " + storageView.getName());
            uem = _dbClient.queryObject(UnManagedExportMask.class, it.next());
            if (uem != null)  {
                break;
            }
        }
        return uem;
    }
    
    /**
     * Discovers storage views on the VPLEX and creates UnManagedExportMasks for any
     * that are not managed by ViPR.
     * 
     * @param accessProfile providing context for this discovery session
     * @param client a reference to the VPLEX API client
     * @param vvolMap map of virtual volume names to virtual volume info objects
     * @param volumeToExportMasksMap map of volumes to a set of associated UnManagedExportMasks
     * @throws BaseCollectionException
     */
    private void discoverUnmanagedStorageViews(AccessProfile accessProfile, VPlexApiClient client, 
            Map<String, VPlexVirtualVolumeInfo> vvolMap, 
            Map <String, Set<UnManagedExportMask>> volumeToExportMasksMap) throws BaseCollectionException {

        String statusMessage = "Starting discovery of Unmanaged VPLEX Storage Views.";
        s_logger.info(statusMessage + " Access Profile Details :  IpAddress : "
                + "PortNumber : {}, namespace : {}",
                accessProfile.getIpAddress() + accessProfile.getPortNumber(),
                accessProfile.getnamespace());

        URI vplexUri = accessProfile.getSystemId();
        StorageSystem vplex = _dbClient.queryObject(StorageSystem.class, vplexUri);
        if (null == vplex) {
            s_logger.error("No VPLEX Device was found in ViPR for URI: " + vplexUri);
            s_logger.error("Unmanaged VPLEX StorageView discovery cannot continue.");
            return;
        }
        
        try {
            Map<String, String> targetPortToPwwnMap = new HashMap<String, String>();
            List<VPlexPortInfo> cachedPortInfos = client.getPortInfo(true);
            for( VPlexPortInfo cachedPortInfo : cachedPortInfos){
                targetPortToPwwnMap.put(cachedPortInfo.getTargetPort(), cachedPortInfo.getPortWwn());
            }

            Set<URI> allCurrentUnManagedExportMaskUris = new HashSet<URI>();
            List<UnManagedExportMask> unManagedExportMasksToCreate = new ArrayList<UnManagedExportMask>();
            List<UnManagedExportMask> unManagedExportMasksToUpdate = new ArrayList<UnManagedExportMask>();

            List<VPlexStorageViewInfo> storageViews = client.getStorageViews();
            for (VPlexStorageViewInfo storageView : storageViews) {
                s_logger.info("discovering storage view: " + storageView.toString());
                List<Initiator> knownInitiators = new ArrayList<Initiator>();
                List<StoragePort> knownPorts = new ArrayList<StoragePort>();
                UnManagedExportMask uem = getUnManagedExportMaskFromDb(storageView);
                if (uem != null) {
                    s_logger.info("found an existing unmanaged export mask for storage view " + storageView.getName());
                    unManagedExportMasksToUpdate.add(uem);
                    
                    // clean up collections (we'll be refreshing them)
                    uem.getKnownInitiatorUris().clear();
                    uem.getKnownInitiatorNetworkIds().clear();
                    uem.getKnownStoragePortUris().clear();
                    uem.getKnownVolumeUris().clear();
                    uem.getUnmanagedInitiatorNetworkIds().clear();
                    uem.getUnmanagedStoragePortNetworkIds().clear();
                    uem.getUnmanagedVolumeUris().clear();
                } else {
                    s_logger.info("creating a new unmanaged export mask for storage view " + storageView.getName());
                    uem = new UnManagedExportMask();
                    unManagedExportMasksToCreate.add(uem);
                }

                // set basic info
                uem.setNativeId(storageView.getPath());
                uem.setMaskingViewPath(storageView.getPath());
                uem.setMaskName(storageView.getName());
                uem.setStorageSystemUri(accessProfile.getSystemId());
                
                s_logger.info("now discovering host initiators in storage view " + storageView.getName());
                for (String initiatorNetworkId : storageView.getInitiatorPwwns()) {
                    
                    s_logger.info("looking at initiator network id " + initiatorNetworkId);
                    if (WWNUtility.isValidWWNAlias(initiatorNetworkId)) {
                        initiatorNetworkId = WWNUtility.getWWNWithColons(initiatorNetworkId);
                        s_logger.info("   wwn normalized to " + initiatorNetworkId);
                    } else if (initiatorNetworkId.matches(ISCSI_PATTERN)
                            && (iSCSIUtility.isValidIQNPortName(initiatorNetworkId) 
                            ||  iSCSIUtility.isValidEUIPortName(initiatorNetworkId))) {
                        s_logger.info("   iSCSI storage port normalized to " + initiatorNetworkId);
                    } else {
                        s_logger.warn("   this is not a valid FC or iSCSI network id format, skipping");
                        continue;
                    }

                    // check if a host initiator exists for this id
                    // if so, add to _knownInitiators
                    // otherwise, add to _unmanagedInitiators
                    Initiator knownInitiator = NetworkUtil.getInitiator(initiatorNetworkId, _dbClient);
                    if (knownInitiator != null) {
                        s_logger.info("   found an initiator in ViPR on host " + knownInitiator.getHostName());
                        uem.getKnownInitiatorUris().add(knownInitiator.getId().toString());
                        uem.getKnownInitiatorNetworkIds().add(knownInitiator.getInitiatorPort());
                        knownInitiators.add(knownInitiator);
                    } else {
                        s_logger.info("   no hosts in ViPR found configured for initiator " + initiatorNetworkId);
                        uem.getUnmanagedInitiatorNetworkIds().add(initiatorNetworkId);
                    }
                }

                s_logger.info("now discovering storage ports in storage view " + storageView.getName());
                List<String> storagePorts = storageView.getPorts();
                
                if (storagePorts.size() == 0) {
                    s_logger.info("no storage ports found in storage view " + storageView.getName());
                    // continue;  ?
                }
                
                // target port has value like - P0000000046E01E80-A0-FC02
                // PortWwn has value like - 0x50001442601e8002
                List<String> portWwns = new ArrayList<String>();
                for(String storagePort : storagePorts){
                    if(targetPortToPwwnMap.keySet().contains(storagePort)){
                        portWwns.add(WwnUtils.convertWWN(targetPortToPwwnMap.get(storagePort), WwnUtils.FORMAT.COLON));
                    }
                }
                
                for (String portNetworkId : portWwns) {
                    s_logger.info("looking at storage port network id " + portNetworkId);

                    // check if a storage port exists for this id in ViPR
                    // if so, add to _storagePorts
                    StoragePort knownStoragePort = NetworkUtil.getStoragePort(portNetworkId, _dbClient);
                    
                    if (knownStoragePort != null) {
                        s_logger.info("   found a matching storage port in ViPR " + knownStoragePort.getLabel());
                        uem.getKnownStoragePortUris().add(knownStoragePort.getId().toString());
                        knownPorts.add(knownStoragePort);
                    } else {
                        s_logger.info("   no storage port in ViPR found matching portNetworkId " + portNetworkId);
                        uem.getUnmanagedStoragePortNetworkIds().add(portNetworkId);
                    }
                }
                
                s_logger.info("now discovering storage volumes in storage view " + storageView.getName());
                for (String volumeName : storageView.getVirtualVolumes()) {

                    s_logger.info("found volume " + volumeName.toString());
                    
                    StringTokenizer tokenizer = new StringTokenizer(volumeName, ",");
                    String hluStr = tokenizer.nextToken();
                    hluStr = hluStr.substring(1); // skips an opening "("
                    Integer volumeHLU = Integer.valueOf(hluStr);
                    volumeName = tokenizer.nextToken();
                    String vpdId = tokenizer.nextToken();
                    int indexColon = vpdId.indexOf(":");
                    String volumeWWN = vpdId.substring(indexColon + 1);
                    
                    VPlexVirtualVolumeInfo vvol = vvolMap.get(volumeName);
                    Volume volume = findVirtualVolumeManagedByVipr(vvol);

                    if (volume != null) {
                        s_logger.info("this is a volume already managed by ViPR: " + volume.getLabel());
                        uem.getKnownVolumeUris().add(volume.getId().toString());
                    }
                    
                    // add to map of volume paths to export masks
                    if (vvol != null) {
                        String nativeGuid = vvol.getPath();
                        s_logger.info("nativeGuid UnManagedVolume key for locating UnManagedExportMasks is " + nativeGuid);
                        Set<UnManagedExportMask> maskSet = volumeToExportMasksMap.get(nativeGuid);
                        if (maskSet == null) {
                            maskSet = new HashSet<UnManagedExportMask>();
                            s_logger.info("   creating new maskSet for nativeGuid " + nativeGuid);
                            volumeToExportMasksMap.put(nativeGuid, maskSet);
                        }
                        maskSet.add(uem);
                    }
                }
                
                if (uem.getId() == null) {
                    uem.setId(URIUtil.createId(UnManagedExportMask.class));
                }
                
                updateZoningMap(uem, knownInitiators, knownPorts);
                persistUnManagedExportMasks(unManagedExportMasksToCreate, unManagedExportMasksToUpdate, false);
                allCurrentUnManagedExportMaskUris.add(uem.getId());
            }
            
            persistUnManagedExportMasks(unManagedExportMasksToCreate, unManagedExportMasksToUpdate, true);
            cleanUpOrphanedExportMasks(vplexUri, allCurrentUnManagedExportMaskUris);
            
        } catch (Exception ex) {
            s_logger.error(ex.getLocalizedMessage(), ex);
            String vplexLabel = vplexUri.toString();
            if (null != vplex) {
                vplexLabel = vplex.getLabel();
            }
            
            throw VPlexCollectionException.exceptions.vplexUnmanagedExportMaskDiscoveryFailed(
                    vplexLabel, ex.getLocalizedMessage());
        } finally {
            if (null != vplex) {
                try {
                    vplex.setLastDiscoveryStatusMessage(statusMessage);
                    _dbClient.persistObject(vplex);
                } catch (Exception ex) {
                    s_logger.error("Error while saving VPLEX discovery status message: {} - Exception: {}", 
                                    statusMessage, ex.getLocalizedMessage());
                }
            }
        }

    }

    private void updateZoningMap(UnManagedExportMask mask, List<Initiator> initiators, List<StoragePort> storagePorts) {
        try {
            s_logger.info("   Updating zoning map for vplex mask " + mask.getMaskName());
            if (mask.getZoningMap() != null) {
                mask.getZoningMap().replace(_networkDeviceController.getInitiatorsZoneInfoMap(initiators, storagePorts));
            } else {
                mask.setZoningMap(_networkDeviceController.getInitiatorsZoneInfoMap(initiators, storagePorts));
            }
        } catch (Exception ex) {
            mask.setZoningMap(null);
            s_logger.error("Failed to get the zoning map for vplex mask {}", mask.getMaskName());
        }
    }
    
    /**
     * Handles persisting UnManagedExportMasks in batches.
     * 
     * @param unManagedExportMasksToCreate UnManagedExportMasks to be created
     * @param unManagedExportMasksToUpdate UnManagedExportMasks to be updated
     * @param flush if true, persistence with be forced
     */
    private void persistUnManagedExportMasks(List<UnManagedExportMask> unManagedExportMasksToCreate, 
            List<UnManagedExportMask> unManagedExportMasksToUpdate, boolean flush) {
        if (null != unManagedExportMasksToCreate) {
            if (flush || (unManagedExportMasksToCreate.size() > BATCH_SIZE)) {
                _partitionManager.insertInBatches(unManagedExportMasksToCreate,
                        BATCH_SIZE, _dbClient, UNMANAGED_EXPORT_MASK);
                unManagedExportMasksToCreate.clear();
            }
        }
        if (null != unManagedExportMasksToUpdate) {
            if (flush || (unManagedExportMasksToUpdate.size() > BATCH_SIZE)) {
                _partitionManager.updateInBatches(unManagedExportMasksToUpdate,
                        BATCH_SIZE, _dbClient, UNMANAGED_EXPORT_MASK);
                unManagedExportMasksToUpdate.clear();
            }
        }
    }
    
    /**
     * Cleans up any UnManagedExportMask objects that are present in the ViPR database,
     * but are no longer present on the VPLEX device.
     * 
     * @param vplexUri device id of the VPLEX
     * @param allCurrentUnManagedExportMaskUris all the UnManagedExportMasks we found in this discovery run
     */
    private void cleanUpOrphanedExportMasks(URI vplexUri, Set <URI> allCurrentUnManagedExportMaskUris) {

        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getStorageSystemUnManagedExportMaskConstraint(vplexUri), result);
        Set<URI> allMasksInDatabase = new HashSet<URI>();
        Iterator<URI> it = result.iterator();
        while (it.hasNext()) {
            allMasksInDatabase.add(it.next());
        }

        SetView<URI> onlyAvailableinDB =  Sets.difference(allMasksInDatabase, allCurrentUnManagedExportMaskUris);
        
        if (onlyAvailableinDB.size() > 0) {
            s_logger.info("these UnManagedExportMasks are orphaned and will be cleaned up:" 
                    + Joiner.on("\t").join(onlyAvailableinDB));

            List<UnManagedExportMask> unManagedExportMasksToBeDeleted = new ArrayList<UnManagedExportMask>();
            Iterator<UnManagedExportMask> unManagedExportMasks =  
                _dbClient.queryIterativeObjects(UnManagedExportMask.class, new ArrayList<URI>(onlyAvailableinDB));

            while (unManagedExportMasks.hasNext()) {
                
                UnManagedExportMask uem = unManagedExportMasks.next();
                if (null == uem || uem.getInactive()) {
                    continue;
                }

                s_logger.info("Setting UnManagedExportMask {} inactive", uem.getMaskingViewPath());
                uem.setStorageSystemUri(NullColumnValueGetter.getNullURI());
                uem.setInactive(true);
                unManagedExportMasksToBeDeleted.add(uem);
            }
            if (unManagedExportMasksToBeDeleted.size() > 0 ) {
                _partitionManager.updateAndReIndexInBatches(unManagedExportMasksToBeDeleted, BATCH_SIZE,
                        _dbClient, UNMANAGED_EXPORT_MASK);
            }
        }
    }
    
    /**
     * Implementation for discovering everything in a VPLEX storage system.
     * 
     * @param accessProfile providing context for this discovery session
     * 
     * @throws BaseCollectionException
     */
    private void discoverAll(AccessProfile accessProfile) throws BaseCollectionException {

        boolean discoverySuccess = true;
        StringBuffer errMsgBuilder = new StringBuffer();
        URI storageSystemURI = null;
        StorageSystem vplexStorageSystem = null;
        String detailedStatusMessage = "Unknown Status";

        try {
            s_logger.info("Access Profile Details :  IpAddress : {}, PortNumber : {}",
                accessProfile.getIpAddress(), accessProfile.getPortNumber());
            storageSystemURI = accessProfile.getSystemId();
            // Get the VPlex storage system from the database.
            vplexStorageSystem = _dbClient.queryObject(StorageSystem.class,
                storageSystemURI);

            s_logger.info("Discover VPlex storage system {} at IP:{}, PORT:{}",
                new Object[] { storageSystemURI.toString(), accessProfile.getIpAddress(),
                    accessProfile.getPortNumber() });

            // Get the Http client for getting information about the VPlex
            // storage system.
            VPlexApiClient client = getVPlexAPIClient(accessProfile);
            s_logger.debug("Got handle to VPlex API client");
            
            // The version for the storage system is the version of its active provider
            // and since we are discovering it, the provider was compatible, so the
            // VPLEX must also be compatible.
            StorageProvider activeProvider = _dbClient.queryObject(StorageProvider.class,
                vplexStorageSystem.getActiveProviderURI());
            vplexStorageSystem.setFirmwareVersion(activeProvider.getVersionString());
            vplexStorageSystem.setCompatibilityStatus(CompatibilityStatus.COMPATIBLE.toString());
           
            // Discover the cluster identification (serial number / cluster id ) mapping
            try {
                s_logger.info("Discovering cluster identification.");
                discoverClusterIdentification(accessProfile, client);
                _completer.statusPending(_dbClient,"Completed cluster identification discovery");
            } catch (VPlexCollectionException vce) {
                discoverySuccess = false;
                String errMsg = String.format("Failed cluster identification discovery for VPlex %s",
                    storageSystemURI.toString());
                s_logger.error(errMsg, vce);
                if (errMsgBuilder.length() != 0) {
                    errMsgBuilder.append(", ");
                }
                errMsgBuilder.append(errMsg);
            }

            List<StoragePort> allPorts = new ArrayList<StoragePort>();
            // Discover the VPlex port information.
            try {
                // When we discover storage ports on the VPlex, we create
                // initiators, if they don't exist, for backend ports. 
                // The backend storage ports serve as initiators for the
                // connected backend storage.
                s_logger.info("Discovering frontend and backend ports.");
                discoverPorts(client, vplexStorageSystem,allPorts);
                _dbClient.persistObject(vplexStorageSystem);
                _completer.statusPending(_dbClient,"Completed port discovery");
            } catch (VPlexCollectionException vce) {
                discoverySuccess = false;
                String errMsg = String.format("Failed port discovery for VPlex %s",
                    storageSystemURI.toString());
                s_logger.error(errMsg, vce);
                if (errMsgBuilder.length() != 0) {
                    errMsgBuilder.append(", ");
                }
                errMsgBuilder.append(errMsg);
            }
            
            try {
                s_logger.info("Discovering connectivity.");
                discoverConnectivity(vplexStorageSystem);
                _dbClient.persistObject(vplexStorageSystem);
                _completer.statusPending(_dbClient,"Completed connectivity verification");
            } catch (VPlexCollectionException vce) {
                discoverySuccess = false;
                String errMsg = String.format(
                    "Failed connectivity discovery for VPlex %s",
                    storageSystemURI.toString());
                s_logger.error(errMsg, vce);
                if (errMsgBuilder.length() != 0) {
                    errMsgBuilder.append(", ");
                }
                errMsgBuilder.append(errMsg);
            }
            
            if(discoverySuccess){
            	vplexStorageSystem.setReachableStatus(true);
            	_dbClient.persistObject(vplexStorageSystem);
            } else {
            	// If part of the discovery process failed, throw an exception.
            	vplexStorageSystem.setReachableStatus(false);
            	_dbClient.persistObject(vplexStorageSystem);
            	throw new Exception(errMsgBuilder.toString());
            }
            
            StoragePortAssociationHelper.runUpdatePortAssociationsProcess(allPorts,null, _dbClient, _coordinator,null);
            // discovery succeeds
            detailedStatusMessage = String.format("Discovery completed successfully for Storage System: %s", 
                    storageSystemURI.toString());
        } catch (Exception e) {
            VPlexCollectionException vce = VPlexCollectionException.exceptions.failedDiscovery(
                storageSystemURI.toString(), e.getLocalizedMessage());
            detailedStatusMessage = vce.getLocalizedMessage();
            s_logger.error(detailedStatusMessage, e);
            throw vce;
        } finally {
            if (vplexStorageSystem != null) {
                try {
                    // set detailed message
                    vplexStorageSystem.setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.persistObject(vplexStorageSystem);
                } catch (DatabaseException ex) {
                    s_logger.error("Error persisting last discovery status for storage system {}",
                        vplexStorageSystem.getId(), ex);
                }
            }
        }
    }
    
    /**
     * Discover the connectivity for the passed VPLEX storage system.
     * 
     * @param storageSystem The VPLEX storage system.
     */
    private void discoverConnectivity(StorageSystem storageSystem) {
    	
        StringSet newVarrays = StoragePoolAssociationHelper
            .getVplexSystemConnectedVarrays(storageSystem.getId(), _dbClient);
    	if (storageSystem.getVirtualArrays() == null) {
    		storageSystem.setVirtualArrays(newVarrays);
    	} else {
    		storageSystem.getVirtualArrays().replace(newVarrays);
    	}
    	_dbClient.updateAndReindexObject(storageSystem);
    }
    
    /**
     * Discovers and creates the ports for the passed VPlex virtual storage
     * system.
     * 
     * @param client The VPlex API client.
     * @param vplexStorageSystem A reference to the VPlex storage system.
     * 
     * @throws VPlexCollectionException When an error occurs discovering the
     *         VPlex ports.
     */
    private void discoverPorts(VPlexApiClient client, StorageSystem vplexStorageSystem,List<StoragePort> allPorts)
        throws VPlexCollectionException {
        List<StoragePort> newStoragePorts = new ArrayList<StoragePort>();
        List<StoragePort> existingStoragePorts = new ArrayList<StoragePort>();
        List<Initiator> newInitiatorPorts = new ArrayList<Initiator>();
        try {
            // Get the port information from the VPlex.
            String initiatorHostName = null;
            List<VPlexPortInfo> portInfoList = client.getPortInfo(false);
            Map<String, VPlexTargetInfo> portTargetMap = client.getTargetInfoForPorts(portInfoList);
            for (VPlexPortInfo portInfo : portInfoList) {
                s_logger.debug("VPlex port info: {}", portInfo.toString());
                
                // VPlex director port can have a variety of roles. They can
                // be front-end ports for exposing VPlex virtual volumes to 
                // hosts. They can be back-end ports that serve as initiators
                // to the connected back-end storage systems and expose back-end
                // storage volumes to the VPlex. They can also be WAN ports that
                // connect VPlex directors in the cluster. For now we are only
                // concerned with front-end and back-end ports. We create 
                // StoragePort instances for these ports.
                if ((!portInfo.isFrontendPort()) && (!portInfo.isBackendPort())) {
                    s_logger.debug("Not a front/back-end port, skipping port {}",
                        portInfo.getName());
                    continue;
                }
                
                String portWWN = WWNUtility.getWWNWithColons(portInfo.getPortWwn());
                String portType = portInfo.isBackendPort() ? PortType.backend.name() : PortType.frontend.name();
                s_logger.info("Found {} port {}", portType, portWWN);
                
                // Ports that are not online can have a WWN that is not set. 
                // In this case, WWN would be 00:00:00:00:00:00:00:00. We 
                // ignore these ports. Change created to address CQ 649101, where
                // the frontend ports on one of the clusters directors were all
                // offline, resulting in 4 ports in the DB with the same unset
                // WWN.
                if ((portWWN == null) || (portWWN.equals(OFFLINE_PORT_WWN))) {
                    s_logger.info("Skipping port {} with WWN {}",
                        portInfo.getName(), portWWN);
                    continue;
                }
                
                // See if the port already exists in the DB. If not we need to
                // create it.
                StoragePort storagePort = findPortInDB(vplexStorageSystem, portInfo);
                if (storagePort == null) {
                    s_logger.info("Creating new port {}", portWWN);
                    storagePort = new StoragePort();
                    storagePort.setId(URIUtil.createId(StoragePort.class));
                    storagePort.setPortNetworkId(portWWN);
                    storagePort.setPortName(portInfo.getName());
                    storagePort.setStorageDevice(vplexStorageSystem.getId());
                    String nativeGuid = NativeGUIDGenerator.generateNativeGuid(_dbClient, storagePort);
                    storagePort.setNativeGuid(nativeGuid);
                    storagePort.setLabel(nativeGuid);
                    storagePort.setPortType(portType);
                    storagePort.setTransportType(StorageProtocol.Block.FC.name()); // Always FC
                    setHADomainForStoragePort(vplexStorageSystem, storagePort, portInfo);
                    storagePort.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
                    newStoragePorts.add(storagePort);
                } else {
                    existingStoragePorts.add(storagePort);
                }

                // CTRL-4701 - if we got to this point, the VPLEX firmware was validated as compatible,
                //             so, the storage port should be marked compatible as well
                storagePort.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
                storagePort.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                // Port speed is current port speed and should be updated
                // for existing ports.
                storagePort.setPortSpeed(portInfo.getCurrentSpeed(SpeedUnits.GBITS_PER_SECOND));
                
                // Set or update the port operational status.
                storagePort.setOperationalStatus(getPortOperationalStatus(portInfo, portTargetMap));
                 
                // If there is not an initiator in the database representing the
                // backend storage port, then create one and add it to the passed 
                // list.
                if (portInfo.isBackendPort()) {
                    Initiator initiatorPort = findInitiatorInDB(portInfo);
                    if (initiatorPort == null) {
                        s_logger.info("Creating initiator for backend port", portWWN);
                        if (initiatorHostName == null) {
                            initiatorHostName = getInitiatorHostName(vplexStorageSystem);
                        }
                        s_logger.info("Host name is {}", initiatorHostName);
                        initiatorPort = new Initiator(StorageProtocol.Block.FC.name(),
                            portWWN, WWNUtility.getWWNWithColons(portInfo.getNodeWwn()),
                            initiatorHostName, false);
                        initiatorPort.setId(URIUtil.createId(Initiator.class));
                        newInitiatorPorts.add(initiatorPort);
                    }
                }
            }
            // Persist changes to new and exiting ports and initiators.
            _dbClient.createObject(newStoragePorts);
            _dbClient.persistObject(existingStoragePorts);
            _dbClient.createObject(newInitiatorPorts);

            allPorts.addAll(newStoragePorts);
            allPorts.addAll(existingStoragePorts);
            List<StoragePort> notVisiblePorts = DiscoveryUtils.checkStoragePortsNotVisible(allPorts, _dbClient,
                    vplexStorageSystem.getId());
            if (notVisiblePorts != null && !notVisiblePorts.isEmpty()) {
                allPorts.addAll(notVisiblePorts);
            }
        } catch (Exception e) {
        	s_logger.error("Error discovering ports for the VPLEX storage system {}:", vplexStorageSystem.getIpAddress(), e);
            throw VPlexCollectionException.exceptions.failedPortsDiscovery(
                vplexStorageSystem.getId().toString(), e.getLocalizedMessage(), e);
        }
    }

    /**
     * Implementation for statistics collection for VPlex storage systems.
     * 
     * @param accessProfile
     * 
     * @throws BaseCollectionException
     */
    @Override
    public void collectStatisticsInformation(AccessProfile accessProfile)
        throws BaseCollectionException {
    }
    
    /**
     * Get the HTTP client for making requests to the VPlex at the 
     * endpoint specified in the passed profile.
     * 
     * @param accessProfile A reference to the access profile.
     * 
     * @return A reference to the VPlex API HTTP client.
     * @throws URISyntaxException 
     */
    private VPlexApiClient getVPlexAPIClient(AccessProfile accessProfile) throws URISyntaxException {
        // Create the URI to access the VPlex Management Station based
        // on the IP and port in the passed access profile.
    	URI vplexEndpointURI = new URI("https", null, accessProfile.getIpAddress(), accessProfile.getPortNumber(), "/", null, null);
        s_logger.debug("VPlex base URI is {}", vplexEndpointURI.toString());
        VPlexApiClient client = _apiFactory.getClient(vplexEndpointURI,
            accessProfile.getUserName(), accessProfile.getPassword());
        return client;
    }
    
    /**
     * Find the port in the data base corresponding to the passed port
     * information.
     * 
     * @param vplexStorageSystem A reference to the port's storage system.
     * @param portInfo The port information.
     * 
     * @return The found StoragePort instance, or null if not found.
     * 
     * @throws IOException When an error occurs querying the database.
     */
    private StoragePort findPortInDB(StorageSystem vplexStorageSystem,
        VPlexPortInfo portInfo) throws IOException {
        StoragePort port = null;
        String portWWN = WWNUtility.getWWNWithColons(portInfo.getPortWwn());
        String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(
            vplexStorageSystem, portWWN, NativeGUIDGenerator.PORT);
        s_logger.debug("Looking for port {} in database", portNativeGuid);
        URIQueryResultList queryResults = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
            .getStoragePortByNativeGuidConstraint(portNativeGuid), queryResults);
        Iterator<URI> resultsIter = queryResults.iterator();
        if (resultsIter.hasNext()) {
            s_logger.debug("Found port {}", portNativeGuid);
            port = _dbClient.queryObject(StoragePort.class, resultsIter.next());
        }
        return port;
    }
    
    /**
     * Find the initiator in the data base corresponding to the passed port
     * information.
     * 
     * @param portInfo The port information.
     * 
     * @return The found Initiator instance, or null if not found.
     * 
     * @throws IOException When an error occurs querying the database.
     */
    private Initiator findInitiatorInDB(VPlexPortInfo portInfo) throws IOException {
        Initiator initiator = null;
        String portWWN = WWNUtility.getWWNWithColons(portInfo.getPortWwn());
        s_logger.debug("Looking for initiator {} in database", portWWN);
        URIQueryResultList queryResults = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
            .getInitiatorPortInitiatorConstraint(portWWN), queryResults);
        Iterator<URI> resultsIter = queryResults.iterator();
        if (resultsIter.hasNext()) {
            s_logger.debug("Found initiator {}", portWWN);
            initiator = _dbClient.queryObject(Initiator.class, resultsIter.next());
        }
        return initiator;
    }
    
    private String getInitiatorHostName(StorageSystem vplexStorageSystem) {
        // By default, we use the IP address of the active VPLEX management
        // server used for discovery for the host name for initiators 
        // associated with the VPLEX.
        URI activeMgmntSvrURI = vplexStorageSystem.getActiveProviderURI();
        StorageProvider activeMgmntSvr = _dbClient.queryObject(StorageProvider.class,
            activeMgmntSvrURI);
        String hostName = activeMgmntSvr.getIPAddress();
        if (IPAddressUtil.isIPv4LiteralAddress(hostName)
            || IPAddressUtil.isIPv6LiteralAddress(hostName)) {
            // The VNX cannot deal with literal IP addresses in the hostName
            // field of an initiator. Make it something more reasonable.
            hostName = "vplex_" + hostName;
            s_logger.info("New host name is {}", hostName);
        }
        
        // The default is to use the IP of the active provider when no initiators
        // are found for this VPLEX system.
        String dfltHostName = hostName;
        
        // Check to see if there are any existing initiators with this host name.
        // If there are, then return this host name.
        List<Initiator> initiatorsWithHostName = CustomQueryUtility
            .queryActiveResourcesByAltId(_dbClient, Initiator.class, "hostname", hostName);
        if (!initiatorsWithHostName.isEmpty()) {
            return hostName;
        }
        
        // Otherwise, see if there are any initiators with a host name based
        // on any other providers for this vplex system. It could be that the
        // active management server has changed, and since that happened new
        // backend ports were added to the VPLEX. We want to make sure that all
        // initiators have the same host name. So it could be that previously
        // discovered backend ports have a host name based on the IP of a now
        // inactive management server.
        StringSet mgmntSvrIds = vplexStorageSystem.getProviders();
        for (String mgmntSvrId : mgmntSvrIds) {
            if (mgmntSvrId.equals(activeMgmntSvrURI.toString())) {
                continue;
            }
            StorageProvider mgmntSvr = _dbClient.queryObject(StorageProvider.class,
                URI.create(mgmntSvrId));
            hostName = mgmntSvr.getIPAddress();
            if (IPAddressUtil.isIPv4LiteralAddress(hostName)
                || IPAddressUtil.isIPv6LiteralAddress(hostName)) {
                hostName = "vplex_" + hostName;
                s_logger.info("New host name is {}", hostName);
            }
            
            // Check to see if there are any existing initiators with 
            // this host name. If there are, then return this host name.
            initiatorsWithHostName = CustomQueryUtility
                .queryActiveResourcesByAltId(_dbClient, Initiator.class, "hostname", hostName);
            if (!initiatorsWithHostName.isEmpty()) {
                return hostName;
            }            
        }
        
        // There are no initiators for the VPLEX. Likely this is the initial 
        // discovery of the VPLEX. Just use the default.
        return dfltHostName;
    }
    
    /**
     * Sets the storage HA domain for the passed port, creating and persisting
     * the domain if necessary.
     * 
     * @param vplexStorageSystem A reference to the VPlex virtual storage
     *        system.
     * @param storagePort The storage port whose HA domain is to be set.
     * @param portInfo The port information from the VPlex.
     * 
     * @throws IOException When an error occurs accessing the database.
     */
    private void setHADomainForStoragePort(StorageSystem vplexStorageSystem,
        StoragePort storagePort, VPlexPortInfo portInfo) throws IOException {
        
        StorageHADomain haDomain = null;
        VPlexDirectorInfo directorInfo = portInfo.getDirectorInfo();
        String directorSerialNumber = directorInfo.getSerialNumber();
        String directorNativeGuid = NativeGUIDGenerator.generateNativeGuid(
            vplexStorageSystem, directorSerialNumber, NativeGUIDGenerator.ADAPTER);
        s_logger.debug("Looking for storage HA domain {}  in the database",
            directorNativeGuid);
        URIQueryResultList queryResults = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
            .getStorageHADomainByNativeGuidConstraint(directorNativeGuid), queryResults);
        Iterator<URI> resultsIter = queryResults.iterator();
        if (resultsIter.hasNext()) {
            s_logger.debug("Found storage HA doamin {}", directorNativeGuid);
            haDomain = _dbClient.queryObject(StorageHADomain.class, resultsIter.next());
        } else {
            s_logger.info("Creating storage HA domain {}", directorNativeGuid);
            haDomain = new StorageHADomain();
            haDomain.setId(URIUtil.createId(StorageHADomain.class));
            haDomain.setName(directorInfo.getName());
            haDomain.setAdapterName(directorInfo.getName());
            haDomain.setStorageDeviceURI(vplexStorageSystem.getId());
            haDomain.setNativeGuid(directorNativeGuid);
            haDomain.setSerialNumber(directorSerialNumber);
            List<PortRole> portRoles = new ArrayList<PortRole>();
            portRoles.add(PortRole.FRONTEND);
            portRoles.add(PortRole.BACKEND);
            haDomain.setNumberofPorts(String.valueOf(directorInfo
                .getNumberOfPortsOfType(portRoles)));
            haDomain.setProtocol(StorageProtocol.Block.FC.name());
            haDomain.setSlotNumber(String.valueOf(directorInfo.getSlotNumber()));
            _dbClient.createObject(haDomain);
        }
        
        s_logger.info("Setting storage HA domain {} for port {}", directorNativeGuid,
            storagePort.getPortNetworkId());
        storagePort.setStorageHADomain(haDomain.getId());
        storagePort.setPortGroup(haDomain.getAdapterName());
    }
    
    /**
     * Gets the operational status for the passed port based on whether it is
     * a frontend or backend port.
     * 
     * @param portInfo Port info for the port.
     * @param portTargetMap The port target info for frontend ports.
     * 
     * @return A String representing the ViPR port status.
     */
    private String getPortOperationalStatus(VPlexPortInfo portInfo, Map<String, VPlexTargetInfo> portTargetMap) {
        s_logger.info("Port status for port {}", portInfo.getPath());
        if (portInfo.isFrontendPort()) {
            s_logger.info("Port is front end");
            // We use the export status of the frontend port from the
            // associated target info for the port to determine the
            // operational status for the port.
            VPlexTargetInfo portTargetInfo = portTargetMap.get(portInfo.getPortWwn());
            if (null == portTargetInfo) {
                // CTRL-11698: return unknown if portTargetInfo is null,
                // which indicate no exports are present on this port
                return StoragePort.OperationalStatus.UNKNOWN.name();
            }
            String portExportStatus = portTargetInfo.getExportStatus();
            s_logger.info("Export status is {}", portExportStatus);
            if (VPlexTargetInfo.ExportStatus.ok.name().equals(portExportStatus)) {
                return StoragePort.OperationalStatus.OK.name();
            } else {
                return StoragePort.OperationalStatus.NOT_OK.name();
            }
        } else { 
            // For backend ports we simply use the operation status
            // of the port.
            String portOperationalStatus = portInfo.getOperationalStatus();
            s_logger.info("Operational status is {}", portOperationalStatus);
            if (VPlexPortInfo.OperationalStatus.ok.name().equals(portOperationalStatus)) {
                return StoragePort.OperationalStatus.OK.name();
            } else {
                return StoragePort.OperationalStatus.NOT_OK.name();
            }
        }
    }
}
