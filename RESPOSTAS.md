# RESPOSTAS — ICEIBank — Sprint 1



## Parte B — Relógio de Lamport e registro de eventos

**1. Por que o relógio de Lamport usa `max(contador_local, timestampRecebido) + 1` ao receber uma mensagem, em vez de simplesmente adotar o timestamp recebido diretamente?**



**2. Se a Agência 0 está no evento de contador 10 e recebe uma mensagem com timestamp 3 (de uma agência mais "atrasada"), qual o novo valor do contador da Agência 0? O que isso implica sobre agências que processam muitos eventos rapidamente versus agências mais lentas?**



---

## Parte D — Transferências

**1. No trecho `agenciaDestino === idAgencia` (mesma agência), por que a transferência local não precisa da lógica de `aoEnviar()`/`aoReceber()` do relógio de Lamport, enquanto a transferência entre agências precisa?**

Porque `aoEnviar()`/`aoReceber()` só fazem sentido quando existe uma
mensagem real trafegando entre dois processos diferentes, cada um com seu
próprio relógio local. Na transferência local (Ana → Carla, ambas na
Agência 0), débito e crédito são só duas operações sequenciais dentro do
mesmo processo, sem nenhuma comunicação de rede envolvida - por isso os dois
eventos usam `eventoLocal()`, e no meu log ficaram com timestamps
consecutivos: [Lamport 5] TRANSFERENCIA_DEBITO e [Lamport 6]
TRANSFERENCIA_CREDITO. É só o contador local incrementando normalmente,
regra 1 do algoritmo.

Já na transferência entre agências (Ana → Bruno, Agência 0 → Agência 1),
existem DOIS relógios independentes envolvidos, um por agência, e a
mensagem HTTP que carrega o crédito remoto é o evento que precisa
sincronizar essa causalidade entre eles. A Agência 0 usa `aoEnviar()` antes
de mandar a requisição (regra 2), anexando esse timestamp no corpo da
mensagem; a Agência 1, ao receber, usa `aoReceber(timestampRecebido)`
(regra 3) para garantir que seu próprio relógio "alcance" o da Agência 0.
No meu teste isso ficou visível: a Agência 0 tinha acabado de debitar em
[Lamport 2], e a Agência 1 - que só tinha processado sua própria criação
de conta em [Lamport 1] - recebeu a mensagem e pulou direto para [Lamport
4] no TRANSFERENCIA_CREDITO_REMOTO (max(1, 3) + 1 = 4, já que o envio da
Agência 0 tinha incrementado para 3 antes de mandar). Sem essa
sincronização, os relógios das duas agências ficariam "desconectados" -
Lamport não capturaria a relação de causa e efeito entre o débito em uma
agência e o crédito na outra.

**2. Reproduza a falha conhecida e observe o saldo da conta de origem depois do erro. Ele foi revertido? O que isso significa em termos de consistência do sistema bancário?**

Não, o saldo não foi revertido. Na minha reprodução, a conta 0 (Ana) estava
com saldo 55 antes do teste. Derrubei a Agência 1 (dona da conta 1, do
Bruno) e tentei transferir 10 da conta 0 para a conta 1. A API retornou
502 (Gateway Incorreto), com a mensagem "Débito já aplicado - inconsistência
conhecida (ver Sprint 4)" - e, ao consultar o saldo da conta 0 logo depois,
ele estava em 45, não em 55.

Isso significa que o sistema, nesse estado, viola a propriedade de
atomicidade que uma operação bancária precisa ter: a transferência deveria
ser "tudo ou nada" (ou debita E credita, ou nenhum dos dois), mas o que
aconteceu foi um estado intermediário - o débito foi persistido, mas o
crédito nunca aconteceu, porque a agência de destino estava inacessível no
momento exato entre as duas operações. Na prática, R$10 "desapareceram" do
sistema: saíram da conta da Ana e não entraram em lugar nenhum, porque a
Agência 1 nem chegou a processar a requisição (ela estava fora do ar).

