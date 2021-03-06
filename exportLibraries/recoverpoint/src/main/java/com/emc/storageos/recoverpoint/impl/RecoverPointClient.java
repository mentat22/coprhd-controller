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
 **/
package com.emc.storageos.recoverpoint.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.fapiclient.ws.ActivationSettingsChangesParams;
import com.emc.fapiclient.ws.ClusterConfiguration;
import com.emc.fapiclient.ws.ClusterRPAsState;
import com.emc.fapiclient.ws.ClusterSANVolumes;
import com.emc.fapiclient.ws.ClusterSettings;
import com.emc.fapiclient.ws.ClusterUID;
import com.emc.fapiclient.ws.ConnectionOutThroughput;
import com.emc.fapiclient.ws.ConsistencyGroupCopyJournal;
import com.emc.fapiclient.ws.ConsistencyGroupCopyRole;
import com.emc.fapiclient.ws.ConsistencyGroupCopySettings;
import com.emc.fapiclient.ws.ConsistencyGroupCopySettingsChangesParam;
import com.emc.fapiclient.ws.ConsistencyGroupCopySettingsParam;
import com.emc.fapiclient.ws.ConsistencyGroupCopyState;
import com.emc.fapiclient.ws.ConsistencyGroupCopyUID;
import com.emc.fapiclient.ws.ConsistencyGroupLinkPolicy;
import com.emc.fapiclient.ws.ConsistencyGroupLinkSettings;
import com.emc.fapiclient.ws.ConsistencyGroupLinkState;
import com.emc.fapiclient.ws.ConsistencyGroupLinkUID;
import com.emc.fapiclient.ws.ConsistencyGroupSettings;
import com.emc.fapiclient.ws.ConsistencyGroupSettingsChangesParam;
import com.emc.fapiclient.ws.ConsistencyGroupState;
import com.emc.fapiclient.ws.ConsistencyGroupUID;
import com.emc.fapiclient.ws.DeviceUID;
import com.emc.fapiclient.ws.FiberChannelInitiatorInformation;
import com.emc.fapiclient.ws.FullConsistencyGroupCopyPolicy;
import com.emc.fapiclient.ws.FullConsistencyGroupLinkPolicy;
import com.emc.fapiclient.ws.FullConsistencyGroupPolicy;
import com.emc.fapiclient.ws.FullRecoverPointSettings;
import com.emc.fapiclient.ws.FunctionalAPIActionFailedException_Exception;
import com.emc.fapiclient.ws.FunctionalAPIImpl;
import com.emc.fapiclient.ws.FunctionalAPIInternalError_Exception;
import com.emc.fapiclient.ws.FunctionalAPIValidationException_Exception;
import com.emc.fapiclient.ws.GlobalCopyUID;
import com.emc.fapiclient.ws.ImageAccessMode;
import com.emc.fapiclient.ws.InitiatorInformation;
import com.emc.fapiclient.ws.JournalVolumeSettings;
import com.emc.fapiclient.ws.LinkAdvancedPolicy;
import com.emc.fapiclient.ws.LinkProtectionPolicy;
import com.emc.fapiclient.ws.MonitoredParameter;
import com.emc.fapiclient.ws.MonitoredParametersStatus;
import com.emc.fapiclient.ws.PipeState;
import com.emc.fapiclient.ws.ProtectionMode;
import com.emc.fapiclient.ws.Quantity;
import com.emc.fapiclient.ws.QuantityType;
import com.emc.fapiclient.ws.RemoteClusterConnectionInformation;
import com.emc.fapiclient.ws.ReplicationSetSettings;
import com.emc.fapiclient.ws.ReplicationSetSettingsChangesParam;
import com.emc.fapiclient.ws.ReplicationSetUID;
import com.emc.fapiclient.ws.RpaConfiguration;
import com.emc.fapiclient.ws.RpaState;
import com.emc.fapiclient.ws.RpaStatistics;
import com.emc.fapiclient.ws.RpaUID;
import com.emc.fapiclient.ws.RpoMinimizationType;
import com.emc.fapiclient.ws.RpoPolicy;
import com.emc.fapiclient.ws.SnapshotGranularity;
import com.emc.fapiclient.ws.SyncReplicationThreshold;
import com.emc.fapiclient.ws.SystemStatistics;
import com.emc.fapiclient.ws.UserVolumeSettings;
import com.emc.fapiclient.ws.UserVolumeSettingsChangesParam;
import com.emc.fapiclient.ws.VolumeInformation;
import com.emc.fapiclient.ws.WanCompression;
import com.emc.storageos.recoverpoint.exceptions.RecoverPointException;
import com.emc.storageos.recoverpoint.objectmodel.RPBookmark;
import com.emc.storageos.recoverpoint.objectmodel.RPConsistencyGroup;
import com.emc.storageos.recoverpoint.objectmodel.RPCopy;
import com.emc.storageos.recoverpoint.objectmodel.RPSite;
import com.emc.storageos.recoverpoint.requests.CGRequestParams;
import com.emc.storageos.recoverpoint.requests.CreateBookmarkRequestParams;
import com.emc.storageos.recoverpoint.requests.CreateCopyParams;
import com.emc.storageos.recoverpoint.requests.CreateRSetParams;
import com.emc.storageos.recoverpoint.requests.CreateVolumeParams;
import com.emc.storageos.recoverpoint.requests.MultiCopyDisableImageRequestParams;
import com.emc.storageos.recoverpoint.requests.MultiCopyEnableImageRequestParams;
import com.emc.storageos.recoverpoint.requests.MultiCopyRestoreImageRequestParams;
import com.emc.storageos.recoverpoint.requests.RPCopyRequestParams;
import com.emc.storageos.recoverpoint.requests.RecreateReplicationSetRequestParams;
import com.emc.storageos.recoverpoint.requests.RecreateReplicationSetRequestParams.CreateRSetVolumeParams;
import com.emc.storageos.recoverpoint.responses.CreateBookmarkResponse;
import com.emc.storageos.recoverpoint.responses.GetBookmarksResponse;
import com.emc.storageos.recoverpoint.responses.MultiCopyDisableImageResponse;
import com.emc.storageos.recoverpoint.responses.MultiCopyEnableImageResponse;
import com.emc.storageos.recoverpoint.responses.MultiCopyRestoreImageResponse;
import com.emc.storageos.recoverpoint.responses.RecoverPointCGResponse;
import com.emc.storageos.recoverpoint.responses.RecoverPointStatisticsResponse;
import com.emc.storageos.recoverpoint.responses.RecoverPointStatisticsResponse.ProtectionSystemParameters;
import com.emc.storageos.recoverpoint.responses.RecoverPointVolumeProtectionInfo;
import com.emc.storageos.recoverpoint.utils.RecoverPointBookmarkManagementUtils;
import com.emc.storageos.recoverpoint.utils.RecoverPointImageManagementUtils;
import com.emc.storageos.recoverpoint.utils.RecoverPointUtils;
import com.emc.storageos.recoverpoint.utils.WwnUtils;

/**
 * Client implementation of the RecoverPoint controller
 *
 */

public class RecoverPointClient {

	// 10s, for RP between RP operations that adds/sets things on the RP. in RP 4.1 SP1 we started encountering an issue which resulted in conflicts 
	// between the RPAs when things ran too quickly.
    private static final int RP_OPERATION_WAIT_TIME = 10000; 

	FunctionalAPIImpl functionalAPI;
    
    public enum RecoverPointReturnCode {
        FAIL,
        SUCCESS,
        PARTIAL_FAIL
    }

    public enum RecoverPointCGState {
        READY,	// All CG copies ready
        PAUSED,	// All CG copies paused
        STOPPED,// All CG copies stopped
        MIXED,	// CG copies are in different states
        GONE	// CG no longer exists
    }

    public enum RecoverPointCGCopyState {
        READY,
        PAUSED,
        STOPPED,
        IMAGE_ENABLED,
    }

    public enum RecoverPointCGCopyType {
        PRODUCTION(0, "production"),
        LOCAL(1, "local"),
        REMOTE(0, "remote");
        
        // copy number is the number of the copy that goes into the GlobalCopyUID
        private final int copyNumber;
        private final String asString;
        
        private RecoverPointCGCopyType(int copyNumber, String str) {
            this.copyNumber = copyNumber;
            this.asString = str;
        }
        
        public int getCopyNumber() {
            return copyNumber;
        }
        
        public boolean isRemote() {
            return this.equals(RecoverPointCGCopyType.REMOTE);
        }
        
        public String toString() {
            return asString;
        }

        /**
         * @return
         */
        public boolean isProduction() {
            return this.equals(RecoverPointCGCopyType.PRODUCTION);
        }
    }

    private static Logger logger = LoggerFactory.getLogger(RecoverPointClient.class);

    private URI _endpoint;
    private String _username;
    private String _password;
    
    /**
     * Default constructor.
     */
    public RecoverPointClient() {
    }

    public RecoverPointClient(URI endpoint, String username, String password) {
        this._endpoint = endpoint;
        this.setUsername(username);
        this.setPassword(password);
    }

    public void setFunctionalAPI(FunctionalAPIImpl functionalAPI) {
    	this.functionalAPI = functionalAPI;
    }

    public URI getEndpoint() {
    	return _endpoint;
    }
    
    /**
     * tests credentials to ensure they are correct, and that the RP site is up and running
     *
     * @return 0 for success
     * @throws RecoverPointException
     **/
    public int ping() throws RecoverPointException {
        String mgmtIPAddress = _endpoint.toASCIIString();
        if (null == mgmtIPAddress) {
            throw RecoverPointException.exceptions.noRecoverPointEndpoint();
        }

        try {
            logger.info("RecoverPoint service: Checking RP access for endpoint: " + _endpoint.toASCIIString());
            functionalAPI.getAccountSettings();
            logger.info("Successful ping for Mgmt IP: " + mgmtIPAddress);
            return 0;
        } catch (Exception e) {
            throw RecoverPointException.exceptions.failedToPingMgmtIP(mgmtIPAddress, getCause(e));
        }
    }

    public Set<String> getClusterTopology() throws RecoverPointException {
    	Set<String> clusterTopology = new HashSet<String>();
    	String mgmtIPAddress = _endpoint.toASCIIString();
    	if (null == mgmtIPAddress) {
    		throw RecoverPointException.exceptions.noRecoverPointEndpoint();
    	}
    	try {
    		Map<Long, String> clusterSiteNameMap = new HashMap<Long, String>();
    		for (ClusterConfiguration clusterInfo : functionalAPI.getFullRecoverPointSettings().getSystemSettings().getGlobalSystemConfiguration().getClustersConfigurations()) {
    			clusterSiteNameMap.put(clusterInfo.getCluster().getId(), clusterInfo.getInternalClusterName());
    		}
    		logger.info("RecoverPoint service: Returning all RP Sites associated with endpoint: " + _endpoint);
    		for (ClusterConfiguration clusterInfo : functionalAPI.getFullRecoverPointSettings().getSystemSettings().getGlobalSystemConfiguration().getClustersConfigurations()) {
    			for (RemoteClusterConnectionInformation connectionInfo : clusterInfo.getRemoteClustersConnectionInformations()) {
    				// Find the internal site name associated with the cluster name 
    				clusterTopology.add(clusterInfo.getInternalClusterName() + " " + clusterSiteNameMap.get(connectionInfo.getCluster().getId()) + " " + connectionInfo.getConnectionType().toString());
    			}
    		}
    		return clusterTopology;
    	} catch (RecoverPointException e) {
    		throw e;
    	} catch (Exception e) {
    		throw RecoverPointException.exceptions.failedToPingMgmtIP(mgmtIPAddress, getCause(e));
    	}
    }
    
    /**
     * Returns a list of sites associated with any given site (or RPA). The user can/should enter a site mgmt IP addr, but they can also
     * support the mgmt IP addr of an RPA. This method will return sites, not RPAs
     *
     * @return set of discovered RP sites
     *
     * @throws RecoverPointException
     **/
    public Set<RPSite> getAssociatedRPSites() throws RecoverPointException {
        String mgmtIPAddress = _endpoint.toASCIIString();
        if (null == mgmtIPAddress) {
            throw RecoverPointException.exceptions.noRecoverPointEndpoint();
        }
        try {
            logger.info("RecoverPoint service: Returning all RP Sites associated with endpoint: " + _endpoint);
            Set<RPSite> returnSiteSet = new HashSet<RPSite>();
            RPSite discoveredSite = null;
            ClusterUID localClusterUID = functionalAPI.getLocalCluster();
            String localSiteName = "unknown";
            FullRecoverPointSettings fullRecoverPointSettings = functionalAPI.getFullRecoverPointSettings();
            SortedSet<String> siteNames = new TreeSet<String>();
            
            for (ClusterSettings siteSettings : fullRecoverPointSettings.getSystemSettings().getClustersSettings()) {
                String siteName = siteSettings.getClusterName();
                siteNames.add(siteName);
            }

            Iterator<String> iter = siteNames.iterator();
            String installationId = "";
            while (iter.hasNext()) { 
            	installationId += (String)iter.next();
            	if (iter.hasNext()) {
            		installationId += "_";
            	}
            }
                
            for (ClusterConfiguration siteSettings : fullRecoverPointSettings.getSystemSettings().getGlobalSystemConfiguration().getClustersConfigurations()) {
            	// TODO: Support multiple management IPs per site
                String siteIP = siteSettings.getManagementIPs().get(0).getIp();
                String siteName = siteSettings.getClusterName();
                if (siteIP == null) {
                    throw RecoverPointException.exceptions.cannotDetermineMgmtIPSite(siteName);
                }
                
                List<RpaConfiguration> rpaList = siteSettings.getRpasConfigurations();
                discoveredSite = new RPSite();
                discoveredSite.setSiteName(siteName);
                discoveredSite.setSiteManagementIPv4(siteIP);
                discoveredSite.setSiteVersion(functionalAPI.getRecoverPointVersion().getVersion());
                discoveredSite.setSiteVolumes(functionalAPI.getClusterSANVolumes(siteSettings.getCluster(), true));
                discoveredSite.setInternalSiteName(siteSettings.getInternalClusterName());
                discoveredSite.setSiteUID(siteSettings.getCluster().getId());
                if (localClusterUID.getId() == siteSettings.getCluster().getId()) {
                    localSiteName = siteName;
                }
                discoveredSite.setNumRPAs(rpaList.size());

                String siteGUID = installationId + ":" + siteSettings.getCluster().getId();
                logger.info("SITE GUID:  " + siteGUID);
                discoveredSite.setSiteGUID(siteGUID);
                if (localClusterUID.getId() == siteSettings.getCluster().getId()) {
                    logger.info("Discovered local site name: " + siteName + ", site IP: " + siteIP + ", RP version: " + discoveredSite.getSiteVersion() + ", num RPAs: "
                            + discoveredSite.getNumRPAs());

                } else {
                    logger.info("Discovered non-local site name: " + siteName + ", site IP: " + siteIP + ", RP version: " + discoveredSite.getSiteVersion()
                            + ", num RPAs: " + discoveredSite.getNumRPAs());
                }
                
                returnSiteSet.add(discoveredSite);
            }

            // 99% of unlicensed RP system errors will be caught here
            if (!RecoverPointUtils.isSiteLicensed(functionalAPI)) {
                throw RecoverPointException.exceptions.siteNotLicensed(localSiteName);
            }

            return returnSiteSet;

        } catch (RecoverPointException e) {
            throw e;
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw RecoverPointException.exceptions.failedToPingMgmtIP(mgmtIPAddress, getCause(e));
        }
    }

    /**
     * Checks to see if the given CG exists.
     * 
     * @param cgName the consistency group name
     * @return true if the consistency group exists, false otherwise
     * @throws RecoverPointException
     */
    public boolean doesCgExist(String cgName) throws RecoverPointException {
        String mgmtIPAddress = _endpoint.toASCIIString();
        
        if (null == mgmtIPAddress) {
            throw RecoverPointException.exceptions.noRecoverPointEndpoint();
        }
        
        try {
            //Make sure the CG name is unique.
            List<ConsistencyGroupUID> allCgs =  functionalAPI.getAllConsistencyGroups();
            for (ConsistencyGroupUID cg : allCgs) {
                ConsistencyGroupSettings settings = functionalAPI.getGroupSettings(cg);
                if (settings.getName().toString().equalsIgnoreCase(cgName)) {
                    return true;
                }
            }  
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw RecoverPointException.exceptions.failedToLookupConsistencyGroup(cgName, getCause(e));
        }
        
        return false;
    }
    
    /**
     * scans all sites until all volumes involved in the Recoverpoint protection are visible
     * @param request
     */
    public void waitForVolumesToBeVisible(CGRequestParams request) {
        scan(request.getCopies(), request.getRsets()); 
    }
    
