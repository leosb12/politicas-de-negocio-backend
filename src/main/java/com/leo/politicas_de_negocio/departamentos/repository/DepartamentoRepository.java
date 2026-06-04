package com.leo.politicas_de_negocio.departamentos.repository;

import com.leo.politicas_de_negocio.departamentos.model.Departamento;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DepartamentoRepository extends MongoRepository<Departamento, String> {

	Optional<Departamento> findByNombreIgnoreCase(String nombre);

	boolean existsByNombreIgnoreCase(String nombre);

	List<Departamento> findAllByOrderByNombreAsc();

	List<Departamento> findAllByActivoTrueOrderByNombreAsc();
}
