package ch.software_atelier.simpleflex.bridge;

import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

final class Config {
    final String type;
    final URI webdavUrl;
    final String webdavUser;
    final String webdavPassword;
    final String sftpHost;
    final int sftpPort;
    final String sftpUser;
    final String sftpPassword;
    final Path sftpKey;
    final Path sftpKnownHosts;
    final String root;
    final int port;

    private Config(Map<String, String> env) {
        type = required(env, "BRIDGE_BACKEND").toLowerCase(Locale.ROOT);
        if (!type.equals("webdav") && !type.equals("sftp")) throw new IllegalArgumentException("BRIDGE_BACKEND must be webdav or sftp");
        port = parsePort(env.getOrDefault("BRIDGE_PORT", "8080"));
        root = env.getOrDefault("BRIDGE_ROOT", "/");
        if (!root.startsWith("/") || root.contains("..") || root.contains("\\") || root.contains("%") || root.contains("//") || root.contains(":")) throw new IllegalArgumentException("BRIDGE_ROOT must be an absolute, normalized path");
        if (type.equals("webdav")) {
            webdavUrl = URI.create(required(env, "WEBDAV_URL"));
            if (!"https".equalsIgnoreCase(webdavUrl.getScheme()) || webdavUrl.getHost() == null || webdavUrl.getUserInfo() != null || webdavUrl.getQuery() != null || webdavUrl.getFragment() != null) throw new IllegalArgumentException("WEBDAV_URL must be an HTTPS URL with a host and without userinfo, query or fragment");
            webdavUser = env.getOrDefault("WEBDAV_USERNAME", "");
            webdavPassword = env.getOrDefault("WEBDAV_PASSWORD", "");
            sftpHost = null; sftpPort = 0; sftpUser = null; sftpPassword = null; sftpKey = null; sftpKnownHosts = null;
        } else {
            webdavUrl = null; webdavUser = null; webdavPassword = null;
            sftpHost = required(env, "SFTP_HOST"); sftpPort = parsePort(env.getOrDefault("SFTP_PORT", "22")); sftpUser = required(env, "SFTP_USERNAME");
            sftpPassword = env.get("SFTP_PASSWORD");
            sftpKey = env.containsKey("SFTP_PRIVATE_KEY") ? Path.of(env.get("SFTP_PRIVATE_KEY")) : null;
            if ((sftpPassword == null) == (sftpKey == null)) throw new IllegalArgumentException("Set exactly one of SFTP_PASSWORD and SFTP_PRIVATE_KEY");
            sftpKnownHosts = Path.of(required(env, "SFTP_KNOWN_HOSTS"));
        }
    }
    static Config from(Map<String, String> env) { return new Config(env); }
    private static String required(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " is required");
        return value;
    }
    private static int parsePort(String value) {
        int port = Integer.parseInt(value);
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid port");
        return port;
    }
}
