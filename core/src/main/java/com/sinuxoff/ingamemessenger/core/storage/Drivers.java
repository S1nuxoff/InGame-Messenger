package com.sinuxoff.ingamemessenger.core.storage;

import com.sinuxoff.ingamemessenger.core.web.Http;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Driver;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.Consumer;

/**
 * JDBC drivers for external databases, downloaded from Maven Central on first use and checked against a known SHA-256,
 * so the mod itself stays small. H2 is bundled.
 */
final class Drivers {
    private record Artifact(String path, String sha256, String driverClass) {
        String fileName() {
            return path.substring(path.lastIndexOf('/') + 1);
        }
    }

    private static final String MAVEN = "https://repo1.maven.org/maven2/";
    private static final Artifact MYSQL = new Artifact("com/mysql/mysql-connector-j/9.1.0/mysql-connector-j-9.1.0.jar",
            "8776e2ebc46072c9a47ea59d98298c4273bd9f16a7b26b5dfa4744535aa26c62", "com.mysql.cj.jdbc.Driver");
    private static final Artifact MARIADB = new Artifact("org/mariadb/jdbc/mariadb-java-client/3.5.1/mariadb-java-client-3.5.1.jar",
            "50a50c4a3c13c30dfbd40587f7ad9a496197d285ede0948641d9eee68fdf2106", "org.mariadb.jdbc.Driver");
    private static final Artifact POSTGRESQL = new Artifact("org/postgresql/postgresql/42.7.4/postgresql-42.7.4.jar",
            "188976721ead8e8627eb6d8389d500dccc0c9bebd885268a3047180274a6031e", "org.postgresql.Driver");

    private Drivers() {
    }

    static Driver load(String type, Path libsDir, Consumer<String> log) throws Exception {
        if (type.equals("h2")) {
            return new org.h2.Driver();
        }
        Artifact a = switch (type) {
            case "mysql" -> MYSQL;
            case "mariadb" -> MARIADB;
            case "postgresql" -> POSTGRESQL;
            default -> throw new IllegalArgumentException("unknown database type: " + type);
        };
        Path jar = libsDir.resolve(a.fileName());
        if (!Files.exists(jar) || !a.sha256().equals(sha256(jar))) {
            download(a, jar, log);
        }
        URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, Drivers.class.getClassLoader());
        return (Driver) Class.forName(a.driverClass(), true, loader).getDeclaredConstructor().newInstance();
    }

    private static void download(Artifact a, Path jar, Consumer<String> log) throws Exception {
        log.accept("Downloading the " + a.fileName() + " database driver from Maven Central");
        Files.createDirectories(jar.getParent());
        Path tmp = jar.resolveSibling(a.fileName() + ".part");
        HttpRequest req = HttpRequest.newBuilder(URI.create(MAVEN + a.path())).timeout(Duration.ofMinutes(2)).build();
        HttpResponse<Path> resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofFile(tmp));
        if (resp.statusCode() != 200) {
            Files.deleteIfExists(tmp);
            throw new IOException("HTTP " + resp.statusCode() + " for " + a.path());
        }
        String got = sha256(tmp);
        if (!a.sha256().equals(got)) {
            Files.deleteIfExists(tmp);
            throw new IOException("checksum mismatch for " + a.fileName() + ": " + got);
        }
        Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String sha256(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
