package com.editbox.database.serialize;

public interface Serializer<E> {

    byte[] fullFormat(E entry) throws ReflectiveOperationException;

    byte[] formatDiff(E oldEntry, E newEntry) throws ReflectiveOperationException;

    void fillEntry(Class<E> clazz, E entry, byte[] serializedData) throws ReflectiveOperationException;
}
