package com.zachduda.puuids.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A deliberately small JDBC pool.
 * <p>
 * Nothing here needs a full pooling library: writes come from one dedicated thread and the only
 * other borrowers are the occasional import / export / join lookup. What it does have to get
 * right is handing back a connection that is actually alive - MySQL closes idle connections
 * after {@code wait_timeout} (eight hours by default), and a Minecraft server is idle at 4am.
 */
final class ConnectionPool {

    private final String url;
    private final Properties properties;
    private final int borrowtimeout;

    private final Semaphore permits;
    private final ConcurrentLinkedQueue<Connection> idle = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    ConnectionPool(String url, Properties properties, int size, int borrowtimeout) {
        this.url = url;
        this.properties = properties;
        this.permits = new Semaphore(size, true);
        this.borrowtimeout = borrowtimeout;
    }

    /**
     * Takes a live connection out of the pool, opening one if the pool isn't full yet.
     * Every successful borrow must be matched by exactly one {@link #release(Connection)} or
     * {@link #discard(Connection)}, or the pool leaks a permit and eventually deadlocks.
     */
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
            Connection pooled;
            while ((pooled = idle.poll()) != null) {
                if (usable(pooled)) {
                    return pooled;
                }
                closeQuietly(pooled);
            }
            return DriverManager.getConnection(url, properties);
        } catch (SQLException | RuntimeException err) {
            permits.release();
            throw err;
        }
    }

    /** Returns a healthy connection to the pool. */
    void release(Connection connection) {
        if (connection == null) {
            permits.release();
            return;
        }

        if (closed.get()) {
            closeQuietly(connection);
        } else {
            idle.add(connection);
        }
        permits.release();
    }

    /** Throws a connection away - use this after any error, the connection may be poisoned. */
    void discard(Connection connection) {
        closeQuietly(connection);
        permits.release();
    }

    void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Connection pooled;
        while ((pooled = idle.poll()) != null) {
            closeQuietly(pooled);
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
            // Already broken; there is nothing useful to do about it.
        }
    }
}
