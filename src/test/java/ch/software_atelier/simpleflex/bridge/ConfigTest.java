package ch.software_atelier.simpleflex.bridge;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {
    @Test void webDavUrlRequiresHttpsHostAndNoEmbeddedCredentialsOrSelectors() {
        assertEquals("https://example.org/dav", Config.from(Map.of(
                "BRIDGE_BACKEND", "webdav", "WEBDAV_URL", "https://example.org/dav")).webdavUrl.toString());
        for (String invalid : new String[] {
                "http://example.org/dav", "https:/dav", "https:///dav",
                "https://user:password@example.org/dav", "https://example.org/dav?token=secret",
                "https://example.org/dav#fragment"
        }) {
            assertThrows(IllegalArgumentException.class, () -> Config.from(Map.of(
                    "BRIDGE_BACKEND", "webdav", "WEBDAV_URL", invalid)), invalid);
        }
    }
}
