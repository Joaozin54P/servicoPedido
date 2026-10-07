# servicoPedido

Pedidos da lanchonete: o cliente monta o carrinho, vira **pedido**, recebe uma **senha de retirada** e acompanha o status até retirar.

**Autores:** João Pedro Machado e Giovanna Aparecida

## Tecnologias
Java 21 · Spring Boot 4.1.1 · Spring Security (valida JWT) · JPA · banco H2 em memória · RabbitMQ (opcional) · Maven

## Porta
`8083` (pelo gateway: `/pedidos/**` em `http://localhost:8080`)

## Estrutura (2 módulos)

```
servicoPedido/
├── pom.xml          projeto (lista os 2 módulos)
├── domain/          REGRAS DE NEGÓCIO: Java puro, sem Spring
│   └── entity/ (Pedido, ItemPedido, StatusPedido, FormaPagamento...)  repository/ (interface)
│       service/PedidoService  integration/ (interface do cardápio)  messaging/ + event/ (avisos)
│       transaction/ (interface)  exception/
└── spring/          tudo que usa o Spring
    └── controller/ (API REST + DTOs)  repository/ (JPA + H2)  integration/ (chama o servicoCardapio)
        messaging/ (RabbitMQ ou log)  security/ (valida o JWT)  transaction/  config/
```

O `domain` decide **o que** acontece (calcular total, validar disponibilidade, avançar status só na ordem certa, cliente só vê o pedido dele). O `spring` decide **como** (JPA/H2, HTTP para o cardápio, RabbitMQ, JWT).

## Fluxo do pedido
`CRIADO → EM_PREPARO → PRONTO → RETIRADO` (ou `CANCELADO`, só enquanto está `CRIADO`)

1. `POST /pedidos` (com o header `Idempotency-Key`): o serviço consulta **preço e disponibilidade no servicoCardapio**, grava o pedido com o preço daquele momento (mudar o cardápio depois não altera pedidos antigos) e gera a senha de retirada.
2. A cozinha avisa `EM_PREPARO` e `PRONTO` (`PATCH /pedidos/{id}/status` ou mensagem na fila).
3. O atendente chama `POST /pedidos/{id}/retirar` e o pedido vira `RETIRADO` (venda concluída).
4. O pedido só pode ser cancelado (`POST /pedidos/{id}/cancelar`) enquanto a cozinha não começou.

## Como rodar
Requisitos: JDK 21 e Maven. O serviço **precisa** do `servicoLogin` (8085, emite o token) e do `servicoCardapio` (8082, preços) no ar.

```bash
mvn clean install
cd spring
mvn spring-boot:run
```

- Console do H2: http://localhost:8083/h2-console (JDBC URL `jdbc:h2:mem:pedidodb`, usuário `sa`, senha vazia)
- Cardápio em outro endereço: variável `CARDAPIO_URL` (padrão `http://localhost:8082`).
- Suba tudo com o H2 recém-iniciado para os ids do cardápio de exemplo baterem.

### Mensageria (RabbitMQ) — desligada por padrão
Controlada por `app.mensageria.habilitada` (variável `MENSAGERIA_HABILITADA`).

- **`false` (padrão):** não usa RabbitMQ. Os avisos (`pedido-criado`, `pedido-cancelado`, `pedido-finalizado`) aparecem só no log. Dá para testar tudo sozinho usando `PATCH /pedidos/{id}/status` no lugar da cozinha.
- **`true`:** publica os avisos no RabbitMQ e escuta a fila `pedido-status-cozinha`. Precisa de um RabbitMQ acessível (CloudAMQP ou instalado na máquina). Configure `spring.rabbitmq.*` com os dados da sua instância, de preferência por variáveis de ambiente, sem gravar usuário e senha no repositório.

### O que aparece no console
Ao criar, cancelar ou finalizar um pedido, o console mostra um "cupom" organizado (senha de retirada, itens, adicionais, observações, total). O texto é montado em `spring/.../messaging/PedidoLogFormatter.java`.

## Rotas
| Rota | Quem acessa |
| --- | --- |
| `POST /pedidos` + header `Idempotency-Key` | qualquer usuário logado → **201** (pedido novo) ou **200** (mesma chave de novo: devolve o pedido existente) |
| `GET /pedidos/meus` | o próprio cliente |
| `GET /pedidos/{id}` | dono do pedido, `COZINHA` ou `ADMIN` |
| `POST /pedidos/{id}/cancelar` | dono do pedido, `COZINHA` ou `ADMIN` (só se `CRIADO`) |
| `GET /pedidos` e `GET /pedidos?status=PRONTO` | `COZINHA` ou `ADMIN` |
| `PATCH /pedidos/{id}/status` `{"status":"EM_PREPARO"}` ou `"PRONTO"` | `COZINHA` ou `ADMIN` |
| `POST /pedidos/{id}/retirar` | `COZINHA` ou `ADMIN` |

Exemplo de criação (token de qualquer usuário logado):

```json
{
  "formaPagamento": "PIX",
  "observacao": "sem guardanapo",
  "itens": [
    { "produtoId": 3, "quantidade": 2, "adicionaisIds": [1], "observacao": "sem cebola" },
    { "produtoId": 5, "quantidade": 1 }
  ]
}
```

