package io.remotedownloader.dao;

import io.netty.buffer.ByteBufUtil;
import io.remotedownloader.model.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class UserDao {
    private final ConcurrentMap<String, User> users;
    private final StorageDao storageDao;

    public UserDao(StorageDao storageDao) {
        this.storageDao = storageDao;
        this.users = new ConcurrentHashMap<>(storageDao.readAllRecords(User.class));

        if (!hasAdminUser()) {
            createAdmin();
        }
    }

    public void saveUser(User user) {
        users.put(user.username(), user);
        storageDao.saveRecord(user);
    }

    public User getUserByUsername(String username) {
        return users.get(username);
    }

    private void createAdmin() {
        try {
            String username = "admin";

            SecureRandom secureRandom = new SecureRandom();
            byte[] randomBytes = new byte[18];
            secureRandom.nextBytes(randomBytes);
            String password = Base64.getUrlEncoder().encodeToString(randomBytes);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(password.getBytes(StandardCharsets.UTF_8));
            byte[] byteData = digest.digest(username.getBytes(StandardCharsets.UTF_8));
            String passwordEncrypted = ByteBufUtil.hexDump(byteData);

            User user = new User(
                    username,
                    passwordEncrypted,
                    true,
                    System.currentTimeMillis()
            );
            saveUser(user);

            String consoleMessage = "===================================\n"
                    + "Created admin user:\n"
                    + "Username: admin\n"
                    + "Password: " + password + "\n"
                    + "===================================\n";
            System.out.print(consoleMessage);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to create admin user: SHA-256 not supported.", e);
        }
    }

    private boolean hasAdminUser() {
        User admin = users.get("admin");
        if (admin != null && admin.isAdmin()) {
            return true;
        }

        for (User user : users.values()) {
            if (user.isAdmin()) {
                return true;
            }
        }
        return false;
    }
}
