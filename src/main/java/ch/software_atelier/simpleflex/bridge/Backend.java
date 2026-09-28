package ch.software_atelier.simpleflex.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.time.Instant;

/** Read-only virtual filesystem; paths are slash-prefixed and already validated. */
interface Backend {
    record Entry(String name, boolean directory, long size, Instant modifiedTime) {
        Entry(String name, boolean directory, long size) { this(name, directory, size, null); }
    }
    record Metadata(boolean directory, long size) {}
    record Content(InputStream stream, long size) {}
    Metadata stat(String path) throws IOException;
    List<Entry> list(String path) throws IOException;
    Content open(String path) throws IOException;
}
