/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package models;

import java.util.List;

import util.StringOption;

import com.google.common.collect.Lists;

public class StorageSystemTypes {
    private static final String OPTION_PREFIX = "StorageSystemType";
    public static final String NONE = "NONE";
    public static final String ISILON = "isilon";
    public static final String VNX_BLOCK = "vnxblock";
    public static final String VNXe = "vnxe";
    public static final String VNX_FILE = "vnxfile";
    public static final String VMAX = "vmax";
    public static final String NETAPP = "netapp";
    public static final String NETAPPC = "netappc";
    public static final String HITACHI = "hds";
    public static final String IBMXIV = "ibmxiv";
    public static final String VPLEX = "vplex";
    public static final String OPENSTACK = "openstack";
    public static final String SCALEIO = "scaleio";
    public static final String XTREMIO = "xtremio";
    public static final String DATA_DOMAIN = "datadomain";
    public static final String STORAGE_PROVIDER_VMAX = "STORAGE_PROVIDER.vmax";
    public static final String STORAGE_PROVIDER_HITACHI = "STORAGE_PROVIDER.hds";
    public static final String STORAGE_PROVIDER_VPLEX = "STORAGE_PROVIDER.vplex";
    public static final String STORAGE_PROVIDER_OPENSTACK = "STORAGE_PROVIDER.cinder";
    public static final String STORAGE_PROVIDER_SCALEIO = "STORAGE_PROVIDER.scaleio";
    public static final String STORAGE_PROVIDER_DATA_DOMAIN = "STORAGE_PROVIDER.ddmc";
    public static final String STORAGE_PROVIDER_IBMXIV = "STORAGE_PROVIDER.ibmxiv";

    public static final String[] BLOCK_TYPES = { VMAX, VNX_BLOCK, VPLEX, HITACHI, OPENSTACK, SCALEIO, XTREMIO, VNXe, IBMXIV };
    public static final String[] FILE_TYPES = { ISILON, VNX_FILE, NETAPP, DATA_DOMAIN, VNXe, NETAPPC };
    public static final String[] STORAGE_PROVIDER_TYPES = { VMAX, VNX_BLOCK, HITACHI, VPLEX, OPENSTACK, SCALEIO, DATA_DOMAIN, IBMXIV };
    public static final String[] NON_SMIS_TYPES = { ISILON, VNX_FILE, NETAPP, XTREMIO, VNXe, NETAPPC };

    public static final StringOption[] OPTIONS = {
        option(ISILON),
        option(VNX_FILE),
        option(NETAPP),
        option(XTREMIO),
        option(VNXe),
        option(NETAPPC),
        new StringOption(VMAX, getDisplayValue(STORAGE_PROVIDER_VMAX)),
        new StringOption(VPLEX, getDisplayValue(STORAGE_PROVIDER_VPLEX)),
        new StringOption(HITACHI, getDisplayValue(STORAGE_PROVIDER_HITACHI)),
        new StringOption(OPENSTACK, getDisplayValue(STORAGE_PROVIDER_OPENSTACK)),
        new StringOption(SCALEIO, getDisplayValue(STORAGE_PROVIDER_SCALEIO)),
        new StringOption(DATA_DOMAIN, getDisplayValue(STORAGE_PROVIDER_DATA_DOMAIN)),
        new StringOption(IBMXIV, getDisplayValue(STORAGE_PROVIDER_IBMXIV))
    };

    public static final StringOption[] SMIS_OPTIONS = StringOption.options(STORAGE_PROVIDER_TYPES, OPTION_PREFIX);
    public static final StringOption[] NON_SMIS_OPTIONS = StringOption.options(NON_SMIS_TYPES, OPTION_PREFIX);
    public static final StringOption[] SSL_DEFAULT_OPTIONS = StringOption.options(new String[]{VNX_BLOCK, VMAX, VPLEX, VNX_FILE, VNXe, IBMXIV}, OPTION_PREFIX);
    public static final StringOption[] NON_SSL_OPTIONS = StringOption.options(new String[]{SCALEIO, XTREMIO});
    public static final StringOption[] MDM_DEFAULT_OPTIONS = StringOption.options(new String[]{SCALEIO});
    public static final StringOption[] ELEMENT_MANAGER_OPTIONS= StringOption.options(new String[]{SCALEIO});
    
    public static boolean isNone(String type) {
        return NONE.equals(type);
    }

    public static boolean isIsilon(String type) {
        return ISILON.equals(type);
    }

    public static boolean isVnxBlock(String type) {
        return VNX_BLOCK.equals(type);
    }

    public static boolean isVnxFile(String type) {
        return VNX_FILE.equals(type);
    }

    public static boolean isVmax(String type) {
        return VMAX.equals(type);
    }

    public static boolean isNetapp(String type) {
        return NETAPP.equals(type);
    } 

    public static boolean isNetappc(String type) {
        return NETAPPC.equals(type);
    }

    public static boolean isVplex(String type) {
        return VPLEX.equals(type);
    }

    public static boolean isScaleIO(String type) {
        return SCALEIO.equals(type);
    }

    public static boolean isXtremIO(String type) {
        return XTREMIO.equals(type);
    }
    
    public static boolean isVNXe(String type) {
        return VNXe.equals(type);
    }

    public static boolean isFileStorageSystem(String type) {
        return contains(FILE_TYPES, type);
    }

    public static boolean isBlockStorageSystem(String type) {
        return contains(BLOCK_TYPES, type);
    }

    public static boolean isStorageProvider(String type) {
        return contains(STORAGE_PROVIDER_TYPES, type);
    }

    private static boolean contains(String[] systemTypes, String type) {
        for (String systemType : systemTypes) {
            if (systemType.equals(type)) {
                return true;
            }
        }
        return false;
    }

    public static StringOption option(String type) {
        return new StringOption(type, getDisplayValue(type));
    }

    public static List<StringOption> options(String... types) {
        List<StringOption> options = Lists.newArrayList();
        for (String type : types) {
            options.add(option(type));
        }
        return options;
    }

    public static String getDisplayValue(String type) {
        return StringOption.getDisplayValue(type, OPTION_PREFIX);
    }
}
