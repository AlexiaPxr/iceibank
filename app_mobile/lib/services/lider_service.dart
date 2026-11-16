import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import '../config.dart';

/// Consulta periodicamente GET /bully/lider para o painel de lideranca.
///
/// - Pergunta a uma agencia; se ela nao responder (fora do ar / timeout), tenta
///   as outras antes de desistir.
/// - Continua usando a ultima agencia que respondeu, para nao ficar alternando
///   entre agencias que podem discordar durante uma eleicao.
class LiderService extends ChangeNotifier {
  LiderService({http.Client? cliente, this.intervalo = const Duration(seconds: 3)})
      : _http = cliente ?? http.Client();

  final http.Client _http;
  final Duration intervalo;
  static const Duration _timeoutPorAgencia = Duration(seconds: 2);

  Timer? _timer;
  bool _consultando = false;
  bool _descartado = false;
  int _agenciaPreferida = 0;

  /// Lider segundo a agencia consultada (null = eleicao em andamento).
  int? lider;

  /// Agencia que respondeu a ultima consulta.
  int? agenciaConsultada;

  /// true quando NENHUMA das agencias respondeu na ultima consulta.
  bool semResposta = false;

  /// false ate a primeira consulta terminar.
  bool jaConsultou = false;

  DateTime? atualizadoEm;

  void iniciar() {
    _timer?.cancel();
    consultar();
    _timer = Timer.periodic(intervalo, (_) => consultar());
  }

  void parar() {
    _timer?.cancel();
    _timer = null;
  }

  /// Agencias que responderam na ultima consulta (indicador de agencias ativas).
  final Map<int, bool> ativas = {for (var i = 0; i < numeroAgencias; i++) i: false};

  int get quantidadeAtivas => ativas.values.where((v) => v).length;

  Future<void> consultar() async {
    if (_consultando) {
      return; // evita consultas sobrepostas quando uma agencia demora
    }
    _consultando = true;
    try {
      // Pergunta as 3 agencias em paralelo: alem do lider, descobre quais estao no ar.
      final respostas = await Future.wait([
        for (var id = 0; id < numeroAgencias; id++) _perguntar(id),
      ]);
      for (var id = 0; id < numeroAgencias; id++) {
        ativas[id] = respostas[id] != null;
      }

      // Continua usando a ultima agencia que respondeu, se ela ainda estiver no ar.
      int? escolhida;
      for (var i = 0; i < numeroAgencias; i++) {
        final id = (_agenciaPreferida + i) % numeroAgencias;
        if (respostas[id] != null) {
          escolhida = id;
          break;
        }
      }

      if (escolhida != null) {
        _agenciaPreferida = escolhida;
        lider = respostas[escolhida]!.lider;
        agenciaConsultada = escolhida;
        semResposta = false;
      } else {
        semResposta = true;
      }
      jaConsultou = true;
      atualizadoEm = DateTime.now();
      _avisar();
    } finally {
      _consultando = false;
    }
  }

  Future<_RespostaLider?> _perguntar(int idAgencia) async {
    try {
      final uri = Uri.parse('${urlDaAgencia(idAgencia)}/bully/lider');
      final resposta = await _http.get(uri).timeout(_timeoutPorAgencia);
      if (resposta.statusCode < 200 || resposta.statusCode >= 300) {
        return null;
      }
      final dados = jsonDecode(utf8.decode(resposta.bodyBytes)) as Map<String, dynamic>;
      final liderAtual = dados['liderAtual'];
      return _RespostaLider(liderAtual is int ? liderAtual : null);
    } catch (_) {
      return null; // fora do ar, timeout ou resposta invalida
    }
  }

  void _avisar() {
    if (!_descartado) {
      notifyListeners();
    }
  }

  @override
  void dispose() {
    _descartado = true;
    parar();
    _http.close();
    super.dispose();
  }
}

class _RespostaLider {
  const _RespostaLider(this.lider);
  final int? lider;
}