Em um banco real, isso é inaceitável - seria dinheiro literalmente perdido,
sem nenhum registro de para onde foi. O único rastro que sobra é o evento
TRANSFERENCIA_FALHOU no log da agência de origem, que documenta a falha mas
não a corrige automaticamente. É exatamente essa lacuna que o Sprint 4
(transações distribuídas, com 2PC ou Saga) precisa fechar: garantir que ou
as duas operações (débito e crédito) aconteçam, ou nenhuma delas aconteça -
mesmo sob falha de rede ou de uma das agências.

**3. Pensando à frente para o Sprint 4: cite, em alto nível, duas formas possíveis de corrigir esse problema.**

Duas abordagens clássicas para garantir atomicidade nesse cenário
(discutidas na literatura de Sistemas Distribuídos, ex.: Coulouris et al.):

1) Two-Phase Commit (2PC): antes de aplicar qualquer mudança definitiva, a
agência de origem (coordenadora) pergunta à agência de destino se ela está
pronta para receber o crédito ("fase de preparação"). Só se AMBAS
confirmarem que conseguem executar a operação, a coordenadora manda o
"commit" definitivo para as duas; se qualquer uma falhar ou não responder,
manda "abort" e nenhuma mudança é aplicada. Isso evitaria exatamente o que
vi no meu teste: o débito só seria persistido de verdade depois de a
Agência 1 confirmar que estava no ar e pronta para creditar.

2) Saga (compensação): ao invés de bloquear as duas agências esperando uma
confirmação mútua, cada operação local é aplicada imediatamente, mas cada
uma vem acompanhada de uma "ação compensatória" pré-definida. Se o passo
seguinte da transação falhar (como a chamada remota nesse teste), o
orquestrador dispara a compensação do passo anterior - nesse caso, um
crédito de estorno na Agência 0, devolvendo os R$10 para a conta da Ana.
Diferente do 2PC, a inconsistência existe por um instante (a "janela" entre
o débito e o estorno), mas o sistema se autocorrige sem precisar que as
duas agências fiquem bloqueadas aguardando uma da outra.

---

## Parte E — Linha do tempo unificada

**1. O relógio de Lamport garante que, se A aconteceu antes de B causalmente, `timestamp(A) < timestamp(B)`. Ele não garante a volta. O que isso significa na prática quando você vê dois eventos com timestamps diferentes na linha do tempo, mas sem saber se um realmente influenciou o outro?**

Significa que a implicação só vale em um sentido. Ver timestamp(A) 
timestamp(B) na minha linha do tempo não me diz se A realmente causou B, ou
se são só dois eventos concorrentes que por acaso ficaram em posições
diferentes na ordenação. Por exemplo, [Lamport 2] TRANSFERENCIA_DEBITO na
Agência 0 e [Lamport 4] CRIAR_CONTA da Carla também na Agência 0 estão em
ordem crescente, mas eu sei (porque acompanhei a execução) que são duas
operações completamente independentes - uma não causou a outra, só
aconteceram nessa ordem porque foram meus comandos sequenciais. Sem esse
contexto externo, olhando só para os números, não dá pra saber com certeza:
o timestamp menor não prova causalidade, só é compatível com ela.

**2. Baseado no que você observou: o relógio de Lamport, sozinho, seria suficiente para um sistema que precisa distinguir com certeza "A e B são concorrentes" de "A aconteceu antes de B"? Por que isso motiva o relógio vetorial do Sprint 2?**

