package com.chatrah.school.config;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Rejects any request that doesn't carry the correct X-Gateway-Secret header.
 * This ensures only the API gateway can talk to the backend.
 */
@Provider
@Priority(Priorities.AUTHENTICATION - 200)
public class GatewaySecretFilter implements ContainerRequestFilter {

    @ConfigProperty(name = "chatrah.gateway.secret")
    String expectedSecret;

    @Override
    public void filter(ContainerRequestContext ctx) {
        // Allow health/readiness probes
        String path = ctx.getUriInfo().getPath();
        if (path.startsWith("q/") || path.equals("health") || path.equals("ready")) {
            return;
        }

        String provided = ctx.getHeaderString("X-Gateway-Secret");
        if (provided == null || !provided.equals(expectedSecret)) {
            ctx.abortWith(Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"error\":\"Direct access forbidden\"}")
                    .header("Content-Type", "application/json")
                    .build());
        }
    }
}
