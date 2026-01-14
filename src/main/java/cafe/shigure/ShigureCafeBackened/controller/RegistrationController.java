package cafe.shigure.ShigureCafeBackened.controller;

import cafe.shigure.ShigureCafeBackened.dto.PagedResponse;
import cafe.shigure.ShigureCafeBackened.dto.RegisterRequest;
import cafe.shigure.ShigureCafeBackened.dto.RegistrationDetailsResponse;
import cafe.shigure.ShigureCafeBackened.model.Role;
import cafe.shigure.ShigureCafeBackened.model.User;
import cafe.shigure.ShigureCafeBackened.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/registrations")
@RequiredArgsConstructor
public class RegistrationController {

    private final UserService userService;
    private final cafe.shigure.ShigureCafeBackened.service.RateLimitService rateLimitService;

    @PostMapping
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
        String auditCode = userService.register(request);
        return ResponseEntity.created(URI.create("/api/v1/registrations/" + auditCode))
                .body(Map.of("auditCode", auditCode));
    }

    @GetMapping
    public ResponseEntity<?> getAllRegistrations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal User currentUser) {
        if (currentUser == null || currentUser.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String identifier = currentUser.getId().toString();
        rateLimitService.checkRateLimit("audits:list:" + identifier, 1);
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("user.username").ascending());
        
        PagedResponse<RegistrationDetailsResponse> auditPage = userService.getAuditsPaged(pageable);
        
        return ResponseEntity.ok(auditPage);
    }

    @GetMapping("/{auditCode}")
    public ResponseEntity<RegistrationDetailsResponse> checkRegistration(@PathVariable String auditCode) {
        return ResponseEntity.ok(userService.getRegistrationDetails(auditCode));
    }

    @PatchMapping("/{auditCode}")
    public ResponseEntity<Void> approveRegistration(@PathVariable String auditCode) {
        userService.approveUser(auditCode);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{auditCode}")
    public ResponseEntity<Void> banUser(@PathVariable String auditCode) {
        userService.banUser(auditCode);
        return ResponseEntity.ok().build();
    }
}