    /**
     * Updates an existing CG by adding new replication sets.
     *
     * @param request - contains all the information required to create the consistency group
     *
     * @return RecoverPointCGResponse - response as to success or fail of creating the consistency group
     *
     * @throws RecoverPointException
     **/
    public RecoverPointCGResponse addReplicationSetsToCG(CGRequestParams request, boolean metropoint) throws RecoverPointException {
        
        if (null == _endpoint.toASCIIString()) {
            throw RecoverPointException.exceptions.noRecoverPointEndpoint();
        }
        
        List<String> replicationSetsRollback = new ArrayList<String>();
        RecoverPointCGResponse response = new RecoverPointCGResponse();
        List<ConsistencyGroupCopySettings> groupCopySettings = null;
        ConsistencyGroupUID cgUID = null;
        
        try {

            //Make sure the CG name is unique.
            List<ConsistencyGroupUID> allCgs =  functionalAPI.getAllConsistencyGroups();
            for (ConsistencyGroupUID cg : allCgs) {
                ConsistencyGroupSettings settings = functionalAPI.getGroupSettings(cg);
                if (settings.getName().toString().equalsIgnoreCase(request.getCgName())) {
                    cgUID = settings.getGroupUID();
                    groupCopySettings = settings.getGroupCopiesSettings();
                    break;
                }
            }
            
            if (cgUID == null) {
                // The CG does not exist so we cannot add replication sets
                throw RecoverPointException.exceptions.failedToAddReplicationSetCgDoesNotExist(request.getCgName());
            }
            
            response.setCgId(cgUID.getId());
            
            // caches site names to cluster id's to reduce calls to fapi for the same information
            Map<String, ClusterUID> clusterIdCache = new HashMap<String, ClusterUID>();
            // prodSites is used for logging and to determine if a non-production copy is local or remote
            List<ClusterUID> prodSites = new ArrayList<ClusterUID>();
            
            // used to set the copy uid on the rset volume when adding rsets
            Map<Long, ConsistencyGroupCopyUID> productionCopiesUID = new HashMap<Long, ConsistencyGroupCopyUID>();
            Map<Long, ConsistencyGroupCopyUID> nonProductionCopiesUID = new HashMap<Long, ConsistencyGroupCopyUID>();
            
            for (ConsistencyGroupCopySettings copySettings : groupCopySettings) {
                GlobalCopyUID globalCopyUID = copySettings.getCopyUID().getGlobalCopyUID();
                if (ConsistencyGroupCopyRole.ACTIVE.equals(copySettings.getRoleInfo().getRole()) || 
                        ConsistencyGroupCopyRole.TEMPORARY_ACTIVE.equals(copySettings.getRoleInfo().getRole())) {
                    productionCopiesUID.put(Long.valueOf(globalCopyUID.getClusterUID().getId()), copySettings.getCopyUID());
                    prodSites.add(globalCopyUID.getClusterUID());
                } else {
                    nonProductionCopiesUID.put(Long.valueOf(globalCopyUID.getClusterUID().getId()), copySettings.getCopyUID());
                }
            }
            
            StringBuffer sb = new StringBuffer();
            for (ClusterUID prodSite : prodSites) {
                sb.append(prodSite.getId());
                sb.append(" ");
            }
            
            logger.info("RecoverPointClient: Adding replication set(s) to consistency group " + request.getCgName() + " for endpoint: " + _endpoint.toASCIIString() + " and production sites: " + sb.toString());
            
            ConsistencyGroupSettingsChangesParam cgSettingsParam = configureCGSettingsChangeParams(request, cgUID, prodSites, clusterIdCache,
                    productionCopiesUID, nonProductionCopiesUID);
            
            logger.info("Adding journals and rsets for CG " + request.getCgName());
            functionalAPI.setConsistencyGroupSettings(cgSettingsParam);
            
            // Sometimes the CG is still active when we start polling for link state and then
            // starts initializing some time afterwards. Adding this sleep to make sure the CG 
            // starts initializing before we check the link states
            logger.info("Sleeping for 10s after enabling the consistency group link");
            try {
                Thread.sleep(RP_OPERATION_WAIT_TIME);
            } catch (InterruptedException e) {  
                Thread.currentThread().interrupt();
            }
            
            logger.info("Waiting for links to become active for CG " + request.getCgName());
            (new RecoverPointImageManagementUtils()).waitForCGLinkState(functionalAPI, cgUID, null, PipeState.ACTIVE);
            logger.info(String.format("Replication sets have been added to consistency group %s.", request.getCgName()));
            
            response.setReturnCode(RecoverPointReturnCode.SUCCESS);
            
            return response;
            
        } catch (Exception e) {
            for (CreateRSetParams rsetParam : request.getRsets()) {
                replicationSetsRollback.add(rsetParam.getName());
            }
            cleanupReplicationSets(functionalAPI, cgUID, replicationSetsRollback);
            throw RecoverPointException.exceptions.failedToAddReplicationSetToConsistencyGroup(request.getCgName(), getCause(e));
        }
    }
    
    /**
     * Creates a consistency group
     *
     * @param request - contains all the information required to create the consistency group
     *
     * @return CreateCGResponse - response as to success or fail of creating the consistency group
     *
     * @throws RecoverPointException
     **/
    public RecoverPointCGResponse createCG(CGRequestParams request, boolean metropoint) throws RecoverPointException {
        
        if (null == _endpoint.toASCIIString()) {
            throw RecoverPointException.exceptions.noRecoverPointEndpoint();
        }
        
        RecoverPointCGResponse response = new RecoverPointCGResponse();
        ConsistencyGroupUID cgUID = null;
        try {

            //Make sure the CG name is unique.
            int cgSuffix=0;
            String cgName = request.getCgName();
            while (doesCgExist(request.getCgName())) {
                request.setCgName(String.format("%s-%d", cgName, ++cgSuffix));
            }
            
            // caches site names to cluster id's to reduce calls to fapi for the same information
            Map<String, ClusterUID> clusterIdCache = new HashMap<String, ClusterUID>();

            // prodSites is used for logging and to determine if a non-production copy is local or remote
            List<ClusterUID> prodSites = getProdSites(request, clusterIdCache);
            StringBuffer sb = new StringBuffer();
            for (ClusterUID prodSite : prodSites) {
                sb.append(prodSite.getId());
                sb.append(" ");
            }
            
            logger.info("RecoverPointClient: Creating recoverPoint consistency group " + request.getCgName() + " for endpoint: " + _endpoint.toASCIIString() + " and production sites: " + sb.toString()); 
            
            // used to set the copy uid on the rset volume when adding rsets
            Map<Long, ConsistencyGroupCopyUID> productionCopiesUID = new HashMap<Long, ConsistencyGroupCopyUID>();
            Map<Long, ConsistencyGroupCopyUID> nonProductionCopiesUID = new HashMap<Long, ConsistencyGroupCopyUID>();
            
            FullConsistencyGroupPolicy fullConsistencyGroupPolicy = configureCGPolicy(request, prodSites, clusterIdCache,
                    productionCopiesUID, nonProductionCopiesUID);

            // create the CG with copies
            logger.info("Adding cg, copies and links for CG: " + request.getCgName());
            functionalAPI.validateAddConsistencyGroupAndCopies(fullConsistencyGroupPolicy);
            cgUID = functionalAPI.addConsistencyGroupAndCopies(fullConsistencyGroupPolicy);
            response.setCgId(cgUID.getId());
            
            ConsistencyGroupSettingsChangesParam cgSettingsParam = configureCGSettingsChangeParams(request, cgUID, prodSites, clusterIdCache,
                    productionCopiesUID, nonProductionCopiesUID);
            
            logger.info("Adding journals and rsets for CG " + request.getCgName());
            functionalAPI.validateSetConsistencyGroupSettings(cgSettingsParam);
            functionalAPI.setConsistencyGroupSettings(cgSettingsParam);
            
            logger.info("Waiting for links to become active for CG " + request.getCgName());
            (new RecoverPointImageManagementUtils()).waitForCGLinkState(functionalAPI, cgUID, null, PipeState.ACTIVE);
            logger.info(String.format("Consistency group %s has been created.", request.getCgName()));
            
            response.setReturnCode(RecoverPointReturnCode.SUCCESS);
            
            return response;
        } catch (Exception e) {
            if (cgUID != null) {
                try {
                    RecoverPointUtils.cleanupCG (functionalAPI, cgUID);  
                } catch (Exception e1) {
                    logger.error("Error removing CG " + request.getCgName() + " after create CG failure");
                    logger.error(e1.getMessage(), e1);
                }
            }
            throw RecoverPointException.exceptions.failedToCreateConsistencyGroup(request.getCgName(), getCause(e));
        } 
        
    }

    /**
     * @param request
     * @param clusterIdCache
     * @return
     * @throws FunctionalAPIActionFailedException_Exception
     * @throws FunctionalAPIInternalError_Exception
     */
    private List<ClusterUID> getProdSites(CGRequestParams request, Map<String, ClusterUID> clusterIdCache)
            throws FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception {
        List<ClusterUID> prodSites = new ArrayList<ClusterUID>();
        for (CreateVolumeParams volume : request.getRsets().get(0).getVolumes()) {
            if (volume.isProduction()) {
                ClusterUID prodSite = getRPSiteID(volume.getInternalSiteName(), clusterIdCache);
                prodSites.add(prodSite);
            }
        }
        return prodSites;
    }
    
    /**
     * returns cluster uid for a copy
     * @param copyParam
     * @param clusterIdCache
     * @return
     * @throws FunctionalAPIActionFailedException_Exception
     * @throws FunctionalAPIInternalError_Exception
     */
    private ClusterUID getClusterUid(CreateCopyParams copyParam, Map<String, ClusterUID> clusterIdCache) throws FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception {
        if (copyParam.getJournals() != null && !copyParam.getJournals().isEmpty()) {
            return getRPSiteID(copyParam.getJournals().iterator().next().getInternalSiteName(), clusterIdCache);
        }
        return null;
    }
    
    /**
     * get copy type (local, remote, production)
     * @param copyParam
     * @param prodSites
     * @param clusterUID
     * @return
     */
    private RecoverPointCGCopyType getCopyType(CreateCopyParams copyParam, List<ClusterUID> prodSites, ClusterUID clusterUID) {
        if (copyParam.getJournals() != null && !copyParam.getJournals().isEmpty()) {
            CreateVolumeParams volume = copyParam.getJournals().iterator().next();
            if (volume.isProduction()) {
                return RecoverPointCGCopyType.PRODUCTION;
            } else {
                if (isLocalCopy(prodSites, clusterUID)) {                
                    return RecoverPointCGCopyType.LOCAL;
                } else {
                    return RecoverPointCGCopyType.REMOTE;
                }                   
            }
        }
        return null;
    }
    
    /**
     * construct a CG copy UID
     * @param clusterUID
     * @param copyType
     * @param cgUID
     * @return
     */
    private ConsistencyGroupCopyUID getCGCopyUid(ClusterUID clusterUID, RecoverPointCGCopyType copyType, ConsistencyGroupUID cgUID) {
        ConsistencyGroupCopyUID cgCopyUID = new ConsistencyGroupCopyUID();
        GlobalCopyUID globalCopyUID = new GlobalCopyUID();              
        globalCopyUID.setClusterUID(clusterUID);
        globalCopyUID.setCopyUID(copyType.getCopyNumber());
        cgCopyUID.setGlobalCopyUID(globalCopyUID);
        cgCopyUID.setGroupUID(cgUID);
        return cgCopyUID;
    }
    
    /**
     * @param request
     * @param prodSites
     * @param clusterIdCache
     * @return
     * @throws FunctionalAPIInternalError_Exception 
     * @throws FunctionalAPIActionFailedException_Exception 
     */
    private ConsistencyGroupSettingsChangesParam configureCGSettingsChangeParams(CGRequestParams request, ConsistencyGroupUID cgUID, List<ClusterUID> prodSites,
            Map<String, ClusterUID> clusterIdCache, Map<Long, ConsistencyGroupCopyUID> productionCopiesUID,
            Map<Long, ConsistencyGroupCopyUID> nonProductionCopiesUID) throws FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception {
        
        Set<RPSite> allSites = getAssociatedRPSites();
        
        // used to set journal volumes and RSets after the CG is created
        ConsistencyGroupSettingsChangesParam cgSettingsParam = new ConsistencyGroupSettingsChangesParam();
        ActivationSettingsChangesParams cgActivationSettings = new ActivationSettingsChangesParams();
        cgActivationSettings.setEnable(true);
        cgActivationSettings.setStartTransfer(true);
        cgSettingsParam.setActivationParams(cgActivationSettings);
        cgSettingsParam.setGroupUID(cgUID);
        
        for (CreateCopyParams copyParam : request.getCopies()){
            
            ClusterUID clusterUID = getClusterUid(copyParam, clusterIdCache);
            if (clusterUID != null) {
                
                RecoverPointCGCopyType copyType = getCopyType(copyParam, prodSites, clusterUID);
            
                if (copyType != null) {
                    
                    ConsistencyGroupCopyUID cgCopyUID = getCGCopyUid(clusterUID, copyType, cgUID);
                    
                    // set up journal params
                    ConsistencyGroupCopySettingsChangesParam copySettingsParam = new ConsistencyGroupCopySettingsChangesParam();
                    copySettingsParam.setCopyUID(cgCopyUID);
                    
                    ActivationSettingsChangesParams copyActivationSettings = new ActivationSettingsChangesParams();
                    copyActivationSettings.setEnable(true);
                    copyActivationSettings.setStartTransfer(true);
                    copySettingsParam.setActivationParams(copyActivationSettings);
                    
                    for (CreateVolumeParams journalVolume : copyParam.getJournals()) {
                        logger.info("Configuring Journal : \n" + journalVolume.toString() + "\n for copy: " + copyParam.getName() +
                                "; CG " + request.getCgName());
                        copySettingsParam.getNewJournalVolumes().add(RecoverPointUtils.getDeviceID(allSites, journalVolume.getWwn()));
                    }
                    
                    cgSettingsParam.getCopiesChanges().add(copySettingsParam);
                    
                } else {
                    logger.warn("No journal volumes specified for CG: " + copyParam.getName());
                }
            } else {
                logger.warn("No journal volumes specified for CG: " + copyParam.getName());
            }
        }
        
        String previousProdCopyName = null;

        // configure replication sets                   
        for (CreateRSetParams rsetParam : request.getRsets()) {  
            
            logger.info("Configuring replication set: " + rsetParam.toString() + " for cg " + request.getCgName());
            
            ReplicationSetSettingsChangesParam repSetSettings = new ReplicationSetSettingsChangesParam();
            repSetSettings.setName(rsetParam.getName());
            repSetSettings.setShouldAttachAsClean(false);
            
            Set<String> sourceWWNsInRset = new HashSet<String>();
            for (CreateVolumeParams volume : rsetParam.getVolumes()) {
                
                UserVolumeSettingsChangesParam volSettings = new UserVolumeSettingsChangesParam();
                volSettings.setNewVolumeID(RecoverPointUtils.getDeviceID(allSites, volume.getWwn()));
                
                ClusterUID volSiteId = getRPSiteID(volume.getInternalSiteName(), clusterIdCache);
                
                if (volume.isProduction()) {
                    // for metropoint, the same production volume will appear twice; we only want to add it once
                    if (sourceWWNsInRset.contains(volume.getWwn())) {
                        continue;
                    }
                    if (previousProdCopyName == null) {
                        previousProdCopyName = volume.getRpCopyName();
                    } else if (!previousProdCopyName.equals(volume.getRpCopyName())) {
                        logger.info(String.format("will not add rset for volume %s to prod copy %s because another rset has already been added to prod copy %s", 
                                rsetParam.getName(), volume.getRpCopyName(), previousProdCopyName));
                        continue;
                    }
                    sourceWWNsInRset.add(volume.getWwn());
                    logger.info("Configuring production copy volume : \n" + volume.toString());  
                    ConsistencyGroupCopyUID copyUID = productionCopiesUID.get(Long.valueOf(volSiteId.getId()));
                    volSettings.setCopyUID(copyUID);
                } else {
                    logger.info("Configuring non-production copy volume : \n" + volume.toString());  
                    ConsistencyGroupCopyUID copyUID = nonProductionCopiesUID.get(Long.valueOf(volSiteId.getId()));
                    volSettings.setCopyUID(copyUID);
                }
                volSettings.getCopyUID().setGroupUID(cgUID);
                repSetSettings.getVolumesChanges().add(volSettings);
            }
            cgSettingsParam.getReplicationSetsChanges().add(repSetSettings);
        }
        
        return cgSettingsParam;
    }

