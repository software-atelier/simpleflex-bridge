package ch.software_atelier.simpleflex.bridge;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.FileMode;
import net.schmizz.sshj.sftp.RemoteFile;
import net.schmizz.sshj.sftp.SFTPClient;
import java.io.FilterInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

final class SftpBackend implements Backend {
    private final Config config;
    SftpBackend(Config config) { this.config = config; }
    private String remote(String path) {
        String root = config.root.replaceAll("/+$", "");
        return root + path;
    }
    private Session connect() throws IOException {
        SSHClient ssh = new SSHClient();
        try {
            ssh.loadKnownHosts(config.sftpKnownHosts.toFile());
            ssh.connect(config.sftpHost, config.sftpPort);
            if (config.sftpKey != null) ssh.authPublickey(config.sftpUser, config.sftpKey.toString());
            else ssh.authPassword(config.sftpUser, config.sftpPassword);
            return new Session(ssh, ssh.newSFTPClient());
        } catch (IOException e) { ssh.close(); throw e; }
    }
    // Never follow a symlink: even a child link could escape BRIDGE_ROOT.
    private FileAttributes safeStat(SFTPClient sftp, String path) throws IOException {
        String base = remote("/").replaceAll("/+$", "");
        String current = "/";
        FileAttributes attrs = sftp.lstat(current);
        if (!base.isEmpty()) for (String segment : base.substring(1).split("/")) {
            if (segment.isEmpty()) continue;
            current = current.equals("/") ? "/" + segment : current + "/" + segment;
            attrs = sftp.statExistence(current) == null ? null : sftp.lstat(current);
            if (attrs == null || attrs.getType() == FileMode.Type.SYMLINK) return null;
        }
        if (!path.equals("/")) for (String segment : path.substring(1).split("/")) {
            if (segment.isEmpty()) continue;
            current = current.equals("/") ? "/" + segment : current + "/" + segment;
            attrs = sftp.statExistence(current) == null ? null : sftp.lstat(current);
            if (attrs == null || attrs.getType() == FileMode.Type.SYMLINK) return null;
        }
        return attrs;
    }
    @Override public Metadata stat(String path) throws IOException {
        try (Session session = connect()) {
            FileAttributes attrs = safeStat(session.sftp, path);
            return attrs == null ? null : new Metadata(attrs.getType() == FileMode.Type.DIRECTORY, attrs.getSize());
        }
    }
    @Override public List<Entry> list(String path) throws IOException {
        try (Session session = connect()) {
            FileAttributes attrs = safeStat(session.sftp, path);
            if (attrs == null || attrs.getType() != FileMode.Type.DIRECTORY) throw new IOException("Directory unavailable");
            return session.sftp.ls(remote(path)).stream()
                    .filter(e -> !e.getName().equals(".") && !e.getName().equals("..") && e.getAttributes().getType() != FileMode.Type.SYMLINK)
                    .map(e -> new Entry(e.getName(), e.isDirectory(), e.getAttributes().getSize(),
                            Instant.ofEpochSecond(e.getAttributes().getMtime()))).toList();
        }
    }
    @Override public Content open(String path) throws IOException {
        Session session = connect();
        try {
            FileAttributes attrs = safeStat(session.sftp, path);
            if (attrs == null || attrs.getType() != FileMode.Type.REGULAR) throw new IOException("File unavailable");
            RemoteFile file = session.sftp.open(remote(path));
            return new Content(new FilterInputStream(file.new RemoteFileInputStream()) {
                @Override public void close() throws IOException {
                    try { super.close(); } finally { try { file.close(); } finally { session.close(); } }
                }
            }, attrs.getSize());
        } catch (IOException e) { session.close(); throw e; }
    }
    private record Session(SSHClient ssh, SFTPClient sftp) implements AutoCloseable {
        @Override public void close() throws IOException { try { sftp.close(); } finally { ssh.close(); } }
    }
}
