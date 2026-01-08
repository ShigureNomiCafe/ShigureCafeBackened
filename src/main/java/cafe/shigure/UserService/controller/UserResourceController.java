package cafe.shigure.UserService.controller;

import cafe.shigure.UserService.dto.AuditResponse;
import cafe.shigure.UserService.dto.UserResponse;
import cafe.shigure.UserService.model.Role;
import cafe.shigure.UserService.model.User;
import cafe.shigure.UserService.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserResourceController {

    private final UserService userService;

    @GetMapping("/audits")
    public ResponseEntity<List<AuditResponse>> getAudits(@AuthenticationPrincipal User currentUser) {
        if (currentUser.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(userService.getPendingAudits());
    }

    @PostMapping("/audits/{id}/approve")
    public ResponseEntity<Void> approveAudit(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        if (currentUser.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        userService.approveAudit(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getUsers(@RequestParam(required = false) String username) {
        if (username != null && !username.isEmpty()) {
             try {
                 User user = userService.getUserByUsername(username);
                 return ResponseEntity.ok(Collections.singletonList(mapToUserResponse(user)));
             } catch (Exception e) {
                 return ResponseEntity.ok(Collections.emptyList());
             }
        }
        
        List<UserResponse> users = userService.getAllUsers().stream()
                .map(this::mapToUserResponse)
                .toList();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{username}")
    public ResponseEntity<UserResponse> getUserByUsername(@PathVariable String username) {
        User user = userService.getUserByUsername(username);
        return ResponseEntity.ok(mapToUserResponse(user));
    }

    @DeleteMapping("/{username}")
    public ResponseEntity<Void> deleteUser(@PathVariable String username, @AuthenticationPrincipal User currentUser) {
        if (currentUser.getUsername().equals(username)) {
            throw new cafe.shigure.UserService.exception.BusinessException("You cannot delete yourself");
        }
        User user = userService.getUserByUsername(username);
        userService.deleteUser(user.getId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{username}/password")
    public ResponseEntity<Void> changePassword(@PathVariable String username, @RequestBody ChangePasswordRequest request, @AuthenticationPrincipal User currentUser) {
        boolean isSelf = currentUser.getUsername().equals(username);
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        if (!isSelf && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        User targetUser = userService.getUserByUsername(username);
        if (isAdmin && !isSelf) {
            userService.resetPassword(targetUser.getId(), request.newPassword());
        } else {
            userService.changePassword(targetUser.getId(), request.oldPassword(), request.newPassword());
        }
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{username}/email")
    public ResponseEntity<Void> updateEmail(@PathVariable String username, @RequestBody cafe.shigure.UserService.dto.UpdateEmailRequest request, @AuthenticationPrincipal User currentUser) {
        boolean isSelf = currentUser.getUsername().equals(username);
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        if (!isSelf && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        User targetUser = userService.getUserByUsername(username);
        
        if (isAdmin && !isSelf) {
            userService.updateEmailDirectly(targetUser.getId(), request.getNewEmail());
        } else {
            // Self update or Admin updating self (still requires verification for safety? Or just allow direct?)
            // Usually if admin updates self, they might want to follow standard flow, or just direct. 
            // Let's assume Admin updating anyone (including self) via this logic *could* be direct, 
            // but the prompt implies "Admin management page". 
            // If I am admin and I use the Profile page, I expect standard flow. 
            // If I use Admin page, I expect direct flow.
            // But the endpoint is shared.
            // Let's say: if verification code is present, use standard. If not and Admin, use direct.
            if (request.getVerificationCode() != null && !request.getVerificationCode().isEmpty()) {
                 userService.updateEmail(targetUser.getId(), request.getNewEmail(), request.getVerificationCode());
            } else if (isAdmin) {
                 userService.updateEmailDirectly(targetUser.getId(), request.getNewEmail());
            } else {
                 throw new cafe.shigure.UserService.exception.BusinessException("Verification code required");
            }
        }
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{username}/role")
    public ResponseEntity<Void> updateRole(@PathVariable String username, @RequestBody ChangeRoleRequest request, @AuthenticationPrincipal User currentUser) {
        if (currentUser.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        User targetUser = userService.getUserByUsername(username);
        userService.updateRole(targetUser.getId(), request.role());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{username}/nickname")
    public ResponseEntity<Void> updateNickname(@PathVariable String username, @RequestBody UpdateNicknameRequest request, @AuthenticationPrincipal User currentUser) {
        boolean isSelf = currentUser.getUsername().equals(username);
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        if (!isSelf && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        User targetUser = userService.getUserByUsername(username);
        userService.updateNickname(targetUser.getId(), request.nickname());
        return ResponseEntity.ok().build();
    }

    private UserResponse mapToUserResponse(User user) {
        return new UserResponse(user.getUsername(), user.getNickname(), user.getEmail(), user.getRole(), user.getStatus());
    }

    public record ChangePasswordRequest(String oldPassword, String newPassword) {}
    public record ChangeRoleRequest(Role role) {}
    public record UpdateNicknameRequest(String nickname) {}
}