    /**
     * @param request
     * @return
     * @throws FunctionalAPIInternalError_Exception 
     * @throws FunctionalAPIActionFailedException_Exception 
     */
    private FullConsistencyGroupPolicy configureCGPolicy(CGRequestParams request, List<ClusterUID> prodSites, Map<String, ClusterUID> clusterIdCache, Map<Long, ConsistencyGroupCopyUID> productionCopiesUID,
            Map<Long, ConsistencyGroupCopyUID> nonProductionCopiesUID) throws FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception {
            
            logger.info("Requesting preferred RPA for cluster " + prodSites.get(0).getId());
            RpaUID preferredRPA = RecoverPointUtils.getPreferredRPAForNewCG(functionalAPI, prodSites.get(0));
            logger.info("Preferred RPA for cluster " + preferredRPA.getClusterUID().getId() + " is RPA " + preferredRPA.getRpaNumber());
            
            // used to create the CG; contains CG settings, copies and links
            FullConsistencyGroupPolicy fullConsistencyGroupPolicy = new FullConsistencyGroupPolicy();
            fullConsistencyGroupPolicy.setGroupName(request.getCgName());
            fullConsistencyGroupPolicy.setGroupPolicy(functionalAPI.getDefaultConsistencyGroupPolicy());
            fullConsistencyGroupPolicy.getGroupPolicy().setPrimaryRPANumber(preferredRPA.getRpaNumber());
            
            for (CreateCopyParams copyParam : request.getCopies()){
                
                ClusterUID clusterUID = getClusterUid(copyParam, clusterIdCache);
                if (clusterUID != null) {
                    
                    RecoverPointCGCopyType copyType = getCopyType(copyParam, prodSites, clusterUID);
                
                    if (copyType != null) {
                    
                        logger.info(String.format("Configuring %s copy %s for CG %s", copyType.toString(), copyParam.getName(), request.getCgName()));
                        
                        ConsistencyGroupCopyUID cgCopyUID = getCGCopyUid(clusterUID, copyType, null);
                        
                        FullConsistencyGroupCopyPolicy copyPolicy = new FullConsistencyGroupCopyPolicy();
                        copyPolicy.setCopyName(copyParam.getName());
                        copyPolicy.setCopyPolicy(functionalAPI.getDefaultConsistencyGroupCopyPolicy());
                        copyPolicy.setCopyUID(cgCopyUID);
                        
                        fullConsistencyGroupPolicy.getCopiesPolicies().add(copyPolicy);
                        
                        if (copyType.isProduction()) {
                            fullConsistencyGroupPolicy.getProductionCopies().add(copyPolicy.getCopyUID());
                            productionCopiesUID.put(Long.valueOf(clusterUID.getId()), copyPolicy.getCopyUID());
                        } else {
                            nonProductionCopiesUID.put(Long.valueOf(clusterUID.getId()), copyPolicy.getCopyUID());
                        }
                        
                    } else {
                        logger.error("No journal volumes specified for create CG: " + copyParam.getName());
                    }
                } else {
                    logger.error("No journal volumes specified for create CG: " + copyParam.getName());
                }
            }
            
            // set links between production and remote/local copies
            configureLinkPolicies(fullConsistencyGroupPolicy, request);
            
            return fullConsistencyGroupPolicy;
    }
    
    /**
     * configure links between each production and each local and/or remote copy in a new CG
     * configured links are added to fullConsistencyGroupPolicy
     * @param fullConsistencyGroupPolicy cg policy with copies populated
     * @param copyType prod, local or remote
     * @param request create cg request used for copy mode and rpo
     */
    private void configureLinkPolicies(FullConsistencyGroupPolicy fullConsistencyGroupPolicy, CGRequestParams request) {
        for (FullConsistencyGroupCopyPolicy copyPolicy : fullConsistencyGroupPolicy.getCopiesPolicies()) {
            
            if (!fullConsistencyGroupPolicy.getProductionCopies().contains(copyPolicy.getCopyUID())) {
                
                for (ConsistencyGroupCopyUID productionCopyUID : fullConsistencyGroupPolicy.getProductionCopies()) {
                        
                    logger.info("Configuring link policy between production copy and local or remote copy on cluster(id) : " + copyPolicy.getCopyUID().getGlobalCopyUID().getClusterUID().getId());
                    
                    ConsistencyGroupLinkUID linkUid = new ConsistencyGroupLinkUID();
                    linkUid.setFirstCopy(productionCopyUID.getGlobalCopyUID());
                    linkUid.setSecondCopy(copyPolicy.getCopyUID().getGlobalCopyUID());
                    
                    boolean isLocal = productionCopyUID.getGlobalCopyUID().getClusterUID().equals(copyPolicy.getCopyUID().getGlobalCopyUID().getClusterUID());
                    RecoverPointCGCopyType copyType = isLocal ? RecoverPointCGCopyType.LOCAL : RecoverPointCGCopyType.REMOTE;
                    
                    ConsistencyGroupLinkPolicy linkPolicy = createLinkPolicy(copyType, request.cgPolicy.copyMode, request.cgPolicy.rpoType, request.cgPolicy.rpoValue);
                    ConsistencyGroupLinkSettings linkSettings = new ConsistencyGroupLinkSettings();
                    linkSettings.setGroupLinkUID(linkUid);
                    linkSettings.setLinkPolicy(linkPolicy);
                    linkSettings.setLocalLink(isLocal);
                    linkSettings.setTransferEnabled(false);
                    
                    FullConsistencyGroupLinkPolicy fullLinkPolicy = new FullConsistencyGroupLinkPolicy();
                    fullLinkPolicy.setLinkPolicy(linkPolicy);
                    fullLinkPolicy.setLinkUID(linkUid);
                   
                    fullConsistencyGroupPolicy.getLinksPolicies().add(fullLinkPolicy);
                }
            }
        }
        
    }
  
    /**
     * @param prodSites - List of production sites
     * @param siteID - current site that is being validated for if its on the same site as a production copy
     * @return boolean
     */
    private boolean isLocalCopy(List<ClusterUID> prodSites, ClusterUID siteID) {
    	for (ClusterUID prodSite : prodSites) {
    		if (prodSite.getId() == siteID.getId()){
    			return true;
    		}
    	}
    	return false;
    }
      
    /**
     * Get the root cause of the failure, if available
     * @param e the base exception
     * @return the exception with valuable information in it
     */
    public static Throwable getCause(Exception e) {
		if (e.getCause() != null) {
			return e.getCause();
		}
		return e;
	}

	/**
     * Walk through the journals and source/target volumes to see where the WWNS lie.
     * 
     * @param copies
     * @param rSets
     * @return set of discovered RP sites
     */
    private Set<RPSite> scan(List<CreateCopyParams> copies, List<CreateRSetParams> rSets) {
        // Setting the MAX_SCAN_WAIT_TOTAL_TRIES = 240
    	// so that we loop for a max of 1 hour (240 * 15000 = 1 hour)
    	final int MAX_SCAN_WAIT_TOTAL_TRIES = 240;
        final int MAX_SCAN_WAIT_RETRY_MILLISECONDS = 15000;
        
        int rescanTries = MAX_SCAN_WAIT_TOTAL_TRIES;
        boolean needsScan = true; // set to true to stay in the loop
        
        Set<RPSite> allSites = null;
        
        while (needsScan && rescanTries-- > 0) {
            // Reset scan flag.  If something goes wrong, it'll get set to true.
            needsScan = false;
            
            if ((MAX_SCAN_WAIT_TOTAL_TRIES - rescanTries) != 1) {
                logger.info("RecoverPointClient: Briefly sleeping to accomodate export group latencies (Attempt #{} / {})", MAX_SCAN_WAIT_TOTAL_TRIES - rescanTries, MAX_SCAN_WAIT_TOTAL_TRIES);
                try { Thread.sleep(MAX_SCAN_WAIT_RETRY_MILLISECONDS); } catch (InterruptedException e1) { Thread.currentThread().interrupt(); }
            }

            // Rescan the san
            logger.info("RecoverPointClient: Rescanning san volumes for endpoint: " + _endpoint.toASCIIString());
            try {
                functionalAPI.rescanSANVolumesInAllClusters(true);
            } catch (FunctionalAPIActionFailedException_Exception e) {
                logger.warn("Exception in call to rescanSANVolumesInAllSites");
            } catch (FunctionalAPIInternalError_Exception e) {
                logger.warn("Exception in call to rescanSANVolumesInAllSites");
            }

            // Get all of the volumes
            allSites = getAssociatedRPSites();

            //
            // Walk through the journals volumes to see where our WWNs lie
            //
            for (CreateCopyParams copy : copies) {
                for (CreateVolumeParams volumeParam : copy.getJournals()) {
                    boolean found = false;
                    for (RPSite rpSite : allSites) {
                        ClusterSANVolumes siteSANVolumes = rpSite.getSiteVolumes();
                        for (VolumeInformation volume : siteSANVolumes.getVolumesInformations()) {
                            String siteVolUID = RecoverPointUtils.getGuidBufferAsString(volume.getRawUids(), false);
                            if (siteVolUID.equalsIgnoreCase(volumeParam.getWwn())) {
                                logger.info("Found site and volume ID for journal: " + volumeParam.getWwn() + " for copy: " + copy.getName());
                                found = true;
                                break;
                            }
                        }
                        if (found) {
                            break;
                        }
                    }
                    if (!found) {
                        needsScan = true; // set that we still need to scan.

                        if (rescanTries <= 0) {
                        	 for (RPSite rpSite : allSites) {
                                 logger.error(String.format("Could not find voume %s on any RP site", volumeParam.getWwn()));
                                 ClusterSANVolumes siteSANVolumes = rpSite.getSiteVolumes();
                                 for (VolumeInformation volume : siteSANVolumes.getVolumesInformations()) {
                                     logger.info(String.format("RP Site: %s; volume from RP: %s", rpSite.getSiteName(), RecoverPointUtils.getGuidBufferAsString(volume.getRawUids(), false)));
                                 }
                             }
                            throw RecoverPointException.exceptions
                            .couldNotFindSiteAndVolumeIDForJournal(volumeParam.getWwn(), copy.getName(),
                                    volumeParam.getInternalSiteName());
                        }
                    }
                }
            }

            //
            // Walk through the source/target volumes to see where our WWNs lie
            //
            for (CreateRSetParams rset : rSets) {
                for (CreateVolumeParams volumeParam : rset.getVolumes()) {
                    boolean found = false;
                    for (RPSite rpSite : allSites) {
                        ClusterSANVolumes siteSANVolumes = rpSite.getSiteVolumes();
                        for (VolumeInformation volume : siteSANVolumes.getVolumesInformations()) {
                            String siteVolUID = RecoverPointUtils.getGuidBufferAsString(volume.getRawUids(), false);
                            if (siteVolUID.equalsIgnoreCase(volumeParam.getWwn())) {                                
                                logger.info(String.format("Found site and volume ID for volume: %s for replication set: %s on site: %s (%s)", volumeParam.getWwn(), rset.getName(), rpSite.getSiteName(), volumeParam.getInternalSiteName()));
                                found = true;
                                break;
                            }
                        }
                        if (found) {
                            break;
                        }
                    }
                    if (!found) {
                        needsScan = true; // set that we still need to scan

                        if (rescanTries <= 0) {
                        	for (RPSite rpSite : allSites) {
                                logger.error(String.format("Could not find voume %s on any RP site", volumeParam.getWwn()));
                                ClusterSANVolumes siteSANVolumes = rpSite.getSiteVolumes();
                                for (VolumeInformation volume : siteSANVolumes.getVolumesInformations()) {
                                    logger.info(String.format("RP Site: %s; volume from RP: %s", rpSite.getSiteName(), RecoverPointUtils.getGuidBufferAsString(volume.getRawUids(), false)));
                                }
                            }
                            throw RecoverPointException.exceptions
                            .couldNotFindSiteAndVolumeIDForVolume(volumeParam.getWwn(), rset.getName(),
                                    volumeParam.getInternalSiteName());
                        }
                    }
                }
            }
        }
        
        return allSites;
    }    
    
    /**
     * Convenience method to set the link policy.
     * 
     * @param remote whether to set the "protect over wan" to true
     * @param prodCopyUID production copy id
     * @param targetCopyUID target copy id
     * @param cgUID cg id
     * @throws FunctionalAPIActionFailedException_Exception
     * @throws FunctionalAPIInternalError_Exception
     */
    private void setLinkPolicy(boolean remote, ConsistencyGroupCopyUID prodCopyUID, 
    		ConsistencyGroupCopyUID targetCopyUID, ConsistencyGroupUID cgUID) throws FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception {
        ConsistencyGroupLinkUID groupLink = new ConsistencyGroupLinkUID();
        
        ConsistencyGroupLinkPolicy linkPolicy = new ConsistencyGroupLinkPolicy();
        linkPolicy.setAdvancedPolicy(new LinkAdvancedPolicy());
        linkPolicy.getAdvancedPolicy().setPerformLongInitialization(true);
        linkPolicy.getAdvancedPolicy().setSnapshotGranularity(SnapshotGranularity.FIXED_PER_SECOND);
        linkPolicy.setProtectionPolicy(new LinkProtectionPolicy());
        linkPolicy.getProtectionPolicy().setBandwidthLimit(0.0);
        linkPolicy.getProtectionPolicy().setCompression(WanCompression.NONE);
        linkPolicy.getProtectionPolicy().setDeduplication(false);
        linkPolicy.getProtectionPolicy().setMeasureLagToTargetRPA(true);
        linkPolicy.getProtectionPolicy().setProtectionType(ProtectionMode.ASYNCHRONOUS);
        linkPolicy.getProtectionPolicy().setReplicatingOverWAN(remote);
        linkPolicy.getProtectionPolicy().setRpoPolicy(new RpoPolicy());
        linkPolicy.getProtectionPolicy().getRpoPolicy().setAllowRegulation(false);
        linkPolicy.getProtectionPolicy().getRpoPolicy().setMaximumAllowedLag(getQuantity(QuantityType.MICROSECONDS, 25000000));
        linkPolicy.getProtectionPolicy().getRpoPolicy().setMinimizationType(RpoMinimizationType.IRRELEVANT);
        linkPolicy.getProtectionPolicy().setSyncReplicationLatencyThresholds(new SyncReplicationThreshold());
        linkPolicy.getProtectionPolicy().getSyncReplicationLatencyThresholds().setResumeSyncReplicationBelow(getQuantity(QuantityType.MICROSECONDS, 3000));
        linkPolicy.getProtectionPolicy().getSyncReplicationLatencyThresholds().setStartAsyncReplicationAbove(getQuantity(QuantityType.MICROSECONDS, 5000));
        linkPolicy.getProtectionPolicy().getSyncReplicationLatencyThresholds().setThresholdEnabled(false);
        linkPolicy.getProtectionPolicy().setSyncReplicationThroughputThresholds(new SyncReplicationThreshold());
        linkPolicy.getProtectionPolicy().getSyncReplicationThroughputThresholds().setResumeSyncReplicationBelow(getQuantity(QuantityType.KB, 35000));
        linkPolicy.getProtectionPolicy().getSyncReplicationThroughputThresholds().setStartAsyncReplicationAbove(getQuantity(QuantityType.KB, 45000));
        linkPolicy.getProtectionPolicy().getSyncReplicationThroughputThresholds().setThresholdEnabled(false);
        linkPolicy.getProtectionPolicy().setWeight(1);
		groupLink.setFirstCopy(prodCopyUID.getGlobalCopyUID());
		groupLink.setSecondCopy(targetCopyUID.getGlobalCopyUID());
		groupLink.setGroupUID(cgUID);
		functionalAPI.addConsistencyGroupLink(groupLink, linkPolicy);		
		logger.info("Sleeping for 10s after enabling the consistency group link");
		try {
			Thread.sleep(RP_OPERATION_WAIT_TIME);
		} catch (InterruptedException e) {	
			//do nothing.
			Thread.currentThread().interrupt();
		}
    }
    
