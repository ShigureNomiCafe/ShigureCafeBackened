package cafe.shigure.UserService.service;

import cafe.shigure.UserService.dto.AuditResponse;
import cafe.shigure.UserService.dto.AuthResponse;
import cafe.shigure.UserService.dto.LoginRequest;
import cafe.shigure.UserService.dto.RegisterRequest;
import cafe.shigure.UserService.exception.BusinessException;
import cafe.shigure.UserService.model.Role;
import cafe.shigure.UserService.model.User;
import cafe.shigure.UserService.model.UserAudit;
import cafe.shigure.UserService.model.UserStatus;
import cafe.shigure.UserService.model.VerificationCode;
import cafe.shigure.UserService.repository.UserAuditRepository;
import cafe.shigure.UserService.repository.UserRepository;
import cafe.shigure.UserService.repository.VerificationCodeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserAuditRepository userAuditRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final cafe.shigure.UserService.repository.TokenBlacklistRepository tokenBlacklistRepository;

    @Transactional
    public void sendVerificationCode(String email, String type) {
        if ("REGISTER".equalsIgnoreCase(type) || "UPDATE_EMAIL".equalsIgnoreCase(type)) {
            if (userRepository.findByEmail(email).isPresent()) {
                throw new BusinessException("Email already in use");
            }
        } else if ("RESET_PASSWORD".equalsIgnoreCase(type)) {
            if (userRepository.findByEmail(email).isEmpty()) {
                throw new BusinessException("User not found");
            }
        }

        // 生成 6 位验证码
        String code = String.format("%06d", new Random().nextInt(999999));

        // 保存或更新验证码
        VerificationCode verificationCode = verificationCodeRepository.findByEmail(email)
                .orElse(new VerificationCode());

        if (verificationCode.getLastSentTime() != null &&
                verificationCode.getLastSentTime().plusSeconds(60).isAfter(LocalDateTime.now())) {
            throw new BusinessException("操作过于频繁，请60秒后再试");
        }

        verificationCode.setEmail(email);
        verificationCode.setCode(code);
        verificationCode.setExpiryDate(java.time.LocalDateTime.now().plusMinutes(5)); // 5分钟有效
        verificationCode.setLastSentTime(LocalDateTime.now());

        verificationCodeRepository.save(verificationCode);

        // 发送邮件
        emailService.sendSimpleMessage(email, "猫咖验证码", "您的验证码是：" + code + "，请在5分钟内使用。");
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );
        var user = userRepository.findByUsername(request.getUsername())
                .or(() -> userRepository.findByEmail(request.getUsername()))
                .orElseThrow(() -> new BusinessException("用户不存在"));
        
        if (user.getStatus() == UserStatus.PENDING) {
            throw new BusinessException("账号审核中，请稍后");
        }
        if (user.getStatus() == UserStatus.BANNED) {
            throw new BusinessException("账号被封禁，请联系管理员");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("账号状态异常，请联系管理员");
        }

        var jwtToken = jwtService.generateToken(user);
        return new AuthResponse(jwtToken);
    }

    public void logout(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            String jwt = token.substring(7);
            java.util.Date expirationDate = jwtService.extractExpiration(jwt);
            tokenBlacklistRepository.save(new cafe.shigure.UserService.model.TokenBlacklist(jwt, expirationDate));
        }
    }

    @Transactional
    public String register(RegisterRequest request) {
        Optional<User> existingUser = userRepository.findByUsername(request.getUsername());
        
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            if (user.getStatus() == UserStatus.ACTIVE) {
                throw new BusinessException("用户已存在");
            }
            if (user.getStatus() == UserStatus.BANNED) {
                throw new BusinessException("用户被封禁");
            }
            // User is PENDING, verify code and refresh audit code
            verifyCode(request.getEmail(), request.getVerificationCode());
            
            // Check existing audit
            UserAudit audit = userAuditRepository.findByUserId(user.getId())
                    .orElse(new UserAudit(user, UUID.randomUUID().toString(), 7));
            
            // Always refresh code and expiry if re-registering
            audit.setAuditCode(UUID.randomUUID().toString());
            audit.setExpiryDate(LocalDateTime.now().plusDays(7));
            userAuditRepository.save(audit);
            
            return audit.getAuditCode();
        }

        // New User
        verifyCode(request.getEmail(), request.getVerificationCode());

        if (request.getNickname() != null && request.getNickname().length() > 50) {
            throw new BusinessException("Nickname too long (max 50 characters)");
        }

        // 创建用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setNickname(request.getNickname() != null && !request.getNickname().isBlank() 
                ? request.getNickname() : request.getUsername());
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
    public void resetPasswordByEmail(cafe.shigure.UserService.dto.ResetPasswordRequest request) {
        verifyCode(request.getEmail(), request.getVerificationCode());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException("用户不存在"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
    
    private void verifyCode(String email, String code) {
        // 验证验证码
        VerificationCode verificationCode = verificationCodeRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("验证码不存在或已过期"));

        if (verificationCode.isExpired()) {
            throw new BusinessException("验证码已过期");
        }

        if (!verificationCode.getCode().equals(code)) {
            throw new BusinessException("验证码错误");
        }

        // 验证通过，删除验证码（防止复用）
        verificationCodeRepository.delete(verificationCode);
    }
    
    public User getUserByAuditCode(String auditCode) {
        UserAudit audit = userAuditRepository.findByAuditCode(auditCode)
                .orElseThrow(() -> new BusinessException("Invalid audit code"));
        return audit.getUser();
    }

    public List<AuditResponse> getPendingAudits() {
        return userAuditRepository.findAll().stream()
                .map(audit -> new AuditResponse(
                        audit.getUser().getId(),
                        audit.getUser().getUsername(),
                        audit.getUser().getEmail(),
                        audit.getUser().getStatus(),
                        audit.getAuditCode(),
                        audit.isExpired()
                ))
                .collect(Collectors.toList());
    }

    @Transactional
    public void approveAudit(Long auditId) {
        UserAudit audit = userAuditRepository.findById(auditId)
                .orElseThrow(() -> new BusinessException("Audit record not found"));

        User user = audit.getUser();
        if (user.getStatus() == UserStatus.ACTIVE) {
            // Already active, maybe just clean up the audit record if it still exists
            userAuditRepository.delete(audit);
            return;
        }
        
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        userAuditRepository.delete(audit);
    }

    @Transactional
    public void approveUser(String auditCode) {
        UserAudit audit = userAuditRepository.findByAuditCode(auditCode)
                .orElseThrow(() -> new BusinessException("Invalid audit code"));
        
        if (audit.isExpired()) {
             throw new BusinessException("Audit code expired. Please request a new one.");
        }

        User user = audit.getUser();
        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new BusinessException("User already active");
        }
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        
        // Remove audit record after successful approval
        userAuditRepository.delete(audit);
    }

    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new BusinessException("User not found");
        }
        userRepository.deleteById(id);
    }

    public void changePassword(Long id, String oldPassword, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found"));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("Old password does not match");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public void resetPassword(Long id, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void updateEmail(Long id, String newEmail, String verificationCode) {
        verifyCode(newEmail, verificationCode);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found"));
        
        // 检查新邮箱是否已被使用 (排除自己)
        userRepository.findByEmail(newEmail).ifPresent(existingUser -> {
            if (!existingUser.getId().equals(id)) {
                throw new BusinessException("Email already in use");
            }
        });

        user.setEmail(newEmail);
        userRepository.save(user);
    }

    @Transactional
    public void updateEmailDirectly(Long id, String newEmail) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found"));

        // Check if new email is already in use (excluding self)
        userRepository.findByEmail(newEmail).ifPresent(existingUser -> {
            if (!existingUser.getId().equals(id)) {
                throw new BusinessException("Email already in use");
            }
        });

        user.setEmail(newEmail);
        userRepository.save(user);
    }

    @Transactional
    public void updateRole(Long id, Role newRole) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found"));
        user.setRole(newRole);
        userRepository.save(user);
    }

    public java.util.List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found"));
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("User not found"));
    }

    @Transactional
    public void updateNickname(Long id, String nickname) {
        if (nickname != null && nickname.length() > 50) {
            throw new BusinessException("Nickname too long (max 50 characters)");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("User not found"));
        user.setNickname(nickname);
        userRepository.save(user);
    }

    @Scheduled(cron = "0 0 * * * ?")
    public void cleanupExpiredCodes() {
        verificationCodeRepository.deleteByExpiryDateBefore(LocalDateTime.now());
    }
}
