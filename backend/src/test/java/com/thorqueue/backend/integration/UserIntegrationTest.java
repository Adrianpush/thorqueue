package com.thorqueue.backend.integration;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.thorqueue.backend.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("thorqueue_test")
          .withUsername("test")
          .withPassword("test")
          .withInitScript("schema.sql");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.issuer-uri",
        () -> "http://localhost:8180/realms/thorqueue");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;

  private static String createdUserId;

  @BeforeEach
  void cleanUp() {
    if (createdUserId == null) {
      userRepository.deleteAll();
    }
  }

  private String userJson(String keycloakId, String email, String name, String admin) {
    return "{\"keycloakId\":\"" + keycloakId
        + "\",\"email\":\"" + email
        + "\",\"displayName\":\"" + name
        + "\",\"isAppAdmin\":" + admin + "}";
  }

  @Test
  @Order(1)
  @DisplayName("POST /api/users - creates a new user")
  void createUser() throws Exception {
    MvcResult result = mockMvc
        .perform(post("/api/users").with(jwt())
            .contentType(MediaType.APPLICATION_JSON)
            .content(userJson("kc-int-001", "int@thorqueue.dev", "Integration User", "true")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("int@thorqueue.dev"))
        .andExpect(jsonPath("$.isAppAdmin").value(true))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.createdAt").isNotEmpty())
        .andReturn();

    String body = result.getResponse().getContentAsString();
    createdUserId = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

    assertThat(userRepository.findById(java.util.UUID.fromString(createdUserId))).isPresent();
  }

  @Test
  @Order(2)
  @DisplayName("POST /api/users - rejects duplicate email")
  void rejectsDuplicateEmail() throws Exception {
    mockMvc.perform(post("/api/users").with(jwt())
            .contentType(MediaType.APPLICATION_JSON)
            .content(userJson("kc-int-002", "int@thorqueue.dev", "Dup User", "false")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Email already in use: int@thorqueue.dev"));
  }

  @Test
  @Order(3)
  @DisplayName("GET /api/users - returns all users")
  void getAllUsers() throws Exception {
    mockMvc.perform(get("/api/users").with(jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].email").value("int@thorqueue.dev"));
  }

  @Test
  @Order(4)
  @DisplayName("GET /api/users/{id} - returns user by ID")
  void getUserById() throws Exception {
    mockMvc.perform(get("/api/users/{id}", createdUserId).with(jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("int@thorqueue.dev"));
  }

  @Test
  @Order(5)
  @DisplayName("GET /api/users/{id} - 404 for unknown ID")
  void notFoundForUnknown() throws Exception {
    mockMvc.perform(get("/api/users/{id}", java.util.UUID.randomUUID()).with(jwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  @Order(6)
  @DisplayName("PUT /api/users/{id} - updates user")
  void updateUser() throws Exception {
    mockMvc.perform(put("/api/users/{id}", createdUserId).with(jwt())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"displayName\":\"Updated User\",\"isAppAdmin\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Updated User"))
        .andExpect(jsonPath("$.isAppAdmin").value(false));
  }

  @Test
  @Order(7)
  @DisplayName("DELETE /api/users/{id} - deletes user")
  void deleteUser() throws Exception {
    mockMvc.perform(delete("/api/users/{id}", createdUserId).with(jwt()))
        .andExpect(status().isNoContent());
    assertThat(userRepository.findById(java.util.UUID.fromString(createdUserId))).isEmpty();
  }

  @Test
  @Order(8)
  @DisplayName("DELETE /api/users/{id} - 404 after deletion")
  void notFoundAfterDeletion() throws Exception {
    mockMvc.perform(delete("/api/users/{id}", createdUserId).with(jwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("GET /api/health - public, no auth needed")
  void healthPublic() throws Exception {
    mockMvc.perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  @DisplayName("GET /api/users - 401 without token")
  void requiresAuth() throws Exception {
    mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
  }
}
