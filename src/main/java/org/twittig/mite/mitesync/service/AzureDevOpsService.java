package org.twittig.mite.mitesync.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.twittig.mite.mitesync.web.model.WorkItemModel;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Queries Azure DevOps work items through the REST API. Auth uses a Personal Access Token (PAT)
 * in the Authorization header.
 */
@Service
public class AzureDevOpsService {

  private static final Logger log = LogManager.getLogger(AzureDevOpsService.class);
  private static final String API_VERSION = "7.1";

  @Value("${daily-reports.azure-devops.organization}")
  private String organization;

  @Value("${daily-reports.azure-devops.project}")
  private String project;

  @Value("${daily-reports.azure-devops.pat}")
  private String pat;

  private HttpClient httpClient = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Returns all work items that the current user (= PAT owner) created or changed on the given
   * date, plus a warning per call that failed.
   */
  public WorkItemResult getWorkItemsChangedByMeOnDate(LocalDate date) {
    String wiql =
        "SELECT [System.Id] FROM WorkItems "
            + "WHERE [System.TeamProject] = '" + project + "' "
            + "AND [System.ChangedDate] >= '" + date + "T00:00:00.0000000' "
            + "AND [System.ChangedDate] < '" + date + "T23:59:59.9999999' "
            + "AND ([System.ChangedBy] = @Me OR [System.CreatedBy] = @Me) "
            + "ORDER BY [System.ChangedDate] DESC";
    return queryAndFetch(wiql, "work items changed today", true, false);
  }

  /**
   * Returns all work items currently assigned to the user that are not in a terminal state, plus a
   * warning per call that failed.
   */
  public WorkItemResult getOpenWorkItemsAssignedToMe() {
    String wiql =
        "SELECT [System.Id] FROM WorkItems "
            + "WHERE [System.TeamProject] = '" + project + "' "
            + "AND [System.AssignedTo] = @Me "
            + "AND [System.State] NOT IN ('Done','Removed','Closed') "
            + "ORDER BY [System.ChangedDate] DESC";
    return queryAndFetch(wiql, "open work items assigned to me", false, true);
  }

  /**
   * Returns a single work item by id (for title/state lookups), or {@code null} when it cannot be
   * fetched. No warning channel: the caller of a single lookup sees the missing item itself.
   */
  public WorkItemModel getWorkItemById(int id) {
    List<WorkItemModel> result = batchFetch(List.of(id), new ArrayList<>());
    return result.isEmpty() ? null : result.get(0);
  }

  // ---------------------------------------------------------------------------
  // Internals
  // ---------------------------------------------------------------------------

  private WorkItemResult queryAndFetch(
      String wiql, String queryLabel, boolean markChangedByMe, boolean markAssignedToMe) {
    List<String> warnings = new ArrayList<>();
    try {
      // URLEncoder produces form-encoding (space → "+"). Azure DevOps URL paths need
      // percent-encoding ("%20") instead, so we patch the output.
      String projectEnc = URLEncoder.encode(project, StandardCharsets.UTF_8).replace("+", "%20");
      URI uri = URI.create(
          "https://dev.azure.com/" + organization + "/" + projectEnc + "/_apis/wit/wiql?api-version=" + API_VERSION);

      String body = objectMapper.writeValueAsString(java.util.Map.of("query", wiql));
      HttpRequest req = HttpRequest.newBuilder(uri)
          .header("Authorization", basicAuthHeader())
          .header("Content-Type", "application/json")
          .header("Accept", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build();
      HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() / 100 != 2) {
        // The body goes to the log only: a 401 answers with a sign-in page, not with a sentence.
        log.error("WIQL query failed: HTTP {} — {}", resp.statusCode(), resp.body());
        warnings.add(
            "Azure DevOps query for " + queryLabel + " failed: HTTP " + resp.statusCode()
                + ". The report shows no DevOps activity for this reason, not because there"
                + " was none.");
        return new WorkItemResult(List.of(), warnings);
      }
      JsonNode root = objectMapper.readTree(resp.body());
      List<Integer> ids = new ArrayList<>();
      root.path("workItems").forEach(n -> ids.add(n.path("id").asInt()));
      if (ids.isEmpty()) return WorkItemResult.of(List.of());

      List<WorkItemModel> items = batchFetch(ids, warnings);
      items.forEach(w -> {
        if (markChangedByMe) w.setChangedByMe(true);
        if (markAssignedToMe) w.setAssignedToMe(true);
      });
      return new WorkItemResult(items, warnings);
    } catch (Exception e) {
      log.error("Azure DevOps WIQL query failed", e);
      warnings.add(
          "Azure DevOps query for " + queryLabel + " failed: " + e.getClass().getSimpleName()
              + " — " + e.getMessage()
              + ". The report shows no DevOps activity for this reason, not because there was"
              + " none.");
      return new WorkItemResult(List.of(), warnings);
    }
  }

  /**
   * Fetches the fields of the given ids. Failures are appended to {@code warnings} rather than
   * thrown: this call sits behind a successful WIQL query, so failing here is the quietest way to
   * lose work items — the ids were found, only their contents never arrive.
   */
  private List<WorkItemModel> batchFetch(List<Integer> ids, List<String> warnings) {
    try {
      URI uri = URI.create(
          "https://dev.azure.com/" + organization + "/_apis/wit/workitemsbatch?api-version=" + API_VERSION);
      var fields = List.of(
          "System.Id",
          "System.WorkItemType",
          "System.Title",
          "System.State",
          "System.AssignedTo",
          "System.ChangedBy",
          "System.ChangedDate");
      String body = objectMapper.writeValueAsString(java.util.Map.of("ids", ids, "fields", fields));
      HttpRequest req = HttpRequest.newBuilder(uri)
          .header("Authorization", basicAuthHeader())
          .header("Content-Type", "application/json")
          .header("Accept", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build();
      HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() / 100 != 2) {
        log.error("WorkItemsBatch failed: HTTP {} — {}", resp.statusCode(), resp.body());
        warnings.add(
            "Azure DevOps returned " + ids.size() + " work item(s) but their details could not be"
                + " fetched: HTTP " + resp.statusCode() + ".");
        return List.of();
      }
      JsonNode root = objectMapper.readTree(resp.body());
      List<WorkItemModel> result = new ArrayList<>();
      root.path("value").forEach(n -> {
        JsonNode f = n.path("fields");
        WorkItemModel m = new WorkItemModel();
        m.setId(f.path("System.Id").asInt());
        m.setType(f.path("System.WorkItemType").asText(null));
        m.setState(f.path("System.State").asText(null));
        m.setTitle(f.path("System.Title").asText(null));
        m.setAssignedTo(f.path("System.AssignedTo").path("displayName").asText(null));
        m.setChangedBy(f.path("System.ChangedBy").path("displayName").asText(null));
        String cd = f.path("System.ChangedDate").asText(null);
        if (cd != null && !cd.isEmpty()) {
          // ISO-8601 with trailing Z — parsing without the offset is fine for our purposes
          m.setChangedDate(LocalDateTime.parse(cd.substring(0, 19), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        result.add(m);
      });
      return result;
    } catch (Exception e) {
      log.error("Azure DevOps batch fetch failed", e);
      warnings.add(
          "Azure DevOps returned " + ids.size() + " work item(s) but their details could not be"
              + " fetched: " + e.getClass().getSimpleName() + " — " + e.getMessage() + ".");
      return List.of();
    }
  }

  private String basicAuthHeader() {
    String creds = ":" + pat;
    return "Basic " + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
  }

  // Utility for callers that need a comma-separated id list
  static String joinIds(List<Integer> ids) {
    return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
  }
}
