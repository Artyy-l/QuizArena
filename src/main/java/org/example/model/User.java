package org.example.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String login;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  public User() {}

  public User(Long id, String login, String passwordHash) {
    this.id = id;
    this.login = login;
    this.passwordHash = passwordHash;
  }
}
