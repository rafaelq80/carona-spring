# RotaService - Documentação

## O que faz?

O `RotaService` é responsável por calcular todas as informações de uma viagem: distância, tempo estimado e valor a ser cobrado.

## Como funciona?

Quando você cria uma viagem, o serviço:

1. Converte os endereços em coordenadas geográficas (latitude/longitude)
2. Calcula a distância real entre os pontos usando as ruas
3. Estima o tempo de viagem baseado no horário (considera trânsito)
4. Calcula o preço da corrida

## Método Principal

### `calcularRota(Viagem viagem)`

Recebe um objeto `Viagem` e preenche automaticamente os seguintes campos:

- `latitudePartida` e `longitudePartida`
- `latitudeDestino` e `longitudeDestino`
- `distancia` (em km)
- `velocidadeMedia` (em km/h)
- `tempoEstimado` (em minutos)
- `valor` (em reais)

**Exemplo de uso:**

```java
@Autowired
private RotaService rotaService;

Viagem viagem = new Viagem();
viagem.setPartida("Avenida Paulista, 1000");
viagem.setDestino("Rua Augusta, 500");
viagem.setDataPartida(LocalDateTime.now());

rotaService.calcularRota(viagem);

// Agora a viagem tem todos os dados calculados
System.out.println("Distância: " + viagem.getDistancia() + " km");
System.out.println("Tempo: " + viagem.getTempoEstimado() + " min");
System.out.println("Valor: R$ " + viagem.getValor());
```

## Velocidades por Horário

O serviço ajusta a velocidade média baseado no horário da viagem:

| Período        | Velocidade | Horário          |
| -------------- | ---------- | ---------------- |
| Pico da manhã  | 30 km/h    | 6h às 9h         |
| Pico da tarde  | 35 km/h    | 16h às 19h       |
| Horário normal | 50 km/h    | Demais horários  |
| Fim de semana  | 60 km/h    | Sábado e domingo |

## Cálculo do Preço

O valor da viagem é composto por:

- **Tarifa base:** R$ 5,00 (fixo)
- **Por distância:** R$ 1,50 por km
- **Por tempo:** R$ 0,50 por minuto
- **Seguro:** R$ 2,00 (fixo)

**Fórmula:** `Valor = 5,00 + (distância × 1,50) + (tempo × 0,50) + 2,00`

## APIs Utilizadas

- **OpenCage:** Converte endereços em coordenadas
- **OSRM:** Calcula a distância real entre pontos

## Configuração Necessária

Adicione no arquivo `.env` na raiz do projeto:

```
API_KEY=sua_chave_da_opencage_aqui
```

Obtenha uma chave gratuita em: https://opencagedata.com/

## Tratamento de Erros

O serviço lança exceções nos seguintes casos:

- **404 (NOT_FOUND):** Endereço não encontrado ou rota impossível
- **500 (INTERNAL_SERVER_ERROR):** Erro ao se comunicar com as APIs externas

## Observações Importantes

- O serviço aguarda 2 segundos entre cada chamada às APIs externas (evita bloqueio por limite de requisições)
- Os endereços são automaticamente preparados, removendo números ao final para melhor precisão
- Todos os endereços assumem que são de São Paulo - SP

## Dependências Adicionais

```xml
<dependency>
    <groupId>io.github.cdimascio</groupId>
    <artifactId>dotenv-java</artifactId>
</dependency>
```