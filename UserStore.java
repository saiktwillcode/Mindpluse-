package com.mindpulse;

import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory user store with BCrypt hashes (swap for a JPA repository when a database is added). */
@Service
public class UserStore {
    public record User(String name, String email, String role, String passwordHash, Instant createdAt) {}
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final BCryptPasswordEncoder enc = new BCryptPasswordEncoder(10);
    private final String dummyHash = enc.encode("timing-equalizer");

    @PostConstruct
    void seed() {
        users.put("student@demo.edu", new User("Demo Student", "student@demo.edu", "STUDENT", enc.encode("Demo@1234"), Instant.now()));
        users.put("faculty@demo.edu", new User("Demo Faculty", "faculty@demo.edu", "FACULTY", enc.encode("Demo@1234"), Instant.now()));
        users.put("admin@demo.edu", new User("Demo Admin", "admin@demo.edu", "ADMIN", enc.encode("Demo@1234"), Instant.now()));
    }

    /** Self-registration always creates a STUDENT. Faculty/admin accounts are provisioned by an admin. */
    public User register(String name, String email, String password) {
        User u = new User(name, email, "STUDENT", enc.encode(password), Instant.now());
        return users.putIfAbsent(email, u) == null ? u : null;
    }

    public User authenticate(String email, String password) {
        User u = users.get(email);
        boolean ok = enc.matches(password, u != null ? u.passwordHash() : dummyHash);
        return (u != null && ok) ? u : null;
    }
}
