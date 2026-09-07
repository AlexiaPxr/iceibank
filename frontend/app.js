/* ==========================================================================
   ICEIBank - Frontend (Parte G)
   Organização inspirada em MVC, mesmo sem framework:
     - MODEL:      objeto `estado` + funções de acesso à API (chamarApi)
     - VIEW:       funções `render*` que só leem `estado` e mexem no DOM
     - CONTROLLER: os event listeners no final do arquivo, que orquestram
                    Model e View a partir da interação do usuário
   ========================================================================== */

// ---------- MODEL ----------
const estado = {
  urlAgencia: "http://localhost:4000",
  token: localStorage.getItem("iceibank_token") || null,
  idConta: localStorage.getItem("iceibank_id_conta")
    ? parseInt(localStorage.getItem("iceibank_id_conta"), 10)
    : null,
  saldo: null,
};

function estaLogado() {
  return !!estado.token;
}

function salvarSessao(token, idConta) {
  estado.token = token;
  estado.idConta = idConta;
  localStorage.setItem("iceibank_token", token);
  localStorage.setItem("iceibank_id_conta", String(idConta));
}

function limparSessao() {
  estado.token = null;
  estado.idConta = null;
  estado.saldo = null;
  localStorage.removeItem("iceibank_token");
  localStorage.removeItem("iceibank_id_conta");
}

