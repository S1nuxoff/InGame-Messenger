package com.sinuxoff.ingamemessenger.core.storage;

/**
 * [database] in the server config. type is "h2" (a file in the world folder, nothing to set up), "mysql", "mariadb"
 * or "postgresql". port 0 means the type's default port. downloadDriver: the owner allows downloading the external
 * database's driver; without it nothing is downloaded.
 */
public record DatabaseConfig(String type, String host, int port, String name, String user, String password, String tablePrefix,
                             boolean downloadDriver) {
    public static DatabaseConfig h2() {
        return new DatabaseConfig("h2", "", 0, "", "", "", "igm_", false);
    }
}
