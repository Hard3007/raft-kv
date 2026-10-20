package io.github.hard3007.raftkv.linearizability;

/** The three possible outcomes of a check. Only {@link #LINEARIZABLE} is a pass. */
public enum Verdict {
    LINEARIZABLE,
    NOT_LINEARIZABLE,
    /** The search ran out of time. Never counts as a pass. */
    INCONCLUSIVE,
}
