package com.example.demo.service;

import com.example.demo.domain.User;
import com.example.demo.dto.AuthDto;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Profile("serverB")
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    public String signup(AuthDto.SignupRequest req) {
        if (req.getEmail() == null || req.getEmail().isBlank()
                || req.getPassword() == null || req.getPassword().isBlank()
                || req.getName() == null || req.getName().isBlank()) {
            throw new IllegalArgumentException("이메일, 비밀번호, 이름은 필수 입력 항목입니다.");
        }

        String normalizedEmail = req.getEmail().trim();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPassword(BCrypt.hashpw(req.getPassword(), BCrypt.gensalt()));
        user.setName(req.getName().trim());
        user.setConsecutiveAttendanceDays(0);

        userRepository.save(user);
        return "회원가입 완료";
    }

    public AuthDto.TokenResponse login(AuthDto.LoginRequest req) {
        if (req.getEmail() == null || req.getEmail().isBlank()
                || req.getPassword() == null || req.getPassword().isBlank()) {
            throw new IllegalArgumentException("이메일과 비밀번호를 입력해 주세요.");
        }

        String normalizedEmail = req.getEmail().trim();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 이메일입니다."));

        if (!BCrypt.checkpw(req.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        String token = jwtProvider.generateToken(user.getId(), user.getEmail());
        return new AuthDto.TokenResponse(token, user.getId(), user.getEmail(), user.getName());
    }
}
