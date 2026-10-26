package com.alasonora.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Límite de los audios a clasificar: el motor los decodifica en memoria con 512 MB, así que es menor que el de Supabase
 * y debe coincidir con environment.audioUpload del frontend.
 */
@ConfigurationProperties(prefix = "audio.upload")
public record AudioUploadProperties(long maxBytes) {
}
