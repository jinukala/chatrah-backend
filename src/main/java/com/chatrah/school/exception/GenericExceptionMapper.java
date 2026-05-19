package com.chatrah.school.exception;

import com.chatrah.school.dto.ErrorResponseDTO;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;

@Provider
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GenericExceptionMapper.class.getName());

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable exception) {
        String path = uriInfo != null && uriInfo.getRequestUri() != null
                ? uriInfo.getRequestUri().getPath() : "unknown";

        LOG.log(Level.SEVERE, "Unhandled exception at " + path, exception);

        ErrorResponseDTO body = new ErrorResponseDTO();
        body.setStatus(500);
        body.setError("Internal Server Error");
        body.setMessage("An unexpected error occurred. Please contact support.");
        body.setPath(path);

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }
}
