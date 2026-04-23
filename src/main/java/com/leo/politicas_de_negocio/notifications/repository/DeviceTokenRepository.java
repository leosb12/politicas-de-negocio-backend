package com.leo.politicas_de_negocio.notifications.repository;

import com.leo.politicas_de_negocio.notifications.model.DeviceToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends MongoRepository<DeviceToken, String> {

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByUserIdAndActiveTrue(String userId);

    long countByUserIdAndActiveTrue(String userId);
}
