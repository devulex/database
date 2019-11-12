package com.editbox.database;

/**
 * Operations that are allowed in the repository.
 *
 * @author Aleksandr Uhanov
 * @since 2018-09-11
 */
public enum RepositoryOperation {

    /**
     * Insert record.
     */
    INSERT((byte) 0x00),

    /**
     * Update record.
     */
    UPDATE((byte) 0x01),

    /**
     * Delete record.
     */
    DELETE((byte) 0x02);

    private final byte code;

    static RepositoryOperation fromCode(byte code) {
        switch (code) {
            case 0x00:
                return INSERT;
            case 0x01:
                return UPDATE;
            case 0x02:
                return DELETE;
            default:
                throw new RuntimeException("Invalid operation code " + code);
        }
    }

    RepositoryOperation(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }
}
