package ch.software_atelier.simpleflex.bridge;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

final class WebDavBackend implements Backend {
    private static final String DAV = "DAV:";
    private static final String PROPFIND_BODY = "<d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/><d:getcontentlength/></d:prop></d:propfind>";
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build();
    private final URI baseUri;
    private final String root;
    private final String username;
    private final String password;
    WebDavBackend(Config config) { this(config.webdavUrl, config.root, config.webdavUser, config.webdavPassword); }
    WebDavBackend(URI baseUri, String root, String username, String password) {
        this.baseUri = baseUri; this.root = root; this.username = username; this.password = password;
    }
    private URI uri(String path) {
        String base = baseUri.toASCIIString().replaceAll("/+$", "");
        String prefix = root.replaceAll("/+$", "");
        String combined = prefix + path;
        StringBuilder encoded = new StringBuilder();
        for (String segment : combined.split("/")) {
            if (segment.isEmpty()) continue;
            encoded.append('/');
            for (byte b : segment.getBytes(StandardCharsets.UTF_8)) {
                int c = b & 0xff;
                if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~') encoded.append((char)c);
                else encoded.append('%').append("0123456789ABCDEF".charAt(c >> 4)).append("0123456789ABCDEF".charAt(c & 15));
            }
        }
        if (combined.endsWith("/")) encoded.append('/');
        return URI.create(base + encoded);
    }
    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(60));
        if (!username.isEmpty()) {
            String token = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + token);
        }
        return builder;
    }
    private HttpResponse<InputStream> send(HttpRequest request) throws IOException {
        try { return client.send(request, HttpResponse.BodyHandlers.ofInputStream()); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Interrupted", e); }
    }
    private List<DavEntry> propfind(String path, int depth) throws IOException {
        HttpRequest req = request(depth == 1 && !path.endsWith("/") ? path + "/" : path).header("Depth", Integer.toString(depth)).header("Content-Type", "application/xml; charset=utf-8")
                .method("PROPFIND", HttpRequest.BodyPublishers.ofString(PROPFIND_BODY)).build();
        HttpResponse<InputStream> response = send(req);
        try (InputStream stream = response.body()) {
            if (response.statusCode() == 404) return List.of();
            if (response.statusCode() != 207) throw new IOException("WebDAV PROPFIND returned " + response.statusCode());
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
            NodeList responses = factory.newDocumentBuilder().parse(stream).getElementsByTagNameNS(DAV, "response");
            List<DavEntry> entries = new ArrayList<>();
            for (int i = 0; i < responses.getLength(); i++) {
                Element el = (Element) responses.item(i);
                String href = text(el, "href");
                if (href == null) continue;
                URI hrefUri = URI.create(href);
                URI resolved = req.uri().resolve(hrefUri);
                if (!resolved.getScheme().equalsIgnoreCase(req.uri().getScheme()) || !resolved.getAuthority().equalsIgnoreCase(req.uri().getAuthority())) continue;
                String raw = resolved.getRawPath().replaceAll("/+$", "");
                String base = req.uri().getRawPath().replaceAll("/+$", "");
                if (!raw.equals(base) && !(depth == 1 && raw.startsWith(base + "/") && raw.substring(base.length()+1).indexOf('/') < 0)) continue;
                NodeList props = el.getElementsByTagNameNS(DAV, "propstat");
                boolean found = false, directory = false;
                long length = 0;
                for (int j = 0; j < props.getLength(); j++) {
                    Element ps = (Element) props.item(j);
                    String status = text(ps, "status");
                    if (status == null || !status.contains(" 200 ")) continue;
                    found = true;
                    directory |= ps.getElementsByTagNameNS(DAV, "collection").getLength() > 0;
                    String lengthText = text(ps, "getcontentlength");
                    if (lengthText != null && !lengthText.isBlank()) length = Long.parseLong(lengthText.trim());
                }
                if (found) {
                    String name = java.net.URLDecoder.decode(raw.substring(raw.lastIndexOf('/') + 1).replace("+", "%2B"), StandardCharsets.UTF_8);
                    entries.add(new DavEntry(raw.equals(base), new Entry(name, directory, length)));
                }
            }
            return entries;
        } catch (Exception e) {
            if (e instanceof IOException ioe) throw ioe;
            throw new IOException("Invalid WebDAV response", e);
        }
    }
    private static String text(Element element, String local) {
        NodeList nodes = element.getElementsByTagNameNS(DAV, local);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }
    @Override public Metadata stat(String path) throws IOException {
        return propfind(path, 0).stream().filter(DavEntry::self).findFirst()
                .map(d -> new Metadata(d.entry.directory(), d.entry.size())).orElse(null);
    }
    @Override public List<Entry> list(String path) throws IOException {
        if (stat(path) == null) throw new IOException("Directory unavailable");
        return propfind(path, 1).stream().filter(d -> !d.self).map(DavEntry::entry).toList();
    }
    @Override public Content open(String path) throws IOException {
        Metadata metadata = stat(path);
        if (metadata == null || metadata.directory()) throw new IOException("File unavailable");
        HttpResponse<InputStream> response = send(request(path).GET().build());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("WebDAV GET returned " + response.statusCode());
        }
        long length = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        if (length >= 0) return new Content(response.body(), length);
        // Simpleflex Base requires a known response length. Spool only if the upstream omits it.
        java.nio.file.Path temporary = java.nio.file.Files.createTempFile("simpleflex-bridge-", ".tmp");
        try (InputStream body = response.body()) {
            java.nio.file.Files.copy(body, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            long size = java.nio.file.Files.size(temporary);
            return new Content(new java.io.FilterInputStream(java.nio.file.Files.newInputStream(temporary)) {
                @Override public void close() throws IOException {
                    try { super.close(); } finally { java.nio.file.Files.deleteIfExists(temporary); }
                }
            }, size);
        } catch (IOException e) { java.nio.file.Files.deleteIfExists(temporary); throw e; }
    }
    private record DavEntry(boolean self, Entry entry) {}
}
