package com.thorqueue.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.thorqueue.backend.dto.CreateUserRequest;
import com.thorqueue.backend.dto.UpdateUserRequest;
import com.thorqueue.backend.dto.UserResponse;
import com.thorqueue.backend.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
class UserControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private UserService userService;

  private final UUID testId = UUID.randomUUID();
  private final Instant now = Instant.now();

  private UserResponse sampleResponse() {
    return UserResponse.builder()
        .id(testId).keycloakId("kc-123").email("test@thorqueue.dev")
        .displayName("Test User").isAppAdmin(false)
        .createdAt(now).updatedAt(now).build();
  }

  /** Minimal JSON serializer — avoids ObjectMapper autowiring issues in Boot 4.x */
  private String toJson(String... keyValues) {
    StringBuilder sb = new StringBuilder("{");
    for (int i = 0; i < keyValues.length; i += 2) {
      if (i > 0) sb.append(",");
      String val = keyValues[i + 1];
      if (val.equals("true") || val.equals("false") || val.equals("null")) {
        sb.append("\"").append(keyValues[i]).append("\":").append(val);
      } else {
        sb.append("\"").append(keyValues[i]).append("\":\"").append(val).append("\"");
      }
    }
    return sb.append("}").toString();
  }

  @Nested
  @DisplayName("GET /api/users")
  class GetAll {
    @Test
    @DisplayName("returns 200 with list")
    void returnsList() throws Exception {
      when(userService.getAllUsers()).thenReturn(List.of(sampleResponse()));
      mockMvc.perform(get("/api/users").with(jwt()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].email").value("test@thorqueue.dev"));
    }

    @Test
    @DisplayName("returns 401 without token")
    void unauthorized() throws Exception {
      mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("GET /api/users/{id}")
  class GetById {
    @Test
    @DisplayName("returns 200 when found")
    void found() throws Exception {
      when(userService.getUserById(testId)).thenReturn(sampleResponse());
      mockMvc.perform(get("/api/users/{id}", testId).with(jwt()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.email").value("test@thorqueue.dev"));
    }

    @Test
    @DisplayName("returns 404 when not found")
    void notFound() throws Exception {
      when(userService.getUserById(testId))
          .thenThrow(new EntityNotFoundException("Not found"));
      mockMvc.perform(get("/api/users/{id}", testId).with(jwt()))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("POST /api/users")
  class Create {
    @Test
    @DisplayName("returns 201 with created user")
    void creates() throws Exception {
      when(userService.createUser(any(CreateUserRequest.class))).thenReturn(sampleResponse());
      mockMvc.perform(post("/api/users").with(jwt())
              .contentType(MediaType.APPLICATION_JSON)
              .content(toJson(
                  "keycloakId", "kc-new",
                  "email", "new@thorqueue.dev",
                  "displayName", "New User",
                  "isAppAdmin", "false")))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.email").value("test@thorqueue.dev"));
    }

    @Test
    @DisplayName("returns 400 on duplicate email")
    void duplicateEmail() throws Exception {
      when(userService.createUser(any()))
          .thenThrow(new IllegalArgumentException("Email already in use"));
      mockMvc.perform(post("/api/users").with(jwt())
              .contentType(MediaType.APPLICATION_JSON)
              .content(toJson(
                  "keycloakId", "kc-new",
                  "email", "dup@thorqueue.dev",
                  "displayName", "User")))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("PUT /api/users/{id}")
  class Update {
    @Test
    @DisplayName("returns 200 with updated user")
    void updates() throws Exception {
      UserResponse updated = UserResponse.builder()
          .id(testId).keycloakId("kc-123").email("test@thorqueue.dev")
          .displayName("Updated").isAppAdmin(false)
          .createdAt(now).updatedAt(Instant.now()).build();
      when(userService.updateUser(eq(testId), any(UpdateUserRequest.class))).thenReturn(updated);
      mockMvc.perform(put("/api/users/{id}", testId).with(jwt())
              .contentType(MediaType.APPLICATION_JSON)
              .content(toJson("displayName", "Updated")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.displayName").value("Updated"));
    }
  }

  @Nested
  @DisplayName("DELETE /api/users/{id}")
  class Delete {
    @Test
    @DisplayName("returns 204 on success")
    void deletes() throws Exception {
      mockMvc.perform(delete("/api/users/{id}", testId).with(jwt()))
          .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("returns 404 when not found")
    void notFound() throws Exception {
      doThrow(new EntityNotFoundException("Not found"))
          .when(userService).deleteUser(testId);
      mockMvc.perform(delete("/api/users/{id}", testId).with(jwt()))
          .andExpect(status().isNotFound());
    }
  }
}
