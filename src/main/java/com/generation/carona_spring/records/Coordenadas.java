package com.generation.carona_spring.records;

/**
 * Representa um par de coordenadas geográficas (latitude e longitude).
 * Como um record, esta classe é imutável e mais simples de manter.
 */
public record Coordenadas(double latitude, double longitude) {}
