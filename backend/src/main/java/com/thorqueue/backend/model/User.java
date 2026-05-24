package com.thorqueue.backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "keycloak_id", nullable = false, unique = true)
  private String keycloakId;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "is_app_admin", nullable = false)
  @Builder.Default
  private Boolean isAppAdmin = false;

@Column(name = "created_at", nullable = false, updatable = false)
private Instant createdAt;

@Column(name = "updated_at", nullable = false)
private Instant updatedAt;

@PrePersist
protected void onCreate() {
  createdAt = Instant.now();
  updatedAt = Instant.now();
}

@PreUpdate
protected void onUpdate() {
  updatedAt = Instant.now();
}
}