    /**
     * Convenience method for creating a link policy object
     * 
     * @param remote whether to set the "protect over wan" to true
     */
    private ConsistencyGroupLinkPolicy createLinkPolicy(RecoverPointCGCopyType copyType, String copyMode, String rpoType, Long rpoValue) {
        
        ConsistencyGroupLinkPolicy linkPolicy = new ConsistencyGroupLinkPolicy();
        linkPolicy.setAdvancedPolicy(new LinkAdvancedPolicy());
        linkPolicy.getAdvancedPolicy().setPerformLongInitialization(true);
        linkPolicy.getAdvancedPolicy().setSnapshotGranularity(SnapshotGranularity.FIXED_PER_SECOND);
        linkPolicy.setProtectionPolicy(new LinkProtectionPolicy());
        linkPolicy.getProtectionPolicy().setBandwidthLimit(0.0);
        linkPolicy.getProtectionPolicy().setCompression(WanCompression.NONE);
        linkPolicy.getProtectionPolicy().setDeduplication(false);
        linkPolicy.getProtectionPolicy().setMeasureLagToTargetRPA(true);
        linkPolicy.getProtectionPolicy().setProtectionType(ProtectionMode.ASYNCHRONOUS);
        linkPolicy.getProtectionPolicy().setReplicatingOverWAN(copyType.isRemote());
        linkPolicy.getProtectionPolicy().setRpoPolicy(new RpoPolicy());
        linkPolicy.getProtectionPolicy().getRpoPolicy().setAllowRegulation(false);
        linkPolicy.getProtectionPolicy().getRpoPolicy().setMaximumAllowedLag(getQuantity(QuantityType.MICROSECONDS, 25000000));
        linkPolicy.getProtectionPolicy().getRpoPolicy().setMinimizationType(RpoMinimizationType.IRRELEVANT);
        linkPolicy.getProtectionPolicy().setSyncReplicationLatencyThresholds(new SyncReplicationThreshold());
        linkPolicy.getProtectionPolicy().getSyncReplicationLatencyThresholds().setResumeSyncReplicationBelow(getQuantity(QuantityType.MICROSECONDS, 3000));
        linkPolicy.getProtectionPolicy().getSyncReplicationLatencyThresholds().setStartAsyncReplicationAbove(getQuantity(QuantityType.MICROSECONDS, 5000));
        linkPolicy.getProtectionPolicy().getSyncReplicationLatencyThresholds().setThresholdEnabled(false);
        linkPolicy.getProtectionPolicy().setSyncReplicationThroughputThresholds(new SyncReplicationThreshold());
        linkPolicy.getProtectionPolicy().getSyncReplicationThroughputThresholds().setResumeSyncReplicationBelow(getQuantity(QuantityType.KB, 35000));
        linkPolicy.getProtectionPolicy().getSyncReplicationThroughputThresholds().setStartAsyncReplicationAbove(getQuantity(QuantityType.KB, 45000));
        linkPolicy.getProtectionPolicy().getSyncReplicationThroughputThresholds().setThresholdEnabled(false);
        linkPolicy.getProtectionPolicy().setWeight(1);
        
        LinkProtectionPolicy linkProtectionPolicy = linkPolicy.getProtectionPolicy();
        if (copyMode != null) {
            logger.info("Setting CG policy of: " + copyMode);
            ProtectionMode protectionMode = ProtectionMode.valueOf(copyMode);
            if (protectionMode == null) {
                // Default to ASYNCHRONOUS
                protectionMode = ProtectionMode.ASYNCHRONOUS;
            }
            linkProtectionPolicy.setProtectionType(protectionMode);
        }
        RpoPolicy rpoPolicy = linkProtectionPolicy.getRpoPolicy();
        if (rpoValue != null && rpoType != null) {
            logger.info("Setting CG RPO policy of: " + rpoValue.toString() + " " + rpoType);
            Quantity rpoQuantity = new Quantity();
            QuantityType quantityType = QuantityType.valueOf(rpoType); 
            rpoQuantity.setType(quantityType);
            rpoQuantity.setValue(rpoValue);
            rpoPolicy.setMaximumAllowedLag(rpoQuantity);
        } else if ((rpoValue == null && rpoType != null) ||
                   (rpoValue != null && rpoType == null)) {
            logger.warn("RPO Policy specified only one of value and type, both need to be specified for RPO policy to be applied.  Ignoring RPO policy.");
        }
        linkProtectionPolicy.setRpoPolicy(rpoPolicy);
        linkPolicy.setProtectionPolicy(linkProtectionPolicy);
        
        return linkPolicy;
        
    }
    
    /**
     * Rollback the replication sets for a CG.
     * 
     * @param functionalAPI
     * @param cgUID
     * @param replicationSetsRollback
     */
    private void cleanupReplicationSets(FunctionalAPIImpl functionalAPI, ConsistencyGroupUID cgUID, 
            List<String> replicationSetsRollback) {
        logger.info("Rolling back any replication sets that were created.");
        // Do not delete the CG because it was already existed before we started messing around with it.
        // Remove the replication sets that were created.  No need to worry about copies since if the
        // CG was existing, we are re-using the copies (journals).
        if (replicationSetsRollback != null) {
            for (String replicationSetName : replicationSetsRollback) {
                try {
                    ReplicationSetUID replicationSetUID = getReplicationSetUID(functionalAPI, cgUID, replicationSetName);
                    if (replicationSetUID == null) {
                        // If we cannot find the replication set, do not fail.  Rollback what we can.
                        logger.error("Cannot rollback replication set.  Unable to find replication set UID for " + replicationSetName);
                        continue;
                    }
                    logger.info("Removing replication set " + replicationSetName);
                    functionalAPI.removeReplicationSet(cgUID, replicationSetUID);
                } catch (FunctionalAPIActionFailedException_Exception e) {
                    logger.error("Problem rolling back RecoverPoint replication set " + replicationSetName, e);
                } catch (FunctionalAPIInternalError_Exception e) {
                    logger.error("Problem rolling back RecoverPoint replication set " + replicationSetName, e);
                }
            }
        }
    }
    
    /**
     * Gets a ReplicationSetUID given the name of the replication set.
     * 
     * @param functionalAPI the functional API instance.
     * @param cgUID the consistency group UID.
     * @param replicationSetName the replication set name.
     * @return the replication set UID.
     * @throws FunctionalAPIActionFailedException_Exception
     * @throws FunctionalAPIInternalError_Exception
     */
    private ReplicationSetUID getReplicationSetUID(FunctionalAPIImpl functionalAPI, ConsistencyGroupUID cgUID, String replicationSetName) 
            throws FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception {
        ConsistencyGroupSettings groupSettings = functionalAPI.getGroupSettings(cgUID);
        for (ReplicationSetSettings replicationSet : groupSettings.getReplicationSetsSettings()) {
            if (replicationSet.getReplicationSetName().equalsIgnoreCase(replicationSetName)) {
                return replicationSet.getReplicationSetUID();
            }
        }
        
        return null;
    }
    
    /**
     * Creates a bookmark against one or more consistency group
     *
     * @param CreateBookmarkRequestParams request - contains the information about which CGs to create bookmarks on
     *
     * @return CreateBookmarkResponse - response as to success or fail of creating the bookmarks
     *
     * @throws RecoverPointException
     **/
    public CreateBookmarkResponse createBookmarks(CreateBookmarkRequestParams request) throws RecoverPointException {
        String mgmtIPAddress = _endpoint.toASCIIString();

        if (null == mgmtIPAddress) {
            throw RecoverPointException.exceptions.noRecoverPointEndpoint();
        }
        
        Set<String>wwnSet = request.getVolumeWWNSet();
        if (wwnSet == null) {
            throw RecoverPointException.exceptions.noWWNsFoundInRequest();
        }
        
        Set<String> unmappedWWNs = new HashSet<String>();
        RecoverPointBookmarkManagementUtils bookmarkManager = new RecoverPointBookmarkManagementUtils();
        Map<String, RPConsistencyGroup> rpCGMap = bookmarkManager.mapCGsForWWNs(functionalAPI, request, unmappedWWNs);
        
        if (!unmappedWWNs.isEmpty()) {
            throw RecoverPointException.exceptions.couldNotMapWWNsToAGroup(unmappedWWNs);
        }
        
        if (rpCGMap == null) {
            throw RecoverPointException.exceptions.couldNotMapWWNsToAGroup(wwnSet);
        }
        
        return bookmarkManager.createCGBookmarks(functionalAPI, rpCGMap, request);
    }

    private Quantity getQuantity(QuantityType type, long value) {
    	Quantity quantity1 = new Quantity();
        quantity1.setType(type);
        quantity1.setValue(value);
        return quantity1;
    }
    /**
     * Get all RP bookmarks for all CGs specified
     *
     * @param request - set of CG integer IDs
     * @return GetBookmarkResponse - a map of CGs to bookmarks for that CG
     * @throws RecoverPointException
     **/
    public GetBookmarksResponse getRPBookmarks(Set<Integer> request) throws RecoverPointException {
        String mgmtIPAddress = _endpoint.toASCIIString();

        if (null == mgmtIPAddress) {
            throw RecoverPointException.exceptions.noRecoverPointEndpoint();
        }

        RecoverPointBookmarkManagementUtils bookmarkManager = new RecoverPointBookmarkManagementUtils();
        GetBookmarksResponse response = new GetBookmarksResponse();
        response.setCgBookmarkMap(new HashMap<Integer, List<RPBookmark>>());
        for (Integer cgID : request) {
            ConsistencyGroupUID cgUID = new ConsistencyGroupUID();
            cgUID.setId(cgID);
            response.getCgBookmarkMap().put(cgID, bookmarkManager.getRPBookmarksForCG(functionalAPI, cgUID));
        }
        
        response.setReturnCode(RecoverPointReturnCode.SUCCESS);
        return response;
    }

    /**
     * Enables copy images for one or more consistency group copies
     *
     * @param MultiCopyEnableImageRequestParams request - contains the information about which CG copies to enable
     *
     * @return MultiCopyEnableImageResponse - response as to success or fail of enabling the image copies
     *
     * @throws RecoverPointException
     **/
    public MultiCopyEnableImageResponse enableImageCopies(MultiCopyEnableImageRequestParams request) throws RecoverPointException {
        MultiCopyEnableImageResponse response = new MultiCopyEnableImageResponse();
        RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();
        RecoverPointBookmarkManagementUtils bookmarkManager = new RecoverPointBookmarkManagementUtils();
        String mgmtIPAddress = _endpoint.toASCIIString();

        if (null == mgmtIPAddress) {
            throw RecoverPointException.exceptions.noRecoverPointEndpoint();
        }
        Set<String>wwnSet = request.getVolumeWWNSet();
        if (wwnSet == null) {
            throw RecoverPointException.exceptions.noWWNsFoundInRequest();
        }
        Set<String> unmappedWWNs = new HashSet<String>();
        CreateBookmarkRequestParams mapRequest = new CreateBookmarkRequestParams();
        mapRequest.setBookmark(request.getBookmark());
        mapRequest.setVolumeWWNSet(wwnSet);
        Map<String, RPConsistencyGroup> rpCGMap = bookmarkManager.mapCGsForWWNs(functionalAPI, mapRequest, unmappedWWNs);
        if(!unmappedWWNs.isEmpty()) {
            throw RecoverPointException.exceptions.couldNotMapWWNsToAGroup(unmappedWWNs);
        }
        if (rpCGMap == null) {
            throw RecoverPointException.exceptions.couldNotMapWWNsToAGroup(wwnSet);
        }
        Set<RPConsistencyGroup> cgSetToEnable = new HashSet<RPConsistencyGroup>();
        for (String volume : rpCGMap.keySet()) {
            //logger.info("Get RPCG for volume: " + volume);
            cgSetToEnable.add(rpCGMap.get(volume));
        }

        // Make sure your copies are OK to enable.
        for (RPConsistencyGroup rpcg : cgSetToEnable) {
        	Set<RPCopy>copies = rpcg.getCopies();
        	for (RPCopy copy : copies) {
        		try {
        			String cgCopyName = functionalAPI.getGroupCopyName(copy.getCGGroupCopyUID());
        			String cgName = functionalAPI.getGroupName(copy.getCGGroupCopyUID().getGroupUID());
        			if (!imageManager.verifyCopyCapableOfEnableImageAccess(functionalAPI, copy.getCGGroupCopyUID(), false)) {
        				logger.info("Copy " + cgCopyName + " of group " + cgName + " is in a mode that disallows enabling the CG copy.");
        				throw RecoverPointException.exceptions.notAllowedToEnableImageAccessToCG(
        						cgName, cgCopyName);
        			}
        		} catch (FunctionalAPIActionFailedException_Exception e) {
        			throw RecoverPointException.exceptions
        			.notAllowedToEnableImageAccessToCGException(e);
        		} catch (FunctionalAPIInternalError_Exception e) {
        			throw RecoverPointException.exceptions
        			.notAllowedToEnableImageAccessToCGException(e);
        		}
        	}
        }
        
        try {
            for (RPConsistencyGroup rpcg : cgSetToEnable) {
                Set<RPCopy>copies = rpcg.getCopies();
                for (RPCopy copy : copies) {
                    boolean waitForLinkState = true;
                    imageManager.enableCGCopy(functionalAPI, copy.getCGGroupCopyUID(), waitForLinkState, ImageAccessMode.LOGGED_ACCESS, request.getBookmark(), request.getAPITTime());
                }
            }
        } catch (RecoverPointException e) {
            logger.error("Caught RecoverPointException exception while enabling CG copies.  Return copies to previous state");
            for (RPConsistencyGroup rpcg : cgSetToEnable) {
                Set<RPCopy>copies = rpcg.getCopies();
                for (RPCopy copy : copies) {
                    imageManager.disableCGCopy(functionalAPI, copy.getCGGroupCopyUID());
                }
            }
            throw e;
        }
        response.setReturnCode(RecoverPointReturnCode.SUCCESS);
        return response;
    }

    /**
     * Disables copy images for one or more consistency group copies
     *
     * @param MultiCopyDisableImageRequestParams request - contains the information about which CG copies to disable
     *
     * @return MultiCopyDisableImageResponse - response as to success or fail of disabling the image copies
     *
     * @throws RecoverPointException
     **/
    public MultiCopyDisableImageResponse disableImageCopies(MultiCopyDisableImageRequestParams request) throws RecoverPointException {
        MultiCopyDisableImageResponse response = new MultiCopyDisableImageResponse();
        RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();
        RecoverPointBookmarkManagementUtils bookmarkManager = new RecoverPointBookmarkManagementUtils();
        String mgmtIPAddress = _endpoint.toASCIIString();
        if (null == mgmtIPAddress) {
            throw RecoverPointException.exceptions.noRecoverPointEndpoint();
        }
        Set<String>wwnSet = request.getVolumeWWNSet();
        if (wwnSet == null) {
            throw RecoverPointException.exceptions.noWWNsFoundInRequest();
        }
        Set<String> unmappedWWNs = new HashSet<String>();
        CreateBookmarkRequestParams mapRequest = new CreateBookmarkRequestParams();
        mapRequest.setVolumeWWNSet(wwnSet);
        Map<String, RPConsistencyGroup> rpCGMap = bookmarkManager.mapCGsForWWNs(functionalAPI, mapRequest, unmappedWWNs);
        if(!unmappedWWNs.isEmpty()) {
            throw RecoverPointException.exceptions.couldNotMapWWNsToAGroup(unmappedWWNs);
        }
        if (rpCGMap == null) {
            throw RecoverPointException.exceptions.couldNotMapWWNsToAGroup(wwnSet);
        }
        Set<RPConsistencyGroup> cgSetToDisable = new HashSet<RPConsistencyGroup>();
        for (String volume : rpCGMap.keySet()) {
            cgSetToDisable.add(rpCGMap.get(volume));
        }
        
        for (RPConsistencyGroup rpcg : cgSetToDisable) {
            Set<RPCopy>copies = rpcg.getCopies();
            for (RPCopy copy : copies) {
                ConsistencyGroupCopyState copyState = imageManager.getCopyState(functionalAPI, copy.getCGGroupCopyUID());             
                if (copyState != null && copyState.getAccessedImage() != null && copyState.getAccessedImage().getDescription() != null &&
            			copyState.getAccessedImage().getDescription().equals(request.getEmName())) {                	
                    imageManager.disableCGCopy(functionalAPI, copy.getCGGroupCopyUID());                	
                }
            }
        }

        response.setReturnCode(RecoverPointReturnCode.SUCCESS);
        return response;
    }

    /**
     * Restore copy images for one or more consistency group copies
     *
     * @param MultiCopyRestoreImageRequestParams request - contains the information about which CG copies to restore
     *
     * @return MultiCopyRestoreImageResponse - response as to success or fail of restoring the image copies
     *
     * @throws RecoverPointException
     **/
    public MultiCopyRestoreImageResponse restoreImageCopies(MultiCopyRestoreImageRequestParams request) throws RecoverPointException {
        MultiCopyRestoreImageResponse response = new MultiCopyRestoreImageResponse();
        RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();
        RecoverPointBookmarkManagementUtils bookmarkManager = new RecoverPointBookmarkManagementUtils();
        String mgmtIPAddress = _endpoint.toASCIIString();

        if (null == mgmtIPAddress) {
            throw RecoverPointException.exceptions.noRecoverPointEndpoint();
        }
        Set<String>wwnSet = request.getVolumeWWNSet();
        if (wwnSet == null) {
            throw RecoverPointException.exceptions.noWWNsFoundInRequest();
        }
        Set<String> unmappedWWNs = new HashSet<String>();
        CreateBookmarkRequestParams mapRequest = new CreateBookmarkRequestParams();
        mapRequest.setBookmark(request.getBookmark());
        mapRequest.setVolumeWWNSet(wwnSet);
        Map<String, RPConsistencyGroup> rpCGMap = bookmarkManager.mapCGsForWWNs(functionalAPI, mapRequest, unmappedWWNs);
        if(!unmappedWWNs.isEmpty()) {
            throw RecoverPointException.exceptions.couldNotMapWWNsToAGroup(unmappedWWNs);
        }
        if (rpCGMap == null) {
            throw RecoverPointException.exceptions.couldNotMapWWNsToAGroup(wwnSet);
        }
        Set<RPConsistencyGroup> cgSetToEnable = new HashSet<RPConsistencyGroup>();
        for (String volume : rpCGMap.keySet()) {
            //logger.info("Get RPCG for volume: " + volume);
            cgSetToEnable.add(rpCGMap.get(volume));
        }
        ClusterUID siteToRestore = null;
        // Verify that we are not restoring from different sites
        for (RPConsistencyGroup rpcg : cgSetToEnable) {
            Set<RPCopy>copies = rpcg.getCopies();
            for (RPCopy copy : copies) {
                if (siteToRestore == null) {
                    siteToRestore = copy.getCGGroupCopyUID().getGlobalCopyUID().getClusterUID();
                } else if (siteToRestore.getId() != copy.getCGGroupCopyUID().getGlobalCopyUID().getClusterUID().getId()) {
                    throw RecoverPointException.exceptions
                            .cannotRestoreVolumesFromDifferentSites(wwnSet);
                }
                try {
                    List<ConsistencyGroupCopyUID> productionCopiesUIDs = functionalAPI.getGroupSettings(copy.getCGGroupCopyUID().getGroupUID()).getProductionCopiesUIDs();
                    for (ConsistencyGroupCopyUID productionCopyUID : productionCopiesUIDs) {
	                    if (RecoverPointUtils.cgCopyEqual(productionCopyUID, copy.getCGGroupCopyUID())) {
	                        throw RecoverPointException.exceptions
	                                .cannotRestoreVolumesInConsistencyGroup(wwnSet);
	                    }
                    }
                } catch (FunctionalAPIActionFailedException_Exception e) {
                    logger.error(e.getMessage());
                    logger.error("Received FunctionalAPIActionFailedException_Exception. Get production copy");
                    throw RecoverPointException.exceptions.failureRestoringVolumes();
                } catch (FunctionalAPIInternalError_Exception e) {
                    logger.error(e.getMessage());
                    logger.error("Received FunctionalAPIActionFailedException_Exception. Get production copy");
                    throw RecoverPointException.exceptions.failureRestoringVolumes();
                }
            }
        }
        try {
            for (RPConsistencyGroup rpcg : cgSetToEnable) {
                Set<RPCopy>copies = rpcg.getCopies();
                for (RPCopy copy : copies) {
                    // For restore, just wait for link state of the copy being restored
                    imageManager.waitForCGLinkState(functionalAPI, copy.getCGGroupCopyUID().getGroupUID(), copy.getCGGroupCopyUID(), PipeState.ACTIVE);
                    boolean waitForLinkState = false;
                    imageManager.enableCGCopy(functionalAPI, copy.getCGGroupCopyUID(), waitForLinkState, ImageAccessMode.LOGGED_ACCESS, request.getBookmark(), request.getAPITTime());
                }
            }
        } catch (RecoverPointException e) {
            logger.error("Caught exception while enabling CG copies for restore.  Return copies to previous state");
            for (RPConsistencyGroup rpcg : cgSetToEnable) {
                Set<RPCopy>copies = rpcg.getCopies();
                for (RPCopy copy : copies) {
                    imageManager.disableCGCopy(functionalAPI, copy.getCGGroupCopyUID());
                }
            }
            throw e;
        }
        for (RPConsistencyGroup rpcg : cgSetToEnable) {
            Set<RPCopy>copies = rpcg.getCopies();
            for (RPCopy copy : copies) {
                imageManager.restoreEnabledCGCopy(functionalAPI, copy.getCGGroupCopyUID());
            }
        }
        response.setReturnCode(RecoverPointReturnCode.SUCCESS);
        return response;
    }

