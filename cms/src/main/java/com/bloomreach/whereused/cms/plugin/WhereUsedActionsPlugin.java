package com.bloomreach.whereused.cms.plugin;

import com.bloomreach.whereused.common.WhereUsedService;
import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.EmptyPanel;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.StringResourceModel;
import org.apache.wicket.util.io.IClusterable;
import org.hippoecm.addon.workflow.MenuDescription;
import org.hippoecm.addon.workflow.StdWorkflow;
import org.hippoecm.addon.workflow.WorkflowDescriptorModel;
import org.hippoecm.frontend.plugin.IPluginContext;
import org.hippoecm.frontend.plugin.config.IPluginConfig;
import org.hippoecm.frontend.plugins.reviewedactions.AbstractDocumentWorkflowPlugin;
import org.hippoecm.frontend.plugins.standards.icon.HippoIcon;
import org.hippoecm.frontend.service.IRestProxyService;
import org.hippoecm.frontend.session.UserSession;
import org.hippoecm.frontend.skin.Icon;
import org.hippoecm.hst.rest.beans.ChannelDocument;
import org.hippoecm.repository.api.HippoNodeType;
import org.hippoecm.repository.api.Workflow;
import org.hippoecm.repository.api.WorkflowException;
import org.hippoecm.repository.api.WorkflowManager;
import org.onehippo.cms7.channelmanager.restproxy.RestProxyServicesManager;
import org.onehippo.cms7.channelmanager.service.IChannelManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.onehippo.cms7.channelmanager.restproxy.RestProxyServicesManager.submitJobs;

/**
 * @version "\$Id$" kenan
 */
public class WhereUsedActionsPlugin extends AbstractDocumentWorkflowPlugin {

    private static final Logger log = LoggerFactory.getLogger(WhereUsedActionsPlugin.class);

    public static final String CONFIG_CHANNEL_MANAGER_SERVICE_ID = "channel.manager.service.id";

    private final IChannelManagerService channelManagerService;

    public WhereUsedActionsPlugin(IPluginContext context, IPluginConfig config) {
        super(context, config);
        channelManagerService = loadService("channel manager service", CONFIG_CHANNEL_MANAGER_SERVICE_ID, IChannelManagerService.class);
        if (channelManagerService != null) {
            WorkflowDescriptorModel model = (WorkflowDescriptorModel) getDefaultModel();
            if (model != null) {
                try {
                    Node node = model.getNode();
                    if (node.isNodeType(HippoNodeType.NT_HANDLE)) {
                        WorkflowManager workflowManager = UserSession.get().getWorkflowManager();
                        Workflow workflow = workflowManager.getWorkflow(model.getObject());
                        if (Boolean.TRUE.equals(workflow.hints().get("previewAvailable"))) {
                            addMenuDescription(model);
                        }
                    }
                } catch (RepositoryException | RemoteException | WorkflowException e) {
                    log.error("Error getting document node from WorkflowDescriptorModel", e);
                }
            }
        }
        add(new EmptyPanel("used"));
    }

