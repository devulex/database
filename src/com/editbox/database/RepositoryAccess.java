package com.editbox.database;

import java.util.UUID;

public abstract class RepositoryAccess {

    private transient boolean readonly = false;

    protected void requireNonReadonly() {
        if (readonly) {
            throw new RuntimeException("This object is readonly");
        }
    }

    public abstract UUID getId();

    public abstract void setId(UUID id);
}
