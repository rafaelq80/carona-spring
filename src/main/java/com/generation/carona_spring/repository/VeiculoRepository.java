package com.generation.carona_spring.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import com.generation.carona_spring.model.Veiculo;

public interface VeiculoRepository extends JpaRepository<Veiculo, Long> {

	public List<Veiculo> findAllByModeloContainingIgnoreCase(@Param("modelo") String modelo);

}
