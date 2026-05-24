package com.thorqueue.backend.dto;

import jakarta.validation.constraints.Email;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserRequest {

  @Email(message = "Email must be valid")
  private String email;

  private String displayName;

  private Boolean isAppAdmin;
}