    /**
     * Get a list of WWNs given a site ID
     *
     * @param int siteID - Site ID to get WWNs for
     *
     * @return Map<String, String> - a list of WWNs
     *
     * @throws RecoverPointException
     */
    public Map<String, String>  getInitiatorWWNs(String internalSiteName) throws RecoverPointException {
        Map<String, String> wwns = new HashMap<String, String>();
        try {
            FullRecoverPointSettings fullRecoverPointSettings = functionalAPI.getFullRecoverPointSettings();
            for (ClusterConfiguration siteSettings : fullRecoverPointSettings.getSystemSettings().getGlobalSystemConfiguration().getClustersConfigurations()) {
                if (!siteSettings.getInternalClusterName().equals(internalSiteName)) {
                    continue;
                }
                ClusterRPAsState clusterRPAState = functionalAPI.getRPAsStateFromCluster(siteSettings.getCluster());
                for (RpaState rpaState : clusterRPAState.getRpasStates()) {
                    for (InitiatorInformation rpaPortState : rpaState.getInitiatorsStates()) {
                    	if (rpaPortState instanceof FiberChannelInitiatorInformation) {
                    		FiberChannelInitiatorInformation initiator = (FiberChannelInitiatorInformation)rpaPortState;
                    		String nodeWWN = WwnUtils.convertWWN(initiator.getNodeWWN(), WwnUtils.FORMAT.COLON);
                    		String portWWN = WwnUtils.convertWWN(initiator.getPortWWN(), WwnUtils.FORMAT.COLON);
                    		wwns.put(portWWN, nodeWWN);
                    		logger.info("RPA Node WWN: " + nodeWWN + ". Port WWN: " + portWWN);
                    	}
                    }
                }
            }
            return wwns;
        }  catch (FunctionalAPIActionFailedException_Exception e) {
            logger.error(e.getMessage());
            logger.error("Received FunctionalAPIActionFailedException_Exception. Get port information");
            throw RecoverPointException.exceptions.failureGettingInitiatorWWNs();
        } catch (FunctionalAPIInternalError_Exception e) {
            logger.error(e.getMessage());
            logger.error("Received FunctionalAPIInternalError_Exception. Get port information");
            throw RecoverPointException.exceptions.failureGettingInitiatorWWNs();
        }
    }

    /**
     * The getProtectionInfoForVolume method takes the WWN, and looks for it in the RP site protection environment.
     * If it finds the WWN as a member of a consistency group, it fills in the information, and returns it to the caller.
     * If it does not find the WWN as a member of a consistency group, it returns null
     *
     * @param String volumeWWN - The WWN being checked for RecoverPoint protection
     *
     * @return RecoverPointVolumeProtectionInfo - description of protection information about the WWN, or null if not protected in CG
     *
     * @throws RecoverPointException
     **/
    public RecoverPointVolumeProtectionInfo getProtectionInfoForVolume(String volumeWWN) throws RecoverPointException {
        RecoverPointVolumeProtectionInfo protectionInfo = null;
        try {
            //logger.info("getProtectionInfoForVolume called for: " + volumeWWN);
            protectionInfo = new RecoverPointVolumeProtectionInfo();
            List<ConsistencyGroupSettings> cgsSettings = functionalAPI.getAllGroupsSettings();
            for (ConsistencyGroupSettings cgSettings : cgsSettings) {
                // See if it is a production source, or an RP target
                for (ReplicationSetSettings rsSettings : cgSettings.getReplicationSetsSettings()) {
                    for (UserVolumeSettings uvSettings : rsSettings.getVolumes()) {
                        String volUID = RecoverPointUtils.getGuidBufferAsString(uvSettings.getVolumeInfo().getRawUids(), false);
                        if (volUID.toLowerCase(Locale.ENGLISH).equalsIgnoreCase(volumeWWN)){
                            ConsistencyGroupUID cgID = uvSettings.getGroupCopyUID().getGroupUID();
                            List<ConsistencyGroupCopyUID> productionCopiesUIDs = functionalAPI.getGroupSettings(cgID).getProductionCopiesUIDs();                                                                       
                            String cgName = cgSettings.getName();
                            String cgCopyName = functionalAPI.getGroupCopyName(uvSettings.getGroupCopyUID());
                            protectionInfo.setRpProtectionName(cgName);
                            protectionInfo.setRpVolumeGroupCopyID(uvSettings.getGroupCopyUID().getGlobalCopyUID().getCopyUID());
                            protectionInfo.setRpVolumeGroupID(cgID.getId());
                            protectionInfo.setRpVolumeSiteID(uvSettings.getClusterUID().getId());
                            protectionInfo.setRpVolumeRSetID(rsSettings.getReplicationSetUID().getId());
                            protectionInfo.setRpVolumeWWN(volumeWWN);
                            if (RecoverPointUtils.isProductionCopy(uvSettings.getGroupCopyUID(), productionCopiesUIDs)) {
                                logger.info("Production volume: " + volumeWWN + " is on copy " + cgCopyName + " of CG " + cgName);
                                protectionInfo.setRpVolumeCurrentProtectionStatus(RecoverPointVolumeProtectionInfo.volumeProtectionStatus.PROTECTED_SOURCE);
                            } else {
                                logger.info("Target volume: " + volumeWWN + " is on copy " + cgCopyName + " of CG " + cgName);
                                protectionInfo.setRpVolumeCurrentProtectionStatus(RecoverPointVolumeProtectionInfo.volumeProtectionStatus.PROTECTED_TARGET);
                            }
                            return protectionInfo;                         
                        }
                    }
                }
                // See if it is a journal volume
                for (ConsistencyGroupCopySettings cgCopySettings : cgSettings.getGroupCopiesSettings()) {
                    ConsistencyGroupCopyJournal cgJournal = cgCopySettings.getJournal();
                    List<JournalVolumeSettings> journalVolumeSettingsList = cgJournal.getJournalVolumes();
                    for (JournalVolumeSettings journalVolumeSettings : journalVolumeSettingsList) {
                        String journalVolUID =
                                RecoverPointUtils.getGuidBufferAsString(journalVolumeSettings.getVolumeInfo().getRawUids(), false);
                        if (journalVolUID.toLowerCase(Locale.ENGLISH).equalsIgnoreCase(volumeWWN)){
                            ConsistencyGroupUID cgID = journalVolumeSettings.getGroupCopyUID().getGroupUID();                         
                            List<ConsistencyGroupCopyUID> productionCopiesUIDs = functionalAPI.getGroupSettings(cgID).getProductionCopiesUIDs();                            
                            String cgName = cgSettings.getName();
                            String cgCopyName = functionalAPI.getGroupCopyName(journalVolumeSettings.getGroupCopyUID());
                            protectionInfo.setRpProtectionName(cgName);
                            protectionInfo.setRpVolumeGroupCopyID(journalVolumeSettings.getGroupCopyUID().getGlobalCopyUID().getCopyUID());
                            protectionInfo.setRpVolumeGroupID(cgID.getId());
                            protectionInfo.setRpVolumeSiteID(journalVolumeSettings.getClusterUID().getId());
                            protectionInfo.setRpVolumeWWN(volumeWWN);
                            if (RecoverPointUtils.isProductionCopy(journalVolumeSettings.getGroupCopyUID(), productionCopiesUIDs)) {
                                logger.info("Production journal: " + volumeWWN + " is on copy " + cgCopyName + " of CG " + cgName);
                                protectionInfo.setRpVolumeCurrentProtectionStatus(RecoverPointVolumeProtectionInfo.volumeProtectionStatus.SOURCE_JOURNAL);
                            } else {
                                logger.info("Target journal: "+ volumeWWN + " is on copy " + cgCopyName + " of CG " + cgName);
                                protectionInfo.setRpVolumeCurrentProtectionStatus(RecoverPointVolumeProtectionInfo.volumeProtectionStatus.TARGET_JOURNAL);
                            }
                            return protectionInfo;                  
                        }
                    }

                }
            }
        }  catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failureGettingProtectionInfoForVolume(volumeWWN,
                    e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failureGettingProtectionInfoForVolume(volumeWWN,
                    e);
        }
        throw RecoverPointException.exceptions.failureGettingProtectionInfoForVolume(volumeWWN);
    }

