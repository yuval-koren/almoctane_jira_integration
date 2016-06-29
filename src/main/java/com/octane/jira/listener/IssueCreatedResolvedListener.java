
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

public class IssueCreatedResolvedListener implements InitializingBean, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(IssueCreatedResolvedListener.class);

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