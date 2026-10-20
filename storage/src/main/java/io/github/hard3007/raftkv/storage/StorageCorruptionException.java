package io.github.hard3007.raftkv.storage;

/** On-disk data failed validation in a way recovery cannot repair. */
public class StorageCorruptionException extends RuntimeException {

    public StorageCorruptionException(String message) {
        super(message);
    }
}
