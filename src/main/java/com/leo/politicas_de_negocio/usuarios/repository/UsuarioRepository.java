package com.leo.politicas_de_negocio.usuarios.repository;

import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends MongoRepository<Usuario, String> {

    Optional<Usuario> findByCorreoAndActivo(String correo, Boolean activo);

    Optional<Usuario> findByIdAndActivo(String id, Boolean activo);

    Optional<Usuario> findByCorreo(String correo);

    boolean existsByCorreo(String correo);

    long countByRolIgnoreCase(String rol);

    long countByDepartamentoId(String departamentoId);

    List<Usuario> findAllByDepartamentoId(String departamentoId);
}
