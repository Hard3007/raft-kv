package io.github.hard3007.raftkv.storage;

/** Thrown by {@link SimulatedFileSystem} once a scheduled crash point is reached. */
public class SimulatedCrashException extends RuntimeException {

    public SimulatedCrashException() {
        super("simulated crash");
    }
}
