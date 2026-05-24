package com.thorqueue.backend.dto;

import com.thorqueue.backend.model.User;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

  private UUID id;
  private String keycloakId;
  private String email;
  private String displayName;
  private Boolean isAppAdmin;
  private Instant createdAt;
  private Instant updatedAt;

  public static UserResponse fromEntity(User user) {
    return UserResponse.builder()
        .id(user.getId())
        .keycloakId(user.getKeycloakId())
        .email(user.getEmail())
        .displayName(user.getDisplayName())
        .isAppAdmin(user.getIsAppAdmin())
        .createdAt(user.getCreatedAt())
        .updatedAt(user.getUpdatedAt())
        .build();
  }
}
