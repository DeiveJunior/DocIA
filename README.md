# DocIA

O DocIA é uma aplicação web para consultar informações em documentos PDF
usando linguagem natural. A pessoa envia um arquivo, escreve o que
deseja localizar ou compreender e recebe os resultados encontrados no
documento.

> **Status:** projeto desenvolvido no escopo atual. Este repositório não
> declara uma publicação em produção.

## Sobre e objetivo

O objetivo do DocIA é facilitar a consulta a informações contidas em
PDFs sem que a pessoa precise percorrer manualmente todas as páginas. A
aplicação recebe comandos em português e combina extração determinística
em Java com inteligência artificial para responder a solicitações que
exigem contexto.

O diferencial dessa combinação é usar extratores Java para informações
estruturadas e recorrer à IA como complemento quando a solicitação é
geral ou os resultados encontrados precisam de interpretação. A IA não
substitui os extratores.

## Como funciona

1.  A pessoa envia um PDF e escreve um comando.
2.  O backend valida o arquivo e o comando.
3.  O `CommandInterpreterService` envia o comando ao Ollama para
    classificação da intenção.
4.  O `PdfExtractionService`, baseado em Apache PDFBox, extrai o texto
    do PDF e mantém a identificação das páginas.
5.  Para intenções específicas, o `DocumentCommandService` executa o
    extrator Java correspondente.
6.  Se a intenção for geral ou o resultado Java precisar de complemento,
    o `GeneralDocumentAnswerService` envia ao Ollama o comando original,
    o texto extraído por página e os resultados Java disponíveis.
7.  A API devolve uma resposta específica ou uma resposta geral, que
    pode preservar os resultados Java e indicar páginas de origem.

## Funcionalidades

-   Extração de nomes identificados em campos e rótulos compatíveis com
    o extrator.
-   Extração de endereços de e-mail.
-   Extração de telefones brasileiros reconhecidos pelos padrões do
    extrator.
-   Identificação de CPF e CNPJ com validação dos dígitos verificadores.
-   Extração de endereços e componentes reconhecidos, como rua, número,
    complemento, bairro, cidade, estado e CEP.
-   Retorno do texto completo do documento.
-   Respostas gerais sobre o texto extraído, incluindo perguntas
    contextuais e pedidos de resumo, quando a classificação ou os
    resultados Java levam ao fallback.
-   Preservação dos resultados Java quando a IA é usada como
    complemento.

## Tecnologias

-   **Java 17**
-   **Spring Boot 4.1.2** e Spring WebMVC
-   **Maven 3.10.0**, com Maven Wrapper 3.3.4
-   **Apache PDFBox 3.0.5** para leitura de PDFs
-   **Ollama** para inferência pelo endpoint de chat
-   **Qwen `qwen2.5-coder:7b`** como modelo padrão configurado no código
-   **HTML, CSS e JavaScript** no frontend, sem framework JavaScript

Durante o desenvolvimento, conforme informado pelo autor, foi utilizada
IA generativa remota --- GitHub Copilot --- como apoio à geração de
código. Essa descrição registra o uso informado sem atribuir a
ferramenta como autora exclusiva de todo o código.

## Arquitetura

O módulo Java fica no diretório `docia/`. O Spring Boot executa a API e
serve os recursos estáticos do frontend.

``` text
docia/
  pom.xml
  src/
    main/
      java/com/docia/
        DociaApplication.java
        controller/
          DociaController.java
          DocumentCommandController.java
        dto/
          respostas específicas e resposta geral
        service/
          extração de PDF, extratores Java e integração Ollama
      resources/
        application.yaml
        static/
          index.html
          styles.css
          app.js
    test/java/com/docia/
      testes da aplicação e dos serviços
```

### Componentes principais

-   **`DociaApplication`** inicializa a aplicação Spring Boot e
    configura o `RestClient.Builder`.
-   **`DociaController`** expõe `GET /api/status`, que retorna `pong`.
-   **`DocumentCommandController`** recebe o PDF e o comando por
    multipart e valida os dados de entrada.
-   **`PdfExtractionService`** usa PDFBox para extrair texto por página;
    PDFs criptografados ou sem texto extraível são rejeitados.
-   **Extratores Java** processam nomes, e-mails, telefones, CPF/CNPJ e
    endereços.
-   **`CommandInterpreterService`** consulta o Ollama para classificar o
    comando.
-   **`DocumentCommandService`** coordena extração, roteamento e decisão
    de usar a resposta geral.
-   **`GeneralDocumentAnswerService`** envia ao Ollama o comando, o
    texto por página e os resultados Java quando é necessário responder
    a uma solicitação geral ou complementar.
-   **`index.html`, `styles.css` e `app.js`** compõem a interface de
    envio e exibição dos resultados.

## Requisitos para execução local