`formaPagamento`: `PIX`, `CREDITO`, `DEBITO` ou `DINHEIRO` (pagamento simulado). `quantidade`: de 1 a 20.

Erros: `{"erro": "mensagem"}` com 400 (dado inválido, sem `Idempotency-Key`, produto indisponível, transição de status não permitida), 401 (sem token), 403 (pedido de outro cliente ou sem permissão), 404 (pedido inexistente), 409 (pedido idêntico ainda sendo processado) e 503 (servicoCardapio fora do ar).

## Proteção contra pedido (e pagamento) em dobro
**Problema:** o cliente clica em "pagar" duas vezes, ou a rede cai e o app tenta de novo. Sem proteção, viram dois pedidos (duas cobranças).

**Solução: `Idempotency-Key`.** O front gera um UUID quando o cliente abre a tela de pagamento e manda o mesmo código em todas as tentativas daquele pedido:

| Situação | Resposta |
| --- | --- |
| 1ª requisição com a chave | `201 Created`: cria o pedido e avisa a cozinha |
| mesma chave de novo (2º clique, retentativa) | `200 OK`: devolve o **mesmo pedido**, sem criar outro nem avisar a cozinha de novo |
| dois cliques **ao mesmo tempo** | só um grava; o outro recebe o mesmo pedido (a restrição única do banco segura) |
| sem o header | `400` |

- A chave vale **por cliente**: a mesma chave de dois clientes não conflita.
- Se o pedido **falhar** (ex.: produto indisponível), nada é gravado e a chave pode ser reusada.
- Uma compra **nova** precisa de chave **nova**.
- Implementação: `PedidoService.criarPedido` (regra) + restrição única `cliente + chave` em `PedidoOrm` (`uk_pedido_cliente_chave`).

Depois que o pedido existe, retirar, cancelar e a cozinha mudar o status travam a linha do pedido (`SELECT ... FOR UPDATE`): retirar duas vezes, ou cancelar no instante em que a cozinha começa, nunca passam os dois.

> O pagamento é **simulado** (só guarda a forma escolhida). Com cobrança real, a mesma chave deve ser repassada ao gateway de pagamento.

## Contrato das mensagens (servicoCozinha e servicoRelatorio)
Filas **durable**, mensagens em JSON (só com a mensageria ligada):

| Fila | Quem publica | Quem lê |
| --- | --- | --- |
| `pedido-criado` | servicoPedido | servicoCozinha |
| `pedido-cancelado` | servicoPedido | servicoCozinha |
| `pedido-status-cozinha` | servicoCozinha | servicoPedido |
| `pedido-finalizado` | servicoPedido | servicoRelatorio |

```json
pedido-criado:   { "pedidoId": 1, "senhaRetirada": "001", "clienteEmail": "ana@x.com", "observacao": null,
                   "criadoEm": "2026-10-01T12:00:00",
                   "itens": [ { "nomeProduto": "Burger Bacon", "quantidade": 2, "adicionais": ["Bacon extra"], "observacao": "sem cebola" } ] }
pedido-cancelado:       { "pedidoId": 1 }
pedido-status-cozinha:  { "pedidoId": 1, "status": "EM_PREPARO" }     (depois "PRONTO")
pedido-finalizado: { "pedidoId": 1, "clienteEmail": "ana@x.com", "total": 80.70, "formaPagamento": "PIX",
                     "finalizadoEm": "2026-10-01T12:25:00",
                     "itens": [ { "produtoId": 3, "nomeProduto": "Burger Bacon", "quantidade": 2, "subtotal": 67.80 } ] }
```

## CORS
Origens liberadas em `app.cors.origins`: `localhost:8081` (Expo web), `3000`, `4200` e `5173`.

## Testar no Postman
1. `Import` → `postman/servicoPedido.postman_collection.json`.
2. Suba servicoLogin, servicoCardapio e servicoPedido.
3. Clique na coleção → **Run** e rode tudo em ordem (logins, criação de pedido, cozinha, cancelamento, produto indisponível e erros de permissão).

Na mão, o token vai em **Authorization → Bearer Token** e o `POST /pedidos` também precisa do header `Idempotency-Key` (um UUID novo por pedido).

## Testes
**`domain`** (sem Spring, sem banco, sem RabbitMQ): `cd domain && mvn test`. Cobre total com adicionais, produto indisponível, ciclo do status, cancelamento, acesso a pedido de outro cliente e clique duplo.

**`spring`** (Spring + H2 + transações reais), em `PedidoConcorrenciaTest`: `cd spring && mvn test`.

| Teste | O que prova |
| --- | --- |
| `cincoCliquesAoMesmoTempoCriamUmSoPedido` | 5 cliques em "pagar" → 1 pedido no banco e 1 aviso para a cozinha |
| `segundoCliqueDepoisDoPrimeiroDevolveOMesmoPedido` | mesma chave de novo → mesmo pedido |
| `comprasDiferentesComChavesDiferentesSaoDoisPedidos` | duas compras de verdade continuam sendo duas |
| `mesmaChaveDeClientesDiferentesNaoConflita` | a chave é de cada cliente |
| `retirarDuasVezesAoMesmoTempoSoUmaPassa` | uma retirada passa, a outra é recusada |
| `cancelarNaHoraQueACozinhaComecaSoUmVence` | nunca fica "cancelado e em preparo" |

Esses testes não precisam do servicoCardapio nem de RabbitMQ.