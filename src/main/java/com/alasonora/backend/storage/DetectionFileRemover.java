package com.alasonora.backend.storage;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.alasonora.backend.config.SupabaseProperties;
import com.alasonora.backend.entity.Detection;

/** Borro de Storage el audio y la foto del observador de una detección ya eliminada. */
@Component
public class DetectionFileRemover {

    private static final Logger log = LoggerFactory.getLogger(DetectionFileRemover.class);

    private final SupabaseStorageObjectRemover remover;
    private final SupabaseProperties.Storage storage;

    public DetectionFileRemover(SupabaseStorageObjectRemover remover, SupabaseProperties supabaseProperties) {
        this.remover = remover;
        this.storage = supabaseProperties.storage();
    }

    public void removeFilesOf(Detection detection) {
        removeQuietly(storage.recordingsBucket(), detection.getAudioStoragePath());
        removeQuietly(storage.observerPhotosBucket(), detection.getObserverPhotoStoragePath());
    }

    // Si Storage falla solo lo registro: la detección ya se borró y no la quiero dejar a medias.
    private void removeQuietly(String bucket, String storagePath) {
        if (storagePath == null) return;
        try {
            if (remover.remove(bucket, List.of(storagePath)) == 0) {
                log.warn("Storage did not remove '{}' from bucket '{}'; the detection is already deleted", storagePath, bucket);
            }
        } catch (RuntimeException ex) {
            log.warn("Could not remove '{}' from bucket '{}'; the detection is already deleted", storagePath, bucket, ex);
        }
    }
}