-   JDK 17.
-   Ollama instalado e em execução.
-   Modelo `qwen2.5-coder:7b` disponível no Ollama.
-   Acesso à internet na primeira execução do Maven Wrapper se a
    distribuição do Maven ainda não estiver em cache.

Baixe o modelo:

``` bash
ollama pull qwen2.5-coder:7b
```

Inicie o Ollama, se ele ainda não estiver sendo executado pelo sistema:

``` bash
ollama serve
```

Em outro terminal, inicie a aplicação:

``` bash
cd docia
./mvnw spring-boot:run
```

No Windows, use o wrapper correspondente:

``` powershell
cd docia
.\mvnw.cmd spring-boot:run
```

Sem configuração externa, o Spring Boot usa a porta `8080`. A interface
fica disponível em `http://localhost:8080`.

### Configuração do Ollama

Os valores padrão declarados nos serviços são:

-   URL base: `http://localhost:11434`
-   Modelo: `qwen2.5-coder:7b`
-   Caminho da API chamado pela aplicação: `/api/chat`

As propriedades Spring `docia.ollama.url` e `docia.ollama.model` podem
ser definidas por variáveis de ambiente:

``` bash
export DOCIA_OLLAMA_URL=http://localhost:11434
export DOCIA_OLLAMA_MODEL=qwen2.5-coder:7b
```

No PowerShell:

``` powershell
$env:DOCIA_OLLAMA_URL = "http://localhost:11434"
$env:DOCIA_OLLAMA_MODEL = "qwen2.5-coder:7b"
```

Configure `DOCIA_OLLAMA_URL` como URL base, sem acrescentar `/api/chat`:
os serviços adicionam esse caminho à requisição. `localhost` aponta para
o ambiente onde a aplicação está sendo executada; em containers ou
servidores, use um endereço alcançável a partir desse ambiente.

## API

### Verificar resposta da aplicação

``` http
GET /api/status
```

A resposta atual é o texto `pong`. Esse endpoint confirma que a
aplicação respondeu, mas não testa a disponibilidade do Ollama.

### Enviar documento e comando

``` http
POST /api/documents/command
Content-Type: multipart/form-data
```

Campos multipart:

  Campo        Conteúdo
  ------------ ----------------------------------
  `document`   Arquivo PDF
  `command`    Solicitação em linguagem natural

Exemplo:

``` bash
curl -X POST http://localhost:8080/api/documents/command \
  -F "document=@./documento.pdf;type=application/pdf" \
  -F "command=Quais são os e-mails encontrados?"
```

O controlador valida arquivo não vazio, extensão `.pdf`, tamanho máximo
de 20 MB, tipo de conteúdo permitido e comando não vazio. O PDF também
precisa ser válido e conter texto extraível.

As respostas específicas contêm o DTO da categoria solicitada, com
estado, número de páginas e resultados. Respostas gerais usam
`responseType: GENERAL_ANSWER`, com estado, texto, páginas de origem e
os resultados Java disponíveis.

Estados usados nas respostas gerais:

-   `ANSWERED`: a IA retornou uma resposta.
-   `NOT_FOUND`: a IA informou que não encontrou suporte para a
    informação no texto recebido.
-   `AI_UNAVAILABLE`: a resposta geral não pôde ser obtida; resultados
    Java disponíveis são preservados na resposta.

## Testes

Os testes automatizados estão em `docia/src/test/`. Para executar a
verificação Maven a partir do módulo:

``` bash
cd docia
./mvnw clean verify
```

Este README descreve o comando para executar os testes; não afirma que
uma execução foi realizada ou aprovada.

## Limitações e estado operacional

-   O limite configurado para upload é de 20 MB.
-   PDFs criptografados não são suportados.
-   PDFs digitalizados sem texto extraível não são processados; OCR não
    está implementado.
-   A extração de nomes depende dos campos e rótulos reconhecidos pelo
    extrator e não equivale a reconhecimento geral de todas as pessoas
    mencionadas no texto.
-   A extração de endereços reconhece padrões estruturados; não há
    garantia de identificar toda forma de endereço presente em
    documentos.
-   A interpretação geral depende de um Ollama alcançável e do modelo
    configurado. A URL `localhost` padrão não torna o Ollama
    automaticamente disponível em servidor externo.
-   O texto do PDF e os resultados Java são enviados ao Ollama quando a
    classificação ou a regra de fallback aciona a resposta geral.
    Considere isso ao escolher o ambiente e avaliar a privacidade dos
    documentos.
-   Não há banco de dados configurado; documentos e resultados não são
    persistidos em banco pela aplicação.
-   Autenticação e autorização não estão implementadas no estado atual.
-   Não foram encontrados Docker, Docker Compose ou perfil Spring `prod`
    na estrutura verificada.
-   O endpoint `/api/status` não é uma verificação de prontidão da IA.
-   O CSS importa fontes do Google Fonts; carregá-las depende de acesso
    externo.
-   O projeto não é declarado como implantado ou validado em produção
    neste repositório.
