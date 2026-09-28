# Simpleflex Bridge

Simpleflex Bridge is a read-only HTTP file server built on [Simpleflex Base](https://github.com/software-atelier/simpleflex-base). One instance uses **either WebDAV or SFTP** as its source. It is packaged as a Docker image.

## Directory behavior

For a requested directory, including a path with or without a trailing slash, Bridge applies these rules in order:

1. Serve `index.html` if it is a regular file.
2. If a regular `.list` file exists, render a responsive HTML directory listing using that file's options.
3. Otherwise return `404 Not Found`.

An empty `.list` file enables the listing with default options. To configure a directory, put one `key=true` or `key=false` option per line in its UTF-8 `.list` file:

```text
hidden=false
up=true
```

`hidden` defaults to `false`, hiding names that begin with a dot. Set it to `true` to include them. `up` defaults to `false`; set it to `true` to show a link to the parent directory (there is no parent link at the root). Options apply to this directory's listing only, not its descendants. Blank lines and lines beginning with `#` are ignored. Unknown options are ignored. `.list` files must be at most 64 KiB.

The listing shows folders first, then files, with file sizes and modification dates when the backend provides them. Missing metadata is shown as a dash; dates are displayed in UTC. Regular files are streamed as-is. `.auth` and `.list` cannot be requested directly and never appear in listings, even with `hidden=true`. Listings escape HTML and URL-encode filenames. Only `GET` is supported. Uploads and write methods are disabled.

## Authentication

Put a UTF-8 `.auth` file in a source directory to require HTTP Basic authentication for that directory and its descendants. Each nonempty, non-comment line is `username:bcrypt-hash`. Lines beginning with `#` are comments. For example:

```text
# One account per line
alice:$2b$12$REPLACE_WITH_A_REAL_53_CHARACTER_BCRYPT_HASH
```

Generate a hash with a bcrypt tool, for example `htpasswd -nbB alice 'your-password'` and copy its `alice:<hash>` output into `.auth`. Supported bcrypt prefixes are `$2a$`, `$2b$` and `$2y$`. Passwords are never stored in plaintext in `.auth`. Malformed or empty files deny access; keep files at or below 64 KiB. Every ancestor `.auth` must accept the *same* supplied credential, so a nested `.auth` further restricts access rather than replacing its parent. Protected successful responses and challenges include `Cache-Control: no-store`.

Serve Bridge behind HTTPS (at a reverse proxy if needed): Basic credentials are otherwise exposed to anyone on the network path. The backend connection to WebDAV must use HTTPS; SFTP verifies its host key against a mounted `known_hosts` file.

## Configuration

| Variable | Required | Meaning |
| --- | --- | --- |
| `BRIDGE_BACKEND` | yes | `webdav` or `sftp`; exactly one backend per instance |
| `BRIDGE_PORT` | no | HTTP port, default `8080` |
| `BRIDGE_ROOT` | no | Additional absolute source path, default `/` |
| `WEBDAV_URL` | WebDAV | HTTPS URL of the WebDAV collection root, without credentials/query/fragment |
| `WEBDAV_USERNAME` | no | WebDAV Basic authentication username |
| `WEBDAV_PASSWORD` | no | WebDAV Basic authentication password |
| `SFTP_HOST` | SFTP | SFTP server hostname |
| `SFTP_PORT` | no | SFTP port, default `22` |
| `SFTP_USERNAME` | SFTP | SFTP account |
| `SFTP_PASSWORD` | SFTP option | Password authentication; mutually exclusive with `SFTP_PRIVATE_KEY` |
| `SFTP_PRIVATE_KEY` | SFTP option | Path to a mounted private key; mutually exclusive with `SFTP_PASSWORD` |
| `SFTP_KNOWN_HOSTS` | SFTP | Path to a mounted OpenSSH `known_hosts` file |

The two backend modes are selected by `BRIDGE_BACKEND`. Variables for the other backend have no effect. Use Docker secrets or a secret manager for credentials and key files rather than committing them to Git. If using a private key, mount it readable by container UID `10001`. A host key can be added to `known_hosts` after verifying its fingerprint out of band.

## Build and run

Requirements for local builds: Java 17 and Maven 3.9+.

```sh
mvn test
mvn package
BRIDGE_BACKEND=webdav WEBDAV_URL=https://dav.example.com/public/ java -jar target/simpleflex-bridge-0.1.0.jar
```

Docker build:

```sh
docker build -t simpleflex-bridge:local .
```

WebDAV example:

```sh
docker run --rm -p 8080:8080 \
  -e BRIDGE_BACKEND=webdav \
  -e WEBDAV_URL=https://dav.example.com/public/ \
  -e WEBDAV_USERNAME=bridge \
  -e WEBDAV_PASSWORD=replace-me \
  simpleflex-bridge:local
```

SFTP example (password authentication):

```sh
docker run --rm -p 8080:8080 \
  -e BRIDGE_BACKEND=sftp \
  -e BRIDGE_ROOT=/srv/public \
  -e SFTP_HOST=sftp.example.com \
  -e SFTP_USERNAME=bridge \
  -e SFTP_PASSWORD=replace-me \
  -e SFTP_KNOWN_HOSTS=/run/secrets/known_hosts \
  -v "$PWD/known_hosts:/run/secrets/known_hosts:ro" \
  simpleflex-bridge:local
```

For key authentication, omit `SFTP_PASSWORD`, set `SFTP_PRIVATE_KEY=/run/secrets/id_ed25519`, and mount the key read-only. Example commands above put passwords in shell history or process metadata; use `--env-file` or orchestrator secrets in deployment.

The container listens on `8080` by default. Expose it through a TLS-enabled reverse proxy. The runtime image runs as non-root UID `10001`.

## Published Docker image

GitHub Actions builds and publishes `ghcr.io/software-atelier/simpleflex-bridge` to GitHub Container Registry on every push to `dev` or `master`. Manual runs are also supported from either branch. Successful `master` builds receive the moving `latest` and `master` tags; successful `dev` builds receive the moving `dev` tag. Every build also receives a commit-specific `sha-<full-commit-sha>` tag. The Docker build runs the Maven tests before publishing. Use `latest` or `master` for normal deployments and `dev` to test the development branch.

Run the published image with WebDAV:

```sh
docker pull ghcr.io/software-atelier/simpleflex-bridge:latest
docker run --rm -p 8080:8080 \
  -e BRIDGE_BACKEND=webdav \
  -e WEBDAV_URL=https://dav.example.com/public/ \
  ghcr.io/software-atelier/simpleflex-bridge:latest
```

For SFTP, set `SFTP_PASSWORD` in your shell environment (or use an environment file), mount a verified `known_hosts` file, and adjust the host, user, and root path:

```sh
docker pull ghcr.io/software-atelier/simpleflex-bridge:latest
docker run --rm -p 8080:8080 \
  -e BRIDGE_BACKEND=sftp \
  -e BRIDGE_ROOT=/srv/public \
  -e SFTP_HOST=sftp.example.com \
  -e SFTP_USERNAME=bridge \
  -e SFTP_PASSWORD \
  -e SFTP_KNOWN_HOSTS=/run/secrets/known_hosts \
  -v "$PWD/known_hosts:/run/secrets/known_hosts:ro" \
  ghcr.io/software-atelier/simpleflex-bridge:latest
```

Set backend credentials and other options as described above. To test the development image, replace `:latest` with `:dev` in the commands. For reproducible deployments, use the `sha-<full-commit-sha>` tag instead of a moving tag. The package is public, so pulling the image does not require a GitHub login.

## Operational behavior and limitations

- The backend is read-only. Each request performs fresh source operations; remote outages or permission errors become `502 Bad Gateway`.
- SFTP symlinks are not served or listed, including symlinks in the configured root path. This prevents traversal outside the configured tree.
- WebDAV redirects are not followed, preventing credentials from being forwarded to another origin. `WEBDAV_URL` must identify the intended collection directly.
- Simpleflex Base requires a response length. WebDAV files without `Content-Length` are temporarily spooled to disk before serving; ensure the container has writable temporary storage and enough space for the largest such file.
- The underlying Simpleflex Base server may advertise methods through its own OPTIONS response; Bridge still implements only GET. Base also has permissive CORS response headers. Deploy behind a proxy if stricter CORS or method policy is required.
- This project overrides Base's logging configuration to avoid logging request header context, which can contain `Authorization`. Do not remove `src/main/resources/log4j2.xml` without reviewing that behavior.

## License

Apache License 2.0; see [LICENSE](LICENSE).
