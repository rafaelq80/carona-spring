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
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.generation.carona_spring.model.Coordenadas;
import com.generation.carona_spring.model.Viagem;

@Service
public class RotaService {

	private static final Logger logger = LoggerFactory.getLogger(RotaService.class);

	private final RestTemplate restTemplate;
	private final ObjectMapper objectMapper;

	private static final String OPEN_CAGE_URL = "https://api.opencagedata.com/geocode/v1/json?q=%s&key=%s&language=pt&format=json";
	private final String API_KEY = "4d9e1e4efa7640488175d79a5e54ac4e";

	private static final String OSRM_URL = "http://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=false";

	private static final LocalTime INICIO_PICO_MANHA = LocalTime.of(6, 0); // 6:00
	private static final LocalTime FIM_PICO_MANHA = LocalTime.of(9, 0); // 10:00
	private static final LocalTime INICIO_PICO_TARDE = LocalTime.of(16, 0); // 16:00
	private static final LocalTime FIM_PICO_TARDE = LocalTime.of(19, 0); // 20:00

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
		}
	}

	public void calcularRota(Viagem viagem) {
		try {
			logger.info("Iniciando cálculo de distância para viagem de {} para {}", viagem.getPartida(),
					viagem.getDestino());

			Coordenadas coordenadasOrigem = getCoordenadas(viagem.getPartida());
			logger.info("Coordenadas origem obtidas: {}, {}", coordenadasOrigem.getLatitude(),
					coordenadasOrigem.getLongitude());

			viagem.setLatitudePartida(coordenadasOrigem.getLatitude());
			viagem.setLongitudePartida(coordenadasOrigem.getLongitude());
			
			Coordenadas coordenadasDestino = getCoordenadas(viagem.getDestino());
			logger.info("Coordenadas destino obtidas: {}, {}", coordenadasDestino.getLatitude(),
					coordenadasDestino.getLongitude());

			viagem.setLatitudeDestino(coordenadasDestino.getLatitude());
			viagem.setLongitudeDestino(coordenadasDestino.getLongitude());
			
			double distanciaEmKm = calcularDistanciaRota(coordenadasOrigem, coordenadasDestino);
			viagem.setDistancia(distanciaEmKm);

			double velocidadeMedia = calcularVelocidadeMedia(viagem.getDataPartida());
			viagem.setVelocidadeMedia(velocidadeMedia);

			double tempoMedio = calcularTempoMedio(distanciaEmKm, velocidadeMedia);
			viagem.setTempoEstimado(tempoMedio);

			double valor = calcularValorViagem(distanciaEmKm, tempoMedio);
			viagem.setValor(new BigDecimal(valor).setScale(2, RoundingMode.HALF_UP));

		} catch (Exception e) {
			logger.error("Erro ao calcular distância: {}", e.getMessage());
			throw new IllegalStateException("Erro ao calcular distância da viagem: " + e.getMessage(), e);
		}
	}

	private Coordenadas getCoordenadas(String endereco) {
		try {
			aplicarRateLimit();

			String url = String.format(OPEN_CAGE_URL, removerNumero(endereco) + ", São Paulo - SP", API_KEY);

			logger.info("Buscando coordenadas para o endereço: {}", endereco);
			logger.info("URL da requisição: {}", url);

			HttpHeaders headers = new HttpHeaders();
			headers.set("User-Agent", "ViagemApp/1.0");

			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers),
					String.class);

			logger.debug("Resposta da API OpenCage: {}", response.getBody());
			JsonNode root = objectMapper.readTree(response.getBody());

			if (root.has("results") && root.get("results").size() > 0) {
				JsonNode location = root.get("results").get(0);
				double latitude = location.get("geometry").get("lat").asDouble();
				double longitude = location.get("geometry").get("lng").asDouble();

				logger.info("Coordenadas encontradas: Lat: {}, Lon: {}", latitude, longitude);
				return new Coordenadas(latitude, longitude);
			} else {
				logger.error("Nenhuma coordenada encontrada para o endereço: {}", endereco);
				throw new IllegalArgumentException("Endereço não encontrado: " + endereco);
			}

		} catch (Exception e) {
			logger.error("Erro ao obter coordenadas para '{}': {}", endereco, e.getMessage());
			throw new IllegalStateException("Erro ao obter coordenadas para: " + endereco, e);
		}
	}

	private double calcularDistanciaRota(Coordenadas origem, Coordenadas destino) {
		try {

			if (origem.getLatitude() == 0.0 || origem.getLongitude() == 0.0 || destino.getLatitude() == 0.0
					|| destino.getLongitude() == 0.0) {
				logger.error("Coordenadas inválidas. Origem: ({}, {}), Destino: ({}, {})", origem.getLatitude(),
						origem.getLongitude(), destino.getLatitude(), destino.getLongitude());
				throw new IllegalArgumentException("Coordenadas inválidas para origem ou destino.");
			}

			aplicarRateLimit();

			String url = String.format(Locale.US, OSRM_URL, origem.getLongitude(), origem.getLatitude(),
					destino.getLongitude(), destino.getLatitude());

			logger.info("Calculando rota com a URL: {}", url);

			HttpHeaders headers = new HttpHeaders();
			headers.set("User-Agent", "ViagemApp/1.0");

			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers),
					String.class);

			logger.debug("Resposta completa da API OSRM: {}", response.getBody());

			JsonNode root = objectMapper.readTree(response.getBody());

			if (!root.has("routes") || root.get("routes").isEmpty()) {
				logger.error("Não foi possível encontrar uma rota entre origem e destino.");
				throw new IllegalStateException("Não foi possível calcular a rota");
			}

			double distanceInMeters = root.get("routes").get(0).get("distance").asDouble();
			double distanceInKm = distanceInMeters / 1000.0;

			logger.info("Distância calculada: {} km", distanceInKm);
			return distanceInKm;

		} catch (Exception e) {
			logger.error("Erro ao calcular rota: {}", e.getMessage());
			throw new IllegalStateException("Erro ao calcular rota: " + e.getMessage(), e);
		}
	}

	private double calcularVelocidadeMedia(LocalDateTime horarioPartida) {

		if (horarioPartida == null) {
			logger.info("Horário de partida não definido. Usando velocidade normal.");
			return VELOCIDADE_NORMAL;
		}

		LocalTime horario = horarioPartida.toLocalTime();

		if (isWeekend(horarioPartida)) {
			logger.info("Final de semana detectado. Usando velocidade de final de semana.");
			return VELOCIDADE_FINAL_SEMANA;
		}

		if (horario.isAfter(INICIO_PICO_MANHA) && horario.isBefore(FIM_PICO_MANHA)) {
			logger.info("Horário de pico matinal detectado. Reduzindo velocidade.");
			return VELOCIDADE_PICO_MANHA;
		}

		if (horario.isAfter(INICIO_PICO_TARDE) && horario.isBefore(FIM_PICO_TARDE)) {
			logger.info("Horário de pico vespertino detectado. Reduzindo velocidade.");
			return VELOCIDADE_PICO_TARDE;
		}

		logger.info("Horário fora do pico. Usando velocidade normal.");
		return VELOCIDADE_NORMAL;

	}

	public Double calcularTempoMedio(double distanciaKm, double velocidade) {

		double tempoEmHoras = distanciaKm / velocidade;

		double tempoEmMinutos = tempoEmHoras * 60;

		logger.info("Tempo estimado para a viagem: {} minutos", tempoEmMinutos);
		return tempoEmMinutos;
	}

	public Double calcularValorViagem(double distanciaKm, double tempoEstimado) {

		double valor = TARIFA_BASE + (distanciaKm * VALOR_KM) + (tempoEstimado * VALOR_MINUTO) + SEGURO;

		logger.info("Valor da viagem calculado: R${}", valor);
		return valor;
	}

	private boolean isWeekend(LocalDateTime dateTime) {
		switch (dateTime.getDayOfWeek()) {
		case SATURDAY:
		case SUNDAY:
			return true;
		default:
			return false;
		}
	}

	private String removerNumero(String endereco) {

		Pattern pattern = Pattern.compile("(.*?)[,\\s]+\\d+$");
		Matcher matcher = pattern.matcher(endereco.trim());

		if (matcher.matches()) {

			return matcher.group(1);
		}

		return endereco;
	}

}
