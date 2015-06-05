/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.systemservices.impl.licensing;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.model.vpool.ManagedResourcesCapacity;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;
import com.emc.storageos.coordinator.client.service.CoordinatorClient.LicenseType;
import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.systemservices.exceptions.CoordinatorClientException;
import com.emc.storageos.systemservices.exceptions.LocalRepositoryException;
import com.emc.storageos.systemservices.exceptions.SysClientException;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.client.SysClientFactory.SysClient;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.vipr.model.sys.licensing.License;
import com.emc.vipr.model.sys.licensing.LicenseFeature;

public class OpenSourceLicenseManagerImpl implements LicenseManager{
    
    private CoordinatorClientExt _coordinator;
    private static final Logger _log = LoggerFactory.getLogger(OpenSourceLicenseManagerImpl.class);

    private static final String LICENSE_TEXT = "INCREMENT ViPR_Controller EMCLM 1.0 permanent uncounted " +
            "VENDOR_STRING=CAPACITY=300;CAPACITY_UNIT=TB;UNIT=TrialMode;SWID=R27WRZ98BBF6XS;PLC=VIPR; " +
            "HOSTID=ANY dist_info=\"ACTIVATED TO ViPR Trial\" ISSUER=EMC " +
            "ISSUED=10-Dec-2013 NOTICE=\"ACTIVATED TO License Site Number: " +
            "ViPRTrialSite\" SN=ViPRTrial SIGN=\"00E7 2A99 8BF5 1676 9FB1 " +
            "297E 83A6 C000 8165 7A29 B14E 478D 3759 98DD 250E\"";

    @Override
    public void addLicense(License license)
            throws LocalRepositoryException, CoordinatorClientException {
        //adding license is not allowed on opensource deployments
    }   
    

    /**
     * Check if it is a one-node deployment required by trial package in vipr 1.1
     * @return true if it is a 1+0 vipr deployment
     */
    public boolean isTrialPackage() {
        // check if it is 1+0 deployment of controller
        return (_coordinator.getNodeCount() == 1 && 
            Constants.CONTROL_NODE_SYSSVC_ID_PATTERN.matcher(
               _coordinator.getMySvcId()).matches());
    }
 
    /**
     * Check if the license is a trial license. For Opensource it's always true.
     * 
     * @param license license object
     * @return true if the license object is a trial license
     */
    @Override
    public boolean isTrialLicense(License license) {
        return true;
    }	
 
    /**
     * Returns a full license object complete with features. 
     * 
     * @return
     */
    public License getLicense() throws Exception {
        License license = new License();
        LicenseFeature licenseFeature = new LicenseFeature();
        licenseFeature.setDateExpires(null);
        licenseFeature.setExpired(false);
        licenseFeature.setStorageCapacity("322122547200");
        licenseFeature.setProductId("R27WRZ98BBF6XS");
        licenseFeature.setSerial("R27WRZ98BBF6XS");
        licenseFeature.setModelId(LicenseConstants.VIPR_CONTROLLER);
        licenseFeature.setDateIssued("01/10/2014");
        licenseFeature.setLicenseIdIndicator("U");
        licenseFeature.setVersion("2.0");
        licenseFeature.setNotice("ACTIVATED TO License Site Number: \"ViPRTrialSite\"");
        licenseFeature.setTrialLicense(true);
        licenseFeature.setLicensed(true);

        license.addLicenseFeature(licenseFeature);
        license.setLicenseText(LICENSE_TEXT);
       
        return license;
    }
    
    /**
     * Verify if product is licensed for the specified feature.
     * 
     * @return boolean
     */
    public boolean isProductLicensed(LicenseType licenseType) {
        return true;
    }
    
    /**
     * Get all license info (features) from coordinator.     
     * 
     * @return LicenseInfoListExt which represent a list of license features
     * @throws Exception
     */
    public LicenseInfoListExt getLicenseInfoListFromCoordinator() {
        return null;
    }
    
    /**
     * Get license info for a specific license type from coordinator.
     * 
     * @return license info for the specified license type
     * @throws Exception
     */
    public LicenseInfoExt getLicenseInfoFromCoordinator(LicenseType licenseType) {
        return null;
    }
    
    /**
     * Get raw license text in LicenseTextInfo from coordinator.
     * 
     * @return
     * @throws Exception
     */
    public LicenseTextInfo getLicenseTextFromCoordinator() throws Exception {
        return null;
    }
        
    /**
     * Update Coordinator Service with the customers actual raw license file text.
     * 
     * @param license
     * @throws CoordinatorClientException
     */
    public void updateCoordinatorWithLicenseText(License license)
            throws CoordinatorClientException {
    }
    
    /**
     * Update Coordinator Service with license information.
     * 
     * @throws CoordinatorClientException
     */
    public void updateCoordinatorWithLicenseInfo(LicenseInfoExt licenseInfo) 
            throws CoordinatorClientException {
    }
     
    /**
     * Verify if the license has expired.
     * 
     * @param licenseInfo
     * @return
     */
    public boolean isLicenseExpired(LicenseInfoExt licenseInfo) {
        return false;
    }

    /**
     * Verify if storage capacity currently used has exceeded the licensed capacity from the license file.  
     * 
     * @param licenseInfo
     * @return true if capacity is exceeded
     */
    public boolean isCapacityExceeded(LicenseInfoExt licenseInfo) {
        return false;
    }
    
    /**
     * Gets capacity from controller.
     * List of returned resources include volume, file and free storage pool capacities.
     */
    public ManagedResourcesCapacity getControllerCapacity() throws InternalServerErrorException {
        _log.info("Getting controller capacity");
        List<Service> services = _coordinator.locateAllServices(
                LicenseConstants.API_SVC_LOOKUP_KEY,
                LicenseConstants.SERVICE_LOOKUP_VERSION, null, null);
        for(Service service: services) {
            try {
                // service could be null, if so get next service.
                if (service != null) {
                    return getClient(service).get(SysClientFactory._URI_PROVISIONING_MANAGED_CAPACITY,
                            ManagedResourcesCapacity.class, null);
                }
            } catch (SysClientException exception) {
                _log.error("LicenseManager::getCapacity for Controller. Cannot connect to host: {}"
                        , service.getEndpoint().toString());
            }
        }
        // if capacity cannot be retrieved
        _log.error("Controller capacity could not be retrieved");
        throw APIException.internalServerErrors.getObjectError("controller capacity",
                null);
    }
    
    /**
     * Get a instance of the SysClient for the base url. 
     * 
     * @return
     */
    private SysClient getClient(Service service) {
        URI hostUri = null;
        hostUri = service.getEndpoint();
        String baseNodeURL = String.format(LicenseConstants.BASE_URL_FORMAT, hostUri.getHost(),
                hostUri.getPort());
        _log.info("Calling URI: " + baseNodeURL);
        return SysClientFactory.getSysClient(URI.create(baseNodeURL));
    }
    
    /**
     * Get a target info lock from coordinator.
     * @return
     */
    public boolean getTargetInfoLock() {
        return true;
    }
    
    /**
     * calls coordinator client to release a target version lock.
     */
    public void releaseTargetVersionLock() {
    }
   
    /**
     * Set the CoordinatorClientExt
     * 
     * @param coordinatorClientExt
     */
    public void setCoordinatorClientExt(CoordinatorClientExt coordinatorClientExt) {
        _coordinator = coordinatorClientExt;
    }
}
