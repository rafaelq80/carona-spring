package com.generation.carona_spring.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
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
import com.generation.carona_spring.model.Viagem;
import com.generation.carona_spring.records.Coordenadas;

import io.github.cdimascio.dotenv.Dotenv;

@Service
public class RotaService {

    private static final Logger logger = LoggerFactory.getLogger(RotaService.class);
    private static final Dotenv dotenv = Dotenv.load();
    
    private final RestTemplate clienteHttp;
    private final ObjectMapper conversorJson;

    // URLs das APIs externas
    private static final String URL_OPENCAGE = "https://api.opencagedata.com/geocode/v1/json?q=%s&key=%s&language=pt&format=json";
    private static final String URL_OSRM = "http://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=false";
    private static final String CHAVE_API = dotenv.get("API_KEY");

    // Horários de pico no trânsito
    private static final LocalTime INICIO_PICO_MANHA = LocalTime.of(6, 0);   // 06:00
    private static final LocalTime FIM_PICO_MANHA = LocalTime.of(9, 0);      // 09:00
    private static final LocalTime INICIO_PICO_TARDE = LocalTime.of(16, 0);  // 16:00
    private static final LocalTime FIM_PICO_TARDE = LocalTime.of(19, 0);     // 19:00

    // Velocidades médias em km/h para cada período
    private static final double VELOCIDADE_NORMAL = 50.0;
    private static final double VELOCIDADE_PICO_MANHA = 30.0;
    private static final double VELOCIDADE_PICO_TARDE = 35.0;
    private static final double VELOCIDADE_FIM_DE_SEMANA = 60.0;

    // Valores para cálculo do preço da viagem
    private static final double TARIFA_BASE = 5.00;        // Valor fixo inicial
    private static final double VALOR_POR_KM = 1.50;       // Preço por quilômetro
    private static final double VALOR_POR_MINUTO = 0.50;   // Preço por minuto
    private static final double VALOR_SEGURO = 2.00;       // Taxa de seguro

    public RotaService(RestTemplate clienteHttp, ObjectMapper conversorJson) {
        this.clienteHttp = clienteHttp;
        this.conversorJson = conversorJson;
    }

