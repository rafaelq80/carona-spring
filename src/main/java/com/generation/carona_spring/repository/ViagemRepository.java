package com.generation.carona_spring.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.generation.carona_spring.model.Viagem;

public interface ViagemRepository extends JpaRepository<Viagem, Long> {

	List<Viagem> findAllByDestinoContainingIgnoreCase(String destino);

}