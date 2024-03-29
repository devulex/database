# database
High performance in-memory database

### Download

Just copy package `com.editbox.database` to your project.

### Example
A simple Java class for repository.
```java
public class User implements RepositoryAccess {
    @Uuid
    @NotNull
    private UUID id;

    @NotNull
    @MaxLength(50)
    private String name;

    @NotNull
    private LocalDateTime created;

    private boolean blocked;
    // getters, setters, some boring stuff
}
```
Create a database instance, add a user and get a list of users.
```java
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
}
```
Output
```
9f451f24-bf95-42a8-9559-563d4d336373 Alex 2019-11-13T01:20:54.601744300
b1221d05-6eae-48e5-b625-a57a56a139a9 Alex 2019-11-13T01:23:01.892311800
5f569221-47d9-45a5-beb9-e24b2eccc049 Alex 2019-11-13T01:21:58.224858400
feaad5e8-2e42-4ca9-a1df-96ba15f5ba77 Alex 2019-11-13T01:20:58.086081700
4bafcab2-d9e3-433b-ad4d-0d0054612406 Alex 2019-11-13T01:31:16.274782700
Number of users: 5
```
