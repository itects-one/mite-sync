package org.twittig.mite.mitesync.service;

import java.util.List;
import org.springframework.stereotype.Component;
import org.twittig.mite.mitesync.persistence.Proposal;
import org.twittig.mite.mitesync.persistence.ProposalEntry;
import org.twittig.mite.mitesync.web.model.ProposalEntryModel;
import org.twittig.mite.mitesync.web.model.ProposalModel;

/** Maps between the persisted {@link Proposal} entities and the web transport models. */
@Component
public class ProposalMapper {

  public ProposalModel toModel(Proposal proposal) {
    ProposalModel m = new ProposalModel();
    m.setId(proposal.getId());
    m.setProfileKey(proposal.getProfileKey());
    m.setDate(proposal.getReportDate());
    m.setStatus(proposal.getStatus() == null ? null : proposal.getStatus().name());
    m.setEntries(proposal.getEntries().stream().map(this::toEntryModel).toList());
    m.setTotalMinutes(proposal.totalMinutes());
    m.setCreatedAt(proposal.getCreatedAt());
    m.setUpdatedAt(proposal.getUpdatedAt());
    m.setBookedAt(proposal.getBookedAt());
    return m;
  }

  public ProposalEntryModel toEntryModel(ProposalEntry e) {
    return new ProposalEntryModel(
        e.getMinutes(), e.getNote(), e.getSource(), e.getPbiId(), e.getPbiTitle());
  }

  public List<ProposalEntry> toEntryEntities(List<ProposalEntryModel> models) {
    return models.stream().map(this::toEntryEntity).toList();
  }

  public ProposalEntry toEntryEntity(ProposalEntryModel m) {
    return new ProposalEntry(
        m.getMinutes(), m.getNote(), m.getSource(), m.getPbiId(), m.getPbiTitle());
  }
}