Não seria suficiente. O caso mais claro que observei foi o empate no
Lamport 4: a Agência 0 criou a conta da Carla e a Agência 1 aplicou um
crédito remoto, ambos com timestamp 4, mas com horaParede bem diferentes
(19:46 e 19:52). Isso confirma que são eventos concorrentes (nenhum causou
o outro) - só que eu só consigo afirmar isso com certeza porque sei o que
rodei manualmente. Se dois eventos tivessem timestamps DIFERENTES ao invés
de empatados, o relógio de Lamport sozinho não me daria como distinguir
"aconteceu antes de fato" de "só ficou com número menor por coincidência,
mas era concorrente". É exatamente essa ambiguidade que motiva o relógio
vetorial: em vez de um único contador por processo, cada processo mantém um
vetor com o contador de TODOS os processos que conhece, o que permite
comparar dois timestamps e determinar com certeza se um "domina" o outro
(causal) ou se nenhum domina o outro (concorrente) - sem depender de
inferência externa como eu tive que fazer aqui.

---

## Parte F — Autenticação (JWT)

### Justificativas de design

**Formato das credenciais escolhido:** login por **id da conta + senha**. A senha é definida no momento da criação da conta (`POST /contas`) e fica guardada na própria conta (nunca é devolvida nas respostas da API — o campo é marcado com `@JsonIgnore`). Essa escolha foi feita porque, neste sprint, a conta é a única entidade que já existe no sistema — criar um cadastro de "usuário" separado adicionaria uma entidade nova sem necessidade real. Como cada agência só guarda as contas sob sua responsabilidade (partição), o login também só funciona na agência dona daquela conta, o que é coerente com o resto da arquitetura.

**Chamada entre agências (`creditar-remoto`):** decidimos que essa chamada **também** precisa de um JWT válido (a rota está protegida pelo mesmo filtro que protege as demais), mas usando um **token interno separado** do token de usuário: gerado sob demanda pela agência de origem, com `subject = "sistema-interno"`, claim `tipo = "interno"` e expiração bem curta (1 minuto — token de uso único, gerado exatamente no momento da chamada). Justificativa: usar o token do usuário que iniciou a transferência exigiria propagá-lo entre agências e não faria sentido semanticamente (a chamada não é "em nome do usuário logado" perante a agência de destino, é uma chamada de sistema-a-sistema). Separar os dois tipos de token também limita o dano caso um vaze: um token interno comprometido não dá acesso às rotas de consulta/operação de uma conta específica.

**Exceção de `POST /contas` na autenticação:** diferente das demais rotas de
conta, a criação de conta não exige token JWT. Isso é proposital: exigir
autenticação para criar a primeira conta geraria um paradoxo (não há como
logar em uma conta que ainda não existe) — a analogia é abrir uma conta no
banco fisicamente, o que não exige já ser cliente. Essa liberação precisou
ser feita em dois pontos do Spring Security, que funcionam em camadas
independentes: no `JwtAuthFilter` (que decide se intercepta a requisição
pedindo token) e no `SecurityConfig` (que decide, via
`authorizeHttpRequests`, se a rota exige autenticação). Esquecer um dos dois
gera comportamentos diferentes: sem a exceção no filtro, o erro é 401 (token
ausente); sem a exceção no `SecurityConfig`, o erro é 403 (proibido, mesmo
sem token ser exigido pelo filtro) — foi exatamente esse segundo caso que
apareceu durante os testes.

### Perguntas

**1. Qual a diferença entre autenticação e autorização? Sua implementação verifica só uma das duas, ou as duas? Um usuário autenticado consegue sacar de uma conta que não é dele, na implementação atual?**

Autenticação responde "quem é você?" - confirma que a pessoa é realmente
quem diz ser (nesse sistema, provando que sabe a senha de uma conta e
recebendo um JWT válido assinado pelo servidor). Autorização responde "o
que você pode fazer?" - verifica se essa identidade já confirmada tem
permissão para a ação específica que está tentando executar.

Minha implementação verifica SÓ autenticação, não autorização. Testei isso
na prática: fiz login como a conta 0 (Ana), peguei o token dela, e usei
esse mesmo token para chamar POST /contas/3/sacar (conta da Carla, não da
Ana). A operação funcionou normalmente e sacou R$5 da conta da Carla - o
log confirma: [Lamport 3] SAQUE {id=3, valor=5.0, novoSaldo=5.0}.

