package org.twittig.mite.mitesync.web.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * A single proposed Mite entry. Returned by the proposal endpoint and may be edited by the
 * client before it is sent to /book.
 */
public class ProposalEntryModel {

  /** A full day — no single entry can legitimately be longer. */
  public static final int MAX_MINUTES = 24 * 60;

  /**
   * Bounded in both directions. Zero or less books nothing while still occupying an entry, and
   * anything past a day's worth of minutes is a typo — one that would not stay harmless: the Mite
   * write API takes a {@code short}, so {@code MiteBookingService} casts, and a value above 32767
   * would silently wrap into a negative duration.
   */
  @Min(value = 1, message = "minutes must be at least 1")
  @Max(value = MAX_MINUTES, message = "minutes must not exceed a full day (" + MAX_MINUTES + ")")
  private int minutes;
  private String note;
  private String source; // provenance, see EntrySource
  private Integer pbiId; // optional, for the overview
  private String pbiTitle; // optional

  public ProposalEntryModel() {}

  public ProposalEntryModel(int minutes, String note, String source, Integer pbiId, String pbiTitle) {
    this.minutes = minutes;
    this.note = note;
    this.source = source;
    this.pbiId = pbiId;
    this.pbiTitle = pbiTitle;
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
}
