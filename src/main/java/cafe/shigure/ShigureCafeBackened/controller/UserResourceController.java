package cafe.shigure.ShigureCafeBackened.controller;

import cafe.shigure.ShigureCafeBackened.annotation.RateLimit;
import cafe.shigure.ShigureCafeBackened.dto.PagedResponse;
import cafe.shigure.ShigureCafeBackened.dto.UpdateEmailRequest;
import cafe.shigure.ShigureCafeBackened.dto.UserResponse;
import cafe.shigure.ShigureCafeBackened.exception.BusinessException;
import cafe.shigure.ShigureCafeBackened.model.Role;
import cafe.shigure.ShigureCafeBackened.model.User;
import cafe.shigure.ShigureCafeBackened.model.UserStatus;
import cafe.shigure.ShigureCafeBackened.service.MinecraftAuthService;
import cafe.shigure.ShigureCafeBackened.service.RateLimitService;
import cafe.shigure.ShigureCafeBackened.service.StorageService;
import cafe.shigure.ShigureCafeBackened.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserResourceController {

    private final UserService userService;
    private final MinecraftAuthService minecraftAuthService;
    private final RateLimitService rateLimitService;
    private final StorageService storageService;

    @Value("${application.microsoft.minecraft.client-id}")
    private String microsoftClientId;

    @GetMapping
    @RateLimit(key = "users:list", expression = "#currentUser.id", milliseconds = 500)
    public ResponseEntity<PagedResponse<UserResponse>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal User currentUser) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("username").ascending());
        return ResponseEntity.ok(userService.getUsersPaged(pageable));
    }

    @GetMapping("/{username}")
    public ResponseEntity<UserResponse> getUserByUsername(@PathVariable String username) {
        User user = userService.getUserByUsername(username);
        return ResponseEntity.ok(userService.mapToUserResponse(user));
    }

    @DeleteMapping("/{username}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable String username, @AuthenticationPrincipal User currentUser) {
        if (currentUser.getUsername().equals(username)) {
            throw new BusinessException("SELF_DELETION_PROTECTED");
        }
        User user = userService.getUserByUsername(username);
        userService.deleteUser(user.getId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{username}/password")
    @PreAuthorize("hasAuthority('ADMIN') or #username == authentication.name")
    public ResponseEntity<Void> changePassword(@PathVariable String username, 
                                             @RequestBody ChangePasswordRequest request, 
                                             @AuthenticationPrincipal User currentUser) {
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        boolean isSelf = currentUser.getUsername().equals(username);

        User targetUser = userService.getUserByUsername(username);
        if (isAdmin && !isSelf) {
            userService.resetPassword(targetUser.getId(), request.newPassword());
        } else {
            userService.changePassword(targetUser.getId(), request.oldPassword(), request.newPassword());
        }
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{username}/email")
    @PreAuthorize("hasAuthority('ADMIN') or #username == authentication.name")
    public ResponseEntity<Void> updateEmail(@PathVariable String username, 
                                          @RequestBody UpdateEmailRequest request, 
                                          @AuthenticationPrincipal User currentUser) {
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        boolean isSelf = currentUser.getUsername().equals(username);

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
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> updateRole(@PathVariable String username, @RequestBody ChangeRoleRequest request) {
        User targetUser = userService.getUserByUsername(username);
        userService.updateRole(targetUser.getId(), request.role());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{username}/status")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> updateStatus(@PathVariable String username, 
                                           @RequestBody ChangeStatusRequest request, 
                                           @AuthenticationPrincipal User currentUser) {
        if (currentUser.getUsername().equals(username) && request.status() == UserStatus.BANNED) {
            throw new BusinessException("SELF_BAN_PROTECTED");
        }
        User targetUser = userService.getUserByUsername(username);
        userService.updateStatus(targetUser.getId(), request.status());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{username}/nickname")
    @PreAuthorize("hasAuthority('ADMIN') or #username == authentication.name")
    public ResponseEntity<Void> updateNickname(@PathVariable String username, @RequestBody UpdateNicknameRequest request) {
        User targetUser = userService.getUserByUsername(username);
        userService.updateNickname(targetUser.getId(), request.nickname());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{username}/2fa")
    @PreAuthorize("#username == authentication.name")
    public ResponseEntity<Void> toggleTwoFactor(@PathVariable String username, 
                                              @RequestBody ToggleTwoFactorRequest request, 
                                              @AuthenticationPrincipal User currentUser) {
        userService.toggleTwoFactor(currentUser.getId(), request.enabled(), request.code());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{username}/2fa/totp/setup")
    @PreAuthorize("#username == authentication.name")
    public ResponseEntity<UserService.TotpSetupResponse> setupTotp(@PathVariable String username, 
                                                                 @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(userService.setupTotp(currentUser.getId()));
    }

    @PostMapping("/{username}/2fa/totp/confirm")
    @PreAuthorize("#username == authentication.name")
    public ResponseEntity<Void> confirmTotp(@PathVariable String username, 
                                          @RequestBody ConfirmTotpRequest request, 
                                          @AuthenticationPrincipal User currentUser) {
        userService.confirmTotp(currentUser.getId(), request.secret(), request.code());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{username}/2fa/totp")
    @PreAuthorize("#username == authentication.name")
    public ResponseEntity<Void> disableTotp(@PathVariable String username, 
                                          @AuthenticationPrincipal User currentUser) {
        userService.disableTotp(currentUser.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/config/microsoft-client-id")
    public ResponseEntity<Map<String, String>> getMicrosoftClientId() {
        return ResponseEntity.ok(Map.of("clientId", microsoftClientId));
    }

    @DeleteMapping("/{username}/2fa")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> resetTwoFactor(@PathVariable String username) {
        User targetUser = userService.getUserByUsername(username);
        userService.resetTwoFactor(targetUser.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{username}/minecraft/bind")
    @PreAuthorize("#username == authentication.name")
    public ResponseEntity<Void> bindMinecraft(@PathVariable String username, 
                                            @RequestBody BindMinecraftRequest request, 
                                            @AuthenticationPrincipal User currentUser) {
        MinecraftAuthService.MinecraftProfile profile = minecraftAuthService.getMinecraftProfile(request.code(), request.redirectUri());
        userService.updateMinecraftInfo(currentUser.getId(), profile.id(), profile.name());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{username}/minecraft/refresh")
    @PreAuthorize("hasAuthority('ADMIN') or #username == authentication.name")
    public ResponseEntity<Void> refreshMinecraft(@PathVariable String username) {
        User targetUser = userService.getUserByUsername(username);
        userService.refreshMinecraftUsername(targetUser.getId(), minecraftAuthService);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{username}/avatar")
    @PreAuthorize("#username == authentication.name")
    @RateLimit(key = "users:avatar", expression = "#currentUser.id", milliseconds = 5000)
    public ResponseEntity<Map<String, String>> uploadAvatar(@PathVariable String username,
                                                           @RequestParam("file") MultipartFile file,
                                                           @AuthenticationPrincipal User currentUser) {
        if (file.isEmpty()) {
            throw new BusinessException("FILE_EMPTY");
        }
        
        // Basic size check (e.g., 2MB)
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new BusinessException("FILE_TOO_LARGE");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException("INVALID_FILE_TYPE");
        }

        String avatarUrl = storageService.uploadFile(file, "avatars");
        userService.updateAvatar(currentUser.getId(), avatarUrl);
        
        return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));
    }

    @GetMapping("/{username}/avatar/presigned-url")
    @PreAuthorize("#username == authentication.name")
    public ResponseEntity<Map<String, String>> getPresignedUrl(@PathVariable String username,
                                                              @RequestParam String contentType) {
        return ResponseEntity.ok(storageService.generatePresignedUploadUrl("avatars", contentType));
    }

    @PutMapping("/{username}/avatar")
    @PreAuthorize("#username == authentication.name")
    public ResponseEntity<Void> updateAvatarUrl(@PathVariable String username,
                                                @RequestBody UpdateAvatarRequest request,
                                                @AuthenticationPrincipal User currentUser) {
        userService.updateAvatar(currentUser.getId(), request.avatarUrl());
        return ResponseEntity.ok().build();
    }

    public record ChangePasswordRequest(String oldPassword, String newPassword) {}
    public record ChangeRoleRequest(Role role) {}
    public record ChangeStatusRequest(UserStatus status) {}
    public record UpdateNicknameRequest(String nickname) {}
    public record UpdateAvatarRequest(String avatarUrl) {}
    public record ToggleTwoFactorRequest(boolean enabled, String code) {}
    public record ConfirmTotpRequest(String secret, String code) {}
    public record BindMinecraftRequest(String code, String redirectUri) {}
}
