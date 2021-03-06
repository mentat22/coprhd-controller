/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.arrays;

import static com.emc.vipr.client.core.util.ResourceUtils.id;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.model.DiscoveredDataObject;
import models.BlockProtocols;
import models.PoolTypes;
import models.RegistrationStatus;
import models.StorageProviderTypes;
import models.StorageSystemTypes;
import models.datatable.StoragePoolDataTable;
import models.datatable.StoragePoolDataTable.StoragePoolInfo;
import models.datatable.StoragePortDataTable;
import models.datatable.StoragePortDataTable.StoragePortInfo;
import models.datatable.StorageSystemDataTable;
import models.datatable.StorageSystemDataTable.StorageSystemInfo;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.Max;
import play.data.validation.MaxSize;
import play.data.validation.Min;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.With;
import util.DefaultStorageArrayPortMap;
import util.EnumOption;
import util.MessagesUtils;
import util.StoragePoolUtils;
import util.StoragePortUtils;
import util.StorageSystemUtils;
import util.StringOption;
import util.datatable.DataTablesSupport;
import util.validation.HostNameOrIpAddress;

import com.emc.storageos.db.client.util.EndpointUtility;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.pools.StoragePoolRestRep;
import com.emc.storageos.model.pools.StoragePoolUpdate;
import com.emc.storageos.model.ports.StoragePortRequestParam;
import com.emc.storageos.model.ports.StoragePortRestRep;
import com.emc.storageos.model.ports.StoragePortUpdate;
import com.emc.storageos.model.smis.StorageProviderRestRep;
import com.emc.storageos.model.systems.StorageSystemRequestParam;
import com.emc.storageos.model.systems.StorageSystemRestRep;
import com.emc.storageos.model.systems.StorageSystemUpdateRequestParam;
import com.emc.storageos.model.valid.Endpoint;
import com.emc.vipr.client.Task;
import com.google.common.collect.Lists;

