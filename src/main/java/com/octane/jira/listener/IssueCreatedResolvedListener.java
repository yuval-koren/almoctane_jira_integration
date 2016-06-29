
package com.octane.jira.listener;

import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.event.type.EventType;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.user.ApplicationUser;
import com.hpe.adm.nga.sdk.NGA;
import com.hpe.adm.nga.sdk.Query;
import com.hpe.adm.nga.sdk.authorisation.Authorisation;
import com.hpe.adm.nga.sdk.metadata.Metadata;
import com.hpe.adm.nga.sdk.model.EntityModel;
import com.hpe.adm.nga.sdk.model.FieldModel;
import com.hpe.adm.nga.sdk.model.ReferenceFieldModel;
import com.hpe.adm.nga.sdk.model.StringFieldModel;
import com.hpe.adm.nga.sdk.utils.CommonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class IssueCreatedResolvedListener implements InitializingBean, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(IssueCreatedResolvedListener.class);
    private static final String DEFECT = "defects";

    private final EventPublisher eventPublisher;

    /**
     * Constructor.
     * @param eventPublisher injected {@code EventPublisher} implementation.
     */
    public IssueCreatedResolvedListener(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    private void setOctaneID(Long issueID, String OctaneID) {
        IssueService issueService = ComponentAccessor.getIssueService();
        IssueInputParameters issueInputParameters = issueService.newIssueInputParameters();
        CustomFieldManager customFieldManager = ComponentAccessor.getCustomFieldManager();
        CustomField field = customFieldManager.getCustomFieldObjectByName("OctaneID");
        issueInputParameters.addCustomFieldValue(field.getId(), OctaneID);
        ApplicationUser user = ComponentAccessor.getUserUtil().getUserByName("admin");

        IssueService.UpdateValidationResult updateValidationResult = issueService.validateUpdate(user, issueID, issueInputParameters);
        if (updateValidationResult.isValid()) {
            issueInputParameters.setSkipScreenCheck(true);
            issueService.update(user, updateValidationResult);
        }
    }

    private String getOctaneID(Issue issue) {
        CustomFieldManager customFieldManager = ComponentAccessor.getCustomFieldManager();
        CustomField field = customFieldManager.getCustomFieldObjectByName("OctaneID");
        return (String) issue.getCustomFieldValue(field);
    }

    @EventListener
    public void onIssueEvent(IssueEvent issueEvent) {
        Long eventTypeId = issueEvent.getEventTypeId();
        Issue issue = issueEvent.getIssue();

        if (eventTypeId.equals(EventType.ISSUE_CREATED_ID)) {
            log.info("Issue {} has been created at {}.", issue.getKey(), issue.getCreated());
            String octaneID = getOctaneID(issue);
            setOctaneID(issue.getId(), "test");
        } else if(eventTypeId.equals(EventType.ISSUE_UPDATED_ID)){
            log.info("Issue {} has been updated", issue.getKey());
        } else if (eventTypeId.equals(EventType.ISSUE_RESOLVED_ID)) {
            log.info("Issue {} has been resolved at {}.", issue.getKey(), issue.getResolutionDate());
        } else if (eventTypeId.equals(EventType.ISSUE_CLOSED_ID)) {
            log.info("Issue {} has been closed at {}.", issue.getKey(), issue.getUpdated());
        }


        //HttpUtils.SetSystemKeepAlive(false);
        //HttpUtils.SetSystemProxy();

        final ConfigurationUtils configuration = ConfigurationUtils.getInstance();
        String url = configuration.getString("sdk.url");
        Authorisation authorisation = AuthorisationUtils.getAuthorisation();
        String sharedSpaceId = configuration.getString("sdk.sharedSpaceId");
        String workspaceId = configuration.getString("sdk.workspaceId");

        NGA nga = ContextUtils.getContextWorkspace(url, authorisation, sharedSpaceId, workspaceId);
        Metadata metadata = nga.metadata();

        Set<FieldModel> fields = new HashSet<FieldModel>();
        Query query = new Query().field("subtype").equal("work_item_root").build();
        Collection<EntityModel> roots = nga.entityList("work_items").get().query(query).execute();
        EntityModel root = roots.iterator().next();

        FieldModel parentField = new ReferenceFieldModel("parent", root);

        Collection<EntityModel> users = nga.entityList("workspace_users").get().execute();
        EntityModel user = users.iterator().next();
        FieldModel author = new ReferenceFieldModel("author", user);

        Query query2 = new Query().field("entity").equal("defect").build();
        Collection<EntityModel> phases = nga.entityList("phases").get().query(query2).execute();
        EntityModel phase = phases.iterator().next();
        FieldModel phaseField = new ReferenceFieldModel("phase", phase);

        FieldModel name = new StringFieldModel("name", "sdk_defect_" + UUID.randomUUID());

        Collection<EntityModel> listNodes = nga.entityList("list_nodes").get().execute();
        EntityModel severity = CommonUtils.getEntityWithStringValue(listNodes, "logical_name", "list_node.severity.low");
        FieldModel severityField = new ReferenceFieldModel("severity", severity);

        fields.add(name);
        fields.add(author);
        fields.add(phaseField);
        fields.add(parentField);
        fields.add(severityField);

        Collection<EntityModel> entities = new ArrayList<EntityModel>();
        EntityModel defect = new EntityModel(fields);
        entities.add(defect);
        nga.entityList(DEFECT).create().entities(entities).execute();

    }

    /**
     * Called when the plugin has been enabled.
     * @throws Exception
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        // register ourselves with the EventPublisher
        eventPublisher.register(this);
    }

    /**
     * Called when the plugin is being disabled or removed.
     * @throws Exception
     */
    @Override
    public void destroy() throws Exception {
        // unregister ourselves with the EventPublisher
        eventPublisher.unregister(this);
    }

}