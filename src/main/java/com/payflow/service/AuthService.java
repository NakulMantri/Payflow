package com.payflow.service;

import com.payflow.config.JwtTokenProvider;
import com.payflow.dto.AuthRequest;
import com.payflow.dto.AuthResponse;
import com.payflow.dto.RegisterRequest;
import com.payflow.dto.UserDto;
import com.payflow.entity.User;
import com.payflow.entity.Wallet;
import com.payflow.enums.Role;
import com.payflow.exception.PayflowException;
import com.payflow.exception.ResourceNotFoundException;
import com.payflow.repository.UserRepository;
import com.payflow.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       WalletRepository walletRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new PayflowException("Email address is already registered", HttpStatus.CONFLICT);
        }

        User user = new User(
                request.getEmail().toLowerCase().trim(),
                passwordEncoder.encode(request.getPassword()),
                request.getFullName().trim(),
                request.getPhone(),
                Role.ROLE_USER
        );
        user = userRepository.save(user);

        // Automatically provision wallet with starter test balance of INR 5000.00
        Wallet wallet = new Wallet(user, "INR", new BigDecimal("5000.0000"));
        walletRepository.save(wallet);

        log.info("Registered new user email={} id={}, initialized wallet id={}", user.getEmail(), user.getId(), wallet.getId());

        String token = jwtTokenProvider.generateToken(user.getEmail(), user.getId(), user.getRole().name());
        UserDto userDto = toUserDto(user);

        return new AuthResponse(token, jwtTokenProvider.getExpirationMs() / 1000, userDto);
    }

    public AuthResponse login(AuthRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail().toLowerCase().trim(), request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String token = jwtTokenProvider.generateToken(user.getEmail(), user.getId(), user.getRole().name());
        UserDto userDto = toUserDto(user);

        log.info("User logged in successfully: email={}", user.getEmail());
        return new AuthResponse(token, jwtTokenProvider.getExpirationMs() / 1000, userDto);
    }

    @Transactional(readOnly = true)
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new PayflowException("User is not authenticated", HttpStatus.UNAUTHORIZED);
        }

        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found with email: " + email));
    }

    public UserDto toUserDto(User user) {
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
