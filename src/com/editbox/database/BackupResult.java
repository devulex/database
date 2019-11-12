package com.editbox.database;

/**
 * The result of database backup.
 *
 * @author Aleksandr Uhanov
 * @since 2019-11-12
 */
public class BackupResult {

    private boolean isSuccessful;
    private long duration;
    private Throwable throwable;

    private BackupResult(boolean isSuccessful, long duration, Throwable throwable) {
        this.isSuccessful = isSuccessful;
        this.duration = duration;
        this.throwable = throwable;
    }

    public static BackupResult ok(long duration) {
        return new BackupResult(true, duration, null);
    }

    public static BackupResult fail(long duration, Throwable throwable) {
        return new BackupResult(false, duration, throwable);
    }

    public boolean isSuccessful() {
        return isSuccessful;
    }

    public long getDuration() {
        return duration;
    }

    public Throwable getThrowable() {
        return throwable;
    }
}
