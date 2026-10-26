package com.alasonora.backend.storage;

/** Puerto que convierte el path de un objeto privado de Storage en una URL firmada de vida corta; nunca la guardo. */
public interface PrivateObjectSignedUrlResolver {

    String resolveSignedUrl(String bucket, String storagePath, int ttlSeconds);
}
