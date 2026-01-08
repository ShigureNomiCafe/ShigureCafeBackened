package cafe.shigure.UserService.controller;

import cafe.shigure.UserService.dto.RegisterRequest;
import cafe.shigure.UserService.dto.UserResponse;
import cafe.shigure.UserService.model.User;
import cafe.shigure.UserService.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/registrations")
@RequiredArgsConstructor
public class RegistrationController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
        String auditCode = userService.register(request);
        return ResponseEntity.created(URI.create("/api/v1/registrations/" + auditCode))
                .body(Map.of("auditCode", auditCode, "message", "Registration pending approval"));
    }

    @GetMapping("/{auditCode}")
    public ResponseEntity<UserResponse> checkRegistration(@PathVariable String auditCode) {
        User user = userService.getUserByAuditCode(auditCode);
        return ResponseEntity.ok(new UserResponse(user.getUsername(), user.getNickname(), user.getEmail(), user.getRole(), user.getStatus()));
    }

    @PatchMapping("/{auditCode}")
    public ResponseEntity<Void> approveRegistration(@PathVariable String auditCode) {
        userService.approveUser(auditCode);
        return ResponseEntity.ok().build();
    }
}
