package com.editbox.database.serialize;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * A byte buffer with auto resize.
 * <p>
 * It stores data in a heap.
 *
 * @author Aleksandr Uhanov
 * @since 2018-09-21
 */
public class ByteBuf {

    /**
     * The content of this buffer.
     */
    private byte[] data;

    /**
     * A buffer's position is the index of the next element to be read or written.
     */
    private int position;

    /**
     * Limit is the actual size which can be changed.
     */
    private int limit;

    /**
     * Actual number of bytes in this buffer.
     */
    private int capacity;

    /**
     * Constructs a new {@link ByteBuf ByteBuf} with a default initial size.
     */
    public ByteBuf() {
        this(256);
    }

    /**
     * Constructs a new {@link ByteBuf ByteBuf} with the specified array.
     */
    public ByteBuf(byte[] array) {
        data = array;
        capacity = array.length;
    }

    /**
     * Constructs a new {@link ByteBuf ByteBuf} with the given initial size.
     *
     * @param capacity the initial size of the byte buffer to be constructed.
     */
    public ByteBuf(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Invalid capacity");
        }
        data = new byte[capacity];
        this.capacity = capacity;
    }

    /**
     * Returns this buffer's capacity.
     *
     * @return The capacity of this buffer
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * Returns this buffer's position.
     *
     * @return The position of this buffer
     */
    public int getPosition() {
        return position;
    }

    /**
     * Sets the position of this buffer.
     *
     * @param newPosition the new position
     */
    public void setPosition(int newPosition) {
        if (newPosition < 0) {
            throw new IllegalArgumentException("Invalid position");
        }
        if (newPosition > capacity) {
            resize(newPosition);
        }
        if (limit < newPosition) {
            limit = newPosition;
        }
        position = newPosition;
    }

    /**
     * Returns this buffer's limit.
     *
     * @return The limit of this buffer
     */
    public int getLimit() {
        return limit;
    }

    /**
     * Sets the limit of the buffer.
     *
     * @param newLimit the new limit
     */
    public void setLimit(int newLimit) {
        if (newLimit < 0) {
            throw new IllegalArgumentException("Invalid position");
        }
        if (newLimit > capacity) {
            resize(newLimit);
        }
        if (limit < position) {
            position = newLimit;
        }
        limit = newLimit;
    }

    /**
     * Writes the given byte into this buffer at the current
     * position, and then increments the position.
     *
     * @param value The byte to be written
     * @return This buffer
     */
    public ByteBuf putByte(byte value) {
        if (position == capacity) {
            resize(capacity * 2);
        }
        data[position] = value;
        if (limit == position) {
            limit++;
        }
        position++;
        return this;
    }

    /**
     * Reads the byte at this buffer's current position, and then increments the position.
     *
     * @return The byte at the buffer's current position
     */
    public byte getByte() {
        if (position == capacity) {
            resize(capacity * 2);
        }
        byte value = data[position];
        if (limit == position) {
            limit++;
        }
        position++;
        return value;
    }

    /**
     * Writes the given boolean into this buffer at the current
     * position, and then increments the position.
     *
     * @param value The boolean to be written
     * @return This buffer
     */
    public ByteBuf putBoolean(boolean value) {
        putByte((byte) (value ? 1 : 0));
        return this;
    }

    /**
     * Reads the byte at this buffer's current position,
     * composing them into a short value, and then increments the position.
     *
     * @return The short value
     */
    public boolean getBoolean() {
        return getByte() != 0;
    }

    /**
     * Writes the given short into this buffer at the current
     * position, and then increments the position.
     *
     * @param value The short to be written
     * @return This buffer
     */
    public ByteBuf putShort(short value) {
        putByte((byte) (value >> 8));
        putByte((byte) value);
        return this;
    }

    /**
     * Reads two bytes at this buffer's current position, composing them into a
     * short value according to the current byte order.
     *
     * @return The short value
     */
    public short getShort() {
        return (short) (getByte() << 8 | getByte() & 0xFF);
    }

    /**
     * Writes four bytes containing the given int value, in the
     * current byte order, into this buffer at the current position, and then
     * increments the position by four.
     *
     * @param value The int value to be written
     * @return This buffer
     */
    public ByteBuf putInt(int value) {
        putByte((byte) (value >> 24));
        putByte((byte) (value >> 16));
        putByte((byte) (value >> 8));
        putByte((byte) value);
        return this;
    }

    /**
     * Reads four bytes at this buffer's current position, composing them into a
     * int value according to the current byte order.
     *
     * @return The int value
     */
    public int getInt() {
        return getInt(4);
    }

    /**
     * Writes the given several bytes of int into this buffer at the current
     * position, and then increments the position.
     *
     * @param value The int to be written
     * @return This buffer
     */
    public ByteBuf putInt(int value, int length) {
        if (length < 0 || length > 4) {
            throw new IllegalArgumentException("Invalid length");
        }
        for (int i = length - 1; i >= 0; i--) {
            putByte((byte) (value >>> (i * 8)));
        }
        return this;
    }

    /**
     * Reads N bytes at this buffer's current position, composing them into a
     * int value according to the current byte order.
     *
     * @return The int value
     */
    public int getInt(int length) {
        if (length < 0 || length > 4) {
            throw new IllegalArgumentException("Invalid length");
        }
        int result = 0;
        for (int i = 0; i < length; i++) {
            result <<= 8;
            result |= (getByte() & 0xFF);
        }
        return result;
    }

    /**
     * Writes the given long into this buffer at the current
     * position, and then increments the position.
     *
     * @param value The long to be written
     * @return This buffer
     */
    public ByteBuf putLong(long value) {
        putInt((int) (value >> 32));
        putInt((int) value);
        return this;
    }

    /**
     * Reads eight bytes at this buffer's current position, composing them into a
     * long value according to the current byte order.
     *
     * @return The int value
     */
    public long getLong() {
        return getLong(8);
    }

    /**
     * Writes the given several bytes of long into this buffer at the current
     * position, and then increments the position.
     *
     * @param value The long to be written
     * @return This buffer
     */
    public ByteBuf putLong(long value, int length) {
        if (length < 0 || length > 8) {
            throw new IllegalArgumentException("Invalid length");
        }
        for (int i = length - 1; i >= 0; i--) {
            putByte((byte) (value >>> (i * 8)));
        }
        return this;
    }

    /**
     * Reads N bytes at this buffer's current position, composing them into a
     * long value according to the current byte order.
     *
     * @return The long value
     */
    public long getLong(int length) {
        if (length < 0 || length > 8) {
            throw new IllegalArgumentException("Invalid length");
        }
        long result = 0;
        for (int i = 0; i < length; i++) {
            result <<= 8;
            result |= (getByte() & 0xFF);
        }
        return result;
    }

    /**
     * Writes the given float into this buffer at the current
     * position, and then increments the position.
     *
     * @param value The float to be written
     * @return This buffer
     */
    public ByteBuf putFloat(float value) {
        putInt(Float.floatToRawIntBits(value));
        return this;
    }

    /**
     * Reads the next four bytes at this buffer's current position,
     * composing them into a float value according to the current byte order,
     * and then increments the position by four.
     *
     * @return The float value at the buffer's current position
     */
    public float getFloat() {
        return Float.intBitsToFloat(getInt());
    }

    /**
     * Writes the given double into this buffer at the current
     * position, and then increments the position.
     *
     * @param value The double to be written
     * @return This buffer
     */
    public ByteBuf putDouble(double value) {
        putLong(Double.doubleToRawLongBits(value));
        return this;
    }

    /**
     * Reads the next eight bytes at this buffer's current position,
     * composing them into a double value according to the current byte order,
     * and then increments the position by eight.
     *
     * @return The double value at the buffer's current position
     */
    public double getDouble() {
        return Double.longBitsToDouble(getLong());
    }

    /**
     * Writes the given UUID into this buffer at the current
     * position, and then increments the position.
     *
     * @param value The UUID to be written
     * @return This buffer
     */
    public ByteBuf putUuid(UUID value) {
        putLong(value.getMostSignificantBits());
        putLong(value.getLeastSignificantBits());
        return this;
    }

    /**
     * Reads the next 16 bytes at this buffer's current position,
     * composing them into a UUID value according to the current byte order,
     * and then increments the position by 16.
     *
     * @return The UUID value at the buffer's current position
     */
    public UUID getUuid() {
        return new UUID(getLong(), getLong());
    }

    /**
     * Writes the given array of bytes into this buffer at the current
     * position, and then increments the position.
     *
     * @param value The array to be written
     * @return This buffer
     */
    public ByteBuf putArray(byte[] value) {
        for (int i = 0; i < value.length; i++) {
            putByte(value[i]);
        }
        return this;
    }

    /**
     * Reads the next N bytes at this buffer's current position,
     * composing them into a byte array according to the current byte order,
     * and then increments the position by N.
     *
     * @return The byte array at the buffer's current position
     */
    public byte[] getArray(int length) {
        byte[] result = new byte[length];
        for (int i = 0; i < length; i++) {
            result[i] = getByte();
        }
        return result;
    }

    /**
     * Writes the given String into this buffer at the current position,
     * and then increments the position.
     *
     * @param value The String to be written
     * @return This buffer
     */
    public ByteBuf putString(String value) {
        putArray(value.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    /**
     * Writes the given String (or truncated string) into this buffer at the current position,
     * and then increments the position.
     *
     * @param value     The String to be written
     * @param maxLength Maximum string length in characters
     * @return This buffer
     */
    public ByteBuf putString(String value, int maxLength) {
        if (value.length() > maxLength) {
            putString(value.substring(0, maxLength));
        } else {
            putString(value);
        }
        return this;
    }

    /**
     * Reads the next N bytes at this buffer's current position,
     * composing them into a string value according to the current byte order,
     * and then increments the position by N.
     *
     * @return The string at the buffer's current position
     */
    public String getString(int lengthInBytes) {
        return new String(getArray(lengthInBytes), 0, lengthInBytes, StandardCharsets.UTF_8);
    }

    /**
     * Trims the capacity of this {@code ByteBuf} instance to be the
     * buffer's current limit. An application can use this operation to minimize
     * the storage of an {@code ByteBuf} instance.
     */
    public void trimToSize() {
        resize(limit);
    }

    /**
     * Returns the byte array that backs this buffer (optional operation).
     * <p>
     * Modifications to this buffer's content will cause the returned
     * array's content to be modified, and vice versa.
     *
     * @return The array that backs this buffer
     */
    public byte[] toArray() {
        byte[] newData = new byte[limit];
        System.arraycopy(data, 0, newData, 0, limit);
        return newData;
    }

    /**
     * Enlarge this byte buffer so that it can receive n more bytes.
     *
     * @param capacity new number of bytes in this buffer.
     */
    private void resize(int capacity) {
        byte[] newData = new byte[capacity];
        int length = Math.min(data.length, capacity);
        System.arraycopy(data, 0, newData, 0, length);
        data = newData;
        this.capacity = capacity;
    }
}