O motivo é que o JwtAuthFilter só valida se existe um token e se ele é
criptograficamente válido (assinatura correta, não expirado) - ele nunca
compara o "subject" do token (o id da conta que fez login, extraído do
claims.getSubject()) com o :id que aparece na URL da requisição. Ou seja,
qualquer conta autenticada tem acesso irrestrito a qualquer outra conta na
mesma agência. Isso seria uma falha grave em um sistema real; a correção
seria adicionar uma checagem explícita nos controllers (ou um filtro
adicional) comparando o subject do token com o id da conta-alvo, rejeitando
com 403 quando forem diferentes.

**2. Por que o servidor não precisa consultar um banco de dados para validar a assinatura de um JWT a cada requisição? O que isso implica sobre escalabilidade, comparado a guardar sessões em memória no servidor?**

Porque o JWT carrega sua própria prova de validade dentro dele: a
assinatura HMAC (gerada com a chave secreta em JwtUtil.gerarToken) permite
que qualquer instância que conheça essa mesma chave verifique
matematicamente se o token não foi alterado, sem precisar perguntar a
ninguém "esse token ainda é válido?". O servidor só recalcula a assinatura
com a chave que já tem localmente (JwtUtil.validarToken) e compara - não
existe consulta a banco de dados nem a nenhum armazenamento externo nesse
processo.

Isso importa muito para escalabilidade porque, em um sistema com sessões em
memória (o modelo tradicional, com sessionId e um HttpSession guardado no
servidor), cada agência ficaria "amarrada" a saber quais sessões ela criou
- se uma requisição do mesmo usuário caísse em outra instância da agência
(em um cenário com múltiplas réplicas atrás de um load balancer, por
exemplo), essa segunda instância não teria a sessão e o usuário perderia o
login. Com JWT stateless (que é exatamente o que configurei em
SecurityConfig com SessionCreationPolicy.STATELESS), qualquer instância que
tenha a mesma chave secreta consegue validar o token de qualquer usuário,
sem precisar compartilhar estado entre elas. Isso é o que permite escalar
horizontalmente (adicionar mais instâncias da mesma agência) sem
complicação extra de sincronizar sessões.

**3. O que aconteceria com a segurança do sistema se a chave secreta usada para assinar o JWT vazasse?**

Seria uma falha crítica e completa. Como qualquer detentor da chave
consegue tanto validar quanto GERAR assinaturas válidas (é a mesma chave
usada nos dois sentidos, já que JwtUtil usa HMAC - um algoritmo simétrico),
alguém com a chave vazada poderia forjar um token JWT válido para
QUALQUER conta, sem nunca ter feito login de verdade e sem saber a senha
de ninguém. Bastaria montar um token com o "subject" da conta que quiser
(inclusive o token interno de agência-a-agência, usando
gerarTokenInterno(), que também usa a mesma chave) e assinar com a chave
vazada - o JwtAuthFilter aceitaria sem questionar, porque a assinatura
bateria matematicamente.

Isso é agravado pela falha de autorização que descobri na pergunta 1: como
o sistema também não verifica se o token corresponde à conta sendo
acessada, um invasor com a chave vazada não só conseguiria se autenticar
como qualquer conta, mas também teria acesso irrestrito a qualquer outra
conta da agência. Na prática, um vazamento da chave secreta comprometeria
a segurança inteira do sistema bancário. Por isso o application.properties
usa jwt.secret=${JWT_SECRET:...} - o valor padrão ali é só um placeholder
de desenvolvimento; em produção, essa variável de ambiente precisaria ser
configurada com um valor gerado aleatoriamente, mantido fora do controle
de versão, e rotacionado periodicamente.

---

## Parte G — Frontend

**1. Como o frontend "lembra" de reenviar o token em cada requisição depois do login? Descreva, em alto nível, o mecanismo implementado.**

