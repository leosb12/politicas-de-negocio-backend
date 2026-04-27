package com.leo.politicas_de_negocio.auth.repository;

import com.leo.politicas_de_negocio.auth.model.PasswordResetToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends MongoRepository<PasswordResetToken, String> {

    Optional<PasswordResetToken> findByToken(String token);

    List<PasswordResetToken> findAllByUsuarioIdAndUsadoFalse(String usuarioId);
}