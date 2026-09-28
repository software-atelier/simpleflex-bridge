package ch.software_atelier.simpleflex.bridge;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Self-contained, responsive directory listing; all backend-provided text is escaped. */
final class ListingPage {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private ListingPage() {}

    static String render(String path, List<Backend.Entry> entries, boolean showHidden, boolean showUp) {
        String title = path.equals("/") ? "Files" : path.substring(path.lastIndexOf('/') + 1);
        String base = encodedPath(path) + (path.equals("/") ? "" : "/");
        List<Backend.Entry> visible = entries.stream()
                .filter(e -> validName(e.name()))
                .filter(e -> showHidden || !e.name().startsWith("."))
                .sorted(Comparator.comparing(Backend.Entry::directory).reversed()
                        .thenComparing(Backend.Entry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        StringBuilder html = new StringBuilder(4096 + visible.size() * 300);
        html.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<meta name=\"color-scheme\" content=\"light dark\"><title>").append(escape(title))
                .append(" · Files</title><style>")
                .append(":root{font-family:Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,\"Segoe UI\",sans-serif;color-scheme:light dark;--bg:#f5f7fb;--surface:#fff;--ink:#17233c;--muted:#66758c;--line:#e7ebf2;--accent:#335dc3;--accent-bg:#edf3ff;--hover:#f7f9fd}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);min-height:100vh}main{max-width:1100px;margin:0 auto;padding:clamp(24px,5vw,64px) clamp(16px,4vw,40px)}.eyebrow{font-size:.75rem;letter-spacing:.13em;text-transform:uppercase;font-weight:800;color:var(--accent);margin:0 0 10px}.heading{display:flex;align-items:end;justify-content:space-between;gap:16px;margin-bottom:30px;flex-wrap:wrap}h1{font-size:clamp(2rem,4vw,3.3rem);line-height:1.1;letter-spacing:-.04em;margin:0;overflow-wrap:anywhere}.path{font-size:.9rem;color:var(--muted);margin:10px 0 0;overflow-wrap:anywhere}.count{color:var(--muted);font-size:.9rem}.panel{background:var(--surface);border:1px solid var(--line);border-radius:18px;box-shadow:0 12px 32px rgba(29,48,90,.045);overflow:hidden}.columns,.row{display:grid;grid-template-columns:minmax(0,1fr) 110px 190px;align-items:center;gap:16px;padding:0 24px}.columns{height:48px;border-bottom:1px solid var(--line);font-size:.72rem;text-transform:uppercase;letter-spacing:.09em;font-weight:750;color:var(--muted)}.row{min-height:72px;text-decoration:none;color:inherit;border-bottom:1px solid var(--line);transition:background .15s}.row:last-child{border-bottom:0}.row:hover,.row:focus-visible{background:var(--hover)}.row:focus-visible{outline:2px solid var(--accent);outline-offset:-2px}.file{display:flex;align-items:center;gap:15px;min-width:0}.icon{flex:none;display:grid;place-items:center;width:40px;height:40px;border-radius:11px;background:var(--accent-bg);color:var(--accent);font-size:1.15rem;font-weight:700}.icon.document{background:#f1f3f7;color:#607089}.name{font-weight:650;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.meta{color:var(--muted);font-size:.87rem;font-variant-numeric:tabular-nums}.empty{padding:70px 24px;text-align:center;color:var(--muted)}.empty .icon{margin:0 auto 14px}@media(max-width:650px){.columns{display:none}.row{grid-template-columns:auto minmax(0,1fr);gap:4px 12px;padding:13px 16px;min-height:72px}.row .file{grid-column:1/-1}.row .size{grid-column:1;font-size:.78rem}.row .date{grid-column:2;text-align:right;font-size:.78rem}.icon{width:36px;height:36px}.file{gap:11px}}@media(prefers-color-scheme:dark){:root{--bg:#0d1422;--surface:#162033;--ink:#edf3ff;--muted:#9aa9c1;--line:#293750;--accent:#99b7ff;--accent-bg:#233a69;--hover:#1c2a42}.icon.document{background:#29344a;color:#bdc8db}}")
                .append("</style></head><body><main><p class=\"eyebrow\">File browser</p><div class=\"heading\"><div><h1>")
                .append(escape(title)).append("</h1><p class=\"path\">").append(escape(path))
                .append("</p></div><span class=\"count\">").append(visible.size()).append(visible.size() == 1 ? " item" : " items")
                .append("</span></div><div class=\"panel\"><div class=\"columns\"><span>Name</span><span>Size</span><span>Modified</span></div>");

        if (showUp && !path.equals("/")) {
            String parent = path.substring(0, path.lastIndexOf('/'));
            if (parent.isEmpty()) parent = "/";
            html.append("<a class=\"row\" href=\"").append(escape(encodedPath(parent)))
                    .append(parent.equals("/") ? "" : "/")
                    .append("\"><span class=\"file\"><span class=\"icon\" aria-hidden=\"true\">↑</span><span class=\"name\">Parent folder</span></span><span class=\"meta size\">—</span><span class=\"meta date\">—</span></a>");
        }
        if (visible.isEmpty()) html.append("<div class=\"empty\"><span class=\"icon document\" aria-hidden=\"true\">∅</span>Nothing here yet</div>");
        for (Backend.Entry entry : visible) {
            boolean folder = entry.directory();
            String href = base + encodeSegment(entry.name()) + (folder ? "/" : "");
            html.append("<a class=\"row\" href=\"").append(escape(href)).append("\"><span class=\"file\"><span class=\"icon")
                    .append(folder ? "\"" : " document\"").append(" aria-hidden=\"true\">").append(folder ? "▣" : "▤")
                    .append("</span><span class=\"name\">").append(escape(entry.name())).append("</span></span><span class=\"meta size\">")
                    .append(folder ? "—" : formatSize(entry.size())).append("</span><span class=\"meta date\">")
                    .append(formatModified(entry)).append("</span></a>");
        }
        return html.append("</div></main></body></html>").toString();
    }

    private static String formatModified(Backend.Entry entry) {
        return entry.modifiedTime() == null ? "—" : DATE.format(entry.modifiedTime());
    }

    private static String formatSize(long size) {
        if (size < 0) return "—";
        if (size < 1024) return size + " B";
        String[] units = {"KB", "MB", "GB", "TB", "PB"};
        double value = size;
        int unit = -1;
        do { value /= 1024; unit++; } while (value >= 1024 && unit < units.length - 1);
        return String.format(Locale.ENGLISH, value < 10 ? "%.1f %s" : "%.0f %s", value, units[unit]);
    }

    private static boolean validName(String name) {
        return name != null && !name.isEmpty() && !name.equals(".") && !name.equals("..")
                && !name.equals(".auth") && !name.equals(".list") && !name.contains("/") && !name.contains("\\");
    }

    private static String encodedPath(String path) {
        if (path.equals("/")) return "/";
        StringBuilder out = new StringBuilder();
        for (String segment : path.substring(1).split("/")) out.append('/').append(encodeSegment(segment));
        return out.toString();
    }

    private static String encodeSegment(String text) {
        StringBuilder out = new StringBuilder();
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 255;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~') out.append((char) c);
            else out.append('%').append("0123456789ABCDEF".charAt(c >> 4)).append("0123456789ABCDEF".charAt(c & 15));
        }
        return out.toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
