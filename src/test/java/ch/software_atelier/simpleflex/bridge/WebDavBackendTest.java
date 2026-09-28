package ch.software_atelier.simpleflex.bridge;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class WebDavBackendTest {
    @Test void percentInFileNameIsEncodedOnceAndForeignEntriesAreIgnored() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> requested = new AtomicReference<>();
        server.createContext("/", exchange -> {
            String rawPath = exchange.getRequestURI().getRawPath();
            requested.set(rawPath);
            String xml = """
                <d:multistatus xmlns:d="DAV:">
                  <d:response><d:href>%s</d:href><d:propstat><d:prop><d:resourcetype/><d:getcontentlength>7</d:getcontentlength></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
                  <d:response><d:href>http://attacker.invalid/secret</d:href><d:propstat><d:prop><d:resourcetype/></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
                </d:multistatus>
                """.formatted(rawPath);
            byte[] data = xml.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(207, data.length);
            exchange.getResponseBody().write(data);
            exchange.close();
        });
        server.start();
        try {
            WebDavBackend backend = new WebDavBackend(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/dav%20base/"), "/root", "", "");
            assertEquals(7, backend.stat("/a%b+ c.txt").size());
            assertEquals("/dav%20base/root/a%25b%2B%20c.txt", requested.get());
            assertTrue(backend.list("/").stream().noneMatch(e -> e.name().equals("secret")));
        } finally { server.stop(0); }
    }

    @Test void collectionHrefAndSplitPropstatAndEncodedName() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> requested = new AtomicReference<>();
        server.createContext("/dav/", exchange -> {
            requested.set(exchange.getRequestURI().getRawPath());
            if (exchange.getRequestMethod().equals("GET")) {
                byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, data.length);
                exchange.getResponseBody().write(data);
            } else {
                String xml = """
                    <d:multistatus xmlns:d="DAV:">
                      <d:response><d:href>/dav/folder/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
                      <d:response><d:href>file%20name.txt</d:href>
                        <d:propstat><d:prop><d:resourcetype/></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                        <d:propstat><d:prop><d:getcontentlength>5</d:getcontentlength><d:getlastmodified>Mon, 28 Sep 2026 14:30:00 GMT</d:getlastmodified></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                      </d:response>
                    </d:multistatus>
                    """;
                byte[] data = xml.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/xml");
                exchange.sendResponseHeaders(207, data.length);
                exchange.getResponseBody().write(data);
            }
            exchange.close();
        });
        server.start();
        try {
            WebDavBackend backend = new WebDavBackend(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/dav"), "/", "", "");
            backend.stat("/");
            assertEquals("/dav/", requested.get());
            assertTrue(backend.stat("/folder").directory());
            assertEquals(-1, backend.stat("/folder").size());
            assertEquals("/dav/folder", requested.get());
            assertEquals("file name.txt", backend.list("/folder").get(0).name());
            assertEquals(5, backend.list("/folder").get(0).size());
            assertEquals(Instant.parse("2026-09-28T14:30:00Z"), backend.list("/folder").get(0).modifiedTime());
            assertEquals("/dav/folder/", requested.get());
            Backend.Content file = backend.open("/folder/file name.txt");
            try (var body = file.stream()) { assertEquals("hello", new String(body.readAllBytes(), StandardCharsets.UTF_8)); }
            assertEquals("/dav/folder/file%20name.txt", requested.get());
        } finally { server.stop(0); }
    }
}