O token retornado por `POST /auth/login` é salvo em `localStorage` (chave `iceibank_token`) e mantido também em memória no objeto `estado`. Toda chamada subsequente passa pela função central `chamarApi()`, que injeta automaticamente o cabeçalho `Authorization: Bearer <token>` quando `estado.token` existe — nenhuma tela precisa lidar com isso individualmente.

**2. Se o token expirar enquanto alguém está usando o frontend no meio de uma operação, o que acontece na implementação atual? A interface avisa a pessoa usuária, ou ela só vê um erro genérico?**

A interface avisa de forma clara, não é um erro genérico. A função central
`chamarApi()` em app.js intercepta especificamente o status HTTP 401: ao
detectar esse código, ela chama `limparSessao()` (removendo o token do
localStorage e do estado em memória) e lança um erro com a mensagem "Sua
sessão expirou. Faça login novamente." - essa mensagem é capturada pelo
bloco try/catch de cada ação (depósito, saque, transferência, etc.) e
exibida na caixa de mensagem vermelha correspondente daquela seção,
igual a qualquer outro erro de validação. Além disso, como `limparSessao()`
também dispara `renderSessao()`, a tela inteira volta para o estado
deslogado automaticamente, escondendo a área logada e mostrando de novo o
formulário de login - a pessoa não fica "presa" numa tela quebrada, é
redirecionada de volta para o login com uma explicação do que aconteceu.

**3. Esta unidade da disciplina trata de arquitetura MVC. No seu frontend, onde fica o "M" (Model), o "V" (View) e o "C" (Controller)? Eles existem de forma clara, ou o código ficou mais misturado do que o padrão sugere?**

Em app.js, dividi o código em três blocos comentados explicitamente:
MODEL (o objeto `estado`, mais a função `chamarApi()` que centraliza toda
comunicação com o backend), VIEW (as funções `render*` e `mostrarMensagem`/
`limparMensagem`, que só leem `estado` e mexem no DOM, sem chamar a API
diretamente) e CONTROLLER (os event listeners no final do arquivo, que
capturam a interação do usuário e orquestram Model e View).

Na prática, essa separação se sustenta razoavelmente bem para as operações
simples (ex.: `atualizarSaldo()` chama `chamarApi()` e depois `renderSaldo()`,
numa sequência clara), mas fica mais tênue no Controller: cada handler de
evento (ex.: o listener do botão "Transferir") mistura, no mesmo bloco,
tanto a leitura de inputs do DOM quanto a chamada à API quanto a atualização
da View - não existe uma camada de "Controller" isolada de verdade, tipo
uma classe separada. Num projeto maior, isso provavelmente precisaria de
uma refatoração para separar melhor "o que aciona a ação" de "como a ação
é executada" - mas para o tamanho e escopo deste sprint, a divisão em três
blocos comentados já deixa a lógica rastreável sem introduzir a
complexidade de um framework completo.

---

## Funcionalidade adicional (seção 2.1)

**Funcionalidade escolhida:** Histórico de transações por conta.

**O que ela faz:** um novo endpoint, `GET /contas/{id}/historico`, retorna a lista de todos os eventos registrados para uma conta específica (criação, depósitos, saques, débitos e créditos de transferência), lidos diretamente do arquivo de log `.jsonl` daquela agência. Cada evento retornado inclui o timestamp de Lamport, a hora de parede e os detalhes da operação.

**Por que essa:** o sistema já registra todo evento com riqueza de detalhes (Parte B), mas não havia nenhuma forma de consultar esses dados via API — só existiam como arquivo de log local ou agregados na linha do tempo geral (Parte E). Esse endpoint reaproveita a infraestrutura de eventos já implementada e entrega valor real: permite auditar o que aconteceu com uma conta específica sem precisar abrir o arquivo `.jsonl` manualmente.

**Evidência de teste:** ver `evidencias/sprint1/funcionalidade-adicional.png`.


