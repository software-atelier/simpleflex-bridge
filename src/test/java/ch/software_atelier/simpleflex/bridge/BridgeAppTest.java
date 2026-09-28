package ch.software_atelier.simpleflex.bridge;

import ch.software_atelier.simpleflex.Request;
import ch.software_atelier.simpleflex.docs.WebDoc;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BridgeAppTest {
    private static final class FakeBackend implements Backend {
        final Map<String, byte[]> files = new HashMap<>();
        final Map<String, Boolean> directories = new HashMap<>();
        FakeBackend() { directories.put("/", true); }
        FakeBackend dir(String path) { directories.put(path, true); return this; }
        FakeBackend file(String path, String content) { files.put(path, content.getBytes(StandardCharsets.UTF_8)); return this; }
        @Override public Metadata stat(String path) {
            if (directories.containsKey(path)) return new Metadata(true, 0);
            byte[] bytes = files.get(path);
            return bytes == null ? null : new Metadata(false, bytes.length);
        }
        @Override public List<Entry> list(String path) {
            String prefix = path.equals("/") ? "/" : path + "/";
            return files.entrySet().stream().filter(e -> e.getKey().startsWith(prefix) && !e.getKey().substring(prefix.length()).contains("/"))
                    .map(e -> new Entry(e.getKey().substring(prefix.length()), false, e.getValue().length)).toList();
        }
        @Override public Content open(String path) throws IOException {
            byte[] bytes = files.get(path);
            if (bytes == null) throw new IOException("missing");
            return new Content(new ByteArrayInputStream(bytes), bytes.length);
        }
    }
    private static Request request(String path, String authorization) throws Exception {
        Request req = new Request(); req.setMethod("GET"); req.setRequestString(path);
        if (authorization != null) {
            Method add = Request.class.getDeclaredMethod("addHeaderLine", String.class);
            add.setAccessible(true); add.invoke(req, "authorization: " + authorization);
        }
        return req;
    }
    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
    private static String body(WebDoc doc) throws Exception {
        try (doc) { return doc.dataType().equals(WebDoc.DATA_BYTE) ? new String(doc.byteData(), StandardCharsets.UTF_8) : new String(doc.streamData().readAllBytes(), StandardCharsets.UTF_8); }
    }
    @Test void directoryPrecedenceAndHiddenControls() throws Exception {
        FakeBackend fs = new FakeBackend().dir("/public").file("/public/index.html", "INDEX").file("/public/.list", "").file("/public/a.txt", "A");
        BridgeApp app = new BridgeApp(fs);
        WebDoc index = app.process(request("/public/", null));
        assertEquals(200, index.getHttpCode().code); assertEquals("INDEX", body(index));
        assertEquals(404, app.process(request("/public/.list", null)).getHttpCode().code);
        fs.files.remove("/public/index.html");
        String listing = body(app.process(request("/public", null)));
        assertTrue(listing.contains("a.txt")); assertFalse(listing.contains(".list"));
        fs.files.remove("/public/.list");
        assertEquals(404, app.process(request("/public", null)).getHttpCode().code);
    }
    @Test void inheritedAuthAndMalformedFilesFailClosed() throws Exception {
        String hash = BCrypt.hashpw("secret", BCrypt.gensalt(4));
        FakeBackend fs = new FakeBackend().dir("/parent").dir("/parent/child")
                .file("/parent/.auth", "alice:" + hash + "\n").file("/parent/child/a.txt", "hello");
        BridgeApp app = new BridgeApp(fs);
        assertEquals(401, app.process(request("/parent/child/a.txt", null)).getHttpCode().code);
        assertEquals(401, app.process(request("/parent/child/a.txt", basic("alice", "wrong"))).getHttpCode().code);
        assertEquals("hello", body(app.process(request("/parent/child/a.txt", basic("alice", "secret")))));
        fs.file("/parent/.auth", "alice:not-a-hash");
        assertEquals(401, app.process(request("/parent/child/a.txt", basic("alice", "secret"))).getHttpCode().code);
    }

    @Test void bcrypt2a2bAnd2yHashesAuthenticateOnlyCorrectPassword() throws Exception {
        String hash2a = BCrypt.hashpw("secret", BCrypt.gensalt(4));
        for (String prefix : new String[] { "$2a$", "$2b$", "$2y$" }) {
            String authFile = "alice:" + prefix + hash2a.substring(4) + "\n";
            assertTrue(BridgeApp.checkAuth(authFile, request("/", basic("alice", "secret"))), prefix);
            assertFalse(BridgeApp.checkAuth(authFile, request("/", basic("alice", "wrong"))), prefix);
        }
    }
    @Test void traversalAndHtmlEscaping() throws Exception {
        FakeBackend fs = new FakeBackend().dir("/public").file("/public/.list", "").file("/public/<script>.txt", "bad");
        BridgeApp app = new BridgeApp(fs);
        assertEquals(400, app.process(request("/public/%2e%2e/private", null)).getHttpCode().code);
        assertEquals(400, app.process(request("/public/%5cprivate", null)).getHttpCode().code);
        String listing = body(app.process(request("/public", null)));
        assertTrue(listing.contains("&lt;script&gt;.txt")); assertFalse(listing.contains("<script>"));
    }

    @Test void listingEncodesEveryParentAndChildPathSegment() throws Exception {
        FakeBackend fs = new FakeBackend().dir("/a b").dir("/a b/é&")
                .file("/a b/é&/.list", "").file("/a b/é&/child %.txt", "content");
        String listing = body(new BridgeApp(fs).process(request("/a b/é&", null)));
        assertTrue(listing.contains("href=\"/a%20b/%C3%A9%26/child%20%25.txt\""), listing);
        assertTrue(listing.contains("<span class=\"name\">child %.txt</span>"), listing);
    }

    @Test void listingOptionsControlHiddenFilesAndParentLink() throws Exception {
        FakeBackend fs = new FakeBackend().dir("/public")
                .file("/public/.list", "")
                .file("/public/visible.txt", "V")
                .file("/public/.hidden.txt", "H");
        BridgeApp app = new BridgeApp(fs);
        String defaults = body(app.process(request("/public", null)));
        assertTrue(defaults.contains("visible.txt"));
        assertFalse(defaults.contains(".hidden.txt"));
        assertFalse(defaults.contains("Parent folder"));
        fs.file("/public/.list", "hidden=true\nup=true\n");
        String configured = body(app.process(request("/public", null)));
        assertTrue(configured.contains(".hidden.txt"));
        assertTrue(configured.contains("Parent folder"));
        assertTrue(configured.contains("href=\"/\""));
        assertFalse(configured.contains(".list</span>"));
        assertFalse(configured.contains(".auth</span>"));
    }
}
