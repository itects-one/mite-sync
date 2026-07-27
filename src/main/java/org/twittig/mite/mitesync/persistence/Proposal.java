package org.twittig.mite.mitesync.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A stored daily booking proposal for one (profile, date). Generated from the existing preview
 * pipeline, reviewed/edited by the user and — once confirmed — booked into Mite.
 *
 * <p>Only accessors and small collection helpers live here; the lifecycle rules are enforced in
 * {@code ProposalService}. Excluded from the JaCoCo coverage gate (accessor-only).
 */
@Entity
public class Proposal {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String profileKey;

  private LocalDate reportDate;

  @Enumerated(EnumType.STRING)
  private ProposalStatus status;

  /** Main PBI supplied on generation (calendar-devops profiles); kept for traceability. */
  private Integer mainPbiId;

  /** Daily target in hours supplied on generation; null means the profile default was used. */
  private Double targetHours;

  private Instant createdAt;
  private Instant updatedAt;
  private Instant bookedAt;

  @OneToMany(mappedBy = "proposal", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderColumn(name = "position")
  private List<ProposalEntry> entries = new ArrayList<>();

  public Proposal() {}

  public Proposal(String profileKey, LocalDate reportDate, ProposalStatus status) {
    this.profileKey = profileKey;
    this.reportDate = reportDate;
    this.status = status;
  }

  /** Replaces all entries in place, keeping the back-reference and order intact. */
  public void replaceEntries(List<ProposalEntry> newEntries) {
    entries.clear();
    for (ProposalEntry e : newEntries) {
      e.setProposal(this);
      entries.add(e);
    }
  }

  /** Sum of the minutes of all entries. */
  public int totalMinutes() {
    return entries.stream().mapToInt(ProposalEntry::getMinutes).sum();
  }

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

  public LocalDate getReportDate() {
    return reportDate;
  }

  public void setReportDate(LocalDate reportDate) {
    this.reportDate = reportDate;
  }

  public ProposalStatus getStatus() {
    return status;
  }

  public void setStatus(ProposalStatus status) {
    this.status = status;
  }

  public Integer getMainPbiId() {
    return mainPbiId;
  }

  public void setMainPbiId(Integer mainPbiId) {
    this.mainPbiId = mainPbiId;
  }

  public Double getTargetHours() {
    return targetHours;
  }

  public void setTargetHours(Double targetHours) {
    this.targetHours = targetHours;
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

  public List<ProposalEntry> getEntries() {
    return entries;
  }

  public void setEntries(List<ProposalEntry> entries) {
    this.entries = entries;
  }
}
