package org.twittig.mite.mitesync.web.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Response representation of a stored booking proposal (the inbox item). Carries the editable
 * {@link ProposalEntryModel} list plus lifecycle metadata.
 */
public class ProposalModel {

  private Long id;
  private String profileKey;
  private LocalDate date;
  private String status;
  private List<ProposalEntryModel> entries;
  private int totalMinutes;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant bookedAt;

  public ProposalModel() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getProfileKey() {
    return profileKey;
  }

  public void setProfileKey(String profileKey) {
    this.profileKey = profileKey;
  }

  public LocalDate getDate() {
    return date;
  }

  public void setDate(LocalDate date) {
    this.date = date;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public List<ProposalEntryModel> getEntries() {
    return entries;
  }

  public void setEntries(List<ProposalEntryModel> entries) {
    this.entries = entries;
  }

  public int getTotalMinutes() {
    return totalMinutes;
  }

  public void setTotalMinutes(int totalMinutes) {
    this.totalMinutes = totalMinutes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Instant getBookedAt() {
    return bookedAt;
  }

  public void setBookedAt(Instant bookedAt) {
    this.bookedAt = bookedAt;
  }
}
