package ch.software_atelier.simpleflex.bridge;

import ch.software_atelier.simpleflex.Request;
import ch.software_atelier.simpleflex.docs.WebDoc;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ErrorPageTest {
    private static Request request(String method, String path) {
        Request request = new Request();
        request.setMethod(method);
        request.setRequestString(path);
        return request;
    }

    private static final Backend EMPTY = new Backend() {
        @Override public Metadata stat(String path) { return null; }
        @Override public List<Entry> list(String path) { return List.of(); }
        @Override public Content open(String path) throws IOException { throw new IOException("private backend detail"); }
    };

    @Test void clientErrorsUseStyledHtmlWithoutChangingStatus() throws Exception {
        BridgeApp app = new BridgeApp(EMPTY);
        for (int code : new int[] {400, 404, 405}) {
            Request request = switch (code) {
                case 400 -> request("GET", "/../bad");
                case 405 -> request("POST", "/");
                default -> request("GET", "/missing");
            };
            try (WebDoc doc = app.process(request)) {
                assertEquals(code, doc.getHttpCode().code);
                assertEquals("text/html; charset=utf-8", doc.mime());
                String html = new String(doc.byteData(), StandardCharsets.UTF_8);
                assertTrue(html.contains("<span class=\"badge\">" + code + "</span>"));
                assertTrue(html.contains("File browser"));
                assertTrue(html.contains("href=\"/\""));
                if (code == 405) assertTrue(doc.getHeaders().stream()
                        .anyMatch(h -> h.name().equalsIgnoreCase("Allow:") && h.value().equals("GET")));
            }
        }
    }

    @Test void unauthorizedKeepsBasicChallengeAndGatewayErrorHidesBackendDetail() throws Exception {
        Backend protectedBackend = new Backend() {
            @Override public Metadata stat(String path) { return path.equals("/.auth") ? new Metadata(false, 3) : null; }
            @Override public List<Entry> list(String path) { return List.of(); }
            @Override public Content open(String path) { return new Content(new java.io.ByteArrayInputStream("bad".getBytes(StandardCharsets.UTF_8)), 3); }
        };
        try (WebDoc doc = new BridgeApp(protectedBackend).process(request("GET", "/"))) {
            assertEquals(401, doc.getHttpCode().code);
            assertTrue(doc.getHeaders().stream().anyMatch(h -> h.name().equalsIgnoreCase("WWW-Authenticate:") && h.value().startsWith("Basic ")));
            assertTrue(new String(doc.byteData(), StandardCharsets.UTF_8).contains("Sign in required"));
        }

        Backend failingBackend = new Backend() {
            @Override public Metadata stat(String path) throws IOException { throw new IOException("private backend detail"); }
            @Override public List<Entry> list(String path) { return List.of(); }
            @Override public Content open(String path) throws IOException { throw new IOException("private backend detail"); }
        };
        try (WebDoc doc = new BridgeApp(failingBackend).process(request("GET", "/"))) {
            assertEquals(502, doc.getHttpCode().code);
            assertEquals("Bad Gateway", doc.getHttpCode().message);
            assertFalse(new String(doc.byteData(), StandardCharsets.UTF_8).contains("private backend detail"));
        }
    }
}
