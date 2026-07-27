package org.twittig.mite.mitesync.web.model;

/**
 * Response of POST /proposals/{id}/confirm: the updated proposal (new status) together with the
 * underlying {@link BookingResultModel} (what was created / what failed in Mite).
 */
public class ConfirmResultModel {

  private ProposalModel proposal;
  private BookingResultModel booking;

  public ConfirmResultModel() {}

  public ConfirmResultModel(ProposalModel proposal, BookingResultModel booking) {
    this.proposal = proposal;
    this.booking = booking;
  }

  public ProposalModel getProposal() {
    return proposal;
  }

  public void setProposal(ProposalModel proposal) {
    this.proposal = proposal;
  }

  public BookingResultModel getBooking() {
    return booking;
  }

  public void setBooking(BookingResultModel booking) {
    this.booking = booking;
  }
}
