package io.github.hard3007.raftkv.client;

/** The final outcome of one client operation. */
public sealed interface KvResult<T> permits KvResult.Ok, KvResult.Unknown, KvResult.Rejected {

    /** The operation took effect (for reads: returned this value). */
    record Ok<T>(T value) implements KvResult<T> {
    }

    /**
     * The client gave up retrying. A write may or may not have taken effect; it will never take
     * effect more than once.
     */
    record Unknown<T>() implements KvResult<T> {
    }

    /** Definitely not applied, for example because a key or value is over the size limit. */
    record Rejected<T>(String reason) implements KvResult<T> {
    }
}
