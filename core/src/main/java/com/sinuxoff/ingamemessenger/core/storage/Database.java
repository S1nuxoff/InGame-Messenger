package com.sinuxoff.ingamemessenger.core.storage;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;
import java.util.function.Consumer;

/** One JDBC connection, reopened when it breaks. Only used from the storage thread, so no pool is needed. */
public final class Database implements AutoCloseable {
    final Dialect dialect;
    final String prefix;
    private final Driver driver;
    private final String url;
    private final Properties props = new Properties();
    private Connection connection;

    private Database(Dialect dialect, String prefix, Driver driver, String url, String user, String password) {
        this.dialect = dialect;
        this.prefix = prefix;
        this.driver = driver;
        this.url = url;
        if (!user.isEmpty()) {
            props.setProperty("user", user);
        }
        if (!password.isEmpty()) {
            props.setProperty("password", password);
        }
    }

    /**
     * dataDir holds the H2 file; libsDir the external databases' drivers. Fails if the driver can't be loaded or the database
     * can't be reached.
     */
    public static Database open(DatabaseConfig cfg, Path dataDir, Path libsDir, Consumer<String> log) throws Exception {
        String type = cfg.type().trim().toLowerCase();
        Driver driver = Drivers.load(type, libsDir, cfg.downloadDriver(), log);
        String prefix = cfg.tablePrefix().replaceAll("[^A-Za-z0-9_]", "");
        Database db = switch (type) {
            case "h2" -> new Database(Dialect.H2, prefix, driver,
                    "jdbc:h2:file:" + dataDir.resolve("chat").toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE", "sa", "");
            case "mysql", "mariadb" -> new Database(Dialect.MYSQL, prefix, driver, "jdbc:" + type + "://" + cfg.host() + ":"
                    + (cfg.port() > 0 ? cfg.port() : 3306) + "/" + cfg.name() + "?useUnicode=true&characterEncoding=utf8",
                    cfg.user(), cfg.password());
            case "postgresql" -> new Database(Dialect.POSTGRESQL, prefix, driver, "jdbc:postgresql://" + cfg.host() + ":"
                    + (cfg.port() > 0 ? cfg.port() : 5432) + "/" + cfg.name(), cfg.user(), cfg.password());
            default -> throw new IllegalArgumentException("unknown database type: " + type);
        };
        db.connection();
        return db;
    }

    /** In-memory H2, for tests. */
    public static Database memory(String name) {
        return new Database(Dialect.H2, "igm_", new org.h2.Driver(), "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1", "sa", "");
    }

    Connection connection() throws SQLException {
        if (connection == null || !connection.isValid(5)) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
            }
            connection = driver.connect(url, props);
            if (connection == null) {
                throw new SQLException("the driver doesn't accept " + url);
            }
        }
        return connection;
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
            connection = null;
        }
    }
}
