package com.sinuxoff.ingamemessenger.core.storage;

/** SQL differences between the supported databases. Everything else uses portable SQL. */
public enum Dialect {
    H2("VARCHAR"),
    MYSQL("MEDIUMTEXT"),
    POSTGRESQL("TEXT");

    /** Column type for long text. */
    final String text;

    Dialect(String text) {
        this.text = text;
    }
}