// Wrapper único para todas as chamadas à API - inclui o token automaticamente
// quando existe, e trata o 401 (token ausente/expirado) de forma centralizada.
async function chamarApi(caminho, { method = "GET", body } = {}) {
  const headers = { "Content-Type": "application/json" };
  if (estado.token) {
    headers["Authorization"] = `Bearer ${estado.token}`;
  }

  let resposta;
  try {
    resposta = await fetch(`${estado.urlAgencia}${caminho}`, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch (erroDeRede) {
    throw new Error("Não foi possível contatar a agência. Ela está rodando nessa URL?");
  }

  const dados = await resposta.json().catch(() => ({}));

  if (resposta.status === 401) {
    // Sessão expirada ou token ausente: desloga e avisa a pessoa usuária.
    limparSessao();
    renderSessao();
    throw new Error("Sua sessão expirou. Faça login novamente.");
  }

  if (!resposta.ok) {
    throw new Error(dados.erro || `Erro inesperado (HTTP ${resposta.status}).`);
  }

  return dados;
}

// ---------- VIEW ----------
function mostrarMensagem(elementoId, texto, tipo) {
  const el = document.getElementById(elementoId);
  el.textContent = texto;
  el.className = `mensagem ${tipo}`;
}

function limparMensagem(elementoId) {
  const el = document.getElementById(elementoId);
  el.className = "mensagem";
  el.textContent = "";
}

function renderSessao() {
  const tag = document.getElementById("tagSessao");
  const info = document.getElementById("infoConta");
  const btnSair = document.getElementById("btnSair");
  const painelLogin = document.getElementById("painelLogin");
  const areaLogada = document.getElementById("areaLogada");

  if (estaLogado()) {
    tag.textContent = "logado";
    tag.className = "tag logado";
    info.textContent = `conta #${estado.idConta}`;
    btnSair.classList.remove("escondido");
    painelLogin.classList.add("escondido");
    areaLogada.classList.remove("escondido");
  } else {
    tag.textContent = "deslogado";
    tag.className = "tag deslogado";
    info.textContent = "";
    btnSair.classList.add("escondido");
    painelLogin.classList.remove("escondido");
    areaLogada.classList.add("escondido");
  }
}

function renderSaldo(saldo) {
  estado.saldo = saldo;
  document.getElementById("saldoAtual").textContent =
    saldo === null ? "—" : `R$ ${Number(saldo).toFixed(2)}`;
}

function renderHistorico(eventos) {
  const container = document.getElementById("listaHistorico");
  if (!eventos.length) {
    container.innerHTML = "<p style='color:#667085; font-size:0.85rem;'>Nenhum evento encontrado ainda.</p>";
    return;
  }
  container.innerHTML = eventos
    .map(
      (ev) => `
      <div class="evento">
        <span class="tipo">${ev.tipo}</span> — Lamport ${ev.timestampLamport}
        <div class="meta">${ev.horaParede} · ${JSON.stringify(ev.detalhes)}</div>
      </div>`
    )
    .join("");
}

// ---------- CONTROLLER ----------

// Configuração da agência (seletor + campo manual)
const seletorUrl = document.getElementById("urlAgencia");
const inputUrlCustom = document.getElementById("urlAgenciaCustom");

seletorUrl.addEventListener("change", () => {
  if (seletorUrl.value === "custom") {
    inputUrlCustom.classList.remove("escondido");
    estado.urlAgencia = inputUrlCustom.value || estado.urlAgencia;
  } else {
    inputUrlCustom.classList.add("escondido");
    estado.urlAgencia = seletorUrl.value;
  }
});
inputUrlCustom.addEventListener("input", () => {
  estado.urlAgencia = inputUrlCustom.value;
});

// Login
document.getElementById("btnLogin").addEventListener("click", async () => {
  limparMensagem("msgLogin");
  const id = parseInt(document.getElementById("loginId").value, 10);
  const senha = document.getElementById("loginSenha").value;

  try {
    const dados = await chamarApi("/auth/login", { method: "POST", body: { id, senha } });
    salvarSessao(dados.token, id);
    renderSessao();
    await atualizarSaldo();
  } catch (erro) {
    mostrarMensagem("msgLogin", erro.message, "erro");
  }
});

// Criar conta
document.getElementById("btnCriar").addEventListener("click", async () => {
  limparMensagem("msgCriar");
  const id = parseInt(document.getElementById("criarId").value, 10);
  const nomeAluno = document.getElementById("criarNome").value;
  const senha = document.getElementById("criarSenha").value;
  const saldoInicial = parseFloat(document.getElementById("criarSaldo").value || "0");

  try {
    await chamarApi("/contas", { method: "POST", body: { id, nomeAluno, senha, saldoInicial } });
    mostrarMensagem("msgCriar", `Conta ${id} criada com sucesso. Você já pode fazer login.`, "sucesso");
  } catch (erro) {
    mostrarMensagem("msgCriar", erro.message, "erro");
  }
});

// Logout
document.getElementById("btnSair").addEventListener("click", () => {
  limparSessao();
  renderSessao();
});

// Saldo
async function atualizarSaldo() {
  limparMensagem("msgSaldo");
  try {
    const conta = await chamarApi(`/contas/${estado.idConta}`);
    renderSaldo(conta.saldo);
  } catch (erro) {
    mostrarMensagem("msgSaldo", erro.message, "erro");
  }
}
document.getElementById("btnAtualizarSaldo").addEventListener("click", atualizarSaldo);

// Depósito / Saque
document.getElementById("btnDepositar").addEventListener("click", () => operacao("depositar"));
document.getElementById("btnSacar").addEventListener("click", () => operacao("sacar"));

async function operacao(tipo) {
  limparMensagem("msgOperacao");
  const valor = parseFloat(document.getElementById("valorOperacao").value);
  try {
    const conta = await chamarApi(`/contas/${estado.idConta}/${tipo}`, { method: "POST", body: { valor } });
    renderSaldo(conta.saldo);
    mostrarMensagem(
      "msgOperacao",
      tipo === "depositar" ? "Depósito realizado com sucesso." : "Saque realizado com sucesso.",
      "sucesso"
    );
  } catch (erro) {
    mostrarMensagem("msgOperacao", erro.message, "erro");
  }
}

// Transferência
document.getElementById("btnTransferir").addEventListener("click", async () => {
  limparMensagem("msgTransferencia");
  const idDestino = parseInt(document.getElementById("transfDestino").value, 10);
  const valor = parseFloat(document.getElementById("transfValor").value);

  try {
    const resultado = await chamarApi("/transferencias", {
      method: "POST",
      body: { idOrigem: estado.idConta, idDestino, valor },
    });
    mostrarMensagem("msgTransferencia", resultado.mensagem, "sucesso");
    await atualizarSaldo();
  } catch (erro) {
    mostrarMensagem("msgTransferencia", erro.message, "erro");
  }
});

// Histórico (funcionalidade adicional)
document.getElementById("btnHistorico").addEventListener("click", async () => {
  limparMensagem("msgHistorico");
  try {
    const eventos = await chamarApi(`/contas/${estado.idConta}/historico`);
    renderHistorico(eventos);
  } catch (erro) {
    mostrarMensagem("msgHistorico", erro.message, "erro");
  }
});

// Inicialização
renderSessao();
if (estaLogado()) {
  atualizarSaldo();
}
