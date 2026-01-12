package cafe.shigure.ShigureCafeBackened.controller;

import cafe.shigure.ShigureCafeBackened.dto.UserResponse;
import cafe.shigure.ShigureCafeBackened.exception.BusinessException;
import cafe.shigure.ShigureCafeBackened.model.Role;
import cafe.shigure.ShigureCafeBackened.model.User;
import cafe.shigure.ShigureCafeBackened.service.UserService;
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
            throw new BusinessException("SELF_DELETION_PROTECTED");
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
    public ResponseEntity<Void> updateEmail(@PathVariable String username, @RequestBody cafe.shigure.ShigureCafeBackened.dto.UpdateEmailRequest request, @AuthenticationPrincipal User currentUser) {
        boolean isSelf = currentUser.getUsername().equals(username);
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        if (!isSelf && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        User targetUser = userService.getUserByUsername(username);
        
        if (isAdmin && !isSelf) {
            userService.updateEmailDirectly(targetUser.getId(), request.getNewEmail());
        } else {
            if (request.getVerificationCode() != null && !request.getVerificationCode().isEmpty()) {
                 userService.updateEmail(targetUser.getId(), request.getNewEmail(), request.getVerificationCode());
            } else if (isAdmin) {
                 userService.updateEmailDirectly(targetUser.getId(), request.getNewEmail());
            } else {
                 throw new BusinessException("VERIFICATION_CODE_REQUIRED");
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

    @PutMapping("/{username}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable String username, @RequestBody ChangeStatusRequest request, @AuthenticationPrincipal User currentUser) {
        if (currentUser.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (currentUser.getUsername().equals(username) && request.status() == cafe.shigure.ShigureCafeBackened.model.UserStatus.BANNED) {
            throw new BusinessException("SELF_BAN_PROTECTED");
        }
        User targetUser = userService.getUserByUsername(username);
        userService.updateStatus(targetUser.getId(), request.status());
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

    @PutMapping("/{username}/2fa")
    public ResponseEntity<Void> toggleTwoFactor(@PathVariable String username, @RequestBody ToggleTwoFactorRequest request, @AuthenticationPrincipal User currentUser) {
        if (!currentUser.getUsername().equals(username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        userService.toggleTwoFactor(currentUser.getId(), request.enabled(), request.code());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{username}/2fa/totp/setup")
    public ResponseEntity<UserService.TotpSetupResponse> setupTotp(@PathVariable String username, @AuthenticationPrincipal User currentUser) {
        if (!currentUser.getUsername().equals(username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(userService.setupTotp(currentUser.getId()));
    }

    @PostMapping("/{username}/2fa/totp/confirm")
    public ResponseEntity<Void> confirmTotp(@PathVariable String username, @RequestBody ConfirmTotpRequest request, @AuthenticationPrincipal User currentUser) {
        if (!currentUser.getUsername().equals(username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        userService.confirmTotp(currentUser.getId(), request.secret(), request.code());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{username}/2fa/totp")
    public ResponseEntity<Void> disableTotp(@PathVariable String username, @AuthenticationPrincipal User currentUser) {
        if (!currentUser.getUsername().equals(username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        userService.disableTotp(currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{username}/2fa")
    public ResponseEntity<Void> resetTwoFactor(@PathVariable String username, @AuthenticationPrincipal User currentUser) {
        if (currentUser.getRole() != Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        User targetUser = userService.getUserByUsername(username);
        userService.resetTwoFactor(targetUser.getId());
        return ResponseEntity.ok().build();
    }

    private UserResponse mapToUserResponse(User user) {
        boolean totpEnabled = user.getTotpSecret() != null;
        return new UserResponse(
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.isEmail2faEnabled() || totpEnabled,
                user.isEmail2faEnabled(),
                totpEnabled
        );
    }

    public record ChangePasswordRequest(String oldPassword, String newPassword) {}
    public record ChangeRoleRequest(Role role) {}
    public record ChangeStatusRequest(cafe.shigure.ShigureCafeBackened.model.UserStatus status) {}
    public record UpdateNicknameRequest(String nickname) {}
    public record ToggleTwoFactorRequest(boolean enabled, String code) {}
    public record ConfirmTotpRequest(String secret, String code) {}
}
