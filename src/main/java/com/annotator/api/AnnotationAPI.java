package com.annotator.api;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/annotations")
public class AnnotationAPI {

    // URL: http://localhost:8080/AapkaProjectName/api/annotations/save/DOC-123
    @POST
    @Path("/save/docid")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveAnnotation(@PathParam("docId") String docId, String jsonPayload) {
        
        System.out.println("📥 Data received for: " + docId);
        // TODO: Yahan aap apna JDBC (MySQL) ka INSERT / UPDATE code likhenge
        // jsonPayload ke andar aapko poora frontend ka JSON string mil jayega
        
        String responseJson = "{\"status\": \"Success\", \"message\": \"Saved perfectly!\"}";
        return Response.ok(responseJson).build();
    }
}