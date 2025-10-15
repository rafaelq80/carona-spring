package com.generation.carona_spring.controller;

import java.util.List;

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

import com.generation.carona_spring.model.Veiculo;
import com.generation.carona_spring.service.VeiculoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/veiculos")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class VeiculoController {

    @Autowired
    private VeiculoService veiculoService;

    @GetMapping
    public ResponseEntity<List<Veiculo>> getAll() {
        return ResponseEntity.ok(veiculoService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Veiculo> getById(@PathVariable Long id) {
        return veiculoService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping("/modelo/{modelo}")
    public ResponseEntity<List<Veiculo>> getByModelo(@PathVariable String modelo) {
        return ResponseEntity.ok(veiculoService.buscarPorModelo(modelo));
    }

    @PostMapping
    public ResponseEntity<Veiculo> post(@Valid @RequestBody Veiculo veiculo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(veiculoService.criar(veiculo));
    }

    @PutMapping
    public ResponseEntity<Veiculo> put(@Valid @RequestBody Veiculo veiculo) {
        return ResponseEntity.ok(veiculoService.atualizar(veiculo));
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        veiculoService.deletar(id);
    }
}
