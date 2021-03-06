/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.export.ExportGroupRestRep;
import com.emc.storageos.model.block.export.ExportUpdateParam;
import com.emc.storageos.model.block.export.VolumeParam;
import com.emc.storageos.model.block.export.VolumeUpdateParam;
import com.emc.vipr.client.Task;

public class AddVolumesToExport extends WaitForTask<ExportGroupRestRep> {
    private URI exportId;
    private Collection<URI> volumeIds;
    private Integer hlu;

    public AddVolumesToExport(URI exportId, Collection<URI> volumeIds, Integer hlu) {
        super();
        this.exportId = exportId;
        this.volumeIds = volumeIds;
        this.hlu = hlu;
        provideDetailArgs(exportId, volumeIds, hlu);
    }

    @Override
    protected Task<ExportGroupRestRep> doExecute() throws Exception {
        ExportUpdateParam export = new ExportUpdateParam();
        List<VolumeParam> volumes = new ArrayList<VolumeParam>();
        Integer currentHlu = hlu;
        for (URI volumeId : volumeIds) {
            VolumeParam volume = new VolumeParam(volumeId);
            if (currentHlu != null) {
                volume.setLun(currentHlu);
            }
            if ((currentHlu != null) && (currentHlu > -1)) {
                currentHlu++;
            }
            volumes.add(volume);
        }
        export.setVolumes(new VolumeUpdateParam(volumes, new ArrayList<URI>()));
        return getClient().blockExports().update(exportId, export);
    }
}
