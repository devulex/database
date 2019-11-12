package com.editbox.database;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import static java.io.File.separator;

public class Database {

    private static Database instance;

    private String dataPath;

    private String backupsPath;

    private Map<Class<? extends RepositoryAccess>, Repository> repositories;

    private Database(String dataPath, String backupsPath) {
        this.dataPath = dataPath;
        this.backupsPath = backupsPath;
        this.repositories = new LinkedHashMap<>();
        if (Files.notExists(Paths.get(dataPath))) {
            throw new RuntimeException(String.format("Database directory %s does not exist", dataPath));
        }
    }

    public static Database configure(String dataPath) {
        return configure(dataPath, dataPath + separator + "backups");
    }

    public static Database configure(String dataPath, String backupsPath) {
        if (instance == null) {
            instance = new Database(dataPath, backupsPath);
            return instance;
        }
        throw new RuntimeException("The database has already been configured");
    }

    /**
     * Enable database backups
     *
     * @param backupTime UTC time, for example: 05:00:00
     * @param callback   a handler after the database backup
     */
    public void enableBackup(String backupTime, Consumer<BackupResult> callback) {
        BackupScheduler.configure(backupTime, callback);
    }

    /**
     * Register repository in database.
     *
     * @param type  datatype
     * @param alias name of repository file
     */
    public synchronized void registerRepository(Class<? extends RepositoryAccess> type, String alias) {
        registerRepository(type, alias, true);
    }

    /**
     * Register repository in database.
     *
     * @param type         datatype
     * @param alias        name of repository file
     * @param isPersistent save data to disk
     */
    public synchronized void registerRepository(Class<? extends RepositoryAccess> type, String alias, boolean isPersistent) {
        Repository repository = new Repository<>(type, alias, isPersistent, dataPath, backupsPath);
        repository.restore();
        this.repositories.put(type, repository);
    }

    public static <T extends RepositoryAccess> Repository<T> getRepository(Class<T> type) {
        return instance.repositories.get(type);
    }

    public static Collection<Repository> getAllRepositories() {
        return instance.repositories.values();
    }
}
