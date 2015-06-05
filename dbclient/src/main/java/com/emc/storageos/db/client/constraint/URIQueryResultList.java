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

package com.emc.storageos.db.client.constraint;

import java.net.URI;
import java.util.UUID;

/**
 * URI specialization for convenience
 */
public class URIQueryResultList extends QueryResultList<URI> {
    @Override
    public URI createQueryHit(URI uri) {
        return uri;
    }

    @Override
    public URI createQueryHit(URI uri, String name, UUID timestamp) {
        return uri;
    }
}
