package com.editbox.database.serialize;

public interface Serializer<E> {

    byte[] fullFormat(E entry);

    byte[] formatDiff(E oldEntry, E newEntry);

    void fillEntry(Class<E> clazz, E entry, byte[] serializedData);
}