import controllers.Common;
import controllers.arrays.StorageProviders.StorageProviderForm;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;
import controllers.util.ViprResourceController;

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class StorageSystems extends ViprResourceController {
    protected static final String SAVED_ARRAY = "StorageSystems.savedArray";
    protected static final String SAVED_SMIS = "StorageSystems.savedSMIS";
    protected static final String DELETED_SUCCESS = "StorageSystems.deleted.success";
    protected static final String DELETED_ERROR = "StorageSystems.deleted.error";
    protected static final String UNKNOWN = "StorageSystems.unknown";
    protected static final String NAME_NOT_AVAILABLE = "StorageSystems.nameNotAvailable";
    protected static final String SAVED_POOL = "StorageSystems.savedPool";
    protected static final String DEREGISTER_SUCCESS = "PhysicalAssets.deregistration.success";
    protected static final String DEREGISTER_ERROR = "PhysicalAssets.deregistration.error";
    protected static final String REGISTER_SUCCESS = "PhysicalAssets.registration.success";
    protected static final String REGISTER_ERROR = "PhysicalAssets.registration.error";
    protected static final String UNKNOWN_PORT = "storageArrayPort.unknown";
    protected static final String NOT_REGISTERED = "StorageSystems.not.registered";

    private static void addReferenceData() {
        renderArgs.put("storageArrayTypeList", Arrays.asList(StorageSystemTypes.OPTIONS));
        renderArgs.put("smisStorageSystemTypeList", Arrays.asList(StorageSystemTypes.SMIS_OPTIONS));
        renderArgs.put("nonSmisStorageSystemTypeList", Arrays.asList(StorageSystemTypes.NON_SMIS_OPTIONS));
        renderArgs.put("sslDefaultStorageSystemList", Arrays.asList(StorageSystemTypes.SSL_DEFAULT_OPTIONS));
        renderArgs.put("nonSSLStorageSystemList", Arrays.asList(StorageSystemTypes.NON_SSL_OPTIONS));
        List<EnumOption> defaultStorageArrayPortMap = Arrays.asList(EnumOption.options(DefaultStorageArrayPortMap.values()));
        renderArgs.put("defaultStorageArrayPortMap", defaultStorageArrayPortMap);

        renderArgs.put("vnxfileStorageSystemType", StorageSystemTypes.VNX_FILE);
        renderArgs.put("scaleIOStorageSystemType", StorageSystemTypes.SCALEIO);
    }

    public static void list() {
        renderArgs.put("dataTable", new StorageSystemDataTable());
        render();
    }

    public static void listJson() {
        performListJson(StorageSystemUtils.getStorageSystems(), new JsonItemOperation());
    }

    public static void itemsJson(@As(",") String[] ids) {
        itemsJson(uris(ids));
    }

    private static void itemsJson(List<URI> ids) {
        performItemsJson(StorageSystemUtils.getStorageSystems(ids), new JsonItemOperation());
    }

    public static void itemDetails(String id) {
        StorageSystemRestRep storageSystem = StorageSystemUtils.getStorageSystem(id);
        if (storageSystem == null) {
            error(MessagesUtils.get(UNKNOWN, id));
        }
        StorageProviderRestRep smisProvider = StorageSystemUtils.getStorageProvider(storageSystem);
        Map<String, Set<NamedRelatedResourceRep>> connectivityMap = StorageSystemUtils
                .getProtectionConnectivityMap(storageSystem);
        render(storageSystem, smisProvider, connectivityMap);
    }

    public static void create() {
        addReferenceData();
        StorageSystemForm storageArray = new StorageSystemForm();
        // put all "initial create only" defaults here rather than field initializers
        storageArray.type = StorageSystemTypes.VMAX;
        storageArray.useSSL = true;
        storageArray.userName = "";
        storageArray.smisProviderUseSSL = true;
        render("@edit", storageArray);
    }

    @FlashException("list")
    public static void edit(String id) {
        addReferenceData();

        StorageSystemRestRep storageSystem = StorageSystemUtils.getStorageSystem(id);
        if (storageSystem != null) {
            StorageSystemForm storageArray = new StorageSystemForm(storageSystem);

            if (storageArray.unregistered) {
                flash.put("warning", MessagesUtils.get(NOT_REGISTERED, storageArray.name));
            }
            render(storageArray);
        }
        else {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }
    }

    public static void editSmisProvider(String id) {
        Common.setReferrer(Common.reverseRoute(StorageSystems.class, "list"));
        StorageProviders.edit(id);
    }

    @FlashException(keep=true, referrer={"create","edit"})
    public static void save(StorageSystemForm storageArray) {
        storageArray.validate("storageArray");
        
        if (Validation.hasErrors()) {
            Common.handleError();
        }

        storageArray.save();
        String message = storageArray.isStorageProviderManaged() && StringUtils.isEmpty(storageArray.id) ? MessagesUtils.get(
                SAVED_SMIS, storageArray.name) : MessagesUtils.get(SAVED_ARRAY, storageArray.name);
        flash.success(message);

        // TODO: cleanup referrer
        if (StringUtils.isNotEmpty(storageArray.referrerUrl)) {
            redirect(storageArray.referrerUrl);
        }
        list();
    }

    public static void delete(@As(",") String[] ids) {
        delete(uris(ids));
    }

    private static void delete(List<URI> ids) {
        List<StorageSystemRestRep> storageSystems = StorageSystemUtils.getStorageSystems(ids);
        performSuccessFail(storageSystems, new DeleteOperation(), DELETED_SUCCESS, DELETED_ERROR);
        list();
    }

    public static void introspect(@As(",") String[] ids) {
        introspect(uris(ids));
    }

    private static void introspect(List<URI> ids) {
        performSuccess(ids, new DiscoveryOperation(), DISCOVERY_STARTED);
        list();
    }

    public static void deregisterArrays(@As(",") String[] ids) {
        deregisterArrays(uris(ids));
    }

    private static void deregisterArrays(List<URI> ids) {
        performSuccessFail(ids, new DeregisterOperation(), DEREGISTER_SUCCESS, DEREGISTER_ERROR);
        list();
    }

    public static void registerArrays(@As(",") String[] ids) {
        registerArrays(uris(ids));
    }

    private static void registerArrays(List<URI> ids) {
        performSuccessFail(ids, new RegisterOperation(), REGISTER_SUCCESS, REGISTER_ERROR);
        list();
    }

    //
    // begin Storage Array > Port functionality
    //

    public static void deregisterPorts(@As(",") String[] ids, String arrayId) {
        deregisterPorts(uris(ids), arrayId);
    }

    private static void deregisterPorts(List<URI> ids, String arrayId) {
        performSuccessFail(ids, new DeregisterPortOperation(), DEREGISTER_SUCCESS, DEREGISTER_ERROR);
        ports(arrayId);
    }

    public static void registerPorts(@As(",") String[] ids, String arrayId) {
        registerPorts(uris(ids), arrayId);
    }

    private static void registerPorts(List<URI> ids, String arrayId) {
        performSuccessFail(ids, new RegisterPortOperation(arrayId), REGISTER_SUCCESS, REGISTER_ERROR);
        ports(arrayId);
    }

    public static void arrayPortsJson(String id) {
        List<StoragePortInfo> results = Lists.newArrayList();
        List<StoragePortRestRep> storagePorts = StoragePortUtils.getStoragePorts(id);
        for (StoragePortRestRep storagePort : storagePorts) {
            results.add(new StoragePortInfo(storagePort));
        }
        renderJSON(DataTablesSupport.createJSON(results, params));
    }
    
    public static void metricDetails(String id) {
        StoragePortRestRep port = StoragePortUtils.getStoragePort(id);
        render(port);
    }

    public static void ports(String id) {
        addReferenceData();
        StorageSystemRestRep storageSystem = StorageSystemUtils.getStorageSystem(id);
        if (storageSystem == null) {
            flash.error(MessagesUtils.get(UNKNOWN, id));
            list();
        }

        StorageArrayPortDataTable dataTable = new StorageArrayPortDataTable(storageSystem);

        render("@listPorts", storageSystem, dataTable);
    }

    public static void createPort(String id) {
        StorageSystemRestRep storageSystem = StorageSystemUtils.getStorageSystem(id);
        StringOption[] portTypeOptions = {
                new StringOption("", ""),
                new StringOption("IP", StringOption.getDisplayValue("IP", "storageArrayPort.portTypes")),
                new StringOption("FC", StringOption.getDisplayValue("FC", "storageArrayPort.portTypes")),
        };
        renderArgs.put("portTypeOptions", Arrays.asList(portTypeOptions));
        render(storageSystem);
    }

    public static void editPort(String id, String portId) {
        StoragePortRestRep storagePort = StoragePortUtils.getStoragePort(portId);
        if (storagePort == null) {
            flash.error(MessagesUtils.get(UNKNOWN_PORT, portId));
            ports(id);
        }
        URI storageSystemId = id(storagePort.getStorageDevice());
        StorageSystemRestRep storageSystem = StorageSystemUtils.getStorageSystem(storageSystemId);
        if (storageSystem == null) {
            flash.error(MessagesUtils.get(UNKNOWN, storageSystemId));
            list();
        }
        if (RegistrationStatus.isUnregistered(storageSystem.getRegistrationStatus())) {
            flash.put("warning", MessagesUtils.get(NOT_REGISTERED, storageSystem.getName()));
        }
        StorageArrayPortForm storageArrayPort = new StorageArrayPortForm();
        storageArrayPort.readFrom(storagePort);
        render(storageArrayPort, storagePort, storageSystem);
    }

    @FlashException(keep=true, referrer={"ports"})
    public static void savePort(StorageArrayPortForm storageArrayPort) {
        storageArrayPort.validate("storageArrayPort");
        if (Validation.hasErrors()) {
            Common.handleError();
        }

        StoragePortRestRep port = storageArrayPort.save();
        flash.success(MessagesUtils.get(SAVED_POOL, port.getPortName()));
        ports(stringId(port.getStorageDevice()));
    }

    //
    // begin Storage Array > Pool functionality
    //

    public static void deregisterPools(@As(",") String[] ids, String arrayId) {
        deregisterPools(uris(ids), arrayId);
    }

    private static void deregisterPools(List<URI> ids, String arrayId) {
        performSuccess(ids, new DeregisterPoolOperation(), DEREGISTER_SUCCESS);
        pools(arrayId);
    }

    public static void registerPools(@As(",") String[] ids, String arrayId) {
        registerPools(uris(ids), arrayId);
    }

    private static void registerPools(List<URI> ids, String arrayId) {
        performSuccess(ids, new RegisterPoolOperation(arrayId), REGISTER_SUCCESS);
        pools(arrayId);
    }

    public static void arrayPoolsJson(String id) {
        List<StoragePoolInfo> results = Lists.newArrayList();
        List<StoragePoolRestRep> storagePools = StoragePoolUtils.getStoragePools(id);
        for (StoragePoolRestRep storagePool : storagePools) {
            if (!DiscoveredDataObject.DiscoveryStatus.NOTVISIBLE.name().equals(storagePool.getDiscoveryStatus())) {
                results.add(new StoragePoolInfo(storagePool));
            }
        }
        renderJSON(DataTablesSupport.createJSON(results, params));
    }

    public static void pools(String id) {
        addReferenceData();

        StorageSystemRestRep storageSystem = StorageSystemUtils.getStorageSystem(id);
        StorageArrayPoolDataTable dataTable = new StorageArrayPoolDataTable();
        if (StorageSystemTypes.isFileStorageSystem(storageSystem.getSystemType())) {
            dataTable.configureForFile();
        }
        render("@listPools", storageSystem, dataTable);
    }

    public static void editPool(String id, String poolId) {
        StoragePoolRestRep storagePool = StoragePoolUtils.getStoragePool(poolId);
        StorageSystemRestRep storageSystem = StorageSystemUtils.getStorageSystem(id(storagePool.getStorageSystem()));
        if (RegistrationStatus.isUnregistered(storageSystem.getRegistrationStatus())) {
            flash.put("warning", MessagesUtils.get(NOT_REGISTERED, storageSystem.getName()));
        }
        StorageArrayPoolForm storageArrayPool = new StorageArrayPoolForm();
        storageArrayPool.readFrom(storagePool);
        render(storageArrayPool, storagePool, storageSystem);
    }

    @FlashException(keep=true, referrer={"editPool"})
    public static void savePool(StorageArrayPoolForm storageArrayPool) {
        storageArrayPool.validate("storageArrayPool");
        if (Validation.hasErrors()) {
            Common.handleError();
        }
        
        StoragePoolRestRep pool = storageArrayPool.save();
        flash.success(MessagesUtils.get(SAVED_POOL, pool.getPoolName()));
        pools(stringId(pool.getStorageSystem()));
    }

    public static class StorageArrayPoolForm {

        public String storageArrayId;

        public String id;

        @MaxSize(128)
        @MinSize(2)
        public String name;

        @Required
        @Min(0)
        @Max(100)
        public Integer maxPoolUtilizationPercentage;

        @Min(0)
        public Integer maxThinPoolSubscriptionPercentage;

        @Min(0)
        public Integer resourceLimit;

        public String poolType;
        public boolean supportsThinProvisioning;

        public StorageArrayPoolForm() {
        }

        public void readFrom(StoragePoolRestRep storagePool) {
            this.storageArrayId = stringId(storagePool.getStorageSystem());
            this.id = stringId(storagePool);
            this.name = storagePool.getPoolName();
            this.maxPoolUtilizationPercentage = storagePool.getMaxPoolUtilizationPercentage();
            if ((storagePool.getMaxResources() != null) && (storagePool.getMaxResources() > -1)) {
                this.resourceLimit = storagePool.getMaxResources();
            }
            this.poolType = storagePool.getPoolServiceType();
            this.supportsThinProvisioning = StoragePoolUtils.supportsThinProvisioning(storagePool);
            if (supportsThinProvisioning) {
                this.maxThinPoolSubscriptionPercentage = storagePool.getMaxThinPoolSubscriptionPercentage();
            }
        }

        public StoragePoolRestRep save() {
            return update();
        }

        private StoragePoolRestRep update() {
            StoragePoolUpdate storagePoolParam = new StoragePoolUpdate();
            storagePoolParam.setMaxPoolUtilizationPercentage(this.maxPoolUtilizationPercentage);
            if (maxThinPoolSubscriptionPercentage != null) {
                storagePoolParam.setMaxThinPoolSubscriptionPercentage(this.maxThinPoolSubscriptionPercentage);
            }

            if ((resourceLimit != null) && (resourceLimit > -1)) {
                storagePoolParam.setMaxResources(this.resourceLimit);
                storagePoolParam.setIsUnlimitedResourcesSet(false);
            }
            else {
                storagePoolParam.setMaxResources(null);
                storagePoolParam.setIsUnlimitedResourcesSet(true);
            }

            return StoragePoolUtils.update(id, storagePoolParam);
        }

        public void validate(String fieldName) {
            Validation.valid(fieldName, this);
        }

    }

    public static class StorageArrayPortForm {

        public URI storageArrayId;

        public URI id;

        @Required
        @MinSize(2)
        @MaxSize(128)
        public String name;

        @Required
        public String portType;

        @Required
        public String port;

        public void readFrom(StoragePortRestRep storagePort) {
            storageArrayId = storagePort.getStorageDevice().getId();
            id = storagePort.getId();
            name = storagePort.getPortName();
            portType = storagePort.getTransportType();
            port = storagePort.getPortNetworkId();
        }

        public StoragePortRestRep save() {
            return (id == null) ? create() : update();
        }

        private StoragePortRestRep create() {
            StoragePortRequestParam storagePortParam = new StoragePortRequestParam();
            storagePortParam.setName(name);
            storagePortParam.setTransportType(portType);
            storagePortParam.setPortNetworkId(port);

            return StoragePortUtils.create(storageArrayId, storagePortParam);
        }

        private StoragePortRestRep update() {
            StoragePortUpdate storagePortParam = new StoragePortUpdate();
            storagePortParam.setPortNetworkId(port);

            return StoragePortUtils.update(id, storagePortParam);
        }

        public void validate(String fieldName) {
            Validation.valid(fieldName, this);

            if (BlockProtocols.isFC(portType)) {
                if (port != null && !EndpointUtility.isValidEndpoint(port, Endpoint.EndpointType.WWN)) {
                    Validation.addError(fieldName + ".port", "storageArrayPort.port.invalidWWN");
                }
            }
            else {
                boolean valid = EndpointUtility.isValidEndpoint(port, Endpoint.EndpointType.IQN);
                if (!valid) {
                    Validation.addError(fieldName + ".port", "storageArrayPort.port.invalidIQN");
                }
            }
        }
    }

    public static class StorageSystemForm {
        public String id;

        @Required
        public String type;

        @MaxSize(2048)
        @Required
        public String name;

        @Required
        @HostNameOrIpAddress
        public String ipAddress;

        @Required
        public Integer portNumber;

        @MaxSize(2048)
        public String userName;

        @MaxSize(2048)
        public String userPassword;

        @MaxSize(2048)
        public String confirmPassword;
        
        @MaxSize(2048)
        public String secondaryUsername = "";
        
        @MaxSize(2048)
        public String secondaryPassword = "";
        
        @MaxSize(2048)
        public String secondaryPasswordConfirm = "";
        
        public String elementManagerURL;

        public boolean useSSL;

        public Integer resourceLimit;
        public String resourceType;
        public boolean unlimitResource;

        //
        // a flag to set if unlimitResource control was previously visible.
        // This is used upon save failure for UI to correctly recover UnlimitResource control
        // state after save failure.
        //
        public boolean unlimitResourceWasVisible;

        //
        // onboard smis provider attributes
        //
        public String smisProviderIpAddress;
        public Integer smisProviderPortNumber;
        public String smisProviderUserName;
        public String smisProviderUserPassword;
        public String smisProviderConfirmPassword;
        public boolean smisProviderUseSSL;

        public String referrerUrl;

        public boolean unregistered;

        public StorageSystemForm() {
            this.userPassword = "";
            this.confirmPassword = "";
            this.smisProviderUserPassword = "";
            this.smisProviderConfirmPassword = "";
        }

        public StorageSystemForm(StorageSystemRestRep storageArray) {
            this.id = storageArray.getId().toString();
            this.name = StorageSystemUtils.getName(storageArray);
            this.type = storageArray.getSystemType();
            // VNX Block uses the same select option as VMAX
            if (StorageSystemTypes.isVnxBlock(type)) {
                this.type = StorageSystemTypes.VMAX;
            }
            this.resourceType = PoolTypes.fromStorageSystemType(storageArray.getSystemType());
            this.userName = storageArray.getUsername();
            this.resourceLimit = storageArray.getMaxResources() != null ? storageArray.getMaxResources() : 0;
            this.unlimitResource = this.unlimitResourceWasVisible = this.resourceLimit != null
                    && this.resourceLimit == -1;
            this.unregistered = RegistrationStatus.isUnregistered(storageArray.getRegistrationStatus());

            if (isStorageProviderManaged()) {
                this.useSSL = storageArray.getSmisUseSSL();
                this.portNumber = storageArray.getSmisPortNumber();
                this.ipAddress = storageArray.getSmisProviderIP();
            }
            else {
                this.portNumber = storageArray.getPortNumber();
                this.ipAddress = storageArray.getIpAddress();

                this.smisProviderIpAddress = storageArray.getSmisProviderIP();
                this.smisProviderPortNumber = storageArray.getSmisPortNumber();
                this.smisProviderUseSSL = storageArray.getSmisUseSSL();
                this.smisProviderUserName = storageArray.getSmisUserName();
            }
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }

        public Task<StorageSystemRestRep> update() {
            StorageSystemUpdateRequestParam storageArray = new StorageSystemUpdateRequestParam();
            storageArray.setName(name);

            // if unlimit resources was unchecked, set the given limit
            if (!unlimitResource) {
                storageArray.setMaxResources(resourceLimit);
                storageArray.setIsUnlimitedResourcesSet(false);
                // allow changing back to unlimited
            }
            else {
                storageArray.setIsUnlimitedResourcesSet(true);
                storageArray.setMaxResources(null);
            }

            if (isVnxFile()) {
                storageArray.setSmisProviderIP(smisProviderIpAddress);
                storageArray.setSmisPortNumber(smisProviderPortNumber);
                storageArray.setSmisUseSSL(smisProviderUseSSL);
                storageArray.setSmisPassword(StringUtils.trimToNull(smisProviderUserPassword));
                storageArray.setSmisUserName(StringUtils.trimToNull(smisProviderUserName));
            }

            if (!isStorageProviderManaged()) {
                storageArray.setIpAddress(ipAddress);
                storageArray.setPortNumber(portNumber);
                storageArray.setPassword(StringUtils.trimToNull(userPassword));
                storageArray.setUserName(StringUtils.trimToNull(userName));
            }

            return StorageSystemUtils.update(id, storageArray);
        }

        public Task<StorageSystemRestRep> create() {
            StorageSystemRequestParam storageArray = new StorageSystemRequestParam();
            storageArray.setName(name);
            storageArray.setSystemType(type);

            storageArray.setPassword(userPassword);
            storageArray.setUserName(userName);
            storageArray.setPortNumber(portNumber);
            storageArray.setIpAddress(ipAddress);
            // storageArray.setRegistrationMode(RegistrationMode.SYSTEM);

            if (isVnxFile()) {
                storageArray.setSmisPassword(smisProviderUserPassword);
                storageArray.setSmisUserName(smisProviderUserName);
                storageArray.setSmisPortNumber(smisProviderPortNumber);
                storageArray.setSmisProviderIP(smisProviderIpAddress);
                storageArray.setSmisUseSSL(smisProviderUseSSL);
            }
            return StorageSystemUtils.create(storageArray);
        }

        public Task<StorageProviderRestRep> createStorageProvider() {
            StorageProviderForm storageProviderForm = new StorageProviderForm();
            storageProviderForm.name = this.name;
            storageProviderForm.password = this.userPassword;
            storageProviderForm.userName = this.userName;
            storageProviderForm.useSSL = this.useSSL;
            storageProviderForm.ipAddress = this.ipAddress;
            storageProviderForm.portNumber = this.portNumber;
            storageProviderForm.interfaceType = StorageProviderTypes.fromStorageArrayType(this.type);
            storageProviderForm.secondaryUsername = this.secondaryUsername;
            storageProviderForm.secondaryPassword = this.secondaryPassword;
            storageProviderForm.elementManagerURL = this.elementManagerURL;

            return storageProviderForm.create();
        }

        public Task<?> save() {
            if (isNew()) {
                if (isStorageProviderManaged()) {
                    return createStorageProvider();
                }
                else {
                    return create();
                }
            }
            else {
                return update();
            }
        }

        public void validate(String fieldName) {
            Validation.valid(fieldName, this);

            if (isVnxFile()) {
                Validation.required(fieldName + ".smisProviderIpAddress", this.smisProviderIpAddress);
                Validation.required(fieldName + ".smisProviderPortNumber", this.smisProviderPortNumber);
            }

            if (isNew()) {
                Validation.required(fieldName + ".userName", this.userName);
                Validation.required(fieldName + ".userPassword", this.userPassword);
                Validation.required(fieldName + ".confirmPassword", this.confirmPassword);

                if (isVnxFile()) {
                    Validation.required(fieldName + ".smisProviderUserName", this.smisProviderUserName);
                    Validation.required(fieldName + ".smisProviderUserPassword", this.smisProviderUserPassword);
                    Validation.required(fieldName + ".smisProviderConfirmPassword", this.smisProviderConfirmPassword);
                }
                
                if (isScaleIO() && !isMatchingPasswords(secondaryPassword, secondaryPasswordConfirm)) {
                    Validation.addError(fieldName + ".secondaryPasswordConfirm",
                            MessagesUtils.get("storageArray.secondaryPassword.confirmPassword.not.match"));
                }
            }
            else {
                if (!unlimitResource) {
                    Validation.required(fieldName + ".resourceLimit", this.resourceLimit);
                    Validation.min(fieldName + ".resourceLimit", this.resourceLimit, 0);
                }
            }

            if (!isMatchingPasswords(userPassword, confirmPassword)) {
                Validation.addError(fieldName + ".confirmPassword",
                        MessagesUtils.get("storageArray.confirmPassword.not.match"));
            }

            if (isVnxFile()) {
                if (!isMatchingPasswords(smisProviderUserPassword, smisProviderConfirmPassword)) {
                    Validation.addError(fieldName + ".smisProviderConfirmPassword",
                            MessagesUtils.get("storageArray.confirmPassword.not.match"));
                }

                Validation.required(fieldName + ".smisProviderIpAddress", this.smisProviderIpAddress);
                Validation.required(fieldName + ".smisProviderPortNumber", this.smisProviderPortNumber);
            }
        }

        private boolean isMatchingPasswords(String password, String confirm) {
            return StringUtils.equals(StringUtils.trimToEmpty(password), StringUtils.trimToEmpty(confirm));
        }

        private boolean isStorageProviderManaged() {
            return StorageSystemTypes.isStorageProvider(type);
        }

        private boolean isVnxFile() {
            return StorageSystemTypes.isVnxFile(type);
        }
        
        private boolean isScaleIO() {
        	return StorageSystemTypes.isScaleIO(type);
        }
    }

    protected static class JsonItemOperation implements ResourceValueOperation<StorageSystemInfo, StorageSystemRestRep> {
        @Override
        public StorageSystemInfo performOperation(StorageSystemRestRep storageSystem) throws Exception {
            return new StorageSystemInfo(storageSystem);
        }
    }

    protected static class DeleteOperation implements
            ResourceValueOperation<Task<StorageSystemRestRep>, StorageSystemRestRep> {
        @Override
        public Task<StorageSystemRestRep> performOperation(StorageSystemRestRep storageSystem) throws Exception {
            if (RegistrationStatus.isRegistered(storageSystem.getRegistrationStatus())) {
                StorageSystemUtils.deregister(id(storageSystem));
            }
            Task<StorageSystemRestRep> task = StorageSystemUtils.deactivate(id(storageSystem));
            return task;
        }
    }

    protected static class DiscoveryOperation implements ResourceIdOperation<Task<StorageSystemRestRep>> {
        @Override
        public Task<StorageSystemRestRep> performOperation(URI id) throws Exception {
            Task<StorageSystemRestRep> task = StorageSystemUtils.discover(id);
            return task;
        }
    }

    protected static class DeregisterOperation implements ResourceIdOperation<StorageSystemRestRep> {
        @Override
        public StorageSystemRestRep performOperation(URI id) throws Exception {
            return StorageSystemUtils.deregister(id);
        }
    }

    protected static class RegisterOperation implements ResourceIdOperation<StorageSystemRestRep> {
        @Override
        public StorageSystemRestRep performOperation(URI id) throws Exception {
            return StorageSystemUtils.register(id);
        }
    }

    protected static class DeregisterPortOperation implements ResourceIdOperation<Void> {
        @Override
        public Void performOperation(URI id) throws Exception {
            StoragePortUtils.deregister(id);
            return null;
        }
    }

    protected static class RegisterPortOperation implements ResourceIdOperation<Void> {
        private URI arrayId;

        public RegisterPortOperation(String arrayId) {
            this.arrayId = uri(arrayId);
        }

        @Override
        public Void performOperation(URI id) throws Exception {
            StoragePortUtils.register(id, arrayId);
            return null;
        }
    }

    protected static class DeregisterPoolOperation implements ResourceIdOperation<Void> {
        @Override
        public Void performOperation(URI id) throws Exception {
            StoragePoolUtils.deregister(id);
            return null;
        }
    }

    protected static class RegisterPoolOperation implements ResourceIdOperation<Void> {
        private URI arrayId;

        public RegisterPoolOperation(String arrayId) {
            this.arrayId = uri(arrayId);
        }

        @Override
        public Void performOperation(URI id) throws Exception {
            StoragePoolUtils.deregister(id);
            StoragePoolUtils.register(id, arrayId);
            return null;
        }
    }

    public static class StorageArrayPoolDataTable extends StoragePoolDataTable {
        public StorageArrayPoolDataTable() {
            alterColumn("name").setRenderFunction("renderStorageArrayPoolEditLink");
            alterColumn("storageSystem").hidden();
        }

        public void configureForFile() {
            alterColumns("driveTypes", "subscribedCapacity").hidden();
        }
    }

    public static class StorageArrayPortDataTable extends StoragePortDataTable {
        public StorageArrayPortDataTable(StorageSystemRestRep storageSystem) {
            alterColumn("name").setRenderFunction("renderStorageArrayPortEditLink");
            if (StorageSystemTypes.isBlockStorageSystem(storageSystem.getSystemType())) {
                alterColumn("iqn").hidden();
            }
        }
    }
}
