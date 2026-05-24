package com.thorqueue.backend.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.thorqueue.backend.dto.CreateUserRequest;
import com.thorqueue.backend.dto.UpdateUserRequest;
import com.thorqueue.backend.dto.UserResponse;
import com.thorqueue.backend.model.User;
import com.thorqueue.backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @InjectMocks private UserService userService;

  private User testUser;
  private UUID testId;

  @BeforeEach
  void setUp() {
    testId = UUID.randomUUID();
    testUser =
        User.builder()
            .id(testId)
            .keycloakId("kc-123")
            .email("test@thorqueue.dev")
            .displayName("Test User")
            .isAppAdmin(false)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
  }

  @Nested
  @DisplayName("getAllUsers")
  class GetAllUsers {
    @Test
    @DisplayName("returns all users as DTOs")
    void returnsAllUsers() {
      when(userRepository.findAll()).thenReturn(List.of(testUser));
      List<UserResponse> result = userService.getAllUsers();
      assertThat(result).hasSize(1);
      assertThat(result.get(0).getEmail()).isEqualTo("test@thorqueue.dev");
    }

    @Test
    @DisplayName("returns empty list when no users")
    void returnsEmptyList() {
      when(userRepository.findAll()).thenReturn(List.of());
      assertThat(userService.getAllUsers()).isEmpty();
    }
  }

  @Nested
  @DisplayName("getUserById")
  class GetUserById {
    @Test
    @DisplayName("returns user when found")
    void returnsUserWhenFound() {
      when(userRepository.findById(testId)).thenReturn(Optional.of(testUser));
      UserResponse result = userService.getUserById(testId);
      assertThat(result.getId()).isEqualTo(testId);
    }

    @Test
    @DisplayName("throws when not found")
    void throwsWhenNotFound() {
      UUID id = UUID.randomUUID();
      when(userRepository.findById(id)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> userService.getUserById(id))
          .isInstanceOf(EntityNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("createUser")
  class CreateUser {
    @Test
    @DisplayName("creates user successfully")
    void createsUser() {
      CreateUserRequest req = CreateUserRequest.builder()
          .keycloakId("kc-new").email("new@thorqueue.dev")
          .displayName("New").isAppAdmin(false).build();
      when(userRepository.existsByEmail(any())).thenReturn(false);
      when(userRepository.existsByKeycloakId(any())).thenReturn(false);
      when(userRepository.save(any(User.class))).thenReturn(testUser);
      assertThat(userService.createUser(req)).isNotNull();
      verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("throws when email exists")
    void throwsOnDuplicateEmail() {
      CreateUserRequest req = CreateUserRequest.builder()
          .keycloakId("kc-new").email("dup@test.dev").displayName("X").build();
      when(userRepository.existsByEmail("dup@test.dev")).thenReturn(true);
      assertThatThrownBy(() -> userService.createUser(req))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Email already in use");
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("throws when keycloak ID exists")
    void throwsOnDuplicateKeycloakId() {
      CreateUserRequest req = CreateUserRequest.builder()
          .keycloakId("kc-dup").email("new@test.dev").displayName("X").build();
      when(userRepository.existsByEmail(any())).thenReturn(false);
      when(userRepository.existsByKeycloakId("kc-dup")).thenReturn(true);
      assertThatThrownBy(() -> userService.createUser(req))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Keycloak ID already registered");
    }

    @Test
    @DisplayName("defaults isAppAdmin to false")
    void defaultsAdmin() {
      CreateUserRequest req = CreateUserRequest.builder()
          .keycloakId("kc-x").email("x@test.dev").displayName("X").isAppAdmin(null).build();
      when(userRepository.existsByEmail(any())).thenReturn(false);
      when(userRepository.existsByKeycloakId(any())).thenReturn(false);
      when(userRepository.save(any(User.class))).thenAnswer(inv -> {
        User u = inv.getArgument(0);
        assertThat(u.getIsAppAdmin()).isFalse();
        return testUser;
      });
      userService.createUser(req);
    }
  }

  @Nested
  @DisplayName("updateUser")
  class UpdateUser {
    @Test
    @DisplayName("updates display name")
    void updatesName() {
      when(userRepository.findById(testId)).thenReturn(Optional.of(testUser));
      when(userRepository.save(any())).thenReturn(testUser);
      userService.updateUser(testId, UpdateUserRequest.builder().displayName("New Name").build());
      assertThat(testUser.getDisplayName()).isEqualTo("New Name");
    }

    @Test
    @DisplayName("throws on taken email")
    void throwsOnTakenEmail() {
      when(userRepository.findById(testId)).thenReturn(Optional.of(testUser));
      when(userRepository.existsByEmail("taken@test.dev")).thenReturn(true);
      assertThatThrownBy(() -> userService.updateUser(testId,
          UpdateUserRequest.builder().email("taken@test.dev").build()))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("allows same email")
    void allowsSameEmail() {
      when(userRepository.findById(testId)).thenReturn(Optional.of(testUser));
      when(userRepository.save(any())).thenReturn(testUser);
      userService.updateUser(testId,
          UpdateUserRequest.builder().email("test@thorqueue.dev").build());
      verify(userRepository, never()).existsByEmail(any());
    }
  }

  @Nested
  @DisplayName("deleteUser")
  class DeleteUser {
    @Test
    @DisplayName("deletes existing user")
    void deletes() {
      when(userRepository.existsById(testId)).thenReturn(true);
      userService.deleteUser(testId);
      verify(userRepository).deleteById(testId);
    }

    @Test
    @DisplayName("throws when not found")
    void throwsWhenNotFound() {
      UUID id = UUID.randomUUID();
      when(userRepository.existsById(id)).thenReturn(false);
      assertThatThrownBy(() -> userService.deleteUser(id))
          .isInstanceOf(EntityNotFoundException.class);
    }
  }
}