    /**
     * Método principal: calcula todos os dados da viagem.
     * 
     * Etapas:
     * 1. Busca coordenadas da partida e destino
     * 2. Calcula a distância entre os pontos
     * 3. Define a velocidade média baseada no horário
     * 4. Calcula o tempo estimado
     * 5. Calcula o valor da viagem
     */
    public void calcularRota(Viagem viagem) {
        try {
            logger.info("Calculando rota de '{}' para '{}'", viagem.getPartida(), viagem.getDestino());

            // Etapa 1: Buscar coordenadas geográficas
            Coordenadas coordenadasPartida = buscarCoordenadas(viagem.getPartida());
            Coordenadas coordenadasDestino = buscarCoordenadas(viagem.getDestino());

            // Salvar coordenadas na viagem
            viagem.setLatitudePartida(coordenadasPartida.latitude());
            viagem.setLongitudePartida(coordenadasPartida.longitude());
            viagem.setLatitudeDestino(coordenadasDestino.latitude());
            viagem.setLongitudeDestino(coordenadasDestino.longitude());

            // Etapa 2: Calcular distância em quilômetros
            double distanciaKm = calcularDistancia(coordenadasPartida, coordenadasDestino);
            viagem.setDistancia(distanciaKm);

            // Etapa 3: Definir velocidade média baseada no horário
            double velocidadeMedia = definirVelocidadeMedia(viagem.getDataPartida());
            viagem.setVelocidadeMedia(velocidadeMedia);

            // Etapa 4: Calcular tempo estimado em minutos
            double tempoMinutos = calcularTempoViagem(distanciaKm, velocidadeMedia);
            viagem.setTempoEstimado(tempoMinutos);

            // Etapa 5: Calcular valor total da viagem
            double valorTotal = calcularValorViagem(distanciaKm, tempoMinutos);
            viagem.setValor(BigDecimal.valueOf(valorTotal).setScale(2, RoundingMode.HALF_UP));

            logger.info("Rota calculada com sucesso: {}km, {}min, R$ {}", 
                       distanciaKm, tempoMinutos, valorTotal);

        } catch (ResponseStatusException e) {
            logger.error("Erro ao calcular rota: {} - {}", e.getStatusCode(), e.getReason());
            throw e;
        } catch (Exception e) {
            logger.error("Erro inesperado ao calcular rota: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                                            "Erro ao calcular a rota", e);
        }
    }

    /**
     * Busca as coordenadas (latitude e longitude) de um endereço.
     * 
     * Usa a API OpenCage para converter endereço em coordenadas geográficas.
     */
    private Coordenadas buscarCoordenadas(String endereco) {
        try {
            // Aguardar 2 segundos para não sobrecarregar a API
            pausarRequisicao();

            // Preparar o endereço (remove número e adiciona cidade/estado)
            String enderecoCompleto = prepararEndereco(endereco) + ", São Paulo - SP";
            String url = String.format(URL_OPENCAGE, enderecoCompleto, CHAVE_API);

            logger.info("Buscando coordenadas para: {}", endereco);

            // Fazer requisição HTTP
            String respostaJson = fazerRequisicaoGET(url);

            // Processar resposta JSON
            JsonNode json = conversorJson.readTree(respostaJson);

            // Verificar se encontrou resultados
            if (json.has("results") && json.get("results").size() > 0) {
                JsonNode primeiroResultado = json.get("results").get(0);
                JsonNode geometria = primeiroResultado.path("geometry");
                
                double latitude = geometria.path("lat").asDouble();
                double longitude = geometria.path("lng").asDouble();
                
                logger.info("Coordenadas encontradas: lat={}, lng={}", latitude, longitude);
                return new Coordenadas(latitude, longitude);
            }

            // Nenhum resultado encontrado
            logger.error("Endereço não encontrado: {}", endereco);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, 
                                            "Endereço não encontrado: " + endereco);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Erro ao buscar coordenadas: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                                            "Erro ao buscar coordenadas", e);
        }
    }

    /**
     * Calcula a distância em quilômetros entre dois pontos.
     * 
     * Usa a API OSRM (Open Source Routing Machine) para calcular 
     * a distância real considerando as ruas e rotas disponíveis.
     */
    private double calcularDistancia(Coordenadas partida, Coordenadas destino) {
        try {
            // Aguardar 2 segundos para não sobrecarregar a API
            pausarRequisicao();

            // Montar URL com as coordenadas (formato: long,lat;long,lat)
            String url = String.format(Locale.US, URL_OSRM,
                                     partida.longitude(), partida.latitude(),
                                     destino.longitude(), destino.latitude());

            logger.info("Calculando distância da rota");

            // Fazer requisição HTTP
            String respostaJson = fazerRequisicaoGET(url);

            // Processar resposta JSON
            JsonNode json = conversorJson.readTree(respostaJson);

            // Verificar se encontrou rotas
            if (json.has("routes") && json.get("routes").size() > 0) {
                JsonNode primeiraRota = json.get("routes").get(0);
                double distanciaMetros = primeiraRota.get("distance").asDouble();
                
                // Converter de metros para quilômetros
                double distanciaKm = distanciaMetros / 1000.0;
                
                logger.info("Distância calculada: {} km", distanciaKm);
                return distanciaKm;
            }

            // Nenhuma rota encontrada
            logger.error("Rota não encontrada entre os pontos");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rota não encontrada");

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Erro ao calcular distância: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                                            "Erro ao calcular distância", e);
        }
    }

    /**
     * Define a velocidade média baseada no horário da viagem.
     * 
     * Considera:
     * - Fim de semana: trânsito mais livre (60 km/h)
     * - Pico da manhã (6h-9h): trânsito lento (30 km/h)
     * - Pico da tarde (16h-19h): trânsito lento (35 km/h)
     * - Horário normal: trânsito regular (50 km/h)
     */
    private double definirVelocidadeMedia(LocalDateTime dataHoraPartida) {
        // Se não informou horário, usa velocidade normal
        if (dataHoraPartida == null) {
            return VELOCIDADE_NORMAL;
        }

        // Verificar se é fim de semana
        if (ehFimDeSemana(dataHoraPartida)) {
            logger.info("Fim de semana: velocidade {} km/h", VELOCIDADE_FIM_DE_SEMANA);
            return VELOCIDADE_FIM_DE_SEMANA;
        }

        // Extrair apenas o horário
        LocalTime horario = dataHoraPartida.toLocalTime();

        // Verificar se está no horário de pico da manhã
        if (horario.isAfter(INICIO_PICO_MANHA) && horario.isBefore(FIM_PICO_MANHA)) {
            logger.info("Pico da manhã: velocidade {} km/h", VELOCIDADE_PICO_MANHA);
            return VELOCIDADE_PICO_MANHA;
        }

        // Verificar se está no horário de pico da tarde
        if (horario.isAfter(INICIO_PICO_TARDE) && horario.isBefore(FIM_PICO_TARDE)) {
            logger.info("Pico da tarde: velocidade {} km/h", VELOCIDADE_PICO_TARDE);
            return VELOCIDADE_PICO_TARDE;
        }

        // Horário normal
        logger.info("Horário normal: velocidade {} km/h", VELOCIDADE_NORMAL);
        return VELOCIDADE_NORMAL;
    }

    /**
     * Calcula o tempo estimado da viagem em minutos.
     * 
     * Fórmula: tempo = (distância / velocidade) * 60
     * Exemplo: 10 km a 50 km/h = (10/50) * 60 = 12 minutos
     */
    private double calcularTempoViagem(double distanciaKm, double velocidadeKmPorHora) {
        double tempoHoras = distanciaKm / velocidadeKmPorHora;
        double tempoMinutos = tempoHoras * 60;
        
        logger.info("Tempo estimado: {} minutos", tempoMinutos);
        return tempoMinutos;
    }

    /**
     * Calcula o valor total da viagem.
     * 
     * Composição do preço:
     * - Tarifa base: R$ 5,00 (valor fixo)
     * - Distância: R$ 1,50 por km
     * - Tempo: R$ 0,50 por minuto
     * - Seguro: R$ 2,00 (valor fixo)
     */
    private double calcularValorViagem(double distanciaKm, double tempoMinutos) {
        double valorDistancia = distanciaKm * VALOR_POR_KM;
        double valorTempo = tempoMinutos * VALOR_POR_MINUTO;
        double valorTotal = TARIFA_BASE + valorDistancia + valorTempo + VALOR_SEGURO;
        
        logger.info("Valor calculado: R$ {}", valorTotal);
        return valorTotal;
    }

    /**
     * Verifica se a data é fim de semana (sábado ou domingo).
     */
    private boolean ehFimDeSemana(LocalDateTime dataHora) {
        DayOfWeek diaDaSemana = dataHora.getDayOfWeek();
        return diaDaSemana == DayOfWeek.SATURDAY || diaDaSemana == DayOfWeek.SUNDAY;
    }

    /**
     * Prepara o endereço para busca, removendo o número.
     * 
     * Exemplo: "Rua das Flores, 123" -> "Rua das Flores"
     * Isso melhora a precisão da busca de coordenadas.
     */
    private String prepararEndereco(String endereco) {
        String enderecoLimpo = endereco.trim();
        
        // Regex para remover vírgula ou espaço seguido de números no final
        Pattern pattern = Pattern.compile("(.*?)[,\\s]+\\d+$");
        Matcher matcher = pattern.matcher(enderecoLimpo);
        
        if (matcher.matches()) {
            return matcher.group(1);
        }
        
        return enderecoLimpo;
    }

    /**
     * Faz uma requisição HTTP GET para uma URL.
     * 
     * Adiciona o cabeçalho User-Agent para identificar nossa aplicação.
     */
    private String fazerRequisicaoGET(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "ViagemApp/1.0");
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        ResponseEntity<String> response = clienteHttp.exchange(url, HttpMethod.GET, request, String.class);
        
        return response.getBody();
    }

    /**
     * Pausa a execução por 2 segundos.
     * 
     * Isso evita fazer muitas requisições seguidas às APIs externas,
     * respeitando os limites de taxa (rate limiting).
     */
    private void pausarRequisicao() {
        try {
            Thread.sleep(2000); // 2000 milissegundos = 2 segundos
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Pausa interrompida: {}", e.getMessage());
        }
    }
}