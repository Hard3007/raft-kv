package io.github.hard3007.raftkv.storage;

import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {@link FileSystem} backed by a real directory. */
public final class DiskFileSystem implements FileSystem {

    private static final Logger log = LoggerFactory.getLogger(DiskFileSystem.class);
    /** An fsync at least this slow is logged (it blocks the node thread for that long). */
    private static final long SLOW_FSYNC_MILLIS = 50;

    private final Path directory;

    public DiskFileSystem(Path directory) {
        this.directory = directory;
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public StorageFile open(String name) {
        Path path = resolve(name);
        boolean existed = Files.exists(path);
        try {
            FileChannel channel = FileChannel.open(path,
                    StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            if (!existed) {
                // A new file's directory entry is only durable once the directory is fsynced.
                forceDirectory();
            }
            return new DiskFile(channel, name);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean exists(String name) {
        return Files.exists(resolve(name));
    }

    @Override
    public void delete(String name) {
        try {
            if (Files.deleteIfExists(resolve(name))) {
                forceDirectory();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void replace(String source, String target) {
        try {
            // rename(2) is atomic on POSIX file systems; fsyncing the directory makes it durable.
            Files.move(resolve(source), resolve(target),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            forceDirectory();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path resolve(String name) {
        if (name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("file name must not contain a path separator: " + name);
        }
        return directory.resolve(name);
    }

    private void forceDirectory() throws IOException {
        try (FileChannel dir = FileChannel.open(directory, StandardOpenOption.READ)) {
            dir.force(true);
        }
    }

    private static final class DiskFile implements StorageFile {

        private final FileChannel channel;
        private final String name;

        DiskFile(FileChannel channel, String name) {
            this.channel = channel;
            this.name = name;
        }

        @Override
        public void read(long position, byte[] dst, int offset, int length) {
            ByteBuffer buffer = ByteBuffer.wrap(dst, offset, length);
            try {
                while (buffer.hasRemaining()) {
                    int n = channel.read(buffer, position + buffer.position() - offset);
                    if (n < 0) {
                        throw new EOFException("read past end of file at " + position);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void write(long position, byte[] src, int offset, int length) {
            ByteBuffer buffer = ByteBuffer.wrap(src, offset, length);
            try {
                long start = System.nanoTime();
                while (buffer.hasRemaining()) {
                    channel.write(buffer, position + buffer.position() - offset);
                }
                long millis = (System.nanoTime() - start) / 1_000_000;
                if (millis >= SLOW_FSYNC_MILLIS) { // a plain write blocks when the kernel throttles dirty pages
                    log.info("event=slow_write file={} ms={} bytes={}", name, millis, length);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public long size() {
            try {
                return channel.size();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void truncate(long size) {
            try {
                channel.truncate(size);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void force() {
            try {
                // force(false) is fdatasync on Linux: it flushes the data plus the metadata
                // (such as file length) needed to read it back, skipping timestamps.
                long start = System.nanoTime();
                channel.force(false);
                long millis = (System.nanoTime() - start) / 1_000_000;
                if (millis >= SLOW_FSYNC_MILLIS) {
                    log.info("event=slow_fsync file={} ms={} bytes={}", name, millis, channel.size());
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void close() {
            try {
                channel.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