    /**
     * Disable (stop) the consistency group protection specified by the input volume info.
     * If a target volume is specified, disable the copy associated with the target.
     * Disable requires a full sweep when enabled
     *
     * @param RecoverPointVolumeProtectionInfo volumeInfo - Volume info for the CG to disable
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void disableProtection(RecoverPointVolumeProtectionInfo volumeInfo) throws RecoverPointException {
        try {
            ConsistencyGroupUID cgUID = new ConsistencyGroupUID();
            cgUID.setId(volumeInfo.getRpVolumeGroupID());
            if (volumeInfo.getRpVolumeCurrentProtectionStatus() == RecoverPointVolumeProtectionInfo.volumeProtectionStatus.PROTECTED_SOURCE) {
                // Disable the whole CG
            	functionalAPI.disableConsistencyGroup(cgUID);
                String cgName = functionalAPI.getGroupName(cgUID);
                logger.info("Protection disabled on CG: " + cgName);
                
                // Weakness: When disabling the entire CG, we are not waiting for the link to be in a stopped (UNKNOWN) state.
                RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();
                imageManager.waitForCGLinkState(functionalAPI, cgUID, null, PipeState.UNKNOWN);
            } else {
                // Disable the CG copy associated with the target
                ConsistencyGroupCopyUID cgCopyUID = RecoverPointUtils.mapRPVolumeProtectionInfoToCGCopyUID(volumeInfo);
                functionalAPI.disableConsistencyGroupCopy(cgCopyUID);
                String cgCopyName=functionalAPI.getGroupCopyName(cgCopyUID);
                String cgName = functionalAPI.getGroupName(cgCopyUID.getGroupUID());
                logger.info("Protection disabled on CG copy " + cgCopyName + " on CG " + cgName);

                // Make sure the CG copy is stopped
                RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();
                imageManager.waitForCGLinkState(functionalAPI, cgUID, cgCopyUID, PipeState.UNKNOWN);
                logger.info("Protection disabled on CG copy " + cgCopyName + " on CG " + cgName);
            }
        }  catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToDisableProtection(
                    volumeInfo.getRpVolumeGroupID(), e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToDisableProtection(
                    volumeInfo.getRpVolumeGroupID(), e);
        }
    }

    /**
     * Enable (start) the consistency group protection specified by the input volume info
     * Requires a full sweep.
     *
     * @param RecoverPointVolumeProtectionInfo volumeInfo - Volume info for the CG to enable
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void enableProtection(RecoverPointVolumeProtectionInfo volumeInfo) throws RecoverPointException {
        try {
            ConsistencyGroupUID cgUID = new ConsistencyGroupUID();
            cgUID.setId(volumeInfo.getRpVolumeGroupID());
            ConsistencyGroupCopyUID cgCopyUID = null;
            cgCopyUID = RecoverPointUtils.mapRPVolumeProtectionInfoToCGCopyUID(volumeInfo);
            if (volumeInfo.getRpVolumeCurrentProtectionStatus() == RecoverPointVolumeProtectionInfo.volumeProtectionStatus.PROTECTED_SOURCE) {
                // Enable the whole CG
            	functionalAPI.enableConsistencyGroup(cgUID, true);
            } else {
                // Enable the CG copy associated with the target
            	functionalAPI.enableConsistencyGroupCopy(cgCopyUID, true);
            }
            // Make sure the CG is ready
            RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();
            imageManager.waitForCGLinkState(functionalAPI, cgUID, cgCopyUID, PipeState.ACTIVE);
            String cgCopyName = functionalAPI.getGroupCopyName(cgCopyUID);
            String cgName = functionalAPI.getGroupName(cgUID);
            logger.info("Protection enabled on CG copy " + cgCopyName + " on CG " + cgName);
        }  catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToEnableProtection(
                    volumeInfo.getRpVolumeGroupID(), e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToEnableProtection(
                    volumeInfo.getRpVolumeGroupID(), e);
        }
    }

    /**
     * Return the state of a consistency group.
     *
     * @param RecoverPointVolumeProtectionInfo volumeInfo - Volume info for the CG to get CG state for
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public RecoverPointCGState getCGState(RecoverPointVolumeProtectionInfo volumeInfo) throws RecoverPointException {
        ConsistencyGroupUID cgUID = new ConsistencyGroupUID();
        cgUID.setId(volumeInfo.getRpVolumeGroupID());
        ConsistencyGroupSettings cgSettings = null;
        ConsistencyGroupState cgState = null;
        try {
            cgSettings = functionalAPI.getGroupSettings(cgUID);
            cgState = functionalAPI.getGroupState(cgUID);
        }  catch (FunctionalAPIActionFailedException_Exception e) {
            // No longer exists
            return RecoverPointCGState.GONE;
        } catch (FunctionalAPIInternalError_Exception e) {
            // No longer exists
            return RecoverPointCGState.GONE;
        }
        if (!cgSettings.isEnabled()) {
            return RecoverPointCGState.STOPPED;
        }
        // First check for disabled copies
        boolean someCopiesEnabled = false;
        boolean someCopiesDisabled = false;
        for (ConsistencyGroupCopyState cgCopyState : cgState.getGroupCopiesStates()){
            if (cgCopyState.isEnabled()) {
                someCopiesEnabled = true;
            } else {
                someCopiesDisabled = true;
            }
        }
        if (someCopiesDisabled && !someCopiesEnabled) {
            // All copies are disabled
            return RecoverPointCGState.STOPPED;
        }

        // Now check to see if all the copies are paused
        boolean someCopiesPaused = false;
        boolean someCopiesNotPaused = false;
        List<ConsistencyGroupLinkState> cgLinkStateList;
        try {
            cgLinkStateList = functionalAPI.getGroupState(cgUID).getLinksStates();
        } catch (FunctionalAPIActionFailedException_Exception e) {
            // No longer exists
            return RecoverPointCGState.GONE;
        } catch (FunctionalAPIInternalError_Exception e) {
            // No longer exists
            return RecoverPointCGState.GONE;
        }

        for (ConsistencyGroupLinkState cgLinkState : cgLinkStateList) {
            // OK, this is our link that we just restored. Check the link state to see if it is active
            if (PipeState.ACTIVE.equals(cgLinkState.getPipeState())) {
                someCopiesNotPaused = true;
            } else {
                someCopiesPaused = true;
            }
        }

        if (someCopiesPaused && !someCopiesNotPaused) {
            // All copies are paused
            return RecoverPointCGState.PAUSED;
        }
        if (someCopiesPaused || someCopiesDisabled) {
            return RecoverPointCGState.MIXED;
        }
        return RecoverPointCGState.READY;
    }

    /**
     * Pause (suspend) the consistency group protection specified by the input volume info.
     *
     * @param RecoverPointVolumeProtectionInfo volumeInfo - Volume info for the CG to pause
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void pauseTransfer(RecoverPointVolumeProtectionInfo volumeInfo) throws RecoverPointException {
        try {
            ConsistencyGroupUID cgUID = new ConsistencyGroupUID();
            cgUID.setId(volumeInfo.getRpVolumeGroupID());
            if (volumeInfo.getRpVolumeCurrentProtectionStatus() == RecoverPointVolumeProtectionInfo.volumeProtectionStatus.PROTECTED_SOURCE) {
                // Pause the whole CG
            	functionalAPI.pauseGroupTransfer(cgUID);
                String cgName = functionalAPI.getGroupName(cgUID);
                logger.info("Protection paused on CG " + cgName);
            } else {
                // Pause the CG copy associated with the target
                ConsistencyGroupCopyUID cgCopyUID = RecoverPointUtils.mapRPVolumeProtectionInfoToCGCopyUID(volumeInfo);
                functionalAPI.pauseGroupCopyTransfer(cgCopyUID);
                String cgCopyName = functionalAPI.getGroupCopyName(cgCopyUID);
                String cgName = functionalAPI.getGroupName(cgCopyUID.getGroupUID());
                logger.info("Protection paused on CG copy " + cgCopyName + " on CG " + cgName);
            }
        }  catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToPauseProtection(
                    volumeInfo.getRpVolumeGroupID(), e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToPauseProtection(
                    volumeInfo.getRpVolumeGroupID(), e);
        }
    }

    /**
     * Resume the consistency group protection specified by the input volume info.
     *
     * @param RecoverPointVolumeProtectionInfo volumeInfo - Volume info for the CG to resume
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void resumeTransfer(RecoverPointVolumeProtectionInfo volumeInfo) throws RecoverPointException {
        try {
            ConsistencyGroupUID cgUID = new ConsistencyGroupUID();
            cgUID.setId(volumeInfo.getRpVolumeGroupID());
            if (volumeInfo.getRpVolumeCurrentProtectionStatus() == RecoverPointVolumeProtectionInfo.volumeProtectionStatus.PROTECTED_SOURCE) {
                // Resume the whole CG
                String cgName = functionalAPI.getGroupName(cgUID);
                logger.info("Protection resumed on CG " + cgName);
                functionalAPI.startGroupTransfer(cgUID);
            } else {
                // Resume the CG copy associated with the target
                ConsistencyGroupCopyUID cgCopyUID = RecoverPointUtils.mapRPVolumeProtectionInfoToCGCopyUID(volumeInfo);
                functionalAPI.startGroupCopyTransfer(cgCopyUID);
                String cgCopyName = functionalAPI.getGroupCopyName(cgCopyUID);
                String cgName = functionalAPI.getGroupName(cgCopyUID.getGroupUID());
                logger.info("Protection resumed on CG copy " + cgCopyName + " on CG " + cgName);
            }
        }  catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToResumeProtection(
                    volumeInfo.getRpVolumeGroupID(), e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToResumeProtection(
                    volumeInfo.getRpVolumeGroupID(), e);
        }
    }

    /**
     * Perform a failover test to the consistency group copy specified by the input request params.
     *
     * @param RPCopyRequestParams copyToFailoverTo - Volume info for the CG to perform a failover test to. Also contains bookmark and APIT info. If no bookmark or APIT specified, failover test to most recent image.
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void failoverCopyTest (RPCopyRequestParams copyToFailoverTo) throws RecoverPointException {
        // Check the params
        // If bookmark != null, enable the bookmark on the copy, and failover to that copy
        // If APITTime != null, enable the specified APIT on the copy, and failover to that copy
        // If both are null, enable the most recent imagem, and failover to that copy
        String bookmarkName = copyToFailoverTo.getBookmarkName();
        Date apitTime = copyToFailoverTo.getApitTime();
        if (bookmarkName != null) {
            logger.info("Failver copy to bookmark : " + bookmarkName);
        } else if (apitTime != null) {
            logger.info("Failover copy to APIT : " + apitTime.toString());
        } else {
            logger.info("Failover copy to most recent image");
        }
        RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();
        imageManager.enableCopyImage(functionalAPI, copyToFailoverTo, false);
        //RecoverPointUtils.mapRPVolumeProtectionInfoToCGCopyUID(copyToFailoverTo.getCopyVolumeInfo());
        RecoverPointVolumeProtectionInfo failoverCopyInfo = copyToFailoverTo.getCopyVolumeInfo();
        pauseTransfer(failoverCopyInfo);
    }

    /**
     * Cancel a failover test for a consistency group copy specified by the input request params.
     *
     * @param RPCopyRequestParams copyToFailoverTo - Volume info for the CG that a previous failover test was performed on
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void failoverCopyTestCancel (RPCopyRequestParams copyToFailoverTo) throws RecoverPointException {
        RecoverPointVolumeProtectionInfo failoverCopyInfo = copyToFailoverTo.getCopyVolumeInfo();
        resumeTransfer(failoverCopyInfo);
        RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();
        imageManager.disableCopyImage(functionalAPI, copyToFailoverTo);
    }

    /**
     * Perform a failover to the consistency group copy specified by the input request params.
     *
     * @param RPCopyRequestParams copyToFailoverTo - Volume info for the CG to perform a failover to. Also contains bookmark and APIT info.  If no bookmark or APIT specified, failover to most recent image.
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void failoverCopy(RPCopyRequestParams copyToFailoverTo) throws RecoverPointException {
        // Check the params
        // If bookmark != null, enable the bookmark on the copy, and failover to that copy
        // If APITTime != null, enable the specified APIT on the copy, and failover to that copy
        // If both are null, enable the most recent imagem, and failover to that copy
        String bookmarkName = copyToFailoverTo.getBookmarkName();
        Date apitTime = copyToFailoverTo.getApitTime();
        if (bookmarkName != null) {
            logger.info("Failver copy to bookmark : " + bookmarkName);
        } else if (apitTime != null) {
            logger.info("Failover copy to APIT : " + apitTime.toString());
        } else {
            logger.info("Failover copy to most recent image");
        }
        RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();
        imageManager.enableCopyImage(functionalAPI, copyToFailoverTo, true);
        // Stop the replication link to this copy
        // ConsistencyGroupCopyUID cgCopyUID = RecoverPointUtils.mapRPVolumeProtectionInfoToCGCopyUID(copyToFailoverTo.getCopyVolumeInfo());
        // imageManager.disableCGCopy(functionalAPI, cgCopyUID);
    }

    /**
     * Cancel a failover operation, usually a failover after a failover without a swap.
     *
     * @param RPCopyRequestParams copyToFailoverTo - Volume info for the CG that a previous failover test was performed on
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void failoverCopyCancel (RPCopyRequestParams copyToFailoverTo) throws RecoverPointException {
        RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();
        imageManager.disableCopyImage(functionalAPI, copyToFailoverTo);
    }

    /**
     * Perform a swap to the consistency group copy specified by the input request params.
     *
     * @param RPCopyRequestParams copyToFailoverTo - Volume info for the CG to perform a swap to.
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
	public void swapCopy(RPCopyRequestParams copyParams) throws RecoverPointException {
		logger.info("Swap copy to current or most recent image");
		// Make sure the copy is already enabled or RP will fail the operation.  If it isn't enabled, enable it.
        RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();
        ConsistencyGroupCopyUID cgCopyUID = RecoverPointUtils.mapRPVolumeProtectionInfoToCGCopyUID(copyParams.getCopyVolumeInfo());
        ConsistencyGroupCopyState copyState = imageManager.getCopyState(functionalAPI, cgCopyUID);
        
        if (copyState != null && copyState.getAccessedImage() == null) {
        	// Enable to the latest image
        	failoverCopy(copyParams);
        }

        // Perform the failover
        imageManager.failoverCGCopy(functionalAPI, cgCopyUID);
        
        // Prepare the link settings for new links
        prepareLinkSettings(cgCopyUID); 
        
        // Set the failover copy as production to resume data flow
        imageManager.setCopyAsProduction(functionalAPI, cgCopyUID);
        
        // wait for links to become active
        ConsistencyGroupUID cgUID = cgCopyUID.getGroupUID();
        String cgName = null;
        try {
            cgName = functionalAPI.getGroupName(cgUID);
        } catch (FunctionalAPIActionFailedException_Exception | FunctionalAPIInternalError_Exception e) {
            // benign error -- cgName is only used for logging
            logger.error(e.getMessage(), e);
        }
        
        logger.info("Waiting for links to become active for CG " + (cgName==null?"unknown CG name":cgName));
        (new RecoverPointImageManagementUtils()).waitForCGLinkState(functionalAPI, cgUID, null, PipeState.ACTIVE);
        logger.info(String.format("Replication sets have been added to consistency group %s.", (cgName==null?"unknown CG name":cgName)));
	}

    /**
	 * Find the link settings corresponding to the given production and target copy identifiers.
	 * 
	 * @param linkSettings the consistency group link settings
	 * @param prodCopyUID the production copy
	 * @param targetCopyUID the target copy 
	 * @param prodCopyName the name of the production copy
	 * @param targetCopyName the name of the target copy
	 * @return the consistency group settings matching the prod/target copy relationship
	 */
	private ConsistencyGroupLinkSettings findLinkSettings(List<ConsistencyGroupLinkSettings> cgLinkSettings, GlobalCopyUID prodCopyUID, 
	        GlobalCopyUID targetCopyUID, String prodCopyName, String targetCopyName) {
	    ConsistencyGroupLinkSettings toRet = null;

	    if (cgLinkSettings != null && !cgLinkSettings.isEmpty()
	            && prodCopyUID != null && targetCopyUID != null) {
	        for (ConsistencyGroupLinkSettings linkSetting : cgLinkSettings) {
	            if (isMatchingLinkSettings(linkSetting, prodCopyUID, targetCopyUID)) {
	                logger.info("Found existing link settings between {} and {}.", prodCopyName, targetCopyName);
	                toRet = linkSetting;
	                break;
	            }
	        }
	    }
	    
	    if (toRet == null) {
	        logger.info("Unable to find existing link settings between {} and {}.", prodCopyName, targetCopyName);
	    }
	    
	    return toRet;
	}

    /**
     * Convenience method used to determine if the provided production copy and target copy
     * are points on the given link settings.
     * 
     * @param linkSetting the link settings to examine.
     * @param prodCopyUID the production copy end of the link settings.
     * @param targetCopyUID the target copy end of the link settings.
     * @return
     */
    private boolean isMatchingLinkSettings(ConsistencyGroupLinkSettings linkSettings, 
            GlobalCopyUID prodCopyUID, GlobalCopyUID targetCopyUID) {
        
        GlobalCopyUID firstCopy = null;
        GlobalCopyUID secondCopy = null;
        
        if (linkSettings.getGroupLinkUID() != null) {
            firstCopy = linkSettings.getGroupLinkUID().getFirstCopy();
            secondCopy = linkSettings.getGroupLinkUID().getSecondCopy();
            
            // Compare both ends of the link to the provided prod and target copies passed in.
            // A link is a match if the prod and target copy are both found, regardless of which
            // end of the link they belong.
            if ((RecoverPointUtils.cgCopyEqual(firstCopy, prodCopyUID) 
                    && RecoverPointUtils.cgCopyEqual(secondCopy, targetCopyUID))
                    || (RecoverPointUtils.cgCopyEqual(firstCopy, targetCopyUID) 
                            && RecoverPointUtils.cgCopyEqual(secondCopy, prodCopyUID))) {
                return true;
            }
        }
        
        return false;
    }
	
	/**
	 * Prepares the link settings between the new production copy and all other copies.
	 * 
	 * @param cgCopyUID the failover/new production copy
	 * @throws RecoverPointException
	 */
	private void prepareLinkSettings(ConsistencyGroupCopyUID cgCopyUID) throws RecoverPointException {    
	    String cgCopyName = null;
	    String cgName = null;
	    
	    logger.info("Preparing link settings between new production copy and local/remote copies after failover.");
	    
	    try {
	        cgCopyName = functionalAPI.getGroupCopyName(cgCopyUID);
	        cgName = functionalAPI.getGroupName(cgCopyUID.getGroupUID());
	        
	        ConsistencyGroupSettings groupSettings = functionalAPI.getGroupSettings(cgCopyUID.getGroupUID());
	        List<ConsistencyGroupLinkSettings> cgLinkSettings = groupSettings.getActiveLinksSettings();
            List<ConsistencyGroupLinkSettings> oldProdCgLinkSettings = groupSettings.getPassiveLinksSettings();
            List<ConsistencyGroupCopyUID> productionCopiesUIDs = groupSettings.getProductionCopiesUIDs();
            for (ConsistencyGroupCopyUID prodCopyUID : productionCopiesUIDs) {
            
	            String prodCopyName = functionalAPI.getGroupCopyName(prodCopyUID);
	            
	            List<ConsistencyGroupCopySettings> copySettings = groupSettings.getGroupCopiesSettings();
	    
	            ConsistencyGroupLinkSettings linkSettings = null;
	            
	            for (ConsistencyGroupCopySettings copySetting : copySettings) {
	                // We need to set the link settings for all orphaned copies.  Orphaned copies
	                // are identified by not being the production copy or the current copy.
	                if (!copySetting.getName().equalsIgnoreCase(prodCopyName) 
	                        && !copySetting.getName().equalsIgnoreCase(cgCopyName)) {
	
                        String copyName = functionalAPI.getGroupCopyName(copySetting.getCopyUID());
	                    // Check to see if a link setting already exists for the link between the 2 copies
	                    linkSettings = findLinkSettings(
	                            cgLinkSettings, cgCopyUID.getGlobalCopyUID(), copySetting.getCopyUID().getGlobalCopyUID(),
	                            cgCopyName, copyName);
	
	                    if (linkSettings == null) {
	                        // Link settings for the source/target copies does not exist so we need to create one
	                        // Find the corresponding link settings prior to the failover.
	                        linkSettings = findLinkSettings(
	                                oldProdCgLinkSettings, prodCopyUID.getGlobalCopyUID(), copySetting.getCopyUID().getGlobalCopyUID(),
	                                prodCopyName, copyName);
	                     
	                        if (linkSettings != null) {
	                            logger.info(String.format("Generate new link settings between %s and %s based on existing link settings between the current production copy %s and %s.",
	                                    cgCopyName, copyName, prodCopyName, copyName));
	                            ConsistencyGroupLinkUID cgLinkUID = linkSettings.getGroupLinkUID();
	                            // Set the link copies appropriately
	                            GlobalCopyUID sourceCopy = cgCopyUID.getGlobalCopyUID();
	                            GlobalCopyUID targetCopy = copySetting.getCopyUID().getGlobalCopyUID();
	                            
	                            cgLinkUID.setFirstCopy(sourceCopy);
	                            cgLinkUID.setSecondCopy(targetCopy);
	                            
	                            ConsistencyGroupLinkPolicy linkPolicy = linkSettings.getLinkPolicy();
	                            
	                            // Check the copy cluster information to determine if this is a local or remote copy
	                            if (sourceCopy.getClusterUID().getId() == targetCopy.getClusterUID().getId()) {
	                                // local copy
	                                logger.info(String.format("Creating new local copy link settings between %s and %s, for consistency group %s.", cgCopyName, copyName, cgName));
	                                linkPolicy.getProtectionPolicy().setReplicatingOverWAN(false);
	                            } else {
	                                // remote copy
	                                logger.info(String.format("Creating new remote copy link settings between %s and %s, for consistency group %s.", cgCopyName, copyName, cgName));
	                                linkPolicy.getProtectionPolicy().setReplicatingOverWAN(true);
	                            }
	           
	                            functionalAPI.addConsistencyGroupLink(cgLinkUID, linkPolicy);
	                        }
	                    }
	                }
	            }
            }
	    } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToFailoverCopy(cgCopyName, cgName, e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToFailoverCopy(cgCopyName, cgName, e);
        } 
	}
	
