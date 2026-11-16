import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

/// Erro de uma chamada a API, ja com mensagem pronta para mostrar na tela.
class ApiException implements Exception {
  ApiException(this.mensagem,
      {this.status, this.semConexao = false, this.sessaoExpirada = false, this.dados});

  final String mensagem;
  final int? status;

  /// Corpo JSON da resposta de erro (ex.: vetorAtual e contaAtual de um 409).
  final Map<String, dynamic>? dados;

  /// Agencia fora do ar, sem rede ou timeout (nao chegou resposta HTTP).
  final bool semConexao;

  /// 401 numa chamada que enviou token: a sessao acabou.
  final bool sessaoExpirada;

  @override
  String toString() => mensagem;
}

/// Wrapper unico para as chamadas HTTP (mesmo papel do chamarApi do frontend web):
/// coloca o token no header e traduz os erros da API ({"erro": "..."}) em ApiException.
class ApiClient {
  ApiClient({http.Client? cliente}) : _http = cliente ?? http.Client();

  final http.Client _http;
  static const Duration _timeout = Duration(seconds: 8);

  /// Modo de teste: finge que a rede caiu (todas as chamadas falham como "sem conexao").
  static bool simularOffline = false;

  Future<Map<String, dynamic>> chamar(
    String baseUrl,
    String caminho, {
    String metodo = 'GET',
    Map<String, dynamic>? corpo,
    String? token,
    bool ignorarSimulacao = false,
  }) async {
    if (simularOffline && !ignorarSimulacao) {
      throw ApiException('Modo offline simulado: sem conexão com a agência.', semConexao: true);
    }
    final uri = Uri.parse('$baseUrl$caminho');
    final headers = <String, String>{
      'Content-Type': 'application/json',
      if (token != null) 'Authorization': 'Bearer $token',
    };

    http.Response resposta;
    try {
      if (metodo == 'POST') {
        resposta = await _http.post(uri, headers: headers, body: jsonEncode(corpo ?? {})).timeout(_timeout);
      } else {
        resposta = await _http.get(uri, headers: headers).timeout(_timeout);
      }
    } on TimeoutException {
      throw ApiException('A agência demorou demais para responder.', semConexao: true);
    } catch (_) {
      throw ApiException('Não foi possível contatar a agência. Ela está no ar?', semConexao: true);
    }

    dynamic dados = <String, dynamic>{};
    if (resposta.bodyBytes.isNotEmpty) {
      try {
        dados = jsonDecode(utf8.decode(resposta.bodyBytes));
      } catch (_) {
        dados = <String, dynamic>{};
      }
    }
    final mapa = dados is Map ? Map<String, dynamic>.from(dados) : <String, dynamic>{};

    if (resposta.statusCode == 401 && token != null) {
      throw ApiException('Sua sessão expirou. Faça login novamente.', status: 401, sessaoExpirada: true);
    }
    if (resposta.statusCode < 200 || resposta.statusCode >= 300) {
      final erro = mapa['erro'];
      throw ApiException(
        erro is String ? erro : 'Erro inesperado (HTTP ${resposta.statusCode}).',
        status: resposta.statusCode,
        dados: mapa,
      );
    }
    return mapa;
  }
}
