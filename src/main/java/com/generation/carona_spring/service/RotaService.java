package com.generation.carona_spring.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.generation.carona_spring.model.Coordenadas;
import com.generation.carona_spring.model.Viagem;

import io.github.cdimascio.dotenv.Dotenv;

@Service
public class RotaService {

    private static final Logger logger = LoggerFactory.getLogger(RotaService.class);
    private static final Dotenv dotenv = Dotenv.load();
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String OPEN_CAGE_URL = "https://api.opencagedata.com/geocode/v1/json?q=%s&key=%s&language=pt&format=json";
    private static final String API_KEY = dotenv.get("API_KEY"); 
    
    private static final String OSRM_URL = "http://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=false";

    private static final LocalTime INICIO_PICO_MANHA = LocalTime.of(6, 0);
    private static final LocalTime FIM_PICO_MANHA = LocalTime.of(9, 0);
    private static final LocalTime INICIO_PICO_TARDE = LocalTime.of(16, 0);
    private static final LocalTime FIM_PICO_TARDE = LocalTime.of(19, 0);

    private static final double VELOCIDADE_NORMAL = 50.0;
    private static final double VELOCIDADE_PICO_MANHA = 30.0;
    private static final double VELOCIDADE_PICO_TARDE = 35.0;
    private static final double VELOCIDADE_FINAL_SEMANA = 60.0;

    private static final double TARIFA_BASE = 5.00;
    private static final double VALOR_KM = 1.50;
    private static final double VALOR_MINUTO = 0.50;
    private static final double SEGURO = 2.00;

    public RotaService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    private void aplicarRateLimit() {
        try {
            Thread.sleep(2000); // Pausa de 2 segundos entre requisições
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Erro ao aplicar rate limit: {}", e.getMessage(), e);
        }
    }

    public void calcularRota(Viagem viagem) {
        try {
        	
            logger.info("Iniciando cálculo da rota para viagem de '{}' para '{}'", viagem.getPartida(), viagem.getDestino());

            Coordenadas coordenadasOrigem = getCoordenadas(viagem.getPartida());
            Coordenadas coordenadasDestino = getCoordenadas(viagem.getDestino());

            viagem.setLatitudePartida(coordenadasOrigem.getLatitude());
            viagem.setLongitudePartida(coordenadasOrigem.getLongitude());
            viagem.setLatitudeDestino(coordenadasDestino.getLatitude());
            viagem.setLongitudeDestino(coordenadasDestino.getLongitude());

            double distanciaEmKm = calcularDistanciaRota(coordenadasOrigem, coordenadasDestino);
            viagem.setDistancia(distanciaEmKm);

            double velocidadeMedia = calcularVelocidadeMedia(viagem.getDataPartida());
            viagem.setVelocidadeMedia(velocidadeMedia);

            double tempoMedio = calcularTempoMedio(distanciaEmKm, velocidadeMedia);
            viagem.setTempoEstimado(tempoMedio);

            double valor = calcularValorViagem(distanciaEmKm, tempoMedio);
            viagem.setValor(BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP));
            
        } catch (ResponseStatusException e) {
            logger.error("Erro HTTP: {} - {}", e.getStatusCode(), e.getReason());
            throw e;
        } catch (Exception e) {
            logger.error("Erro inesperado ao calcular rota: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao calcular a rota", e);
        }
    }

    private Coordenadas getCoordenadas(String endereco) {
        try {
            aplicarRateLimit();
            String url = String.format(OPEN_CAGE_URL, removerNumero(endereco) + ", São Paulo - SP", API_KEY);

            logger.info("Buscando coordenadas para o endereço: {}", endereco);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "ViagemApp/1.0");

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());

            if (root.has("results") && !root.get("results").isEmpty()) {
                for (JsonNode location : root.get("results")) {
                    JsonNode components = location.path("components");
                    if ("São Paulo".equalsIgnoreCase(components.path("city").asText())) {
                        double latitude = location.path("geometry").path("lat").asDouble();
                        double longitude = location.path("geometry").path("lng").asDouble();
                        return new Coordenadas(latitude, longitude);
                    }
                }
            }
            logger.error("Nenhuma coordenada encontrada para o endereço: {}", endereco);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Endereço não encontrado: " + endereco);
        } catch (Exception e) {
            logger.error("Erro ao obter coordenadas para '{}': {}", endereco, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao buscar coordenadas", e);
        }
    }

    private double calcularDistanciaRota(Coordenadas origem, Coordenadas destino) {
        try {
            aplicarRateLimit();
            String url = String.format(Locale.US, OSRM_URL, origem.getLongitude(), origem.getLatitude(), destino.getLongitude(), destino.getLatitude());

            logger.info("Calculando rota com a URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "ViagemApp/1.0");

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());

            if (root.has("routes") && !root.get("routes").isEmpty()) {
                double distanceInMeters = root.get("routes").get(0).get("distance").asDouble();
                return distanceInMeters / 1000.0;
            }

            logger.error("Não foi possível encontrar uma rota válida.");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rota não encontrada");
        } catch (Exception e) {
            logger.error("Erro ao calcular rota: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao calcular rota", e);
        }
    }

    private double calcularVelocidadeMedia(LocalDateTime horarioPartida) {
        if (horarioPartida == null) return VELOCIDADE_NORMAL;

        LocalTime horario = horarioPartida.toLocalTime();

        if (isWeekend(horarioPartida)) return VELOCIDADE_FINAL_SEMANA;
        if (horario.isAfter(INICIO_PICO_MANHA) && horario.isBefore(FIM_PICO_MANHA)) return VELOCIDADE_PICO_MANHA;
        if (horario.isAfter(INICIO_PICO_TARDE) && horario.isBefore(FIM_PICO_TARDE)) return VELOCIDADE_PICO_TARDE;

        return VELOCIDADE_NORMAL;
    }

    private double calcularTempoMedio(double distanciaKm, double velocidade) {
        return (distanciaKm / velocidade) * 60;
    }

    private double calcularValorViagem(double distanciaKm, double tempoEstimado) {
        return TARIFA_BASE + (distanciaKm * VALOR_KM) + (tempoEstimado * VALOR_MINUTO) + SEGURO;
    }

    private boolean isWeekend(LocalDateTime dateTime) {
        return dateTime.getDayOfWeek().getValue() >= 6;
    }
    
    private String removerNumero(String endereco) {
        Matcher matcher = Pattern.compile("(.*?)[,\\s]+\\d+$").matcher(endereco.trim());
        return matcher.matches() ? matcher.group(1) : endereco;
    }
}
