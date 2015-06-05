/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.model;

import com.emc.storageos.db.client.upgrade.AddIndexMigrationCallback;

import java.lang.annotation.*;

/**
 * Index for case insensitive prefix search
 */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@UpgradeAllowed(migrationCallback=AddIndexMigrationCallback.class)
public @interface PrefixIndex {
    String cf() default "";
    int minChars() default 2;
    boolean scoped() default false;
}