    /**
     * Delete the consistency group copy specified by the input volume info.
     *
     * @param RecoverPointVolumeProtectionInfo volumeInfo - Volume info for the CG copy to delete (can't be production)
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void deleteCopy (RecoverPointVolumeProtectionInfo copyToDelete) throws RecoverPointException {
        ConsistencyGroupCopyUID cgCopyUID = RecoverPointUtils.mapRPVolumeProtectionInfoToCGCopyUID(copyToDelete);
        String copyName = null;
        String cgName = null;
        try {
            copyName = functionalAPI.getGroupCopyName(cgCopyUID);
            cgName = functionalAPI.getGroupName(cgCopyUID.getGroupUID());        
            List<ConsistencyGroupCopyUID> productionCopiesUIDs = functionalAPI.getGroupSettings(cgCopyUID.getGroupUID()).getProductionCopiesUIDs();
            for (ConsistencyGroupCopyUID productionCopyUID : productionCopiesUIDs) {
	            if (RecoverPointUtils.cgCopyEqual(productionCopyUID, cgCopyUID)) {
	                // Can't call delete copy using the production CG copy
	                throw RecoverPointException.exceptions.cantCallDeleteCopyUsingProductionVolume(copyName, cgName);
	            }
	            functionalAPI.removeConsistencyGroupCopy(cgCopyUID);
	            logger.info ("Deleted copy " + copyName + " for consistency group " + cgName);
            }
        }  catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToDeleteCopy(copyName, cgName, e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToDeleteCopy(copyName, cgName, e);
        }
    }

    /**
     * Delete the consistency group specified by the input volume info.
     *
     * @param RecoverPointVolumeProtectionInfo volumeInfo - Volume info for the CG to delete
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void deleteCG (RecoverPointVolumeProtectionInfo cgToDelete) throws RecoverPointException {
        ConsistencyGroupCopyUID cgCopyUID = RecoverPointUtils.mapRPVolumeProtectionInfoToCGCopyUID(cgToDelete);
        String cgName = null;
        try {
            cgName = functionalAPI.getGroupName(cgCopyUID.getGroupUID());
            ConsistencyGroupSettings groupSettings = functionalAPI.getGroupSettings(cgCopyUID.getGroupUID());
            List<ConsistencyGroupCopyUID> productionCopiesUIDs = groupSettings.getProductionCopiesUIDs();

            for (ConsistencyGroupCopyUID productionCopyUID : productionCopiesUIDs) {
	            if (!cgToDelete.isMetroPoint() && !RecoverPointUtils.cgCopyEqual(productionCopyUID, cgCopyUID)) {
	                // Can't call delete CG using anything but the production CG copy
	                throw RecoverPointException.exceptions
	                        .cantCallDeleteCGUsingProductionCGCopy(cgName);
	            }
            }
            functionalAPI.removeConsistencyGroup(cgCopyUID.getGroupUID());
            logger.info ("Deleted consistency group " + cgName);
        }  catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToDeleteConsistencyGroup(cgName, e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToDeleteConsistencyGroup(cgName, e);
        }
    }
    
    /**
     * Delete a journal volume (WWN) from the consistency group copy specified by the input volume info.
     *
     * @param RecoverPointVolumeProtectionInfo copyToModify - Volume info for the CG to add a journal volume to
     *
     * @param String journalWWNToDelete - WWN of the journal volume to delete

     * @return void
     *
     * @throws RecoverPointException
     **/
    public void deleteJournalFromCopy (RecoverPointVolumeProtectionInfo copyToModify, String journalWWNToDelete) throws RecoverPointException {
        ConsistencyGroupCopyUID cgCopyUID = RecoverPointUtils.mapRPVolumeProtectionInfoToCGCopyUID(copyToModify);
        String copyName = null;
        String cgName = null;
        try {
            copyName = functionalAPI.getGroupCopyName(cgCopyUID);
            cgName = functionalAPI.getGroupName(cgCopyUID.getGroupUID());
            logger.info("Request to delete journal " +  journalWWNToDelete +  " from copy " + copyName + " for consistency group " + cgName);

            Set<RPSite> allSites = getAssociatedRPSites();
            DeviceUID journalDeviceUIDToDelete = RecoverPointUtils.getDeviceID(allSites, journalWWNToDelete);
            if (journalDeviceUIDToDelete == null) {
                throw RecoverPointException.exceptions.cannotFindJournal(journalWWNToDelete);
            }
            functionalAPI.removeJournalVolume(cgCopyUID, journalDeviceUIDToDelete);

        }  catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToDeleteJournal(journalWWNToDelete,
                    copyName, cgName, e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToDeleteJournal(journalWWNToDelete,
                    copyName, cgName, e);
        }
    }

    /**
     * Delete a replication set based on the volume info sent in.
     *
     * @param RecoverPointVolumeProtectionInfo volume - Volume info for the CG to remove the replication set from
     * @param String volumeWWNToDelete - WWN of the volume to delete the entire replication set for
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void deleteReplicationSet(RecoverPointVolumeProtectionInfo volume, String volumeWWNToDelete) throws RecoverPointException {
        boolean rsetRemoved = false;
        try {
            ConsistencyGroupUID cgID = new ConsistencyGroupUID();
            cgID.setId(volume.getRpVolumeGroupID());
            ReplicationSetUID repSetUID = new ReplicationSetUID();
            repSetUID.setId(volume.getRpVolumeRSetID());

            ConsistencyGroupSettings groupSettings = functionalAPI.getGroupSettings(cgID);
            for (ReplicationSetSettings replicationSet : groupSettings.getReplicationSetsSettings()) {
                if (replicationSet.getReplicationSetUID().getId() == repSetUID.getId()) {
                	functionalAPI.removeReplicationSet(cgID, replicationSet.getReplicationSetUID());
                    logger.info("Request to delete replication set" +  replicationSet.getReplicationSetName() +  " from consistency group " + cgID);
                    rsetRemoved = true;
                }
            }

            if (!rsetRemoved) {
                throw RecoverPointException.exceptions.cannotFindReplicationSet(volumeWWNToDelete);
            }
        }  catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToDeleteReplicationSet(volumeWWNToDelete,
                    e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToDeleteReplicationSet(volumeWWNToDelete,
                    e);
        }
    }

    /**
     * Get RP site statistics for use in collectStatisticsInformation
     *
     * @param RecoverPointVolumeProtectionInfo copyToModify - Volume info for the CG to add a journal volume to
     *
     * @param String journalWWNToDelete - WWN of the journal volume to delete

     * @return RecoverPointStatisticsResponse
     *
     * @throws RecoverPointException
     **/
    public RecoverPointStatisticsResponse getRPSystemStatistics () throws RecoverPointException {
    	logger.info("Collecting RecoverPoint System Statistics.");
        RecoverPointStatisticsResponse response = new RecoverPointStatisticsResponse();
        try {
            Map<Long,Double> siteAvgCPUUsageMap = new HashMap<Long,Double>();
            Map<Long,Long> siteInputAvgThroughput = new HashMap<Long,Long>();
            Map<Long,Long> siteOutputAvgThroughput = new HashMap<Long,Long>();
            Map<Long,Long> siteIncomingAvgWrites = new HashMap<Long,Long>();
            SystemStatistics systemStatistics = functionalAPI.getSystemStatistics();
            Set<ClusterUID> ClusterUIDList = new HashSet<ClusterUID>();
            List<RpaStatistics> rpaStatisticsList = systemStatistics.getRpaStatistics();

            for (RpaStatistics rpaStatistics : rpaStatisticsList) {
                ClusterUID siteID = rpaStatistics.getRpaUID().getClusterUID();
                boolean foundSite = false;
                for (ClusterUID siteListUID : ClusterUIDList) {
                    if (siteID.getId() == siteListUID.getId()) {
                        foundSite = true;
                        break;
                    }
                }
                if (!foundSite) {
                    ClusterUIDList.add(siteID);
                }
            }

            for (ClusterUID ClusterUID : ClusterUIDList) {
                List<Double> rpaCPUList = new LinkedList<Double>();
                List<Long> rpaSiteInputAvgThroughputList = new LinkedList<Long>();
                List<Long> rpaSiteOutputAvgThroughputList = new LinkedList<Long>();
                List<Long> rpaSiteInputAvgIncomingWritesList = new LinkedList<Long>();
                for (RpaStatistics rpaStatistics : rpaStatisticsList) {
                    if (rpaStatistics.getRpaUID().getClusterUID().getId() == ClusterUID.getId()) {
                        rpaCPUList.add(Double.valueOf(rpaStatistics.getCpuUsage()));
                        rpaSiteInputAvgThroughputList.add(rpaStatistics.getTraffic().getApplicationThroughputStatistics().getInThroughput());
                        for (ConnectionOutThroughput cot : rpaStatistics.getTraffic().getApplicationThroughputStatistics().getConnectionsOutThroughputs()) {
                        	rpaSiteOutputAvgThroughputList.add(cot.getOutThroughput());
                        }
                        rpaSiteInputAvgIncomingWritesList.add(rpaStatistics.getTraffic().getApplicationIncomingWrites());
                    }
                }
                Double cpuTotalUsage = 0.0;
                Long incomingWritesTotal = Long.valueOf(0);
                Long inputThoughputTotal = Long.valueOf(0);
                Long outputThoughputTotal = Long.valueOf(0);

                for (Double rpaCPUs : rpaCPUList) {
                    cpuTotalUsage += rpaCPUs;
                }
                for (Long siteInputThroughput : rpaSiteInputAvgThroughputList) {
                    inputThoughputTotal += siteInputThroughput;
                }
                for (Long siteOutputThroughput : rpaSiteOutputAvgThroughputList) {
                    outputThoughputTotal += siteOutputThroughput;
                }
                for (Long incomingWrites : rpaSiteInputAvgIncomingWritesList) {
                    incomingWritesTotal += incomingWrites;
                }
                logger.info("Average CPU usage for site: " + ClusterUID.getId() + " is " + cpuTotalUsage / rpaCPUList.size());
                logger.info("Average input throughput for site: " + ClusterUID.getId() + " is " + inputThoughputTotal / rpaCPUList.size() + " kb/s");
                logger.info("Average output throughput for site: " + ClusterUID.getId() + " is " + outputThoughputTotal / rpaCPUList.size() + " kb/s");
                logger.info("Average incoming writes for site: " + ClusterUID.getId() + " is " + incomingWritesTotal / rpaCPUList.size() + " writes/s");

                siteAvgCPUUsageMap.put(ClusterUID.getId(), cpuTotalUsage / rpaCPUList.size());
                siteInputAvgThroughput.put(ClusterUID.getId(), inputThoughputTotal / rpaCPUList.size());
                siteOutputAvgThroughput.put(ClusterUID.getId(), outputThoughputTotal / rpaCPUList.size());
                siteIncomingAvgWrites.put(ClusterUID.getId(), incomingWritesTotal / rpaCPUList.size());

            }
            response.setSiteCPUUsageMap(siteAvgCPUUsageMap);
            response.setSiteInputAvgIncomingWrites(siteIncomingAvgWrites);
            response.setSiteOutputAvgThroughput(siteOutputAvgThroughput);
            response.setSiteInputAvgThroughput(siteInputAvgThroughput);
            List<ProtectionSystemParameters> systemParameterList = new LinkedList<ProtectionSystemParameters>();
            MonitoredParametersStatus monitoredParametersStatus = functionalAPI.getMonitoredParametersStatus();
            List<MonitoredParameter> monitoredParameterList = monitoredParametersStatus.getParameters();

            for (MonitoredParameter monitoredParameter : monitoredParameterList) {
                ProtectionSystemParameters param = response.new ProtectionSystemParameters();
                param.parameterName = monitoredParameter.getKey().getParameterType().value();
                param.parameterLimit = monitoredParameter.getValue().getParameterWaterMarks().getLimit();
                param.currentParameterValue = monitoredParameter.getValue().getValue();
                
                if (monitoredParameter.getKey().getClusterUID() != null) {
                	param.siteID = monitoredParameter.getKey().getClusterUID().getId();
                } 
                
                systemParameterList.add(param);
            }
            response.setParamList(systemParameterList);

            for (ProtectionSystemParameters monitoredParameter : response.getParamList()) {
                logger.info("Key: " + monitoredParameter.parameterName);
                logger.info("Current Value: " + monitoredParameter.currentParameterValue);
                logger.info("Max Value: " + monitoredParameter.parameterLimit);
            }
            return response;
        }  catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToGetStatistics(e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToGetStatistics(e);
        } catch (Exception e) {
            throw RecoverPointException.exceptions.failedToGetStatistics(e);
        }
    }

    /**
     * Find the transient site ID, given the permanent/unchanging unique internal site name.
     * Needed for some external operations, like filling in proper copy info in a snapshot.
     * 
     * @param internalSiteName internal site name, never changes
     * @param clusterIdCache cache of already discovered cluster ids (can be null)
     * @return ClusterUID corresponding to the site that has that internal site name.
     * @throws FunctionalAPIActionFailedException_Exception
     * @throws FunctionalAPIInternalError_Exception
     */
    private ClusterUID getRPSiteID(String internalSiteName, Map<String, ClusterUID> clusterIdCache) throws FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception  {
        if (clusterIdCache != null && clusterIdCache.containsKey(internalSiteName)) {
            return clusterIdCache.get(internalSiteName);
        } else {
            ClusterUID clusterId = RecoverPointUtils.getRPSiteID(functionalAPI, internalSiteName);
            if (clusterIdCache != null) {
                clusterIdCache.put(internalSiteName, clusterId);
            }
            return clusterId;
        }
    }
    
    /**
     * Find the transient site ID, given the permanent/unchanging unique internal site name.
     * Needed for some external operations, like filling in proper copy info in a snapshot.
     * 
     * @param internalSiteName internal site name, never changes
     * @return ClusterUID corresponding to the site that has that internal site name.
     * @throws RecoverPointException 
     */
    public ClusterUID getRPSiteID(String internalSiteName) throws RecoverPointException  {
        try {
            return RecoverPointUtils.getRPSiteID(functionalAPI, internalSiteName);
        }
        catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToGetRPSiteID(e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToGetRPSiteID(e);
        }
    }
    
    /**
     * Get the replication set information associated with this volume.  This is important when assembling a workflow to 
     * recreate the replication set for the purpose of expanding volumes.
     * 
     * Steps are as follows:
     *    This method: Get the state information associated with the replication set
     *    Delete method below: Delete the replication set
     *    RP Controller: Expand volumes
     *    Recreate method below: Perform a rescan_san
     *    Recreate method below: Create the replication set
     *
     * @param RecoverPointVolumeProtectionInfo volume - Volume info for the CG to remove the replication set from
     * @return void
     *
     * @throws RecoverPointException
     **/
    public RecreateReplicationSetRequestParams getReplicationSet(RecoverPointVolumeProtectionInfo volume) throws RecoverPointException {
        ReplicationSetSettings rsetSettings = null;

        try {
            ConsistencyGroupUID cgID = new ConsistencyGroupUID();
            cgID.setId(volume.getRpVolumeGroupID());
            ReplicationSetUID repSetUID = new ReplicationSetUID();
            repSetUID.setId(volume.getRpVolumeRSetID());
            rsetSettings = getReplicationSetSettings(functionalAPI, rsetSettings, cgID,	repSetUID);

            if (rsetSettings == null) {
                throw RecoverPointException.exceptions.cannotFindReplicationSet(volume
                        .getRpVolumeWWN());
            }               

            RecreateReplicationSetRequestParams response = new RecreateReplicationSetRequestParams();
            response.cgName = volume.getRpProtectionName();
            response.name = rsetSettings.getReplicationSetName();
            response.setConsistencyGroupUID(cgID);
            response.volumes = new ArrayList<CreateRSetVolumeParams>();
            for (UserVolumeSettings volumeSettings : rsetSettings.getVolumes()) {
            	CreateRSetVolumeParams volumeParams = new CreateRSetVolumeParams();
            	volumeParams.setDeviceUID(volumeSettings.getVolumeInfo().getVolumeID());
            	volumeParams.setConsistencyGroupCopyUID(volumeSettings.getGroupCopyUID());
            	response.volumes.add(volumeParams);
            }
            
            return response;
        }  catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.cannotFindReplicationSet(
                    volume.getRpVolumeWWN(), e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.cannotFindReplicationSet(
                    volume.getRpVolumeWWN(), e);
        }
    }
    
