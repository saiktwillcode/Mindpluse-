package com.mindpulse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public record Reg(String name, String email, String password) {}
    public record Login(String email, String password) {}
    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private final UserStore users;
    private final JwtService jwt;

    public AuthController(UserStore users, JwtService jwt) { this.users = users; this.jwt = jwt; }

    private static ResponseEntity<?> err(int code, String msg) { return ResponseEntity.status(code).body(Map.of("error", msg)); }
    private ResponseEntity<?> session(UserStore.User u) {
        return ResponseEntity.ok(Map.of("token", jwt.create(u), "name", u.name(), "role", u.role()));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Reg r) {
        if (r.name() == null || r.name().isBlank() || r.name().length() > 80) return err(400, "Enter your name (max 80 characters).");
        if (r.email() == null || !EMAIL.matcher(r.email().trim()).matches()) return err(400, "Enter a valid email address.");
        if (r.password() == null || r.password().length() < 8 || r.password().length() > 72) return err(400, "Password must be 8 to 72 characters.");
        UserStore.User u = users.register(r.name().trim(), r.email().trim().toLowerCase(), r.password());
        return u == null ? err(409, "An account with this email already exists.") : session(u);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Login r) {
        if (r.email() == null || r.password() == null) return err(400, "Enter your email and password.");
        UserStore.User u = users.authenticate(r.email().trim().toLowerCase(), r.password());
        return u == null ? err(401, "Invalid email or password.") : session(u);
    }
}
