package org.twittig.mite.mitesync.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * A single proposed Mite entry belonging to a {@link Proposal}. Mirrors the fields of
 * {@code ProposalEntryModel} so it can be booked through the existing pipeline unchanged.
 *
 * <p>Accessor-only; excluded from the JaCoCo coverage gate.
 */
@Entity
public class ProposalEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private int minutes;

  @Column(length = 1000)
  private String note;

  private String source;

  private Integer pbiId;

  private String pbiTitle;

  @ManyToOne
  @JoinColumn(name = "proposal_id")
  private Proposal proposal;

  public ProposalEntry() {}

  public ProposalEntry(int minutes, String note, String source, Integer pbiId, String pbiTitle) {
    this.minutes = minutes;
    this.note = note;
    this.source = source;
    this.pbiId = pbiId;
    this.pbiTitle = pbiTitle;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public int getMinutes() {
    return minutes;
  }

  public void setMinutes(int minutes) {
    this.minutes = minutes;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public Integer getPbiId() {
    return pbiId;
  }

  public void setPbiId(Integer pbiId) {
    this.pbiId = pbiId;
  }

  public String getPbiTitle() {
    return pbiTitle;
  }

  public void setPbiTitle(String pbiTitle) {
    this.pbiTitle = pbiTitle;
  }

  public Proposal getProposal() {
    return proposal;
  }

  public void setProposal(Proposal proposal) {
    this.proposal = proposal;
  }
}