    private void addMenuDescription(final WorkflowDescriptorModel model) {
        add(new MenuDescription() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getLabel() {
                Fragment fragment = new Fragment("label", "description", WhereUsedActionsPlugin.this);
                fragment.add(new Label("label", new StringResourceModel("label", WhereUsedActionsPlugin.this, null)));
                return fragment;
            }

            @Override
            public MarkupContainer getContent() {
                Fragment fragment = new Fragment("used", "actions", WhereUsedActionsPlugin.this);
                try {
                    Node node = model.getNode();
                    String handleUuid = node.getIdentifier();
                    fragment.add(createMenu(handleUuid));
                } catch (RepositoryException e) {
                    log.warn("Unable to create channel menu", e);
                    fragment.addOrReplace(new EmptyPanel("whereused"));
                }
                WhereUsedActionsPlugin.this.addOrReplace(fragment);
                return fragment;
            }
        });
    }

    private <T extends IClusterable> T loadService(final String name, final String configServiceId, final Class<T> clazz) {
        final String serviceId = getPluginConfig().getString(configServiceId, clazz.getName());
        log.debug("Using {} with id '{}'", name, serviceId);

        final T service = getPluginContext().getService(serviceId, clazz);
        if (service == null) {
            log.info("Could not get service '{}' of type {}", serviceId, clazz.getName());
        }

        return service;
    }

    private MarkupContainer createMenu(final String documentUuid) {

        final Map<String, IRestProxyService> liveRestProxyServices = RestProxyServicesManager.getLiveRestProxyServices(getPluginContext(), getPluginConfig());
        if (liveRestProxyServices.isEmpty()) {
            log.info("No rest proxies services available. Cannot create menu for available channels");
            return new EmptyPanel("whereused");
        }

        // a rest proxy can only return ChannelDocument for the webapp the proxy belongs to. Hence we need to
        // invoke all rest proxies to get all available channel documents
        List<Callable<ArrayList<ChannelDocument>>> restProxyJobs = new ArrayList<>();

        for (final Map.Entry<String, IRestProxyService> entry : liveRestProxyServices.entrySet()) {
            final WhereUsedService whereUsedService = entry.getValue().createSecureRestProxy(WhereUsedService.class);
            restProxyJobs.add(() -> whereUsedService.getChannels(documentUuid));
        }

        final ArrayList<ChannelDocument> combinedChannelDocuments = new ArrayList<>();
        final List<Future<ArrayList<ChannelDocument>>> futures = submitJobs(restProxyJobs);


        for (Future<ArrayList<ChannelDocument>> future : futures) {
            try {
                combinedChannelDocuments.addAll(future.get());
            } catch (InterruptedException | ExecutionException e) {
                if (log.isDebugEnabled()) {
                    log.warn("Exception while trying to find Channel for document with uuid '{}'.", documentUuid, e);
                } else {
                    log.warn("Exception while trying to find Channel for document with uuid '{}' : {}", documentUuid, e.toString());
                }
            }
        }

        Collections.sort(combinedChannelDocuments, getChannelDocumentComparator());

        final Map<String, ChannelDocument> idToChannelMap = new LinkedHashMap<String, ChannelDocument>();

        for (final ChannelDocument channelDocument : combinedChannelDocuments) {
            idToChannelMap.put(channelDocument.getChannelId() + channelDocument.getPathInfo(), channelDocument);
        }

        return new ListView<String>("whereused", new LoadableDetachableModel<List<String>>() {

            @Override
            protected List<String> load() {
                if (!combinedChannelDocuments.isEmpty()) {
                    return new ArrayList<>(idToChannelMap.keySet());
                } else {
                    return Arrays.asList("<empty>");
                }
            }
        }) {

            {
                onPopulate();
            }

            @Override
            protected void populateItem(final ListItem<String> item) {
                final String channelId = item.getModelObject();
                ChannelDocument channel = idToChannelMap.get(channelId);
                if (channel != null) {
                    item.add(new ViewWhereUsed("view-whereused", channel));
                } else {
                    item.add(new WhereUsedActionsPlugin.ViewWhereUsedUnavailable("view-whereused"));
                }
            }
        };

    }

    private static final Comparator<ChannelDocument> DEFAULT_CHANNEL_DOCUMENT_COMPARATOR = new WhereUsedActionsPlugin.ChannelDocumentNameComparator();

    protected Comparator<ChannelDocument> getChannelDocumentComparator() {
        return DEFAULT_CHANNEL_DOCUMENT_COMPARATOR;
    }


    private class ViewWhereUsedUnavailable extends StdWorkflow {

        public ViewWhereUsedUnavailable(final String id) {
            super(id, "whereused" + id);
            setEnabled(false);
        }

        @Override
        public String getSubMenu() {
            return "whereused";
        }

        @Override
        protected Component getIcon(final String id) {
            return HippoIcon.fromSprite(id, Icon.GLOBE);
        }

        @Override
        protected IModel getTitle() {
            return new StringResourceModel("unavailable", WhereUsedActionsPlugin.this, null);
        }
    }

    private class ViewWhereUsed extends StdWorkflow {

        private static final long serialVersionUID = 1L;
        private ChannelDocument channelDocument;

        ViewWhereUsed(String id, ChannelDocument channelDocument) {
            super(id, "whereused" + id);
            this.channelDocument = channelDocument;
        }

        @Override
        public String getSubMenu() {
            return "whereused";
        }

        @Override
        protected IModel<String> getTitle() {
            return new LoadableDetachableModel<String>() {
                private static final long serialVersionUID = 1L;

                @Override
                protected String load() {
                    final boolean root = channelDocument.getPathInfo().equals("/") || channelDocument.getPathInfo().equals("");
                    String path = root ? "/ (root)" : channelDocument.getPathInfo();
                    return channelDocument.getChannelName() + ": " + path;
                }
            };
        }

        @Override
        protected Component getIcon(final String id) {
            return HippoIcon.fromSprite(id, Icon.GLOBE);
        }

        @Override
        protected void invoke() {
            if (channelManagerService != null) {
                channelManagerService.viewChannel(channelDocument.getChannelId(), channelDocument.getPathInfo());
            } else {
                log.info("Cannot view channel, no channel manager service available");
            }
        }
    }

    protected static class ChannelDocumentNameComparator implements Comparator<ChannelDocument>, Serializable {

        @Override
        public int compare(final ChannelDocument o1, final ChannelDocument o2) {
            return o1.getChannelName().compareTo(o2.getChannelName());
        }

    }


}

