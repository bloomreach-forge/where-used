package com.bloomreach.whereused.common;

import org.hippoecm.hst.rest.beans.ChannelDocument;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;

@Produces({MediaType.APPLICATION_JSON})
@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED})
@Path("/locate/")
public interface WhereUsedService {

    @GET
    @Path("/{uuid}/")
    @Produces(MediaType.APPLICATION_JSON)
    ArrayList<ChannelDocument> getChannels(@PathParam("uuid") String uuid);
}
