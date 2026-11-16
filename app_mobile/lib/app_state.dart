import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'api_client.dart';
import 'config.dart';

/// Estado da sessao e do saldo. A tela observa este objeto (ChangeNotifier).
class AppState extends ChangeNotifier {
  AppState({ApiClient? api}) : _api = api ?? ApiClient();

  final ApiClient _api;

  static const String _chaveToken = 'iceibank_token';
  static const String _chaveConta = 'iceibank_id_conta';

  int? idConta;
  String? token;
  double? saldo;

  /// true enquanto le a sessao salva no aparelho (ao abrir o app).
  bool restaurando = true;

  /// Mensagem para a tela de login (ex.: "sua sessao expirou").
  String? avisoLogin;

  /// Offline-first: ultimo vetor logico conhecido da agencia (devolvido em GET /contas/{id}).
  List<int>? vetor;

  /// Fila de operacoes feitas offline: {tipo: DEPOSITO|SAQUE, valor: double}.
  final List<Map<String, dynamic>> pendentes = [];

  /// Conflito (409) aguardando decisao da pessoa: {op, saldoAtual, vetorAtual}.
  Map<String, dynamic>? conflito;

  bool offline = false;
  bool sincronizando = false;
  String? avisoSync;
  Timer? _timer;

  bool get logado => token != null && idConta != null;

  /// Agencia onde a conta logada existe (particao id % 3).
  String get _urlDaConta => urlDaAgencia(agenciaDaConta(idConta!));

  String get _chaveCache => 'iceibank_cache_$idConta';

