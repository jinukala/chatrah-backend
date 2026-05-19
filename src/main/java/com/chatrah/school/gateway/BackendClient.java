package com.chatrah.school.gateway;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/")
@RegisterRestClient(configKey = "backend-api")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface BackendClient {

    @GET
    @Path("{path: .*}")
    Object forwardGet(@PathParam("path") String path,
                      @HeaderParam("Authorization") String auth);

    @POST
    @Path("{path: .*}")
    Object forwardPost(@PathParam("path") String path,
                       Object body,
                       @HeaderParam("Authorization") String auth);

    @PUT
    @Path("{path: .*}")
    Object forwardPut(@PathParam("path") String path,
                      Object body,
                      @HeaderParam("Authorization") String auth);

    @DELETE
    @Path("{path: .*}")
    Object forwardDelete(@PathParam("path") String path,
                         @HeaderParam("Authorization") String auth);
}
