package org.twittig.mite.mitesync.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of the daily-report workflows. Each profile describes one project: which workflow
 * type composes the proposal, which Mite instance is read from and booked into, and the booking
 * rules.
 *
 * <p>Bound from {@code daily-reports.*} in {@code application.yml}; all values can be overridden
 * via environment variables (Spring relaxed binding, e.g. {@code
 * DAILY_REPORTS_PROFILES_DEFAULT_RULES_TARGET_MINUTES}).
 */
@ConfigurationProperties(prefix = "daily-reports")
public class DailyReportProperties {

  /** Profile key used by the legacy endpoints without a project path segment. */
  private String defaultProfile = "default";

  private Map<String, Profile> profiles = new LinkedHashMap<>();

  public String getDefaultProfile() {
    return defaultProfile;
  }

  public void setDefaultProfile(String defaultProfile) {
    this.defaultProfile = defaultProfile;
  }

  public Map<String, Profile> getProfiles() {
    return profiles;
  }

  public void setProfiles(Map<String, Profile> profiles) {
    this.profiles = profiles;
  }

  /** Which sources compose the booking proposal of a profile. */
  public enum WorkflowType {
    /** Meetings from Google Calendar + work items from Azure DevOps + fill-up onto a main PBI. */
    CALENDAR_DEVOPS,
    /** Proposal derived purely from local git history. */
    GIT_ACTIVITY
  }

  /** One project profile. */
  public static class Profile {

    private WorkflowType workflowType = WorkflowType.CALENDAR_DEVOPS;

    /** Key of the Mite instance this profile reads from and books into ("source" or "target"). */
    private String miteInstance = "source";

    /** Mite project id used for reading already-booked entries and for created time entries. */
    private String projectId;

    /** Mite service id for created time entries. */
    private String serviceId;

    /** Collector PBI for meeting notes (calendar events are booked under this number). */
    private String meetingCollectorPbi;

    private Rules rules = new Rules();

    /** Git activity source settings — only relevant for {@link WorkflowType#GIT_ACTIVITY}. */
    private GitActivity git = new GitActivity();

    public WorkflowType getWorkflowType() {
      return workflowType;
    }

    public void setWorkflowType(WorkflowType workflowType) {
      this.workflowType = workflowType;
    }

    public String getMiteInstance() {
      return miteInstance;
    }

    public void setMiteInstance(String miteInstance) {
      this.miteInstance = miteInstance;
    }

    public String getProjectId() {
      return projectId;
    }

    public void setProjectId(String projectId) {
      this.projectId = projectId;
    }

    public String getServiceId() {
      return serviceId;
    }

    public void setServiceId(String serviceId) {
      this.serviceId = serviceId;
    }

    public String getMeetingCollectorPbi() {
      return meetingCollectorPbi;
    }

    public void setMeetingCollectorPbi(String meetingCollectorPbi) {
      this.meetingCollectorPbi = meetingCollectorPbi;
    }

    public Rules getRules() {
      return rules;
    }

    public void setRules(Rules rules) {
      this.rules = rules;
    }

    public GitActivity getGit() {
      return git;
    }

    public void setGit(GitActivity git) {
      this.git = git;
    }
  }

  /** Settings of the git activity source: which repositories to read and how to interpret them. */
  public static class GitActivity {

    /** Absolute paths of the local working copies whose history contributes to the proposal. */
    private List<String> repositories = new ArrayList<>();

    /**
     * Author filter: only commits whose author name or email contains this string
     * (case-insensitive) are counted. Blank = all commits.
     */
    private String author = "";

    /** Regex whose first group extracts the ticket id from the start of a commit message. */
    private String ticketPattern = "^([A-Z]+-\\d+)";

    /** Gaps between consecutive commits larger than this split the day into work sessions. */
    private int sessionGapMinutes = 90;

    /**
     * Minutes added to each session for the work leading up to its first commit. Also gives
     * single-commit sessions a non-zero duration.
     */
    private int leadInMinutes = 30;

