package org.twittig.mite.mitesync.web.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Response of POST /daily-reports/{date}/preview. Contains all source data and a booking
 * proposal that the client can edit before sending it to /book.
 */
public class DailyReportModel {

  private LocalDate date;
  private String dayOfWeek;

  /** Calendar events for the day (filtered: all that are not skipped). */
  private List<CalendarEventModel> calendarEvents;

  /** Mite entries already booked for the day. */
  private List<MiteEntryModel> alreadyBookedInMite;

  /** Work items the user changed on the date. */
  private List<WorkItemModel> devOpsActivityOnDate;

  /** Currently open work items assigned to the user (for manual selection). */
  private List<WorkItemModel> openWorkItems;

  /** Commits backing the proposal — only filled for git-activity profiles. */
  private List<GitCommitModel> gitCommits;

  /** Generated proposal: the list of entries that would be booked. */
  private List<ProposalEntryModel> proposal;

  /** Total minutes across the proposed entries. */
  private int proposalTotalMinutes;

  /**
   * What the app could not read while assembling this report — a configured repository that has
   * moved, for instance. Empty rather than null: without these, a misconfiguration is
   * indistinguishable from a day without activity.
   */
  private List<String> warnings = List.of();

  public List<String> getWarnings() {
    return warnings;
  }

  public void setWarnings(List<String> warnings) {
    this.warnings = warnings == null ? List.of() : warnings;
  }

  public LocalDate getDate() {
    return date;
  }

  public void setDate(LocalDate date) {
    this.date = date;
  }

  public String getDayOfWeek() {
    return dayOfWeek;
  }

  public void setDayOfWeek(String dayOfWeek) {
    this.dayOfWeek = dayOfWeek;
  }

  public List<CalendarEventModel> getCalendarEvents() {
    return calendarEvents;
  }

  public void setCalendarEvents(List<CalendarEventModel> calendarEvents) {
    this.calendarEvents = calendarEvents;
  }

  public List<MiteEntryModel> getAlreadyBookedInMite() {
    return alreadyBookedInMite;
  }

  public void setAlreadyBookedInMite(List<MiteEntryModel> alreadyBookedInMite) {
    this.alreadyBookedInMite = alreadyBookedInMite;
  }

  public List<WorkItemModel> getDevOpsActivityOnDate() {
    return devOpsActivityOnDate;
  }

  public void setDevOpsActivityOnDate(List<WorkItemModel> devOpsActivityOnDate) {
    this.devOpsActivityOnDate = devOpsActivityOnDate;
  }

  public List<WorkItemModel> getOpenWorkItems() {
    return openWorkItems;
  }

  public void setOpenWorkItems(List<WorkItemModel> openWorkItems) {
    this.openWorkItems = openWorkItems;
  }

  public List<GitCommitModel> getGitCommits() {
    return gitCommits;
  }

  public void setGitCommits(List<GitCommitModel> gitCommits) {
    this.gitCommits = gitCommits;
  }

  public List<ProposalEntryModel> getProposal() {
    return proposal;
  }

  public void setProposal(List<ProposalEntryModel> proposal) {
    this.proposal = proposal;
  }

  public int getProposalTotalMinutes() {
    return proposalTotalMinutes;
  }

  public void setProposalTotalMinutes(int proposalTotalMinutes) {
    this.proposalTotalMinutes = proposalTotalMinutes;
  }
}
