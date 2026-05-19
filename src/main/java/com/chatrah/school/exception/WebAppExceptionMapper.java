// src/main/java/com/chatrah/school/exception/WebAppExceptionMapper.java
package com.chatrah.school.exception;

import com.chatrah.school.dto.ErrorResponseDTO;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Exception mapper for JAX-RS WebApplicationException.
 * Converts thrown WebApplicationException into a structured JSON response.
 */
@Provider
public class WebAppExceptionMapper implements ExceptionMapper<WebApplicationException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(WebApplicationException exception) {
        Response original = exception.getResponse();
        int status = original != null ? original.getStatus() : 500;

        ErrorResponseDTO body = new ErrorResponseDTO();
        body.setStatus(status);
        body.setError(original != null && original.getStatusInfo() != null
                ? original.getStatusInfo().getReasonPhrase()
                : "Error");
        body.setMessage(exception.getMessage());
        if (uriInfo != null && uriInfo.getRequestUri() != null) {
            body.setPath(uriInfo.getRequestUri().getPath());
        }

        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }
}
