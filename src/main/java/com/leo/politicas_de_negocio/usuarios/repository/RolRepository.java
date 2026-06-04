package com.leo.politicas_de_negocio.usuarios.repository;

import com.leo.politicas_de_negocio.usuarios.model.Rol;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface RolRepository extends MongoRepository<Rol, String> {

    Optional<Rol> findByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCase(String nombre);

    List<Rol> findAllByOrderByNombreAsc();

    List<Rol> findAllByActivoTrueOrderByNombreAsc();
}
