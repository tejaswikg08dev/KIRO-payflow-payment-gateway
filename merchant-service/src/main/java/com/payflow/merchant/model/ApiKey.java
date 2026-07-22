package com.payflow.merchant.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "api_keys", schema = "merchant")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey {

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "merchant_id", nullable = false, length = 50)
    private String merchantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "key_type", nullable = false, length = 10)
    private KeyType keyType;

    @Column(name = "public_key", nullable = false, unique = true, length = 100)
    private String publicKey;

    @Column(name = "secret_key_hash", nullable = false, length = 255)
    private String secretKeyHash;

    @Column(name = "key_prefix", nullable = false, length = 30)
    private String keyPrefix;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private KeyStatus status = KeyStatus.ACTIVE;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum KeyType {
        TEST, LIVE
    }

    public enum KeyStatus {
        ACTIVE, REVOKED
    }
}
