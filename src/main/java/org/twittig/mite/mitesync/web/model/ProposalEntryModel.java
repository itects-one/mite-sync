package org.twittig.mite.mitesync.web.model;

/**
 * A single proposed Mite entry. Returned by the proposal endpoint and may be edited by the
 * client before it is sent to /book.
 */
public class ProposalEntryModel {

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
