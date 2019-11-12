package com.editbox.database;

import java.util.function.Consumer;

/**
 * Scheduling database backups.
 *
 * @author Aleksandr Uhanov
 * @since 2019-10-31
 */
public class BackupScheduler {

    private static Thread thread;

    private BackupScheduler() {
    }

    static void configure(String backupTime, Consumer<BackupResult> callback) {
        if (thread != null) {
            thread.interrupt();
        }
        if (backupTime != null) {
            int startLocalTime = parseTime(backupTime);
            thread = new Thread(() -> execute(startLocalTime, callback));
            thread.setName("backup");
            thread.start();
        }
    }

    private static void execute(int startLocalTime, Consumer<BackupResult> callback) {
        while (true) {
            int currentLocalTime = (int) (System.currentTimeMillis() % 86_400_000);
            int diffTime = currentLocalTime - startLocalTime;
            if (diffTime >= 0 && diffTime < 1500) {
                createBackup(callback);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static void createBackup(Consumer<BackupResult> callback) {
        long start = System.currentTimeMillis();
        try {
            for (Repository repository : Database.getAllRepositories()) {
                repository.optimizeAndBackup();
            }
            callback.accept(BackupResult.ok(System.currentTimeMillis() - start));
        } catch (Throwable th) {
            callback.accept(BackupResult.fail(System.currentTimeMillis() - start, th));
            throw new RuntimeException("Error creating backup", th);
        }
    }

    private static int parseTime(String time) {
        if (time == null || time.length() == 0) {
            throw new IllegalArgumentException("Backup time is blank");
        }
        if (time.length() != 8) {
            throw new IllegalArgumentException("Backup time is invalid format");
        }
        int hour = Integer.parseInt(time.substring(0, 2));
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("Hour in backup time is invalid");
        }
        int minute = Integer.parseInt(time.substring(3, 5));
        if (minute < 0 || minute > 59) {
            throw new IllegalArgumentException("Minute in backup time is invalid");
        }
        int second = Integer.parseInt(time.substring(6, 8));
        if (second < 0 || second > 59) {
            throw new IllegalArgumentException("Second in backup time is invalid");
        }
        return (hour * 3600 + minute * 60 + second) * 1000;
    }
}