  Future<void> _salvarCache() async {
    if (idConta == null) return;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _chaveCache,
      jsonEncode({'saldo': saldo, 'vetor': vetor, 'pendentes': pendentes}),
    );
  }

  Future<void> _carregarCache() async {
    final prefs = await SharedPreferences.getInstance();
    final texto = prefs.getString(_chaveCache);
    pendentes.clear();
    if (texto == null) return;
    try {
      final m = jsonDecode(texto) as Map<String, dynamic>;
      saldo = (m['saldo'] as num?)?.toDouble();
      vetor = (m['vetor'] as List?)?.map((e) => (e as num).toInt()).toList();
      for (final p in (m['pendentes'] as List? ?? [])) {
        final mp = Map<String, dynamic>.from(p as Map);
        pendentes.add({'tipo': mp['tipo'], 'valor': (mp['valor'] as num).toDouble()});
      }
    } catch (_) {}
  }

  void _iniciarSincronizacaoAutomatica() {
    _timer?.cancel();
    // Tenta sincronizar a cada 5 s: ao voltar a conexao, a fila e enviada sozinha.
    _timer = Timer.periodic(const Duration(seconds: 5), (_) => sincronizar());
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> restaurarSessao() async {
    final prefs = await SharedPreferences.getInstance();
    token = prefs.getString(_chaveToken);
    idConta = prefs.getInt(_chaveConta);
    if (logado) {
      await _carregarCache();
      _iniciarSincronizacaoAutomatica();
    }
    restaurando = false;
    notifyListeners();
    if (logado) {
      try {
        await atualizarSaldo();
      } catch (_) {
        // sem rede agora: continua logado, o saldo aparece quando a agencia voltar
      }
    }
  }

  Future<void> login(int id, String senha) async {
    final dados = await _api.chamar(
      urlDaAgencia(agenciaDaConta(id)),
      '/auth/login',
      metodo: 'POST',
      corpo: {'id': id, 'senha': senha},
    );
    token = dados['token'] as String;
    idConta = id;
    avisoLogin = null;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_chaveToken, token!);
    await prefs.setInt(_chaveConta, id);
    await _carregarCache();
    _iniciarSincronizacaoAutomatica();
    notifyListeners();
    try {
      await atualizarSaldo();
    } catch (_) {
      // login ok; o saldo pode ser atualizado depois
    }
  }

  Future<void> criarConta(int id, String nome, String senha, double saldoInicial) async {
    await _api.chamar(
      urlDaAgencia(agenciaDaConta(id)),
      '/contas',
      metodo: 'POST',
      corpo: {'id': id, 'nomeAluno': nome, 'senha': senha, 'saldoInicial': saldoInicial},
    );
  }

  Future<void> logout() async {
    _timer?.cancel();
    avisoSync = null;
    pendentes.clear();
    conflito = null;
    vetor = null;
    token = null;
    idConta = null;
    saldo = null;
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_chaveToken);
    await prefs.remove(_chaveConta);
    notifyListeners();
  }

  Future<void> atualizarSaldo() async {
    try {
      final conta = await _autenticado('/contas/$idConta');
      offline = false;
      // Com operacoes pendentes, o saldo mostrado continua o otimista (local).
      if (pendentes.isEmpty) saldo = (conta['saldo'] as num).toDouble();
      vetor = (conta['vetorAtual'] as List).map((e) => (e as num).toInt()).toList();
      await _salvarCache();
      notifyListeners();
    } on ApiException catch (e) {
      if (e.semConexao) {
        offline = true;
        notifyListeners();
      }
      rethrow;
    }
  }

  /// Retorna true se a operacao ficou PENDENTE (sem conexao), false se foi aplicada na hora.
  Future<bool> depositar(double valor) => _operar('DEPOSITO', valor);

  Future<bool> sacar(double valor) => _operar('SAQUE', valor);

  Future<bool> _operar(String tipo, double valor) async {
    avisoSync = null;
    if (pendentes.isNotEmpty) {
      _enfileirar(tipo, valor); // mantem a ordem: nada passa na frente da fila
      return true;
    }
    final caminho = tipo == 'DEPOSITO' ? 'depositar' : 'sacar';
    try {
      final conta = await _autenticado('/contas/$idConta/$caminho', metodo: 'POST', corpo: {'valor': valor});
      saldo = (conta['saldo'] as num).toDouble();
      offline = false;
      notifyListeners();
      try {
        await atualizarSaldo(); // renova o vetor conhecido
      } catch (_) {}
      return false;
    } on ApiException catch (e) {
      if (e.semConexao) {
        offline = true;
        _enfileirar(tipo, valor);
        return true;
      }
      rethrow;
    }
  }

  /// UI otimista: aplica no saldo local e guarda na fila para sincronizar depois.
  void _enfileirar(String tipo, double valor) {
    final atual = saldo ?? 0;
    if (tipo == 'SAQUE' && atual < valor) {
      throw ApiException('Saldo insuficiente (saldo local, incluindo pendentes).');
    }
    saldo = tipo == 'DEPOSITO' ? atual + valor : atual - valor;
    pendentes.add({'tipo': tipo, 'valor': valor});
    _salvarCache();
    notifyListeners();
  }

  /// Envia a fila em ordem. Para no primeiro conflito (409) ou na primeira falha de rede.
  Future<void> sincronizar() async {
    if (!logado || sincronizando || pendentes.isEmpty || conflito != null) return;
    sincronizando = true;
    try {
      if (vetor == null) {
        await atualizarSaldo();
      }
      while (pendentes.isNotEmpty && vetor != null) {
        final op = pendentes.first;
        try {
          final conta = await _autenticado(
            '/contas/$idConta/sincronizar',
            metodo: 'POST',
            corpo: {'tipo': op['tipo'], 'valor': op['valor'], 'vetorConhecido': vetor},
          );
          pendentes.removeAt(0);
          vetor = (conta['vetorAtual'] as List).map((e) => (e as num).toInt()).toList();
          if (pendentes.isEmpty) saldo = (conta['saldo'] as num).toDouble();
          offline = false;
          avisoSync = 'Operação pendente sincronizada com sucesso.';
          await _salvarCache();
          notifyListeners();
        } on ApiException catch (e) {
          if (e.semConexao) {
            offline = true;
            notifyListeners();
            break;
          }
          if (e.status == 409 && e.dados != null) {
            final atual = e.dados!['contaAtual'] as Map?;
            conflito = {
              'op': op,
              'saldoAtual': (atual?['saldo'] as num?)?.toDouble(),
              'vetorAtual': (e.dados!['vetorAtual'] as List).map((x) => (x as num).toInt()).toList(),
            };
            notifyListeners();
            break;
          }
          // 400 (ex.: saldo insuficiente no servidor): descarta a operacao e avisa.
          pendentes.removeAt(0);
          avisoSync = 'Operação pendente recusada pela agência: ${e.mensagem}';
          await _salvarCache();
          notifyListeners();
        }
      }
    } finally {
      sincronizando = false;
    }
  }

  /// Decisao da pessoa apos um 409: aplicar mesmo assim (usa o vetor atual) ou descartar.
  Future<void> resolverConflito({required bool aplicar}) async {
    final c = conflito;
    if (c == null) return;
    conflito = null;
    vetor = List<int>.from(c['vetorAtual'] as List);
    if (aplicar) {
      avisoSync = 'Conflito resolvido: operação reenviada com o estado atual da conta.';
      notifyListeners();
      await sincronizar();
    } else {
      pendentes.removeAt(0);
      // volta ao saldo real da agencia e reaplica o que ainda esta pendente
      var s = (c['saldoAtual'] as double?) ?? saldo ?? 0;
      for (final p in pendentes) {
        s += p['tipo'] == 'DEPOSITO' ? (p['valor'] as double) : -(p['valor'] as double);
      }
      saldo = s;
      avisoSync = 'Operação pendente descartada. Saldo atualizado com o valor real da conta.';
      await _salvarCache();
      notifyListeners();
    }
  }

  // ---------------- Modo de teste ----------------

  bool get simulandoOffline => ApiClient.simularOffline;

  /// Liga/desliga a "queda de rede" simulada. Ao desligar, sincroniza na hora.
  void definirSimularOffline(bool valor) {
    ApiClient.simularOffline = valor;
    offline = valor;
    notifyListeners();
    if (!valor) sincronizar();
  }

  /// Faz um deposito direto na agencia (fora da fila), como se outro aparelho
  /// tivesse mexido na conta: o vetor da agencia avanca e a proxima sincronizacao da 409.
  Future<void> simularMudancaExterna(double valor) async {
    await _api.chamar(
      _urlDaConta,
      '/contas/$idConta/depositar',
      metodo: 'POST',
      corpo: {'valor': valor},
      token: token,
      ignorarSimulacao: true,
    );
    avisoSync = 'Mudança externa simulada: depósito direto de R\$ ${valor.toStringAsFixed(2)} na agência. '
        'O vetor da agência avançou.';
    notifyListeners();
  }

  /// Cria as contas 0, 1 e 2 (senha 123, saldo 100) - uma por agencia.
  Future<String> criarContasDeTeste() async {
    final criadas = <int>[];
    final jaExistiam = <int>[];
    for (var id = 0; id < numeroAgencias; id++) {
      try {
        await criarConta(id, 'Conta $id', '123', 100);
        criadas.add(id);
      } on ApiException catch (e) {
        if (e.status == 409) {
          jaExistiam.add(id);
        } else {
          rethrow;
        }
      }
    }
    return 'Criadas: ${criadas.isEmpty ? 'nenhuma' : criadas.join(', ')}'
        '${jaExistiam.isEmpty ? '' : ' · já existiam: ${jaExistiam.join(', ')}'} (senha 123, saldo R\$ 100).';
  }

  /// Transferencia local ou entre agencias (a propria agencia decide pelo id de destino).
  Future<String> transferir(int idDestino, double valor) async {
    final resultado = await _autenticado(
      '/transferencias',
      metodo: 'POST',
      corpo: {'idOrigem': idConta, 'idDestino': idDestino, 'valor': valor},
    );
    try {
      await atualizarSaldo();
    } catch (_) {
      // a transferencia ja foi feita; o saldo e atualizado na proxima consulta
    }
    return (resultado['mensagem'] as String?) ?? 'Transferência realizada.';
  }

  /// Chamada autenticada: se a API devolver 401 (token expirado), desloga e
  /// volta para o login com um aviso.
  Future<Map<String, dynamic>> _autenticado(
    String caminho, {
    String metodo = 'GET',
    Map<String, dynamic>? corpo,
  }) async {
    try {
      return await _api.chamar(_urlDaConta, caminho, metodo: metodo, corpo: corpo, token: token);
    } on ApiException catch (e) {
      if (e.sessaoExpirada) {
        avisoLogin = e.mensagem;
        await logout();
      }
      rethrow;
    }
  }
}
