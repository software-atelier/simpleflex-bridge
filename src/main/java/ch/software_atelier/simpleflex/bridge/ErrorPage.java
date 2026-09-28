package ch.software_atelier.simpleflex.bridge;

/** A self-contained error page styled to match the directory listing. */
final class ErrorPage {
    private ErrorPage() {}

    static String render(int code) {
        String title;
        String description;
        switch (code) {
            case 400 -> { title = "Bad request"; description = "This address could not be opened. Check the URL and try again."; }
            case 401 -> { title = "Sign in required"; description = "This location requires a valid username and password."; }
            case 403 -> { title = "Access denied"; description = "You do not have permission to open this location."; }
            case 404 -> { title = "Not found"; description = "The file or folder you requested is not available."; }
            case 405 -> { title = "Method not allowed"; description = "This request method is not supported here."; }
            case 502 -> { title = "Service unavailable"; description = "The files could not be reached right now. Please try again later."; }
            default -> { title = "Something went wrong"; description = "We could not complete your request. Please try again later."; }
        }
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<meta name=\"color-scheme\" content=\"light dark\"><title>" + code + " · " + title + " · Files</title><style>"
                + ":root{font-family:Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,\"Segoe UI\",sans-serif;color-scheme:light dark;--bg:#f5f7fb;--surface:#fff;--ink:#17233c;--muted:#66758c;--line:#e7ebf2;--accent:#335dc3;--accent-bg:#edf3ff}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);min-height:100vh}main{max-width:1100px;margin:0 auto;padding:clamp(24px,5vw,64px) clamp(16px,4vw,40px)}.eyebrow{font-size:.75rem;letter-spacing:.13em;text-transform:uppercase;font-weight:800;color:var(--accent);margin:0 0 10px}.panel{background:var(--surface);border:1px solid var(--line);border-radius:18px;box-shadow:0 12px 32px rgba(29,48,90,.045);padding:clamp(28px,6vw,64px);max-width:720px}.badge{display:inline-grid;place-items:center;min-width:64px;height:44px;padding:0 12px;border-radius:11px;background:var(--accent-bg);color:var(--accent);font-weight:800;font-size:1.05rem;font-variant-numeric:tabular-nums}h1{font-size:clamp(2rem,4vw,3.3rem);line-height:1.1;letter-spacing:-.04em;margin:24px 0 12px}p{color:var(--muted);line-height:1.65;margin:0 0 28px}.action{display:inline-block;border-radius:10px;padding:12px 18px;background:var(--accent);color:#fff;text-decoration:none;font-weight:650}.action:hover,.action:focus-visible{filter:brightness(.9)}.action:focus-visible{outline:3px solid var(--ink);outline-offset:3px}@media(prefers-color-scheme:dark){:root{--bg:#0d1422;--surface:#162033;--ink:#edf3ff;--muted:#9aa9c1;--line:#293750;--accent:#99b7ff;--accent-bg:#233a69}.action{background:#99b7ff;color:#0d1422}}"
                + "</style></head><body><main><div class=\"eyebrow\">File browser</div><div class=\"panel\"><span class=\"badge\">" + code + "</span><h1>" + title + "</h1><p>" + description + "</p><a class=\"action\" href=\"/\">Go to files</a></div></main></body></html>";
    }
}
