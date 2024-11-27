package com.generation.carona_spring.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.generation.carona_spring.model.Veiculo;
import com.generation.carona_spring.repository.VeiculoRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/veiculos")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class VeiculoController {

	@Autowired
	private VeiculoRepository veiculoRepository;

	@GetMapping
	public ResponseEntity<List<Veiculo>> getAll() {
		return ResponseEntity.ok(veiculoRepository.findAll());
	}

	@GetMapping("/{id}")
	public ResponseEntity<Veiculo> getById(@PathVariable Long id) {
		return veiculoRepository.findById(id).map(resposta -> ResponseEntity.ok(resposta))
				.orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
	}

	@GetMapping("/modelo/{modelo}")
	public ResponseEntity<List<Veiculo>> getByModelo(@PathVariable String modelo) {
		return ResponseEntity.ok(veiculoRepository.findAllByModeloContainingIgnoreCase(modelo));
	}

	@PostMapping
	public ResponseEntity<Veiculo> post(@Valid @RequestBody Veiculo veiculo) {

		System.out.println(veiculo.getId());

		return ResponseEntity.status(HttpStatus.CREATED).body(veiculoRepository.save(veiculo));
	}

	@PutMapping
	public ResponseEntity<Veiculo> put(@Valid @RequestBody Veiculo veiculo) {
		return veiculoRepository.findById(veiculo.getId())
				.map(resposta -> ResponseEntity.status(HttpStatus.OK).body(veiculoRepository.save(veiculo)))
				.orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
	}

	@ResponseStatus(HttpStatus.NO_CONTENT)
	@DeleteMapping("/{id}")
	public void delete(@PathVariable Long id) {
		Optional<Veiculo> veiculo = veiculoRepository.findById(id);

		if (veiculo.isEmpty())
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);

		veiculoRepository.deleteById(id);
	}

}
