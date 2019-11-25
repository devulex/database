package com.editbox.database.serialize;

import com.editbox.database.RepositoryAccess;
import com.editbox.database.annotation.Uuid;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

public class BinarySerializer<E extends RepositoryAccess> implements Serializer<E> {

    private static Map<Class, Map<Short, Field>> cacheFields = new HashMap<>();

    @Override
    public byte[] fullFormat(E entry) {
        ByteBuf buf = new ByteBuf();
        Field[] fields = entry.getClass().getDeclaredFields();
        try {
            for (Field field : fields) {
                field.setAccessible(true);
                if (field.get(entry) == null || field.getAnnotation(Uuid.class) != null) {
                    continue;
                }
                String typeName = field.getType().getName();
                putFieldValue(buf, typeName, field, entry);
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return buf.toArray();
    }

    public byte[] formatDiff(E oldEntry, E newEntry) {
        ByteBuf buf = new ByteBuf();
        Field[] fields = oldEntry.getClass().getDeclaredFields();
        try {
            for (Field field : fields) {
                field.setAccessible(true);
                if (field.get(oldEntry) == null && field.get(newEntry) == null) {
                    continue;
                }
                if (field.get(oldEntry) != null && field.get(newEntry) == null) {
                    buf.putShort(hashName(field.getName()));
                    buf.putByte((byte) 0x7F);
                    continue;
                }
                String typeName = field.getType().getName();
                if (field.get(oldEntry) == null && field.get(newEntry) != null) {
                    putFieldValue(buf, typeName, field, newEntry);
                    continue;
                }
                boolean areEqual;
                if (typeName.equals("[B")) {
                    areEqual = Arrays.equals((byte[]) field.get(oldEntry), (byte[]) field.get(newEntry));
                } else {
                    areEqual = field.get(oldEntry).equals(field.get(newEntry));
                }
                if (!areEqual) {
                    putFieldValue(buf, typeName, field, newEntry);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return buf.toArray();
    }

    private void putFieldValue(ByteBuf buf, String typeName, Field field, E entry) throws ReflectiveOperationException {
        buf.putShort(hashName(field.getName()));
        boolean isPrimitive = field.getType().isPrimitive();
        switch (typeName) {
            case "boolean":
            case "java.lang.Boolean":
                buf.putBoolean(isPrimitive ? field.getBoolean(entry) : (Boolean) field.get(entry));
                break;
            case "byte":
            case "java.lang.Byte":
            case "short":
            case "java.lang.Short":
            case "int":
            case "java.lang.Integer":
            case "long":
            case "java.lang.Long":
                long longValue = isPrimitive ? field.getLong(entry) : ((Number) field.get(entry)).longValue();
                if (longValue <= Byte.MAX_VALUE && longValue >= Byte.MIN_VALUE) {
                    buf.putByte((byte) 0x02);
                    buf.putByte((byte) longValue);
                } else if (longValue <= Short.MAX_VALUE && longValue >= Short.MIN_VALUE) {
                    buf.putByte((byte) 0x03);
                    buf.putShort((short) longValue);
                } else if (longValue <= Integer.MAX_VALUE && longValue >= Integer.MIN_VALUE) {
                    buf.putByte((byte) 0x04);
                    buf.putInt((int) longValue);
                } else {
                    buf.putByte((byte) 0x05);
                    buf.putLong(longValue);
                }
                break;
            case "float":
            case "java.lang.Float":
                buf.putByte((byte) 0x06);
                buf.putFloat(isPrimitive ? field.getFloat(entry) : (Float) field.get(entry));
                break;
            case "double":
            case "java.lang.Double":
                buf.putByte((byte) 0x07);
                buf.putDouble(isPrimitive ? field.getDouble(entry) : (Double) field.get(entry));
                break;
            case "java.math.BigInteger":
                buf.putByte((byte) 0x08);
                String bigIntegerString = field.get(entry).toString();
                buf.putByte((byte) bigIntegerString.length());
                buf.putString(bigIntegerString, Byte.MAX_VALUE);
                break;
            case "java.math.BigDecimal":
                buf.putByte((byte) 0x09);
                String bigDecimalString = field.get(entry).toString();
                buf.putByte((byte) bigDecimalString.length());
                buf.putString(bigDecimalString, Byte.MAX_VALUE);
                break;
            case "[B":
                byte[] blobValue = (byte[]) field.get(entry);
                if (blobValue.length <= Byte.MAX_VALUE) {
                    buf.putByte((byte) 0x0A); // Short Blob
                    buf.putByte((byte) blobValue.length);
                } else if (blobValue.length <= Short.MAX_VALUE) {
                    buf.putByte((byte) 0x0B); // Medium Blob
                    buf.putShort((short) blobValue.length);
                } else {
                    buf.putByte((byte) 0x0C); // Long Blob
                    buf.putInt(blobValue.length);
                }
                buf.putArray(blobValue);
                break;
            case "java.lang.String":
                String stringValue = field.get(entry).toString();
                byte[] stringAsArray = stringValue.getBytes(StandardCharsets.UTF_8);
                if (stringAsArray.length <= Byte.MAX_VALUE) {
                    buf.putByte((byte) 0x0D); // Short String
                    buf.putByte((byte) stringAsArray.length);
                } else if (stringAsArray.length <= Short.MAX_VALUE) {
                    buf.putByte((byte) 0x0E); // Medium String
                    buf.putShort((short) stringAsArray.length);
                } else {
                    buf.putByte((byte) 0x0F); // Long String
                    buf.putInt(stringAsArray.length);
                }
                buf.putArray(stringAsArray);
                break;
            case "java.util.Date":
                buf.putByte((byte) 0x10);
                buf.putLong(((Date) field.get(entry)).getTime());
                break;
            case "java.time.LocalDate":
                buf.putByte((byte) 0x11);
                buf.putLong(((LocalDate) field.get(entry)).toEpochDay(), 4);
                break;
            case "java.time.LocalTime":
                buf.putByte((byte) 0x12);
                buf.putLong(((LocalTime) field.get(entry)).toNanoOfDay(), 6);
                break;
            case "java.time.LocalDateTime":
                buf.putByte((byte) 0x13);
                LocalDateTime localDateTime = (LocalDateTime) field.get(entry);
                buf.putLong(localDateTime.toLocalDate().toEpochDay(), 4);
                buf.putLong(localDateTime.toLocalTime().toNanoOfDay(), 6);
                break;
            case "java.time.ZonedDateTime":
                buf.putByte((byte) 0x14);
                ZonedDateTime zonedDateTime = (ZonedDateTime) field.get(entry);
                buf.putLong(zonedDateTime.toLocalDate().toEpochDay(), 4);
                buf.putLong(zonedDateTime.toLocalTime().toNanoOfDay(), 6);
                byte[] zone = zonedDateTime.getZone().toString().getBytes(StandardCharsets.UTF_8);
                buf.putInt(zone.length, 1);
                buf.putArray(zone);
                break;
            case "java.util.UUID":
                buf.putByte((byte) 0x15);
                buf.putUuid((UUID) field.get(entry));
                break;
            default:
                throw new RuntimeException("Type " + typeName + " is not supported");
        }
    }

    @Override
    public void fillEntry(Class<E> clazz, E entry, byte[] serializedData) {
        ByteBuf buf = new ByteBuf(serializedData);
        Map<Short, Field> fields = getFields(clazz);
        while (buf.getPosition() < buf.getCapacity()) {
            short nameHash = buf.getShort();
            Field field = fields.get(nameHash);
            if (field != null) {
                field.setAccessible(true);
            }
            byte dataTypeId = buf.getByte();
            Object value = null;
            long integerValue = 0;
            boolean isInteger = false;
            switch (dataTypeId) {
                case 0x00: // False Boolean
                    value = false;
                    break;
                case 0x01: // True Boolean
                    value = true;
                    break;
                case 0x02: // Byte
                    integerValue = buf.getByte();
                    isInteger = true;
                    break;
                case 0x03: // Short
                    integerValue = buf.getShort();
                    isInteger = true;
                    break;
                case 0x04: // Integer
                    integerValue = buf.getInt();
                    isInteger = true;
                    break;
                case 0x05: // Long
                    integerValue = buf.getLong();
                    isInteger = true;
                    break;
                case 0x06: // Float
                    value = buf.getFloat();
                    break;
                case 0x07: // Double
                    value = buf.getDouble();
                    break;
                case 0x08: // BigInteger
                    value = new BigInteger(buf.getString(buf.getByte()));
                    break;
                case 0x09: // BigDecimal
                    value = new BigDecimal(buf.getString(buf.getByte()));
                    break;
                case 0x0A: // Short Blob (byte array)
                    value = buf.getArray(buf.getByte());
                    break;
                case 0x0B: // Medium Blob (byte array)
                    value = buf.getArray(buf.getShort());
                    break;
                case 0x0C: // Long Blob (byte array)
                    value = buf.getArray(buf.getInt());
                    break;
                case 0x0D: // Short String
                    value = buf.getString(buf.getByte());
                    break;
                case 0x0E: // Medium String
                    value = buf.getString(buf.getShort());
                    break;
                case 0x0F: // Long String
                    value = buf.getString(buf.getInt());
                    break;
                case 0x10: // Date
                    value = new Date(buf.getLong());
                    break;
                case 0x11: // LocalDate
                    value = LocalDate.ofEpochDay(buf.getInt());
                    break;
                case 0x12: // LocalTime
                    value = LocalTime.ofNanoOfDay(buf.getLong(6));
                    break;
                case 0x13: // LocalDateTime
                    LocalDate localDate = LocalDate.ofEpochDay(buf.getLong(4));
                    LocalTime localTime = LocalTime.ofNanoOfDay(buf.getLong(6));
                    value = LocalDateTime.of(localDate, localTime);
                    break;
                case 0x14: // ZonedDateTime
                    LocalDate localDate2 = LocalDate.ofEpochDay(buf.getLong(4));
                    LocalTime localTime2 = LocalTime.ofNanoOfDay(buf.getLong(6));
                    int zoneIdSize = buf.getByte();
                    ZoneId zone = ZoneId.of(buf.getString(zoneIdSize));
                    value = ZonedDateTime.of(localDate2, localTime2, zone);
                    break;
                case 0x15: // UUID
                    value = buf.getUuid();
                    break;
                case 0x7F: // Null
                    value = null;
                    break;
                default:
                    throw new RuntimeException("Type " + String.format("%02X", dataTypeId) + " is not supported");
            }
            try {
                if (field != null) {
                    if (isInteger) {
                        switch (field.getType().getName()) {
                            case "byte":
                            case "java.lang.Byte":
                                field.set(entry, (byte) integerValue);
                                break;
                            case "short":
                            case "java.lang.Short":
                                field.set(entry, (short) integerValue);
                                break;
                            case "int":
                            case "java.lang.Integer":
                                field.set(entry, (int) integerValue);
                                break;
                            case "long":
                            case "java.lang.Long":
                                field.set(entry, integerValue);
                                break;
                        }
                    } else {
                        field.set(entry, value);
                    }
                }
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private Map<Short, Field> getFields(Class clazz) {
        if (cacheFields.get(clazz) == null) {
            Map<Short, Field> fields = new HashMap<>();
            for (Field field : clazz.getDeclaredFields()) {
                fields.put(hashName(field.getName()), field);
            }
            cacheFields.put(clazz, fields);
        }
        return cacheFields.get(clazz);
    }

    private short hashName(String fieldName) {
        int h = 0;
        for (byte v : fieldName.getBytes(StandardCharsets.UTF_8)) {
            h = 31 * h + (v & 0xff);
        }
        return (short) h;
    }
}
