package cafe.shigure.UserService.service;

import cafe.shigure.UserService.dto.RegisterRequest;
import cafe.shigure.UserService.exception.BusinessException;
import cafe.shigure.UserService.model.Role;
import cafe.shigure.UserService.model.User;
import cafe.shigure.UserService.model.VerificationCode;
import cafe.shigure.UserService.repository.UserRepository;
import cafe.shigure.UserService.repository.VerificationCodeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Transactional
    public void sendVerificationCode(String email) {
        if (userRepository.findByUsername(email).isPresent()) {
            // 这里根据需求处理重名逻辑
        }
        // 生成 6 位验证码
        String code = String.format("%06d", new Random().nextInt(999999));

        // 保存或更新验证码
        VerificationCode verificationCode = verificationCodeRepository.findByEmail(email)
                .orElse(new VerificationCode());
        verificationCode.setEmail(email);
        verificationCode.setCode(code);
        verificationCode.setExpiryDate(java.time.LocalDateTime.now().plusMinutes(5)); // 5分钟有效

        verificationCodeRepository.save(verificationCode);

        // 发送邮件
        emailService.sendSimpleMessage(email, "猫咖验证码", "您的验证码是：" + code + "，请在5分钟内使用。");
    }

    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new BusinessException("用户已存在");
        }

        // 验证验证码
        VerificationCode verificationCode = verificationCodeRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException("验证码不存在或已过期"));

        if (verificationCode.isExpired()) {
            throw new BusinessException("验证码已过期");
        }

        if (!verificationCode.getCode().equals(request.getVerificationCode())) {
            throw new BusinessException("验证码错误");
        }

        // 验证通过，删除验证码（防止复用）
        verificationCodeRepository.delete(verificationCode);

        // 创建用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);

        return userRepository.save(user);
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
}
