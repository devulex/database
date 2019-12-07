package com.editbox.database;

import com.editbox.database.annotation.MaxLength;
import com.editbox.database.annotation.NotNull;
import com.editbox.database.annotation.Uuid;
import com.editbox.database.serialize.BinarySerializer;
import com.editbox.database.serialize.ByteBuf;
import com.editbox.database.serialize.Serializer;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.*;

import static com.editbox.database.RepositoryOperation.*;
import static java.io.File.separator;

/**
 * Repository that represents all objects of a certain type in memory map with persistence.
 *
 * @author Aleksandr Uhanov
 * @since 2018-09-11
 */
public class Repository<E extends RepositoryAccess> {

    private static final String ext = ".edb";

    private Class<E> objectsType;

    private Field readonlyField;

    private String alias;

    private Serializer<E> serializer;

    private boolean isPersistent;

    private String dataPath;

    private String backupsPath;

    private Map<UUID, E> data;

    private RandomAccessFile file;

    public Repository(Class<E> objectsType, String alias, boolean isPersistent, String dataPath, String backupsPath) {
        this.objectsType = objectsType;
        this.alias = alias;
        this.serializer = new BinarySerializer<>();
        this.isPersistent = isPersistent;
        this.dataPath = dataPath;
        this.backupsPath = backupsPath;
        this.data = new HashMap<>();
        this.file = openFile(getDataFilePath());
        try {
            this.readonlyField = objectsType.getSuperclass().getDeclaredField("readonly");
            this.readonlyField.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Add the object to repository. If persistent = true, then the object will be save to disk.
     *
     * @param object a transient instance of a persistent class
     */
    public synchronized void add(E object) {
        try {
            validate(object);
            UUID id = object.getId();
            if (id == null) {
                throw new RuntimeException("Method getId() cannot return null");
            }
            if (data.get(id) != null) {
                throw new RuntimeException("Object with uuid = " + object.getId() + " already exists");
            }
            readonlyField.set(object, true);
            if (isPersistent) {
                file.write(serializeEntry(object));
            }
            data.put(id, object);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Return the readonly instance of the entity class.
     * Use the instance only for reading! Do not use setters and subsequent update!
     *
     * @param entryId uuid of an existing instance of the class
     * @return the instance
     */
    public synchronized E getForRead(UUID entryId) {
        return data.get(entryId);
    }

    /**
     * Return the readonly all instances of the entity class.
     * Use the instances only for reading! Do not use setters and subsequent update!
     *
     * @return list of instances
     */
    public synchronized List<E> getAllForRead() {
        return new ArrayList<>(data.values());
    }

    /**
     * Return the instance of the given entity class with the given identifier,
     * assuming that the instance exists. Use the instance for read and update.
     *
     * @param entryId uuid of an existing instance of the class
     * @return the instance
     */
    public synchronized E getForUpdate(UUID entryId) {
        E entry = data.get(entryId);
        if (entry == null) {
            return null;
        }
        E copiedEntry;
        try {
            copiedEntry = objectsType.getConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        copiedEntry.setId(entryId);
        serializer.fillEntry(objectsType, copiedEntry, serializer.fullFormat(entry));
        return copiedEntry;
    }

    /**
     * Update the instance with the identifier of the given detached instance.
     * <p>
     * Partition value cannot be changed.
     *
     * @param newEntry instance containing updated state
     */
    public synchronized void update(E newEntry) {
        try {
            validate(newEntry);
            if (readonlyField.getBoolean(newEntry)) {
                throw new RuntimeException("This object is readonly");
            }
            UUID id = newEntry.getId();
            E oldEntry = data.get(id);
            if (oldEntry == null) {
                throw new RuntimeException(String.format("Entry with uuid = %s does not exist", id));
            }
            readonlyField.set(newEntry, true);
            if (isPersistent) {
                byte[] bytes = serializer.formatDiff(oldEntry, newEntry);
                if (bytes.length != 0) {
                    int bytesForLength = usefulBytes(bytes.length);
                    ByteBuf buf = new ByteBuf(1 + 16 + 1 + bytesForLength + bytes.length);
                    buf.putByte(UPDATE.getCode());
                    buf.putUuid(id);
                    buf.putByte((byte) bytesForLength);
                    buf.putInt(bytes.length, bytesForLength);
                    buf.putArray(bytes);
                    file.write(buf.toArray());
                }
            }
            data.put(id, newEntry);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Remove the object from the repository.
     *
     * @param entryId The entity uuid for the instance to be removed.
     */
    public synchronized boolean delete(UUID entryId) {
        try {
            if (data.get(entryId) != null) {
                if (isPersistent) {
                    ByteBuf buf = new ByteBuf(1 + 16);
                    buf.putByte(DELETE.getCode());
                    buf.putUuid(entryId);
                    file.write(buf.toArray());
                }
                data.remove(entryId);
                return true;
            }
            return false;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Perform repository file optimization.
     * <p>
     * Update and delete records will be excluded from the file. Therefore, the file size is reduced.
     */
    public synchronized void optimize() {
        try {
            String path = dataPath + separator + alias + '_' + ext;
            RandomAccessFile backupFile = openFile(path);
            for (E entry : data.values()) {
                backupFile.write(serializeEntry(entry));
            }
            backupFile.close();
            file.close();
            Files.move(Paths.get(path), Paths.get(getDataFilePath()), StandardCopyOption.REPLACE_EXISTING);
            file = openFile(getDataFilePath());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Copy repository file to backup directory.
     */
    public synchronized void createBackup() {
        try {
            String backupTodayPath = backupsPath + separator + LocalDate.now();
            String backupFilePath = backupTodayPath + separator + alias + ext;
            Files.createDirectories(Paths.get(backupTodayPath));
            Files.copy(Paths.get(getDataFilePath()), Paths.get(backupFilePath), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Perform repository file optimization and create backup.
     */
    public synchronized void optimizeAndBackup() {
        optimize();
        createBackup();
    }

    /**
     * Restore all the objects from a file into memory.
     */
    public synchronized void restore() {
        try {
            byte[] allBytes = Files.readAllBytes(Paths.get(getDataFilePath()));
            ByteBuf buf = new ByteBuf(allBytes);
            while (buf.getPosition() < buf.getCapacity()) {
                RepositoryOperation operation = RepositoryOperation.fromCode(buf.getByte());
                UUID id = new UUID(buf.getLong(), buf.getLong());
                switch (operation) {
                    case INSERT:
                        int bytesForLength = buf.getByte();
                        int dataSize = buf.getInt(bytesForLength);
                        byte[] serializedData = buf.getArray(dataSize);
                        E entry = objectsType.getConstructor().newInstance();
                        entry.setId(id);
                        serializer.fillEntry(objectsType, entry, serializedData);
                        readonlyField.set(entry, true);
                        data.put(id, entry);
                        break;
                    case UPDATE:
                        bytesForLength = buf.getByte();
                        dataSize = buf.getInt(bytesForLength);
                        serializedData = buf.getArray(dataSize);
                        entry = getForUpdate(id);
                        serializer.fillEntry(objectsType, entry, serializedData);
                        readonlyField.set(entry, true);
                        data.put(id, entry);
                        break;
                    case DELETE:
                        data.remove(id);
                        break;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error restore objects of repository from file " + getDataFilePath(), e);
        }
    }

    public int size() {
        return data.size();
    }

    private RandomAccessFile openFile(String path) {
        try {
            RandomAccessFile file = new RandomAccessFile(path, "rw");
            file.seek(file.length());
            return file;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getDataFilePath() {
        return dataPath + separator + alias + ext;
    }

    private byte[] serializeEntry(E entry) {
        byte[] bytes = serializer.fullFormat(entry);
        int bytesForLength = usefulBytes(bytes.length);
        ByteBuf buf = new ByteBuf(1 + 16 + 1 + bytesForLength + bytes.length);
        buf.putByte(INSERT.getCode());
        buf.putUuid(entry.getId());
        buf.putByte((byte) bytesForLength);
        buf.putInt(bytes.length, bytesForLength);
        buf.putArray(bytes);
        return buf.toArray();
    }

    private void validate(E entry) throws ReflectiveOperationException {
        Field[] fields = entry.getClass().getDeclaredFields();
        boolean idFieldExisting = false;
        for (Field field : fields) {
            field.setAccessible(true);
            if (field.get(entry) == null) {
                if (field.isAnnotationPresent(NotNull.class) || field.isAnnotationPresent(Uuid.class)) {
                    throw new RuntimeException("Error validating object class '" + entry.getClass().getName() +
                            "'. Field '" + field.getName() + "' cannot be null.");
                }
                continue;
            }
            if (field.isAnnotationPresent(Uuid.class)) {
                if (idFieldExisting) {
                    throw new RuntimeException("Error validating object class '" + entry.getClass().getName() +
                            "'. Only one field can be annotated with 'Uuid'.");
                }
                idFieldExisting = true;
            }
            if (field.isAnnotationPresent(MaxLength.class)) {
                int maxLength = field.getAnnotation(MaxLength.class).value();
                String typeName = field.getType().getName();
                switch (typeName) {
                    case "[B":
                        byte[] blobValue = (byte[]) field.get(entry);
                        if (blobValue.length > maxLength) {
                            throw new RuntimeException("Error validating object class " + entry.getClass().getName() +
                                    ". The maximum length of the field '" + field.getName() + "' is " + maxLength);
                        }
                        break;
                    case "java.lang.String":
                        String stringValue = field.get(entry).toString();
                        if (stringValue.length() > maxLength) {
                            throw new RuntimeException("Error serializing object class '" + entry.getClass().getName() +
                                    "'. The maximum length of the field '" + field.getName() + "' is " + maxLength);
                        }
                        break;
                    default:
                        throw new RuntimeException("Error validating object class '" + entry.getClass().getName() +
                                "'. Annotation 'MaxLength' is not supported for type '" + typeName + "'.");
                }
            }
        }
        if (!idFieldExisting) {
            throw new RuntimeException("Error validating object class " + entry.getClass().getName() +
                    ". Field annotated with 'Uuid' does not exist.");
        }
    }

    /**
     * Return number of useful bytes in positive integer value. It does not work for negative.
     *
     * @param value positive integer value
     */
    private int usefulBytes(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Value should be positive. Value = " + value);
        }
        return (value <= Byte.MAX_VALUE) ? 1 : (value <= Short.MAX_VALUE) ? 2 : 4;
    }
}
