package com.generation.carona_spring.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "tb_viagens")
public class Viagem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank(message = "O local de partida é obrigatório")
	private String partida;

	@NotBlank(message = "O destino é obrigatório")
	private String destino;

	@NotNull(message = "A data de partida é obrigatória")
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime dataPartida;

	private BigDecimal valor;

	private Double distancia;
	
	private Double velocidadeMedia;

	private Double tempoEstimado;
	
	private Double latitudePartida;
	
	private Double longitudePartida;
	
	private Double latitudeDestino;
	
	private Double longitudeDestino;

	@ManyToOne
	@JsonIgnoreProperties("viagem")
	private Veiculo veiculo;

	@ManyToOne
	@JsonIgnoreProperties("viagem")
	private Usuario usuario;

	public Viagem() { }

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getPartida() {
		return partida;
	}

	public void setPartida(String partida) {
		this.partida = partida;
	}

	public String getDestino() {
		return destino;
	}

	public void setDestino(String destino) {
		this.destino = destino;
	}

	public LocalDateTime getDataPartida() {
		return dataPartida;
	}

	public void setDataPartida(LocalDateTime dataPartida) {
		this.dataPartida = dataPartida;
	}

	public BigDecimal getValor() {
		return valor;
	}

	public void setValor(BigDecimal valor) {
		this.valor = valor;
	}

	public Double getDistancia() {
		return distancia;
	}

	public void setDistancia(Double distancia) {
		this.distancia = distancia;
	}

	public Double getVelocidadeMedia() {
		return velocidadeMedia;
	}

	public void setVelocidadeMedia(Double velocidadeMedia) {
		this.velocidadeMedia = velocidadeMedia;
	}

	public Double getTempoEstimado() {
		return tempoEstimado;
	}

	public void setTempoEstimado(Double tempoEstimado) {
		this.tempoEstimado = tempoEstimado;
	}

	public Double getLatitudePartida() {
		return latitudePartida;
	}

	public void setLatitudePartida(Double latitudePartida) {
		this.latitudePartida = latitudePartida;
	}

	public Double getLongitudePartida() {
		return longitudePartida;
	}

	public void setLongitudePartida(Double longitudePartida) {
		this.longitudePartida = longitudePartida;
	}

	public Double getLatitudeDestino() {
		return latitudeDestino;
	}

	public void setLatitudeDestino(Double latitudeDestino) {
		this.latitudeDestino = latitudeDestino;
	}

	public Double getLongitudeDestino() {
		return longitudeDestino;
	}

	public void setLongitudeDestino(Double longitudeDestino) {
		this.longitudeDestino = longitudeDestino;
	}

	public Veiculo getVeiculo() {
		return veiculo;
	}

	public void setVeiculo(Veiculo veiculo) {
		this.veiculo = veiculo;
	}

	public Usuario getUsuario() {
		return usuario;
	}

	public void setUsuario(Usuario usuario) {
		this.usuario = usuario;
	}

}