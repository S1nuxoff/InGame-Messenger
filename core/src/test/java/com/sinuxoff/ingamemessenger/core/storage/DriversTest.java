package com.sinuxoff.ingamemessenger.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriversTest {
    @TempDir
    Path libs;

    @Test
    void h2NeedsNothing() throws Exception {
        assertNotNull(Drivers.load("h2", libs, false, msg -> {
        }));
    }

    @Test
    void nothingIsDownloadedWithoutConsent() throws Exception {
        for (String type : new String[]{"mysql", "mariadb", "postgresql"}) {
            IOException e = assertThrows(IOException.class, () -> Drivers.load(type, libs, false, msg -> {
            }));
            assertTrue(e.getMessage().contains("downloadDriver = true"), e.getMessage());
            assertTrue(e.getMessage().contains("repo1.maven.org"), e.getMessage());
        }
        try (var files = Files.list(libs)) {
            assertFalse(files.findAny().isPresent(), "nothing written to the libs folder");
        }
    }

    @Test
    void aWrongFileIsNotReplacedWithoutConsent() throws Exception {
        Files.writeString(libs.resolve("postgresql-42.7.4.jar"), "not a driver");
        IOException e = assertThrows(IOException.class, () -> Drivers.load("postgresql", libs, false, msg -> {
        }));
        assertTrue(e.getMessage().contains("not the expected file"), e.getMessage());
        assertTrue(Files.readString(libs.resolve("postgresql-42.7.4.jar")).equals("not a driver"));
    }
}