    /** Ticket id that commits without a recognizable ticket are booked under. Blank = no id. */
    private String fallbackTicket = "";

    /**
     * Regexes for commits that are not billable — release mechanics, generated version bumps and
     * the like. Matched against the commit subject with {@code find()}, so anchor with {@code ^}
     * where the match has to sit at the start. A matching commit gets no entry of its own; it stays
     * part of its session, so its share of the session's minutes is redistributed over the
     * session's remaining commits rather than lost.
     */
    private List<String> nonBillablePatterns = new ArrayList<>();

    /**
     * When set, the gap between the estimated entries and the daily target is filled with one
     * entry on this ticket. Blank (default) = book only what the history shows.
     */
    private String fillUpTicket = "";

    /** Note text of the fill-up entry (prefixed with {@code #<fill-up-ticket>}). */
    private String fillUpNote = "Development";

    public List<String> getRepositories() {
      return repositories;
    }

    public void setRepositories(List<String> repositories) {
      this.repositories = repositories;
    }

    public String getAuthor() {
      return author;
    }

    public void setAuthor(String author) {
      this.author = author;
    }

    public String getTicketPattern() {
      return ticketPattern;
    }

    public void setTicketPattern(String ticketPattern) {
      this.ticketPattern = ticketPattern;
    }

    public int getSessionGapMinutes() {
      return sessionGapMinutes;
    }

    public void setSessionGapMinutes(int sessionGapMinutes) {
      this.sessionGapMinutes = sessionGapMinutes;
    }

    public int getLeadInMinutes() {
      return leadInMinutes;
    }

    public void setLeadInMinutes(int leadInMinutes) {
      this.leadInMinutes = leadInMinutes;
    }

    public String getFallbackTicket() {
      return fallbackTicket;
    }

    public void setFallbackTicket(String fallbackTicket) {
      this.fallbackTicket = fallbackTicket;
    }

    public List<String> getNonBillablePatterns() {
      return nonBillablePatterns;
    }

    public void setNonBillablePatterns(List<String> nonBillablePatterns) {
      this.nonBillablePatterns =
          nonBillablePatterns == null ? new ArrayList<>() : nonBillablePatterns;
    }

    public String getFillUpTicket() {
      return fillUpTicket;
    }

    public void setFillUpTicket(String fillUpTicket) {
      this.fillUpTicket = fillUpTicket;
    }

    public String getFillUpNote() {
      return fillUpNote;
    }

    public void setFillUpNote(String fillUpNote) {
      this.fillUpNote = fillUpNote;
    }
  }

  /** Booking rules of a profile. */
  public static class Rules {

    /** The daily is always booked at {@link #dailyFixedMinutes}, regardless of duration. */
    private String dailyEventSummary = "Team Daily";

    private int dailyFixedMinutes = 15;

    /** Other meetings are rounded up to the next step. */
    private int roundingStepMinutes = 15;

    /** Daily target when the request does not override it. 375 min = 6.25 h. */
    private int targetMinutes = 375;

    public String getDailyEventSummary() {
      return dailyEventSummary;
    }

    public void setDailyEventSummary(String dailyEventSummary) {
      this.dailyEventSummary = dailyEventSummary;
    }

    public int getDailyFixedMinutes() {
      return dailyFixedMinutes;
    }

    public void setDailyFixedMinutes(int dailyFixedMinutes) {
      this.dailyFixedMinutes = dailyFixedMinutes;
    }

    public int getRoundingStepMinutes() {
      return roundingStepMinutes;
    }

    public void setRoundingStepMinutes(int roundingStepMinutes) {
      this.roundingStepMinutes = roundingStepMinutes;
    }

    public int getTargetMinutes() {
      return targetMinutes;
    }

    public void setTargetMinutes(int targetMinutes) {
      this.targetMinutes = targetMinutes;
    }
  }
}
