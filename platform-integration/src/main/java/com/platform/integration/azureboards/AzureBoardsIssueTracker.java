package com.platform.integration.azureboards;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.integration.port.IssueReference;
import com.platform.integration.port.IssueRequest;
import com.platform.integration.port.IssueTrackerPort;
import com.platform.integration.port.IssueUpdate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link IssueTrackerPort} backed by Azure DevOps Boards work items.
 *
 * <p>Open work items are located via a {@code platform:testId:{testId}} tag. Closing/reopening sets
 * {@code System.State} to the configured done/reopen states (default {@code Closed}/{@code Active},
 * which suit the Agile process; override per credential for Basic/Scrum/CMMI processes).
 */
public class AzureBoardsIssueTracker implements IssueTrackerPort {

  public static final String TRACKER_TYPE = "AZURE_DEVOPS_BOARDS";
  private static final Logger log = LoggerFactory.getLogger(AzureBoardsIssueTracker.class);

  private final AzureBoardsClient client;
  private final AzureBoardsIssueMapper mapper;
  private final String defaultWorkItemType;
  private final String areaPath;
  private final String doneState;
  private final String reopenState;

  public AzureBoardsIssueTracker(
      AzureBoardsClient client,
      AzureBoardsIssueMapper mapper,
      String defaultWorkItemType,
      String areaPath,
      String doneState,
      String reopenState) {
    this.client = client;
    this.mapper = mapper;
    this.defaultWorkItemType = defaultWorkItemType == null ? "Bug" : defaultWorkItemType;
    this.areaPath = areaPath;
    this.doneState = doneState == null ? "Closed" : doneState;
    this.reopenState = reopenState == null ? "Active" : reopenState;
  }

  @Override
  public String trackerType() {
    return TRACKER_TYPE;
  }

  @Override
  public IssueReference createIssue(IssueRequest request) {
    String type =
        (request.issueType() != null && !request.issueType().isBlank())
            ? request.issueType()
            : defaultWorkItemType;
    JsonNode resp = client.createWorkItem(type, mapper.toCreatePatch(request, areaPath));
    String id = resp.path("id").asText();
    String url = resp.path("_links").path("html").path("href").asText(client.workItemUrl(id));
    String state = resp.path("fields").path("System.State").asText("New");
    log.info("[AzureBoards] Created work item {} ({})", id, type);
    return new IssueReference(id, url, state, type);
  }

  @Override
  public void updateIssue(String issueKey, IssueUpdate update) {
    if (update.comment() != null && !update.comment().isBlank()) {
      client.addComment(issueKey, update.comment());
    }
    if (update.newStatus() != null && !update.newStatus().isBlank()) {
      client.updateWorkItem(issueKey, mapper.toStatePatch(update.newStatus()));
    }
  }

  @Override
  public void closeIssue(String issueKey, String comment) {
    if (comment != null && !comment.isBlank()) client.addComment(issueKey, comment);
    client.updateWorkItem(issueKey, mapper.toStatePatch(doneState));
  }

  @Override
  public void reopenIssue(String issueKey, String comment) {
    if (comment != null && !comment.isBlank()) client.addComment(issueKey, comment);
    client.updateWorkItem(issueKey, mapper.toStatePatch(reopenState));
  }

  @Override
  public Optional<IssueReference> findOpenIssue(String testId, String projectKey) {
    String tag = mapper.testIdTag(testId);
    String wiql =
        "SELECT [System.Id] FROM workitems"
            + " WHERE [System.Tags] CONTAINS '"
            + tag.replace("'", "''")
            + "'"
            + " AND [System.State] <> '"
            + doneState.replace("'", "''")
            + "'"
            + " ORDER BY [System.ChangedDate] DESC";
    JsonNode resp = client.queryWiql(wiql);
    JsonNode items = resp.path("workItems");
    if (!items.isArray() || items.isEmpty()) return Optional.empty();

    String id = items.get(0).path("id").asText();
    JsonNode wi = client.getWorkItem(id);
    String state = wi.path("fields").path("System.State").asText("Active");
    String type = wi.path("fields").path("System.WorkItemType").asText(defaultWorkItemType);
    String url = wi.path("_links").path("html").path("href").asText(client.workItemUrl(id));
    return Optional.of(new IssueReference(id, url, state, type));
  }
}
