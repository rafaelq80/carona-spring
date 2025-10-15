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
    
    private final RestTemplate clienteHttp;
    
    /** 
     * ObjectMapper é uma classe da biblioteca Jackson usada para converter objetos Java 
     * em JSON e JSON em objetos Java.
     * */
    private final ObjectMapper mapperJson;

    private static final String URL_OPENCAGE = "https://api.opencagedata.com/geocode/v1/json?q=%s&key=%s&language=pt&format=json";
    private static final String CHAVE_API = dotenv.get("API_KEY"); 
    private static final String URL_OSRM = "http://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=false";

    // Faixas de horário de pico
    private static final LocalTime INICIO_PICO_MANHA = LocalTime.of(6, 0);
    private static final LocalTime FIM_PICO_MANHA = LocalTime.of(9, 0);
    private static final LocalTime INICIO_PICO_TARDE = LocalTime.of(16, 0);
    private static final LocalTime FIM_PICO_TARDE = LocalTime.of(19, 0);

    // Velocidades médias estimadas (km/h)
    private static final double VELOCIDADE_NORMAL = 50.0;
    private static final double VELOCIDADE_PICO_MANHA = 30.0;
    private static final double VELOCIDADE_PICO_TARDE = 35.0;
    private static final double VELOCIDADE_FINAL_DE_SEMANA = 60.0;

    // Parâmetros de cálculo da viagem
    private static final double TARIFA_BASE = 5.00;
    private static final double VALOR_POR_KM = 1.50;
    private static final double VALOR_POR_MINUTO = 0.50;
    private static final double VALOR_SEGURO = 2.00;

    public RotaService(RestTemplate clienteHttp, ObjectMapper mapperJson) {
        this.clienteHttp = clienteHttp;
        this.mapperJson = mapperJson;
    }

    /** Aplica uma pausa de 2 segundos para evitar excesso de requisições à API */
    private void aplicarLimitadorDeRequisicoes() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Erro ao aplicar limitador de requisições: {}", e.getMessage(), e);
        }
    }

    /** Calcula todos os parâmetros da viagem com base nos endereços informados */
    public void calcularRota(Viagem viagem) {
        try {
            logger.info("Iniciando cálculo da rota de '{}' para '{}'", viagem.getPartida(), viagem.getDestino());

            // Obtenção das coordenadas de origem e destino
            Coordenadas coordenadasOrigem = obterCoordenadas(viagem.getPartida());
            Coordenadas coordenadasDestino = obterCoordenadas(viagem.getDestino());

            viagem.setLatitudePartida(coordenadasOrigem.latitude());
            viagem.setLongitudePartida(coordenadasOrigem.longitude());
            viagem.setLatitudeDestino(coordenadasDestino.latitude());
            viagem.setLongitudeDestino(coordenadasDestino.longitude());

            // Cálculo da distância e velocidade média
            double distanciaKm = calcularDistanciaRota(coordenadasOrigem, coordenadasDestino);
            viagem.setDistancia(distanciaKm);

            double velocidadeMedia = calcularVelocidadeMedia(viagem.getDataPartida());
            viagem.setVelocidadeMedia(velocidadeMedia);

            // Tempo estimado e valor da viagem
            double tempoEstimado = calcularTempoMedio(distanciaKm, velocidadeMedia);
            viagem.setTempoEstimado(tempoEstimado);

            double valorViagem = calcularValorViagem(distanciaKm, tempoEstimado);
            viagem.setValor(BigDecimal.valueOf(valorViagem).setScale(2, RoundingMode.HALF_UP));

        } catch (ResponseStatusException e) {
            logger.error("Erro HTTP: {} - {}", e.getStatusCode(), e.getReason());
            throw e;
        } catch (Exception e) {
            logger.error("Erro inesperado ao calcular rota: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao calcular a rota", e);
        }
    }

    /** Busca as coordenadas geográficas de um endereço usando a API OpenCage */
    private Coordenadas obterCoordenadas(String endereco) {
        try {
            aplicarLimitadorDeRequisicoes();
            String url = String.format(URL_OPENCAGE, removerNumeroDoEndereco(endereco) + ", São Paulo - SP", CHAVE_API);
            logger.info("Buscando coordenadas para: {}", endereco);

            HttpHeaders cabecalhos = new HttpHeaders();
            cabecalhos.set("User-Agent", "ViagemApp/1.0");

            ResponseEntity<String> resposta = clienteHttp.exchange(url, HttpMethod.GET, new HttpEntity<>(cabecalhos), String.class);
            JsonNode raiz = mapperJson.readTree(resposta.getBody());

            if (raiz.has("results") && !raiz.get("results").isEmpty()) {
                JsonNode geometria = raiz.get("results").get(0).path("geometry");
                double latitude = geometria.path("lat").asDouble();
                double longitude = geometria.path("lng").asDouble();
                return new Coordenadas(latitude, longitude);
            }

            logger.error("Nenhuma coordenada encontrada para o endereço: {}", endereco);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Endereço não encontrado: " + endereco);

        } catch (Exception e) {
            logger.error("Erro ao obter coordenadas: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao buscar coordenadas", e);
        }
    }

    /** Calcula a distância (em km) entre dois pontos usando a API OSRM */
    private double calcularDistanciaRota(Coordenadas origem, Coordenadas destino) {
        try {
            aplicarLimitadorDeRequisicoes();
            String url = String.format(Locale.US, URL_OSRM,
                    origem.longitude(), origem.latitude(),
                    destino.longitude(), destino.latitude());

            logger.info("Calculando rota: {}", url);

            HttpHeaders cabecalhos = new HttpHeaders();
            cabecalhos.set("User-Agent", "ViagemApp/1.0");

            ResponseEntity<String> resposta = clienteHttp.exchange(url, HttpMethod.GET, new HttpEntity<>(cabecalhos), String.class);
            JsonNode raiz = mapperJson.readTree(resposta.getBody());

            if (raiz.has("routes") && !raiz.get("routes").isEmpty()) {
                double distanciaMetros = raiz.get("routes").get(0).get("distance").asDouble();
                return distanciaMetros / 1000.0;
            }

            logger.error("Rota não encontrada.");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rota não encontrada");

        } catch (Exception e) {
            logger.error("Erro ao calcular rota: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao calcular rota", e);
        }
    }

    /** Calcula a velocidade média com base no horário de partida */
    private double calcularVelocidadeMedia(LocalDateTime horarioPartida) {
        if (horarioPartida == null) return VELOCIDADE_NORMAL;

        LocalTime horario = horarioPartida.toLocalTime();
        if (isFimDeSemana(horarioPartida)) return VELOCIDADE_FINAL_DE_SEMANA;
        if (horario.isAfter(INICIO_PICO_MANHA) && horario.isBefore(FIM_PICO_MANHA)) return VELOCIDADE_PICO_MANHA;
        if (horario.isAfter(INICIO_PICO_TARDE) && horario.isBefore(FIM_PICO_TARDE)) return VELOCIDADE_PICO_TARDE;

        return VELOCIDADE_NORMAL;
    }

    /** Converte a distância e velocidade em minutos de viagem */
    private double calcularTempoMedio(double distanciaKm, double velocidade) {
        return (distanciaKm / velocidade) * 60;
    }

    /** Calcula o valor total da viagem considerando distância, tempo e tarifas fixas */
    private double calcularValorViagem(double distanciaKm, double tempoEstimado) {
        return TARIFA_BASE + (distanciaKm * VALOR_POR_KM) + (tempoEstimado * VALOR_POR_MINUTO) + VALOR_SEGURO;
    }

    /** Verifica se a data informada é fim de semana */
    private boolean isFimDeSemana(LocalDateTime dataHora) {
        return dataHora.getDayOfWeek().getValue() >= 6;
    }

    /** Remove o número do endereço (ex: "Rua das Flores, 123" -> "Rua das Flores") */
    private String removerNumeroDoEndereco(String endereco) {
        Matcher matcher = Pattern.compile("(.*?)[,\\s]+\\d+$").matcher(endereco.trim());
        return matcher.matches() ? matcher.group(1) : endereco;
    }
}
