package org.twittig.mite.mitesync.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.twittig.mite.mitesync.persistence.Proposal;
import org.twittig.mite.mitesync.persistence.ProposalEntry;
import org.twittig.mite.mitesync.persistence.ProposalStatus;
import org.twittig.mite.mitesync.web.model.ProposalEntryModel;
import org.twittig.mite.mitesync.web.model.ProposalModel;

class ProposalMapperTest {

  private final ProposalMapper mapper = new ProposalMapper();

  @Test
  void toModel_mapsAllFields() {
    Proposal p = new Proposal("default", LocalDate.of(2026, 7, 20), ProposalStatus.BOOKED);
    p.setId(42L);
    p.setMainPbiId(655021);
    p.setTargetHours(6.25);
    Instant created = Instant.parse("2026-07-20T08:00:00Z");
    p.setCreatedAt(created);
    p.setBookedAt(Instant.parse("2026-07-20T17:00:00Z"));
    p.replaceEntries(
        List.of(
            new ProposalEntry(15, "#595 Daily", "calendar", 595, "Daily"),
            new ProposalEntry(300, "#655 Dev", "dev", 655, "Dev")));

    ProposalModel m = mapper.toModel(p);

    assertThat(m.getId()).isEqualTo(42L);
    assertThat(m.getProfileKey()).isEqualTo("default");
    assertThat(m.getDate()).isEqualTo(LocalDate.of(2026, 7, 20));
    assertThat(m.getStatus()).isEqualTo("BOOKED");
    assertThat(m.getTotalMinutes()).isEqualTo(315);
    assertThat(m.getCreatedAt()).isEqualTo(created);
    assertThat(m.getEntries()).hasSize(2);
    assertThat(m.getEntries().get(0).getNote()).isEqualTo("#595 Daily");
    assertThat(m.getEntries().get(1).getMinutes()).isEqualTo(300);
  }

  @Test
  void toModel_handlesNullStatus() {
    Proposal p = new Proposal("default", LocalDate.of(2026, 7, 20), null);
    assertThat(mapper.toModel(p).getStatus()).isNull();
    assertThat(mapper.toModel(p).getTotalMinutes()).isZero();
  }

  @Test
  void toEntryEntities_roundTripsFields() {
    ProposalEntryModel model = new ProposalEntryModel(120, "#1 note", "dev", 1, "Title");

    List<ProposalEntry> entities = mapper.toEntryEntities(List.of(model));

    assertThat(entities).hasSize(1);
    ProposalEntry e = entities.get(0);
    assertThat(e.getMinutes()).isEqualTo(120);
    assertThat(e.getNote()).isEqualTo("#1 note");
    assertThat(e.getSource()).isEqualTo("dev");
    assertThat(e.getPbiId()).isEqualTo(1);
    assertThat(e.getPbiTitle()).isEqualTo("Title");

    ProposalEntryModel back = mapper.toEntryModel(e);
    assertThat(back.getMinutes()).isEqualTo(120);
    assertThat(back.getNote()).isEqualTo("#1 note");
  }
}
