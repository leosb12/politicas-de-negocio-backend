package com.leo.politicas_de_negocio.simulation.repository;

import com.leo.politicas_de_negocio.simulation.model.SimulationRun;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SimulationRepository extends MongoRepository<SimulationRun, String> {

    List<SimulationRun> findByPolicyIdOrderByCreatedAtDesc(String policyId);
}
