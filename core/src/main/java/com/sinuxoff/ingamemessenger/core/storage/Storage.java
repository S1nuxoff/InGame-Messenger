package com.sinuxoff.ingamemessenger.core.storage;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The chat's storage: the database (used on one thread only) and the file store (blocking network or disk I/O, on its
 * own threads), so the server thread never waits for either.
 */
public final class Storage {
    public final ChatStore chat;
    /** The channel model's database (null while the old one is used). */
    public final Store store;
    public final BlobStore files;
    public final Executor db;
    public final Executor io;
    private final ExecutorService ownDb, ownIo;

    private Storage(ChatStore chat, Store store, BlobStore files, Executor db, Executor io, ExecutorService ownDb, ExecutorService ownIo) {
        this.chat = chat;
        this.store = store;
        this.files = files;
        this.db = db;
        this.io = io;
        this.ownDb = ownDb;
        this.ownIo = ownIo;
    }

    /** With background threads, for a real server. */
    public static Storage threaded(ChatStore chat, BlobStore files) {
        ExecutorService db = Executors.newSingleThreadExecutor(r -> daemon(r, "InGameMessenger db"));
        ExecutorService io = Executors.newFixedThreadPool(4, r -> daemon(r, "InGameMessenger files"));
        return new Storage(chat, null, files, db, io, db, io);
    }

    /** With background threads, for a real server. */
    public static Storage threaded(Store store, BlobStore files) {
        ExecutorService db = Executors.newSingleThreadExecutor(r -> daemon(r, "InGameMessenger db"));
        ExecutorService io = Executors.newFixedThreadPool(4, r -> daemon(r, "InGameMessenger files"));
        return new Storage(null, store, files, db, io, db, io);
    }

    /** Runs everything on the calling thread, for tests. */
    public static Storage inline(Store store, BlobStore files) {
        return new Storage(null, store, files, Runnable::run, Runnable::run, null, null);
    }

    /** Runs everything on the calling thread, for tests. */
    public static Storage inline(ChatStore chat, BlobStore files) {
        return new Storage(chat, null, files, Runnable::run, Runnable::run, null, null);
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    /** Lets queued work finish (up to 10 s), then closes the database. */
    public void close() {
        for (ExecutorService e : new ExecutorService[]{ownIo, ownDb}) {
            if (e != null) {
                e.shutdown();
                try {
                    e.awaitTermination(10, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        if (chat != null) {
            chat.close();
        }
        if (store != null) {
            store.close();
        }
    }
}
