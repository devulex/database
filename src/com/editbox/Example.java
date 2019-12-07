package com.editbox;

import com.editbox.database.BackupResult;
import com.editbox.database.Database;
import com.editbox.database.Repository;
import com.editbox.database.RepositoryAccess;
import com.editbox.database.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

public class Example {

    public static void main(String[] args) {

        Database database = Database.configure("C:\\myapp\\database", "C:\\myapp\\backups");
        database.registerRepository(User.class, "users");
        database.enableBackup("05:00:00", Example::logBackupResult);

        final Repository<User> userRepository = Database.getRepository(User.class);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Alex");
        user.setCreated(LocalDateTime.now());

        userRepository.add(user);

        for (User u : userRepository.getAllForRead()) {
            System.out.println(u.getId() + " " + u.getName() + " " + u.getCreated());
        }
        System.out.println("Number of users: " + userRepository.size());
    }

    private static void logBackupResult(BackupResult result) {
        double duration = result.getDuration() / 1000d;
        if (result.isSuccessful()) {
            System.out.println("Backup created in " + duration + " seconds");
        } else {
            System.out.println("Error creating backup in " + duration + " seconds. " + result.getThrowable().getMessage());
        }
    }

    public static class User extends RepositoryAccess {

        @Uuid
        @NotNull
        private UUID id;

        @NotNull
        @MaxLength(50)
        private String name;

        @NotNull
        private LocalDateTime created;

        private boolean blocked;

        @Override
        public UUID getId() {
            return id;
        }

        @Override
        public void setId(UUID id) {
            requireNonReadonly();
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            requireNonReadonly();
            this.name = name;
        }

        public LocalDateTime getCreated() {
            return created;
        }

        public void setCreated(LocalDateTime created) {
            requireNonReadonly();
            this.created = created;
        }

        public boolean isBlocked() {
            return blocked;
        }

        public void setBlocked(boolean blocked) {
            requireNonReadonly();
            this.blocked = blocked;
        }
    }
}
