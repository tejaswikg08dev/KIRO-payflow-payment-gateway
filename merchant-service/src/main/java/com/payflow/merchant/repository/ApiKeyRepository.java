package com.payflow.merchant.repository;

import com.payflow.merchant.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, String> {

    List<ApiKey> findByMerchantIdAndStatus(String merchantId, ApiKey.KeyStatus status);

    Optional<ApiKey> findBySecretKeyHashAndStatus(String secretKeyHash, ApiKey.KeyStatus status);

    Optional<ApiKey> findByPublicKey(String publicKey);
}