    /**
     * Delete the Replication Set associated with the volume sent in.  This is useful when volumes are resized.
     * It is step one of a multi-step process.
     *
     * @param RecoverPointVolumeProtectionInfo volume - Volume info for the CG to remove the replication set from
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void deleteReplicationSet(RecoverPointVolumeProtectionInfo volume) throws RecoverPointException {
        ReplicationSetSettings rsetSettings = null;

        try {
            ConsistencyGroupUID cgID = new ConsistencyGroupUID();
            cgID.setId(volume.getRpVolumeGroupID());
            ReplicationSetUID repSetUID = new ReplicationSetUID();
            repSetUID.setId(volume.getRpVolumeRSetID());
            rsetSettings = getReplicationSetSettings(functionalAPI, rsetSettings, cgID, repSetUID);

            if (rsetSettings == null) {
                throw RecoverPointException.exceptions.cannotFindReplicationSet(volume
                        .getRpVolumeWWN());
            }               

            // Remove the replication set
            disableProtection(volume);
            functionalAPI.removeReplicationSet(cgID, rsetSettings.getReplicationSetUID());
            logger.info("Request to delete replication set" +  rsetSettings.getReplicationSetName() +  " from consistency group " + cgID);
        }  catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToRecreateReplicationSet(
                    volume.getRpVolumeWWN(), e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToRecreateReplicationSet(
                    volume.getRpVolumeWWN(), e);
        }
    }


    /**
     * Perform Step 2 of expanding a volume, Recreate a replication set that was previously removed.
     * 
     * @param volume volume base of replication set
     * @param rsetSettings replication set information used to create replication set
     * @throws RecoverPointException
     */
    public void recreateReplicationSet(String wwn, RecreateReplicationSetRequestParams rsetParams) throws RecoverPointException {
        
        try {
        	ConsistencyGroupUID cgID = rsetParams.getConsistencyGroupUID();
            ConsistencyGroupSettings groupSettings = functionalAPI.getGroupSettings(cgID);
            
            // Rescan the SAN
            functionalAPI.rescanSANVolumesInAllClusters(true);
           
            // Create replication set
            logger.info("Adding replication set: " + rsetParams.name);
            functionalAPI.addReplicationSet(cgID, rsetParams.name);

            // Update the group settings after new RSet has been created
            groupSettings = functionalAPI.getGroupSettings(cgID);
            
            // Find the new replication set and add the volumes.
            
            for (ReplicationSetSettings replicationSet : groupSettings.getReplicationSetsSettings()) {
            	Set<Long> deviceUIDSet = new HashSet<Long>();
                if (replicationSet.getReplicationSetName().equalsIgnoreCase(rsetParams.name)) {
                	// Get the new replication set identifier
                    ReplicationSetUID rSetUID = replicationSet.getReplicationSetUID();
                for (CreateRSetVolumeParams volumeParam : rsetParams.volumes) {   
                	    if (deviceUIDSet.contains(volumeParam.getDeviceUID().getId())) {
                	    	// Add the volumes only once, in the case of MetroPoint the source volume is exposed via both the legs of the VPLEX cluster, 
                	    	// this ensures that we are trying to add them twice to the replication set.
                	    	logger.info("Skipping adding the volume to the CG, already added. ");
                	    	continue;
                	    }
                    	functionalAPI.addUserVolume(volumeParam.getConsistencyGroupCopyUID(), rSetUID, volumeParam.getDeviceUID());
                    	deviceUIDSet.add(volumeParam.getDeviceUID().getId());
                    }                    
                    break;
                }
            }
            
            RecoverPointVolumeProtectionInfo volume = getProtectionInfoForVolume(wwn);
            enableProtection(volume);
            logger.info("Checking for volumes unattached to splitters");
            RecoverPointUtils.verifyCGVolumesAttachedToSplitter(functionalAPI, cgID);
        }  catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToRecreateReplicationSet(wwn, e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToRecreateReplicationSet(wwn, e);
        }
    }

	private ReplicationSetSettings getReplicationSetSettings(FunctionalAPIImpl impl, 
			ReplicationSetSettings rsetSettings,
			ConsistencyGroupUID cgID, ReplicationSetUID repSetUID)
			throws FunctionalAPIActionFailedException_Exception,
			FunctionalAPIInternalError_Exception {
		ConsistencyGroupSettings groupSettings = impl.getGroupSettings(cgID);
		for (ReplicationSetSettings replicationSet : groupSettings.getReplicationSetsSettings()) {
		    if (replicationSet.getReplicationSetUID().getId() == repSetUID.getId()) {
		        rsetSettings = replicationSet;
		        break;
		    }
		}
		return rsetSettings;
	}

	/**
	 * Returns the array serial numbers associated with each RP Cluster.
	 * That is, all arrays that have "visibility" according to the RP Cluster.
	 * 
	 * @return a Map of RP Cluster ID -> a Set of array serial numbers
	 * @throws RecoverPointException
	 */
	public Map<String, Set<String>> getArraysForClusters() throws RecoverPointException {
        String mgmtIPAddress = _endpoint.toASCIIString();
        if (null == mgmtIPAddress) {
            throw RecoverPointException.exceptions.noRecoverPointEndpoint();
        }
        try {
            logger.info("RecoverPoint service: Returning all RP Clusters associated with endpoint: " + _endpoint);
            FullRecoverPointSettings fullRecoverPointSettings = functionalAPI.getFullRecoverPointSettings();
            Map<String, Set<String>> clusterStorageSystems = new HashMap<String, Set<String>>();
            
            for (ClusterConfiguration siteSettings : fullRecoverPointSettings.getSystemSettings().getGlobalSystemConfiguration().getClustersConfigurations()) {
                String siteName = siteSettings.getInternalClusterName();
                clusterStorageSystems.put(siteName, RecoverPointUtils.getArraysForCluster(functionalAPI, siteSettings.getCluster()));
            }

            return clusterStorageSystems;
		} catch (FunctionalAPIActionFailedException_Exception e) {
			logger.error(e.getMessage(), e);
			throw RecoverPointException.exceptions.exceptionGettingArrays(e);
		} catch (FunctionalAPIInternalError_Exception e) {			
			logger.error(e.getMessage(), e);
			throw RecoverPointException.exceptions.exceptionGettingArrays(e);
		} 
	}

	public String getUsername() {
		return _username;
	}

	public void setUsername(String _username) {
		this._username = _username;
	}

	public String getPassword() {
		return _password;
	}

	public void setPassword(String _password) {
		this._password = _password;
	}
    
    /**
     * in a Metropoint environment, adds a link between the standby production copy and the remote/DR copy 
     * @param activeProdCopy the CG copy uid of the active production copy
     * @param standbyProdCopy the CG copy uid of the standby production copy
     * @throws FunctionalAPIInternalError_Exception 
     * @throws FunctionalAPIActionFailedException_Exception 
     * @throws FunctionalAPIValidationException_Exception 
     * @throws RecoverPointException
     */
    private void addStandbyCopyLinkSettings(ConsistencyGroupCopyUID activeProdCopy, ConsistencyGroupCopyUID standbyProdCopy)
            throws FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception, FunctionalAPIValidationException_Exception {

        logger.info("Preparing link settings between standby production copy and remote copy after Metropoint swap production copies.");

        String activeCgCopyName = functionalAPI.getGroupCopyName(activeProdCopy);
        String standbyCgCopyName = functionalAPI.getGroupCopyName(standbyProdCopy);
        String cgName = functionalAPI.getGroupName(activeProdCopy.getGroupUID());

        ConsistencyGroupSettings groupSettings = functionalAPI.getGroupSettings(activeProdCopy.getGroupUID());

        // find the remote copy; with metropoint, you're only allowed one remote copy
        // so it must be the one with cluster id not equal to the active or standby cluster ids

        ClusterUID activeClusterId = activeProdCopy.getGlobalCopyUID().getClusterUID();
        ClusterUID standbyClusterId = standbyProdCopy.getGlobalCopyUID().getClusterUID();
        for (ConsistencyGroupCopySettings copySetting : groupSettings.getGroupCopiesSettings()) {

            // see if this is the remote copy; that is it's not the active and not the standby
            ClusterUID copyClusterId = copySetting.getCopyUID().getGlobalCopyUID().getClusterUID();
            if (copyClusterId.getId() != activeClusterId.getId() && copyClusterId.getId() != standbyClusterId.getId()) {

                String targetCopyName = functionalAPI.getGroupCopyName(copySetting.getCopyUID());
                
                // get the link settings for the active production copy and remote copy
                ConsistencyGroupLinkSettings linkSettings = findLinkSettings(groupSettings.getActiveLinksSettings(),
                        activeProdCopy.getGlobalCopyUID(), copySetting.getCopyUID().getGlobalCopyUID(), activeCgCopyName, targetCopyName);

                if (linkSettings != null) {
                    logger.info(String
                            .format("Generate new link settings between %s and %s based on existing link settings between the current production copy %s and %s.",
                                    standbyCgCopyName, targetCopyName, activeCgCopyName, targetCopyName));
                    ConsistencyGroupLinkUID cgLinkUID = linkSettings.getGroupLinkUID();
                    // Set the link copies appropriately
                    GlobalCopyUID standbyCopyUid = standbyProdCopy.getGlobalCopyUID();
                    GlobalCopyUID remoteTargetCopyUid = copySetting.getCopyUID().getGlobalCopyUID();

                    cgLinkUID.setFirstCopy(standbyCopyUid);
                    cgLinkUID.setSecondCopy(remoteTargetCopyUid);

                    ConsistencyGroupLinkPolicy linkPolicy = linkSettings.getLinkPolicy();

                    // create the link between the standby production copy and the remote copy
                    // this has to be a remote copy
                    logger.info(String.format("Creating new remote copy link settings between %s and %s, for consistency group %s.",
                            standbyCgCopyName, targetCopyName, cgName));
                    linkPolicy.getProtectionPolicy().setReplicatingOverWAN(true);

                    functionalAPI.validateAddConsistencyGroupLink(cgLinkUID, linkPolicy);
                    functionalAPI.addConsistencyGroupLink(cgLinkUID, linkPolicy);

                    break;
                }
            }
        }
    }
    
    /**
     * adds one copy to an existing CG
     * @param cgUID CG uid where new copy should be added
     * @param allSites list of sites that see journal and copy file WWN's 
     * @param copyParams the copy to be added
     * @param clusterUid the uid of the cluster the copy should be added to
     * @param rSetUid replication set uid where copy files should be added to
     * @param volumes list of copy files to add to the replication set
     * @param copyType either production, local or remote
     * @return the CG copy uid that was added
     * @throws FunctionalAPIActionFailedException_Exception
     * @throws FunctionalAPIInternalError_Exception
     * @throws FunctionalAPIValidationException_Exception
     */
    private ConsistencyGroupCopyUID addCopyToCG(ConsistencyGroupUID cgUID, Set<RPSite> allSites, CreateCopyParams copyParams,
            ClusterUID clusterUid, List<CreateRSetParams> rSets, RecoverPointCGCopyType copyType) 
                    throws FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception,
            FunctionalAPIValidationException_Exception {
  
        boolean isProduction = copyType == RecoverPointCGCopyType.PRODUCTION;
        String copyTypeStr = copyType.toString();
        
        logger.info(String.format("Adding new copy %s to cg", copyParams.getName()));
        
        ConsistencyGroupCopyUID copyUid = new ConsistencyGroupCopyUID();
        ConsistencyGroupCopySettingsParam copySettingsParam = new ConsistencyGroupCopySettingsParam();
        
        GlobalCopyUID globalCopyUID = new GlobalCopyUID();              
        globalCopyUID.setClusterUID(clusterUid);
        globalCopyUID.setCopyUID(copyType.getCopyNumber());
        
        copyUid.setGroupUID(cgUID);
        copyUid.setGlobalCopyUID(globalCopyUID);
                       
        copySettingsParam.setCopyName(copyParams.getName());
        copySettingsParam.setCopyPolicy(null);
        copySettingsParam.setEnabled(false);
        copySettingsParam.setGroupCopy(copyUid);
        copySettingsParam.setProductionCopy(isProduction);
        copySettingsParam.setTransferEnabled(false); 
        
        // we can't call validateAddConsistencyGroupCopy here because during a swap operation, it throws an exception
        // which is just a warning that a full sweep will be required. There didn't seem to be a way to catch
        // just the warning and let other errors propegate as errors.
        logger.info("Add Production copy (no validation): " + copyParams.toString());
        functionalAPI.addConsistencyGroupCopy(copySettingsParam);
        
        // add journals
        for (CreateVolumeParams journalVolume : copyParams.getJournals()) {
            logger.info("Adding Journal : " + journalVolume.toString() + " for Production copy : " + copyParams.getName());
            functionalAPI.addJournalVolume(copyUid, RecoverPointUtils.getDeviceID(allSites, journalVolume.getWwn()));
        }
        
        if (rSets != null) {
            ConsistencyGroupSettings groupSettings = functionalAPI.getGroupSettings(cgUID);
            for (CreateRSetParams rSet : rSets) {
                ReplicationSetUID rSetUid = null;
                if (rSet != null && rSet.getName() != null && !rSet.getName().isEmpty()) {
                    for (ReplicationSetSettings rSetSetting : groupSettings.getReplicationSetsSettings()) {
                        if (rSetSetting.getReplicationSetName().equalsIgnoreCase(rSet.getName())) {
                            rSetUid = rSetSetting.getReplicationSetUID();
                            break;
                        }
                    }
                }
                if (rSetUid != null) {
                    for (CreateVolumeParams volume : rSet.getVolumes()) {
                        if ( (isProduction && volume.isProduction()) || (!isProduction && !volume.isProduction())) {
                            logger.info(String.format("Adding %s copy volume : %s", copyTypeStr, copyParams.toString()));    
                            functionalAPI.addUserVolume(copyUid, rSetUid, RecoverPointUtils.getDeviceID(allSites, volume.getWwn()));
                        }
                    }
                }
            }
        }
        
        return copyUid;
    }
    
    /**
     * In a metropoint environment, adds the standby production and CDP copies to the CG after failover  
     * and set as production back to the original vplex metro 
     * 
     * @param standbyProdCopy has info about the standby production copy to be added
     * @param standbyLocalCopyParams local standby copies
     * @param rSet contains volume info for standby local copies
     * @param activeProdCopy has info about the active production copy
     * 
     */
    public void addStandbyProductionCopy(CreateCopyParams standbyProdCopy, 
            CreateCopyParams standbyLocalCopyParams, List<CreateRSetParams> rSets, 
            RPCopyRequestParams activeProdCopy) {

        String cgName = "";
        String activeCgCopyName = "";
        
        try {
            ConsistencyGroupCopyUID activeProdCopyUID = RecoverPointUtils.mapRPVolumeProtectionInfoToCGCopyUID(activeProdCopy.getCopyVolumeInfo());
            ConsistencyGroupUID cgUID = activeProdCopyUID.getGroupUID();
            cgName = functionalAPI.getGroupName(cgUID);
            
            logger.info(String.format("Adding Standby production and local volumes to Metropoint CG %s", cgName));
            
            activeCgCopyName = functionalAPI.getGroupCopyName(activeProdCopyUID);
            List<CreateCopyParams> copies = new ArrayList<CreateCopyParams>();
            copies.add(standbyProdCopy);            
            if (standbyLocalCopyParams != null) {
                copies.add(standbyLocalCopyParams);
            }
            Set<RPSite> allSites = scan(copies, rSets);
            CreateVolumeParams volume = standbyProdCopy.getJournals().get(0);
            ClusterUID clusterUid = RecoverPointUtils.getRPSiteID(functionalAPI, volume.getInternalSiteName());

            // add the standby production copy
            ConsistencyGroupCopyUID standbyProdCopyUID = addCopyToCG(cgUID, allSites, standbyProdCopy, clusterUid, 
                    null, RecoverPointCGCopyType.PRODUCTION);
            
            // set up a link between the newly added standby prod copy and the remote copy
            addStandbyCopyLinkSettings(activeProdCopyUID, standbyProdCopyUID);
            
            // add the standby local copies if we have any
            ConsistencyGroupCopyUID standbyLocalCopyUID = null;
            if (standbyLocalCopyParams != null) {
                standbyLocalCopyUID = addCopyToCG(cgUID, allSites, standbyLocalCopyParams, clusterUid,
                        rSets, RecoverPointCGCopyType.LOCAL);
                
                logger.info("Setting link policy between production copy and local copy on standby cluster(id) : " + standbyLocalCopyUID.getGlobalCopyUID().getClusterUID().getId());
                setLinkPolicy(false, standbyProdCopyUID, standbyLocalCopyUID, cgUID);
            }
            
            // enable the local copy
            if (standbyLocalCopyUID != null) {
                logger.info("enable standby local copy for CG ", cgName);
                functionalAPI.enableConsistencyGroupCopy(standbyLocalCopyUID, true);
            }
            
            // enable the production copy
            logger.info("enable production standby copy for CG ", cgName);
            functionalAPI.enableConsistencyGroupCopy(standbyProdCopyUID, true);
            
            // enable the CG
            logger.info("enable CG " + cgName + " after standby copies added");
            functionalAPI.startGroupTransfer(cgUID);
            
            RecoverPointImageManagementUtils rpiMgmt = new RecoverPointImageManagementUtils();
            rpiMgmt.waitForCGLinkState(functionalAPI, cgUID, null, PipeState.ACTIVE);
            
        } catch (Exception e) {
            throw RecoverPointException.exceptions.failedToFailoverCopy(activeCgCopyName, cgName, e);
        }
    }
    
    /**
     * checks to see if there is a protection volume with a given wwn
     * @param volumeWWN the WWN of the volume being checked for existence
     * @return
     */
    public boolean doesProtectionVolumeExist(String volumeWWN) {
        try {
            List<ConsistencyGroupSettings> cgsSettings = functionalAPI.getAllGroupsSettings();
            for (ConsistencyGroupSettings cgSettings : cgsSettings) {
                // See if it is a production source, or an RP target
                for (ReplicationSetSettings rsSettings : cgSettings.getReplicationSetsSettings()) {
                    for (UserVolumeSettings uvSettings : rsSettings.getVolumes()) {
                        String volUID = RecoverPointUtils.getGuidBufferAsString(uvSettings.getVolumeInfo().getRawUids(), false);
                        if (volUID.toLowerCase(Locale.ENGLISH).equalsIgnoreCase(volumeWWN)){
                         return true;                         
                        }
                    }
                }
            }
        }  catch (FunctionalAPIActionFailedException_Exception e) {
            logger.error(e.getMessage(), e);
            return false;
        } catch (FunctionalAPIInternalError_Exception e) {
            logger.error(e.getMessage(), e);
            return false;
        }
        return false;
    }
}
