package cafe.shigure.ShigureCafeBackened.service;

import cafe.shigure.ShigureCafeBackened.dto.AuthResponse;
import cafe.shigure.ShigureCafeBackened.dto.LoginRequest;
import cafe.shigure.ShigureCafeBackened.dto.RegisterRequest;
import cafe.shigure.ShigureCafeBackened.exception.BusinessException;
import cafe.shigure.ShigureCafeBackened.model.Role;
import cafe.shigure.ShigureCafeBackened.model.User;
import cafe.shigure.ShigureCafeBackened.model.UserAudit;
import cafe.shigure.ShigureCafeBackened.model.UserStatus;
import cafe.shigure.ShigureCafeBackened.repository.UserAuditRepository;
import cafe.shigure.ShigureCafeBackened.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserAuditRepository userAuditRepository;
    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final cafe.shigure.ShigureCafeBackened.repository.TokenBlacklistRepository tokenBlacklistRepository;

    private final SecretGenerator totpSecretGenerator = new DefaultSecretGenerator();
    private final CodeVerifier totpVerifier = new DefaultCodeVerifier(
            new DefaultCodeGenerator(),
            new SystemTimeProvider());

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public boolean checkUsersModified(Long t) {
        if (t == null) {
            return true;
        }
        return userRepository.findMaxUpdatedAt()
                .map(upd -> upd > t)
                .orElse(true);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public boolean checkAuditsNotModified(Long t) {
        if (t == null) {
            return false;
        }
        return userAuditRepository.findMaxUpdatedAt()
                .map(upd -> upd <= t)
                .orElse(t >= 0);
    }

    @Transactional
    public void sendVerificationCode(String email, String type) {
        if ("REGISTER".equalsIgnoreCase(type) || "UPDATE_EMAIL".equalsIgnoreCase(type)) {
            if (userRepository.findByEmail(email).isPresent()) {
                throw new BusinessException("EMAIL_IN_USE");
            }
        } else if ("RESET_PASSWORD".equalsIgnoreCase(type) || "2FA".equalsIgnoreCase(type)) {
            if (userRepository.findByEmail(email).isEmpty()) {
                throw new BusinessException("USER_NOT_FOUND");
            }
        }

        // 生成 6 位验证码
        String code = String.format("%06d", new Random().nextInt(999999));

        // 保存或更新验证码
        String limitKey = "verify:limit:" + email;
        String codeKey = "verify:code:" + email;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(limitKey))) {
            Long expire = redisTemplate.getExpire(limitKey, TimeUnit.SECONDS);
            throw new BusinessException("RATE_LIMIT_EXCEEDED",
                    java.util.Map.of("retryAfter", expire != null ? expire : 60));
        }

        redisTemplate.opsForValue().set(codeKey, code, 5, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(limitKey, "1", 60, TimeUnit.SECONDS);

        // 发送邮件
        emailService.sendSimpleMessage(email, "猫咖验证码", "您的验证码是：" + code + "，请在5分钟内使用。");
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()));
        var user = userRepository.findByUsername(request.getUsername())
                .or(() -> userRepository.findByEmail(request.getUsername()))
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND"));

        if (user.getStatus() == UserStatus.PENDING) {
            throw new BusinessException("ACCOUNT_PENDING");
        }
        if (user.getStatus() == UserStatus.BANNED) {
            throw new BusinessException("ACCOUNT_BANNED");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("ACCOUNT_INACTIVE");
        }

        if (user.isEmail2faEnabled() || user.getTotpSecret() != null) {
            return new AuthResponse(null, true, user.getTotpSecret() != null, user.isEmail2faEnabled(),
                    user.getEmail());
        }

        var jwtToken = jwtService.generateToken(user);
        return new AuthResponse(jwtToken);
    }

    @Transactional
    public AuthResponse verify2FA(String username, String code) {
        var user = userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username))
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND"));

        boolean verified = false;
        if (user.getTotpSecret() != null) {
            if (totpVerifier.isValidCode(user.getTotpSecret(), code)) {
                verified = true;
            }
        }

        if (!verified && user.isEmail2faEnabled()) {
            verifyCode(user.getEmail(), code);
            verified = true;
        }

        if (!verified) {
            throw new BusinessException("INVALID_2FA_CODE");
        }

        var jwtToken = jwtService.generateToken(user);
        return new AuthResponse(jwtToken);
    }

    public TotpSetupResponse setupTotp(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND"));

        String secret = totpSecretGenerator.generate();

        QrData data = new QrData.Builder()
                .label(user.getEmail())
                .secret(secret)
                .issuer("ShigureCafe")
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        return new TotpSetupResponse(secret, data.getUri());
    }

    public record TotpSetupResponse(String secret, String uri) {
    }

    @Transactional
    public void confirmTotp(Long userId, String secret, String code) {
        if (!totpVerifier.isValidCode(secret, code)) {
            throw new BusinessException("INVALID_2FA_CODE");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND"));
        user.setTotpSecret(secret);
        userRepository.save(user);
    }

    @Transactional
    public void disableTotp(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND"));
        user.setTotpSecret(null);
        userRepository.save(user);
    }

    @Transactional
    public void toggleTwoFactor(Long id, boolean enabled, String code) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND"));

        if (enabled) {
            if (code == null || code.isBlank()) {
                throw new BusinessException("VERIFICATION_CODE_REQUIRED");
            }
            verifyCode(user.getEmail(), code);
        }

        user.setEmail2faEnabled(enabled);
        userRepository.save(user);
    }

    @Transactional
    public void resetTwoFactor(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND"));
        user.setEmail2faEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);
    }

    public void logout(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            String jwt = token.substring(7);
            long expirationDate = jwtService.extractExpiration(jwt);
            tokenBlacklistRepository
                    .save(new cafe.shigure.ShigureCafeBackened.model.TokenBlacklist(jwt, expirationDate));
        }
    }

    @Transactional
    public String register(RegisterRequest request) {
        Optional<User> existingUser = userRepository.findByUsername(request.getUsername());

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            if (user.getStatus() == UserStatus.ACTIVE) {
                throw new BusinessException("USER_ALREADY_EXISTS");
            }
            if (user.getStatus() == UserStatus.BANNED) {
                throw new BusinessException("ACCOUNT_BANNED");
            }
            // User is PENDING, verify code and refresh audit code
            verifyCode(request.getEmail(), request.getVerificationCode());

            // Check existing audit
            UserAudit audit = userAuditRepository.findByUserId(user.getId())
                    .orElse(new UserAudit(user, UUID.randomUUID().toString(), 7));

            // Always refresh code and expiry if re-registering
            audit.setAuditCode(UUID.randomUUID().toString());
            audit.setExpiryDate(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000);
            userAuditRepository.save(audit);

            return audit.getAuditCode();
        }

        // New User
        verifyCode(request.getEmail(), request.getVerificationCode());

        if (request.getNickname() != null && request.getNickname().length() > 50) {
            throw new BusinessException("NICKNAME_TOO_LONG", java.util.Map.of("maxLength", 50));
        }

        // 创建用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setNickname(request.getNickname() != null && !request.getNickname().isBlank()
                ? request.getNickname()
                : request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);
        user.setStatus(UserStatus.PENDING);

        userRepository.save(user);

        // 生成审核码 (7天有效)
        String auditCode = UUID.randomUUID().toString();
        UserAudit userAudit = new UserAudit(user, auditCode, 7);
        userAuditRepository.save(userAudit);

        return auditCode;
    }

    @Transactional
    public void resetPasswordByEmail(cafe.shigure.ShigureCafeBackened.dto.ResetPasswordRequest request) {
        verifyCode(request.getEmail(), request.getVerificationCode());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    private void verifyCode(String email, String code) {
        String codeKey = "verify:code:" + email;
        String storedCode = redisTemplate.opsForValue().get(codeKey);

        if (storedCode == null) {
            throw new BusinessException("VERIFICATION_CODE_NOT_FOUND");
        }

        if (!storedCode.equals(code)) {
            throw new BusinessException("VERIFICATION_CODE_INVALID");
        }

        // 验证通过，删除验证码（防止复用）
        redisTemplate.delete(codeKey);
    }

    public User getUserByAuditCode(String auditCode) {
        UserAudit audit = userAuditRepository.findByAuditCode(auditCode)
                .orElseThrow(() -> new BusinessException("INVALID_AUDIT_CODE"));
        return audit.getUser();
    }

    public List<String> getAllAuditCodes() {
        return userAuditRepository.findAll().stream()
                .map(UserAudit::getAuditCode)
                .collect(Collectors.toList());
    }

    public cafe.shigure.ShigureCafeBackened.dto.PagedResponse<cafe.shigure.ShigureCafeBackened.dto.RegistrationDetailsResponse> getAuditsPaged(
            Pageable pageable) {
        Page<cafe.shigure.ShigureCafeBackened.dto.RegistrationDetailsResponse> page = userAuditRepository
                .findAll(pageable)
                .map(this::mapToRegistrationDetailsResponse);
        Long timestamp = userAuditRepository.findMaxUpdatedAt()
                .orElse(0L);
        return cafe.shigure.ShigureCafeBackened.dto.PagedResponse.fromPage(page, timestamp);
    }

    public cafe.shigure.ShigureCafeBackened.dto.RegistrationDetailsResponse getRegistrationDetails(String auditCode) {
        UserAudit audit = userAuditRepository.findByAuditCode(auditCode)
                .orElseThrow(() -> new BusinessException("INVALID_AUDIT_CODE"));
        return mapToRegistrationDetailsResponse(audit);
    }

    public cafe.shigure.ShigureCafeBackened.dto.RegistrationDetailsResponse mapToRegistrationDetailsResponse(
            UserAudit audit) {
        User user = audit.getUser();
        return new cafe.shigure.ShigureCafeBackened.dto.RegistrationDetailsResponse(
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getStatus(),
                audit.getAuditCode(),
                audit.isExpired());
    }

    @Transactional
    public void approveUser(String auditCode) {
        UserAudit audit = userAuditRepository.findByAuditCode(auditCode)
                .orElseThrow(() -> new BusinessException("INVALID_AUDIT_CODE"));

        if (audit.isExpired()) {
            throw new BusinessException("AUDIT_CODE_EXPIRED");
        }

        User user = audit.getUser();
        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new BusinessException("USER_ALREADY_ACTIVE");
        }
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        // Remove audit record after successful approval
        userAuditRepository.delete(audit);
    }

    @Transactional
    public void banUser(String auditCode) {
        UserAudit audit = userAuditRepository.findByAuditCode(auditCode)
                .orElseThrow(() -> new BusinessException("INVALID_AUDIT_CODE"));

        User user = audit.getUser();
        user.setStatus(UserStatus.BANNED);
        userRepository.save(user);

        // Remove audit record after banning
        userAuditRepository.delete(audit);
    }

    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new BusinessException("USER_NOT_FOUND");
        }
        userRepository.deleteById(id);
    }

    public void changePassword(Long id, String oldPassword, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND"));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("OLD_PASSWORD_MISMATCH");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public void resetPassword(Long id, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void updateEmail(Long id, String newEmail, String verificationCode) {
        verifyCode(newEmail, verificationCode);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND"));

        // 检查新邮箱是否已被使用 (排除自己)
        userRepository.findByEmail(newEmail).ifPresent(existingUser -> {
            if (!existingUser.getId().equals(id)) {
                throw new BusinessException("EMAIL_IN_USE");
            }
        });

        user.setEmail(newEmail);
        userRepository.save(user);
    }

    @Transactional
    public void updateEmailDirectly(Long id, String newEmail) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND"));

        // Check if new email is already in use (excluding self)
        userRepository.findByEmail(newEmail).ifPresent(existingUser -> {
            if (!existingUser.getId().equals(id)) {
                throw new BusinessException("EMAIL_IN_USE");
            }
        });

        user.setEmail(newEmail);
        userRepository.save(user);
    }

    @Transactional
    public void updateRole(Long id, Role newRole) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND"));
        user.setRole(newRole);
        userRepository.save(user);
    }

    @Transactional
    public void updateStatus(Long id, UserStatus newStatus) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND"));
        user.setStatus(newStatus);
        userRepository.save(user);
    }

    public java.util.List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public cafe.shigure.ShigureCafeBackened.dto.PagedResponse<cafe.shigure.ShigureCafeBackened.dto.UserResponse> getUsersPaged(
            Pageable pageable) {
        Page<cafe.shigure.ShigureCafeBackened.dto.UserResponse> page = userRepository.findAll(pageable)
                .map(this::mapToUserResponse);
        Long timestamp = userRepository.findMaxUpdatedAt()
                .orElse(System.currentTimeMillis());
        return cafe.shigure.ShigureCafeBackened.dto.PagedResponse.fromPage(page, timestamp);
    }

    public cafe.shigure.ShigureCafeBackened.dto.UserResponse mapToUserResponse(User user) {
        boolean totpEnabled = user.getTotpSecret() != null;
        return new cafe.shigure.ShigureCafeBackened.dto.UserResponse(
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.isEmail2faEnabled() || totpEnabled,
                user.isEmail2faEnabled(),
                totpEnabled,
                user.getMinecraftUuid(),
                user.getMinecraftUsername());
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND"));
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND"));
    }

    @Transactional
    public void updateNickname(Long id, String nickname) {
        if (nickname != null && nickname.length() > 50) {
            throw new BusinessException("NICKNAME_TOO_LONG", java.util.Map.of("maxLength", 50));
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND"));
        user.setNickname(nickname);
        userRepository.save(user);
    }

    @Transactional
    public void updateMinecraftInfo(Long id, String uuid, String username) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND"));
        user.setMinecraftUuid(uuid);
        user.setMinecraftUsername(username);
        userRepository.save(user);
    }

    @Transactional
    public void refreshMinecraftUsername(Long id,
            cafe.shigure.ShigureCafeBackened.service.MinecraftAuthService minecraftAuthService) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND"));
        if (user.getMinecraftUuid() == null) {
            throw new BusinessException("MINECRAFT_NOT_BOUND");
        }
        String currentUsername = minecraftAuthService.getMinecraftUsername(user.getMinecraftUuid());
        user.setMinecraftUsername(currentUsername);
        userRepository.save(user);
    }

}