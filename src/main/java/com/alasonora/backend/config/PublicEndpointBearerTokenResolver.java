package com.alasonora.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/** Ignoro el token en los endpoints públicos: si no, uno vencido daba 401 incluso en rutas permitAll. */
public class PublicEndpointBearerTokenResolver implements BearerTokenResolver {

    private final BearerTokenResolver delegate = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        return PublicEndpoints.READ_ONLY.matches(request) ? null : delegate.resolve(request);
    }
}
