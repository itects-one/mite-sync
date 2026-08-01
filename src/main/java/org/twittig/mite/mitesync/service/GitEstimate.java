package org.twittig.mite.mitesync.service;

import java.util.List;

import org.twittig.mite.mitesync.web.model.ProposalEntryModel;

/**
 * What the duration estimation made of one day's commits: the proposed entries, and what the
 * reviewer has to know about how they came about.
 *
 * <p>The warnings are here for the same reason as in {@link GitActivityResult}: an entry that looks
 * ordinary but carries no ticket reference is the one most easily waved through, so it is named
 * instead of left to be spotted.
 *
 * @param entries one entry per ticket the day's commits were attributed to
 * @param warnings messages about the estimation run itself, empty when nothing is remarkable
 */
public record GitEstimate(List<ProposalEntryModel> entries, List<String> warnings) {}
