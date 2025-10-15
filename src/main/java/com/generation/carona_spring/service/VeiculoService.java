package com.generation.carona_spring.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.generation.carona_spring.model.Veiculo;
import com.generation.carona_spring.repository.VeiculoRepository;

import jakarta.validation.Valid;

@Service
public class VeiculoService {

    @Autowired
    private VeiculoRepository veiculoRepository;

    public List<Veiculo> listarTodos() {
        return veiculoRepository.findAll();
    }

    public Optional<Veiculo> buscarPorId(Long id) {
        return veiculoRepository.findById(id);
    }

    public List<Veiculo> buscarPorModelo(String modelo) {
        return veiculoRepository.findAllByModeloContainingIgnoreCase(modelo);
    }

    public Veiculo criar(@Valid Veiculo veiculo) {
        return veiculoRepository.save(veiculo);
    }

    public Veiculo atualizar(@Valid Veiculo veiculo) {
        return veiculoRepository.findById(veiculo.getId())
                .map(v -> veiculoRepository.save(veiculo))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Veículo não encontrado"));
    }

    public void deletar(Long id) {
        Optional<Veiculo> veiculo = veiculoRepository.findById(id);

        if (veiculo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Veículo não encontrado");
        }

        veiculoRepository.deleteById(id);
    }
}
