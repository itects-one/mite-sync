package org.twittig.mite.mitesync.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.twittig.mite.mitesync.web.model.MiteEntryModel;
import org.twittig.mite.mitesync.web.model.ProposalEntryModel;
import org.twittig.mite.mitesync.web.model.WorkItemModel;

/**
 * Checks a proposal against the day's evidence before it is allowed to become a stored DRAFT.
 *
 * <p>This exists for proposals that were composed by a language model (see issue #16). The rules in
 * {@code BookingProposalService} cannot invent a ticket — every note they produce is built from a
 * commit subject or a work item that was just read. A model can, and a plausible-looking entry on a
 * ticket that was never touched is the failure mode worth guarding against: it books real, billed
 * time onto someone else's work, and it survives review precisely because it reads like the others.
 *
 * <p>The guard is deliberately deterministic and pure. It never asks a model whether a model's
 * output was reasonable, and it does not repair anything — it reports what is wrong and leaves the
 * decision to the caller.
 *
 * <p>What it does <b>not</b> check: whether the estimated durations are a fair reflection of the
 * day. That is a judgement no amount of code settles, and it is what the human review before
 * {@code confirm} is for.
 */
@Service
public class ProposalGuard {

  /** Leading {@code #<id>} of an entry note — the ticket the entry books onto. */
  private static final Pattern NOTE_TICKET = Pattern.compile("^#(\\S+)");

  /**
   * Checks a proposed entry list against the day's evidence.
   *
   * @param proposed the entries to check, as composed by the agent
   * @param evidence what the day actually shows
   * @param ticketPattern the profile's {@code git.ticket-pattern}, used to recognize ticket ids in
   *     commit subjects
   * @param targetMinutes the daily target the proposal is measured against
   * @param maxOvershootMinutes how far the day's total may exceed the target before that counts as
   *     a violation; a negative value turns the check off. Overshooting is the direction that
   *     invents time — booking less than the target is legitimate and never reported.
   */
  public GuardResult check(
      List<ProposalEntryModel> proposed,
      DayEvidence evidence,
      String ticketPattern,
      int targetMinutes,
      int maxOvershootMinutes) {

    List<String> violations = new ArrayList<>();

    if (proposed == null || proposed.isEmpty()) {
      // An empty proposal is the right answer for a day with nothing on it, and the wrong one for
      // a day full of commits.
      if (evidence.hasActivity()) {
        violations.add(
            "The proposal is empty although the day has evidence: "
                + describeActivity(evidence)
                + ".");
      }
      return new GuardResult(violations);
    }

    Set<String> known = knownTickets(evidence, ticketPattern);
    List<String> bookedNotes = normalizedNotes(evidence.alreadyBooked());

    for (ProposalEntryModel entry : proposed) {
      String note = entry.getNote() == null ? "" : entry.getNote().strip();
      String label = note.isBlank() ? "(entry without a note)" : "\"" + note + "\"";

      if (note.isBlank()) {
        violations.add("An entry has no note, so nothing would say what the time was spent on.");
      }
      if (entry.getMinutes() < 1) {
        violations.add(label + " books " + entry.getMinutes() + " minutes.");
      } else if (entry.getMinutes() > ProposalEntryModel.MAX_MINUTES) {
        violations.add(
            label
                + " books "
                + entry.getMinutes()
                + " minutes, more than the "
                + ProposalEntryModel.MAX_MINUTES
                + " of a full day.");
      }

      String ticket = ticketOf(note);
      if (ticket != null && !known.contains(ticket)) {
        violations.add(
            "Ticket #"
                + ticket
                + " ("
                + label
                + ") appears nowhere in the day's evidence and is not configured for this"
                + " profile.");
      }

      if (!note.isBlank() && bookedNotes.contains(note.toLowerCase())) {
        violations.add(label + " is already booked in Mite for this day.");
      }
    }

    if (maxOvershootMinutes >= 0) {
      int proposedMinutes = proposed.stream().mapToInt(ProposalEntryModel::getMinutes).sum();
      int total = proposedMinutes + evidence.alreadyBookedMinutes();
      if (total > targetMinutes + maxOvershootMinutes) {
        violations.add(
            "The day would total "
                + total
                + " minutes against a target of "
                + targetMinutes
                + " (tolerance "
                + maxOvershootMinutes
                + ").");
      }
    }

    return new GuardResult(violations);
  }

  /**
   * Ticket ids the day can legitimately be booked onto: the work items the tracker reported, the
   * ids found in the day's commit subjects, and the ones the profile configures (main PBI, meeting
   * collector, fill-up ticket).
   */
  private static Set<String> knownTickets(DayEvidence evidence, String ticketPattern) {
    Set<String> known = new LinkedHashSet<>();
    evidence.workItems().stream().map(WorkItemModel::getId).map(String::valueOf).forEach(known::add);
    evidence.allowedTickets().stream()
        .filter(t -> t != null && !t.isBlank())
        .map(String::strip)
        .forEach(known::add);

    Pattern pattern = Pattern.compile(ticketPattern);
    for (var commit : evidence.gitCommits()) {
      String subject = commit.getSubject();
      if (subject == null) {
        continue;
      }
      Matcher m = pattern.matcher(subject);
      if (m.find() && m.groupCount() >= 1 && m.group(1) != null) {
        known.add(m.group(1));
      }
    }
    return known;
  }

  private static String ticketOf(String note) {
    Matcher m = NOTE_TICKET.matcher(note);
    return m.find() ? m.group(1) : null;
  }

  private static List<String> normalizedNotes(List<MiteEntryModel> alreadyBooked) {
    return alreadyBooked.stream()
        .map(MiteEntryModel::getNote)
        .filter(java.util.Objects::nonNull)
        .map(s -> s.strip().toLowerCase())
        .toList();
  }

  private static String describeActivity(DayEvidence evidence) {
    List<String> parts = new ArrayList<>();
    if (!evidence.gitCommits().isEmpty()) {
      parts.add(evidence.gitCommits().size() + " commit(s)");
    }
    if (!evidence.calendarEvents().isEmpty()) {
      parts.add(evidence.calendarEvents().size() + " calendar event(s)");
    }
    if (!evidence.workItems().isEmpty()) {
      parts.add(evidence.workItems().size() + " work item(s)");
    }
    return String.join(", ", parts);
  }
}
