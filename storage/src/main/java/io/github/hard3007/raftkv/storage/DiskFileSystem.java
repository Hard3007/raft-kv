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

/** {@link FileSystem} backed by a real directory. */
public final class DiskFileSystem implements FileSystem {

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
            return new DiskFile(channel);
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

        DiskFile(FileChannel channel) {
            this.channel = channel;
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
                while (buffer.hasRemaining()) {
                    channel.write(buffer, position + buffer.position() - offset);
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
                channel.force(false);
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
