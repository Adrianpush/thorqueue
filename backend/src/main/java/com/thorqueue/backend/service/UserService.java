package com.thorqueue.backend.service;

import com.thorqueue.backend.dto.CreateUserRequest;
import com.thorqueue.backend.dto.UpdateUserRequest;
import com.thorqueue.backend.dto.UserResponse;
import com.thorqueue.backend.model.User;
import com.thorqueue.backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public List<UserResponse> getAllUsers() {
    return userRepository.findAll().stream().map(UserResponse::fromEntity).toList();
  }

  @Transactional(readOnly = true)
  public UserResponse getUserById(UUID id) {
    return userRepository
        .findById(id)
        .map(UserResponse::fromEntity)
        .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
  }

  @Transactional(readOnly = true)
  public UserResponse getUserByKeycloakId(String keycloakId) {
    return userRepository
        .findByKeycloakId(keycloakId)
        .map(UserResponse::fromEntity)
        .orElseThrow(
            () -> new EntityNotFoundException("User not found with keycloakId: " + keycloakId));
  }

  @Transactional
  public UserResponse createUser(CreateUserRequest request) {
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new IllegalArgumentException("Email already in use: " + request.getEmail());
    }
    if (userRepository.existsByKeycloakId(request.getKeycloakId())) {
      throw new IllegalArgumentException(
          "Keycloak ID already registered: " + request.getKeycloakId());
    }

    User user =
        User.builder()
            .keycloakId(request.getKeycloakId())
            .email(request.getEmail())
            .displayName(request.getDisplayName())
            .isAppAdmin(request.getIsAppAdmin() != null ? request.getIsAppAdmin() : false)
            .build();

    User saved = userRepository.save(user);
    return UserResponse.fromEntity(saved);
  }

  @Transactional
  public UserResponse updateUser(UUID id, UpdateUserRequest request) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));

    if (request.getEmail() != null) {
      if (!request.getEmail().equals(user.getEmail())
          && userRepository.existsByEmail(request.getEmail())) {
        throw new IllegalArgumentException("Email already in use: " + request.getEmail());
      }
      user.setEmail(request.getEmail());
    }

    if (request.getDisplayName() != null) {
      user.setDisplayName(request.getDisplayName());
    }

    if (request.getIsAppAdmin() != null) {
      user.setIsAppAdmin(request.getIsAppAdmin());
    }
    user.setUpdatedAt(Instant.now());
    User saved = userRepository.save(user);
    return UserResponse.fromEntity(saved);
  }

  @Transactional
  public void deleteUser(UUID id) {
    if (!userRepository.existsById(id)) {
      throw new EntityNotFoundException("User not found: " + id);
    }
    userRepository.deleteById(id);
  }
}
