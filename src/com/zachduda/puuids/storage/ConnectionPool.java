package com.zachduda.puuids.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class ConnectionPool {

    private final String url;
    private final Properties properties;
    private final int borrowtimeout;

    // A connection used this recently is trusted without a round trip to check it.
    private static final long VALIDATE_AFTER_NANOS = TimeUnit.SECONDS.toNanos(30);

    private final Semaphore permits;
    // Most recently used first, so the ones left idle longest age out at the tail.
    private final ConcurrentLinkedDeque<Idle> idle = new ConcurrentLinkedDeque<>();

    private record Idle(Connection connection, long since) {
    }
    private final AtomicBoolean closed = new AtomicBoolean(false);

    ConnectionPool(String url, Properties properties, int size, int borrowtimeout) {
        this.url = url;
        this.properties = properties;
        this.permits = new Semaphore(size, true);
        this.borrowtimeout = borrowtimeout;
    }

    Connection borrow() throws SQLException {
        if (closed.get()) {
            throw new SQLException("The puuids MySQL pool has been shut down.");
        }

        try {
            if (!permits.tryAcquire(borrowtimeout, TimeUnit.SECONDS)) {
                throw new SQLException("Timed out waiting " + borrowtimeout + "s for a free MySQL connection.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for a MySQL connection.", interrupted);
        }

        try {
            Idle pooled;
            while ((pooled = idle.pollFirst()) != null) {
                if (System.nanoTime() - pooled.since() < VALIDATE_AFTER_NANOS || usable(pooled.connection())) {
                    return pooled.connection();
                }
                closeQuietly(pooled.connection());
            }
            return DriverManager.getConnection(url, properties);
        } catch (SQLException | RuntimeException err) {
            permits.release();
            throw err;
        }
    }

    void release(Connection connection) {
        if (connection == null) {
            permits.release();
            return;
        }

        if (closed.get()) {
            closeQuietly(connection);
        } else {
            idle.offerFirst(new Idle(connection, System.nanoTime()));
        }
        permits.release();
    }

    void discard(Connection connection) {
        closeQuietly(connection);
        permits.release();
    }

    void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Idle pooled;
        while ((pooled = idle.pollFirst()) != null) {
            closeQuietly(pooled.connection());
        }
    }

    private static boolean usable(Connection connection) {
        try {
            return !connection.isClosed() && connection.isValid(2);
        } catch (SQLException err) {
            return false;
        }
    }

    private static void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
