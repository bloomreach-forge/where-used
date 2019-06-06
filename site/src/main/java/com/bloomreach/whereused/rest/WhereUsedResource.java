package com.bloomreach.whereused.rest;

import com.bloomreach.whereused.common.WhereUsedService;
import org.apache.commons.lang.StringUtils;
import org.hippoecm.hst.cmsrest.services.BaseResource;
import org.hippoecm.hst.configuration.hosting.Mount;
import org.hippoecm.hst.container.RequestContextProvider;
import org.hippoecm.hst.core.linking.HstLink;
import org.hippoecm.hst.core.linking.HstLinkCreator;
import org.hippoecm.hst.core.request.HstRequestContext;
import org.hippoecm.hst.rest.beans.ChannelDocument;
import org.hippoecm.hst.util.PathUtils;
import org.onehippo.cms7.services.hst.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WhereUsedResource extends BaseResource implements WhereUsedService {

    private static Logger log = LoggerFactory.getLogger(WhereUsedResource.class);

    private HstLinkCreator hstLinkCreator;

    public void setHstLinkCreator(HstLinkCreator hstLinkCreator) {
        this.hstLinkCreator = hstLinkCreator;
    }

    public ArrayList<ChannelDocument> getChannels(String uuid) {

        HstRequestContext requestContext = RequestContextProvider.get();

        Node handle = getNode(requestContext, uuid);
        if (handle == null) {
            return new ArrayList<>();
        }

        List<HstLink> links = hstLinkCreator.createAll(handle, requestContext, getHostGroupNameForCmsHost(), null, true);

        ArrayList<ChannelDocument> channelDocuments = new ArrayList<>(links.size());


        for (HstLink link : links) {
            final Mount linkMount = link.getMount();
            final Channel channel = linkMount.getChannel();

            if (channel == null) {
                log.debug("Skipping link for mount '{}' since it does not have a channel", linkMount.getName());
                continue;
            }

            if (!channelFilter.apply(channel)) {
                log.info("Skipping channel '{}' because filtered out by channel filters", channel.toString());
                continue;
            }

            ChannelDocument document = new ChannelDocument();
            document.setChannelId(channel.getId());
            document.setChannelName(channel.getName());
            if (StringUtils.isNotEmpty(link.getPath())) {
                if (link.getPath().equals("/")) {
                    document.setPathInfo(link.getPath());
                } else {
                    document.setPathInfo("/" + link.getPath());
                }
            } else {
                document.setPathInfo(StringUtils.EMPTY);
            }
            document.setMountPath(link.getMount().getMountPath());
            document.setHostName(link.getMount().getVirtualHost().getHostName());

            // The preview in the cms always accesses the hst site through the hostname of the cms, but
            // adds the contextpath of the website.
            if (link.getMount().getContextPath() != null) {
                document.setContextPath(link.getMount().getContextPath());
            } else {
                // if there is no contextpath configured on the Mount belonging to the HstLink, we use the contextpath
                // from the current HttpServletRequest
                document.setContextPath(requestContext.getServletRequest().getContextPath());
            }

            // set the cmsPreviewPrefix through which prefix after the contextPath the channels can be accessed
            document.setCmsPreviewPrefix(link.getMount().getVirtualHost().getVirtualHosts().getCmsPreviewPrefix());

            channelDocuments.add(document);

        }

        return channelDocuments;
    }

    /**
     * Returns the node with the given UUID using the session of the given request context.
     *
     * @param requestContext the request context
     * @param uuidParam      a UUID
     * @return the node with the given UUID, or null if no such node could be found.
     */
    static Node getNode(final HstRequestContext requestContext, final String uuidParam) {
        if (uuidParam == null) {
            log.info("UUID is null, returning null", uuidParam);
            return null;
        }

        final String uuid = PathUtils.normalizePath(uuidParam);

        try {
            UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            log.info("Illegal UUID: '{}', returning null", uuidParam);
            return null;
        }

        try {
            final Session jcrSession = requestContext.getSession();
            return jcrSession.getNodeByIdentifier(uuid);
        } catch (ItemNotFoundException e) {
            log.warn("Node not found: '{}', returning null", uuid);
        } catch (RepositoryException e) {
            log.warn("Error while fetching node with UUID '" + uuid + "', returning null", e);
        }
        return null;
    }
}
