package com.e_commerce.user_service.service;

import com.e_commerce.user_service.dto.AuthResponse;
import com.e_commerce.user_service.dto.CreateUserRequest;
import com.e_commerce.user_service.dto.LoginRequest;
import com.e_commerce.user_service.dto.UpdateUserRequest;
import com.e_commerce.user_service.dto.UserResponse;
import com.e_commerce.user_service.entity.OutboxEvent;
import com.e_commerce.user_service.entity.OutboxStatus;
import com.e_commerce.user_service.entity.Role;
import com.e_commerce.user_service.entity.User;
import com.e_commerce.user_service.exception.EmailAlreadyExistsException;
import com.e_commerce.user_service.exception.UserNotFoundException;
import com.e_commerce.user_service.repository.OutboxEventRepository;
import com.e_commerce.user_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.role() == null ? Role.USER : request.role());

        User savedUser = userRepository.save(user);
        outboxEventRepository.save(userCreatedEvent(savedUser));

        return UserResponse.from(savedUser);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsers() {
        return userRepository.findAll()
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        return UserResponse.from(findUser(id));
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new UserNotFoundException(email));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = findUser(id);

        if (request.email() != null && !request.email().isBlank()) {
            String email = normalizeEmail(request.email());
            if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
                throw new EmailAlreadyExistsException(email);
            }
            user.setEmail(email);
        }

        if (request.password() != null && !request.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }

        if (request.role() != null) {
            user.setRole(request.role());
        }

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = findUser(id);
        userRepository.delete(user);
    }

    public void logout(String token) {
        jwtService.parseToken(token).ifPresent(claims -> {
            long ttlSeconds = Math.max(0, claims.expiresAt().getEpochSecond() - Instant.now().getEpochSecond());
            if (ttlSeconds > 0) {
                tokenBlacklistService.blacklist(token, Duration.ofSeconds(ttlSeconds));
            }
        });
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> new UserNotFoundException(request.email()));

        boolean authenticated = passwordEncoder.matches(request.password(), user.getPassword());
        return new AuthResponse(
                authenticated,
                authenticated ? UserResponse.from(user) : null,
                authenticated ? jwtService.generateToken(user) : null
        );
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private OutboxEvent userCreatedEvent(User user) {
        String eventId = UUID.randomUUID().toString();
        String eventType = "UserCreated";
        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        event.setAggregateType("User");
        event.setAggregateId(user.getId());
        event.setEventType(eventType);
        event.setStatus(OutboxStatus.PENDING);
        event.setPayload(userCreatedPayload(user, eventId, eventType));
        return event;
    }

    private String userCreatedPayload(User user, String eventId, String eventType) {
        return """
                {"eventId":"%s","eventType":"%s","id":%d,"email":"%s","role":"%s","createdAt":"%s"}
                """.formatted(
                eventId,
                eventType,
                user.getId(),
                escapeJson(user.getEmail()),
                user.getRole().name(),
                user.getCreatedAt()
        ).trim();
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

}
