package ch.software_atelier.simpleflex.bridge;

import ch.software_atelier.simpleflex.Request;
import ch.software_atelier.simpleflex.SimpleFlexAccesser;
import ch.software_atelier.simpleflex.apps.WebApp;
import ch.software_atelier.simpleflex.docs.HeaderField;
import ch.software_atelier.simpleflex.docs.WebDoc;
import ch.software_atelier.simpleflex.docs.impl.ByteDoc;
import ch.software_atelier.simpleflex.docs.impl.ErrorDoc;
import ch.software_atelier.simpleflex.docs.impl.InputStreamDoc;
import org.mindrot.jbcrypt.BCrypt;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public final class BridgeApp implements WebApp {
    private Backend backend;
    public BridgeApp() {}
    BridgeApp(Backend backend) { this.backend = backend; }
    @Override public void start(String name, HashMap<String, Object> ignored, SimpleFlexAccesser accesser) {
        if (backend == null) {
            Config config = Config.from(System.getenv());
            backend = config.type.equals("webdav") ? new WebDavBackend(config) : new SftpBackend(config);
        }
    }
    @Override public WebDoc process(Request request) {
        if (!"GET".equals(request.getMethod())) return error(405, "Method Not Allowed");
        String path = normalize(request.getReqestString());
        if (path == null) return error(400, "Bad Request");
        if (isControl(path)) return error(404, "Not Found");
        try {
            Backend.Metadata metadata = backend.stat(path);
            WebDoc denied = authorize(path, request, metadata != null && metadata.directory());
            if (denied != null) return denied;
            if (metadata == null) return error(404, "Not Found");
            if (metadata.directory()) {
                String dir = path.equals("/") ? "" : path;
                String index = dir + "/index.html";
                Backend.Metadata indexMeta = backend.stat(index);
                if (indexMeta != null && !indexMeta.directory()) return serve(index, indexMeta);
                Backend.Metadata listMeta = backend.stat(dir + "/.list");
                if (listMeta != null && !listMeta.directory()) return listing(path, dir + "/.list", listMeta);
                return error(404, "Not Found");
            }
            return serve(path, metadata);
        } catch (IOException | RuntimeException e) {
            return error(502, "Bad Gateway");
        }
    }
    private WebDoc serve(String path, Backend.Metadata metadata) throws IOException {
        Backend.Content content = backend.open(path);
        InputStreamDoc doc = new InputStreamDoc(content.stream());
        doc.setSize(content.size()); doc.setName(path.substring(path.lastIndexOf('/') + 1));
        String mime = java.net.URLConnection.guessContentTypeFromName(doc.name());
        doc.setMime(mime == null ? "application/octet-stream" : mime);
        doc.getHeaders().add(new HeaderField("X-Content-Type-Options:", "nosniff"));
        doc.getHeaders().add(new HeaderField("Cache-Control:", "no-store"));
        return doc;
    }
    private WebDoc listing(String path, String configPath, Backend.Metadata configMeta) throws IOException {
        if (configMeta.size() > 64 * 1024) throw new IOException("Invalid listing configuration");
        byte[] configBytes;
        try (var stream = backend.open(configPath).stream()) { configBytes = stream.readNBytes(64 * 1024 + 1); }
        if (configBytes.length > 64 * 1024) throw new IOException("Invalid listing configuration");
        ListConfig config = ListConfig.parse(new String(configBytes, StandardCharsets.UTF_8));
        byte[] data = ListingPage.render(path, backend.list(path), config.hidden(), config.up()).getBytes(StandardCharsets.UTF_8);
        ByteDoc doc = new ByteDoc(data, "index.html", "text/html; charset=utf-8");
        doc.getHeaders().add(new HeaderField("X-Content-Type-Options:", "nosniff"));
        doc.getHeaders().add(new HeaderField("Cache-Control:", "no-store"));
        return doc;
    }
    private WebDoc authorize(String path, Request request, boolean directory) throws IOException {
        String current = "";
        String[] segments = path.equals("/") ? new String[0] : path.substring(1).split("/");
        // Every .auth on the path applies, including the target directory's own file.
        for (int i = 0; i < segments.length + (directory || segments.length == 0 ? 1 : 0); i++) {
            String authPath = current + "/.auth";
            Backend.Metadata auth = backend.stat(authPath);
            if (auth != null) {
                if (auth.directory() || auth.size() > 64 * 1024) return error(502, "Invalid authentication file");
                byte[] bytes;
                try (var stream = backend.open(authPath).stream()) { bytes = stream.readNBytes(64 * 1024 + 1); }
                if (bytes.length > 64 * 1024) return error(502, "Invalid authentication file");
                if (!checkAuth(new String(bytes, StandardCharsets.UTF_8), request)) return challenge();
            }
            if (i < segments.length) current += "/" + segments[i];
        }
        return null;
    }
    static boolean checkAuth(String content, Request request) {
        String authorization = request.getHeaders().entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase("Authorization"))
                .map(java.util.Map.Entry::getValue).findFirst().orElse("");
        if (!authorization.regionMatches(true, 0, "Basic ", 0, 6)) return false;
        String supplied;
        try { supplied = new String(Base64.getDecoder().decode(authorization.substring(6).trim()), StandardCharsets.UTF_8); }
        catch (IllegalArgumentException e) { return false; }
        int separator = supplied.indexOf(':');
        if (separator <= 0) return false;
        String user = supplied.substring(0, separator), password = supplied.substring(separator + 1);
        boolean validFile = false, matched = false;
        for (String line : content.split("\\R")) {
            if (line.isBlank() || line.startsWith("#")) continue;
            int colon = line.indexOf(':');
            if (colon <= 0 || !line.substring(colon + 1).matches("\\$2[aby]\\$\\d\\d\\$[./A-Za-z0-9]{53}")) return false;
            validFile = true;
            if (line.substring(0, colon).equals(user)) {
                try { matched |= BCrypt.checkpw(password, line.substring(colon + 1).replaceFirst("^\\$2[by]\\$", "\\$2a\\$")); }
                catch (IllegalArgumentException e) { return false; }
            }
        }
        return validFile && matched;
    }
    private static WebDoc challenge() {
        WebDoc doc = error(401, "Unauthorized");
        doc.getHeaders().add(new HeaderField("WWW-Authenticate:", "Basic realm=\"Simpleflex Bridge\", charset=\"UTF-8\""));
        doc.getHeaders().add(new HeaderField("Cache-Control:", "no-store"));
        return doc;
    }
    private static WebDoc error(int code, String reason) { return new ErrorDoc(reason, code, reason); }
    private static boolean isControl(String path) {
        return Arrays.stream(path.split("/")).anyMatch(s -> s.equals(".auth") || s.equals(".list"));
    }
    static String normalize(String path) {
        if (path == null || !path.startsWith("/") || path.startsWith("//") || path.contains("\\") || path.indexOf('\0') >= 0) return null;
        for (int i = 0; i < path.length(); i++) if (Character.isISOControl(path.charAt(i))) return null;
        List<String> clean = new ArrayList<>();
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty()) continue;
            if (segment.equals(".") || segment.equals("..") || segment.indexOf(':') >= 0) return null;
            clean.add(segment);
        }
        return "/" + String.join("/", clean);
    }
    private static String encodeSegment(String text) {
        StringBuilder out = new StringBuilder();
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 255;
            if (Character.isLetterOrDigit(c) && c < 128 || c == '-' || c == '_' || c == '.' || c == '~') out.append((char)c);
            else out.append('%').append("0123456789ABCDEF".charAt(c >> 4)).append("0123456789ABCDEF".charAt(c & 15));
        }
        return out.toString();
    }
    private static String encodePath(String path) {
        if (path.equals("/")) return "/";
        StringBuilder out = new StringBuilder();
        for (String segment : path.substring(1).split("/")) out.append('/').append(encodeSegment(segment));
        return out.toString();
    }
    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
    @Override public long maxPostingSize(String path) { return NO_UPLOAD; }
    @Override public void quit() {}
}
