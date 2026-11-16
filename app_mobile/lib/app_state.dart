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

  bool get logado => token != null && idConta != null;

  /// Agencia onde a conta logada existe (particao id % 3).
  String get _urlDaConta => urlDaAgencia(agenciaDaConta(idConta!));

  Future<void> restaurarSessao() async {
    final prefs = await SharedPreferences.getInstance();
    token = prefs.getString(_chaveToken);
    idConta = prefs.getInt(_chaveConta);
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
    token = null;
    idConta = null;
    saldo = null;
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_chaveToken);
    await prefs.remove(_chaveConta);
    notifyListeners();
  }

  Future<void> atualizarSaldo() async {
    final conta = await _autenticado('/contas/$idConta');
    saldo = (conta['saldo'] as num).toDouble();
    notifyListeners();
  }

  Future<void> depositar(double valor) async {
    final conta = await _autenticado('/contas/$idConta/depositar', metodo: 'POST', corpo: {'valor': valor});
    saldo = (conta['saldo'] as num).toDouble();
    notifyListeners();
  }

  Future<void> sacar(double valor) async {
    final conta = await _autenticado('/contas/$idConta/sacar', metodo: 'POST', corpo: {'valor': valor});
    saldo = (conta['saldo'] as num).toDouble();
    notifyListeners();
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
