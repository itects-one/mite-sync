package org.twittig.mite.mitesync.service;

import java.util.List;

/**
 * What one day's read of the configured repositories produced: the commits, and what could not be
 * read.
 *
 * <p>The warnings exist because skipping a broken repository is right but silent skipping is not:
 * an empty commit list otherwise looks exactly like a quiet day, and a configuration pointing at a
 * moved repository stays invisible until someone reads the server log.
 *
 * @param commits the day's commits across all readable repositories, chronologically
 * @param warnings one message per repository that could not be read, naming path and reason
 */
public record GitActivityResult(List<GitCommit> commits, List<String> warnings) {}
