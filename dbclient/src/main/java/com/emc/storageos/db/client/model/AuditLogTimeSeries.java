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

import com.emc.storageos.db.client.TimeSeriesMetadata;

/**
 * CF definition for auditlog time series data
 */
@Cf("AuditLogs")
@Shards(10)
@BucketGranularity(TimeSeriesMetadata.TimeBucket.HOUR)
@Ttl(60 * 60 * 24 * 90 /* 90 days */)
public class AuditLogTimeSeries implements TimeSeries<AuditLog> {
    private AuditLogSerializer _serializer = new AuditLogSerializer();

    @Override
    public AuditLogSerializer getSerializer() {
        return _serializer;
    }

    /**
     * AuditLog serializer implementation
     */
    public static class AuditLogSerializer implements TimeSeriesSerializer<AuditLog> {
        private GenericSerializer _genericSerializer = new GenericSerializer();
        @Override
        public byte[] serialize(AuditLog data) {
            return _genericSerializer.toByteArray(AuditLog.class, data);
        }

        @Override
        public AuditLog deserialize(byte[] data) {
            return _genericSerializer.fromByteArray(AuditLog.class, data);
        }
    }
}
