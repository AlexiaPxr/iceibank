import 'package:flutter/material.dart';

import '../api_client.dart';
import '../app_state.dart';
import '../config.dart';
import '../services/lider_service.dart';
import '../utils.dart';
import '../widgets/mensagem_box.dart';
import '../widgets/painel_lider.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.app, required this.lider});

  final AppState app;
  final LiderService lider;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final _valorOperacao = TextEditingController();
  final _destino = TextEditingController();
  final _valorTransferencia = TextEditingController();

  final Map<String, Mensagem> _mensagens = {};
  final Set<String> _ocupados = {};

  @override
  void initState() {
    super.initState();
    widget.lider.iniciar(); // painel de lideranca atualizando sozinho
  }

  @override
  void dispose() {
    widget.lider.parar();
    _valorOperacao.dispose();
    _destino.dispose();
    _valorTransferencia.dispose();
    super.dispose();
  }

  double? _lerValor(TextEditingController controlador) {
    final valor = double.tryParse(controlador.text.trim().replaceAll(',', '.'));
    return (valor == null || valor <= 0) ? null : valor;
  }

  /// Executa uma acao da API controlando "carregando" e a mensagem de cada cartao.
  Future<void> _executar(String area, Future<String?> Function() acao) async {
    setState(() {
      _ocupados.add(area);
      _mensagens.remove(area);
    });
    try {
      final texto = await acao();
      if (!mounted) return;
      if (texto != null) {
        setState(() => _mensagens[area] = Mensagem(texto, false));
      }
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() => _mensagens[area] = Mensagem(e.mensagem, true));
    } finally {
      if (mounted) setState(() => _ocupados.remove(area));
    }
  }

  void _erro(String area, String texto) {
    setState(() => _mensagens[area] = Mensagem(texto, true));
  }

  void _operar({required bool deposito}) {
    final valor = _lerValor(_valorOperacao);
    if (valor == null) {
      _erro('operacao', 'Informe um valor maior que zero.');
      return;
    }
    _executar('operacao', () async {
      if (deposito) {
        await widget.app.depositar(valor);
        return 'Depósito realizado com sucesso.';
      }
      await widget.app.sacar(valor);
      return 'Saque realizado com sucesso.';
    });
  }

  void _transferir() {
    final destino = int.tryParse(_destino.text.trim());
    final valor = _lerValor(_valorTransferencia);
    if (destino == null || valor == null) {
      _erro('transferencia', 'Informe a conta de destino e um valor maior que zero.');
      return;
    }
    _executar('transferencia', () => widget.app.transferir(destino, valor));
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.app,
      builder: (context, _) {
        final app = widget.app;
        final saldo = app.saldo;
        return Scaffold(
          appBar: AppBar(
            title: Text('Conta #${app.idConta}'),
            actions: [
              IconButton(
                tooltip: 'Sair',
                icon: const Icon(Icons.logout),
                onPressed: () => app.logout(),
              ),
            ],
          ),
          body: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              PainelLider(servico: widget.lider),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Saldo', style: Theme.of(context).textTheme.titleMedium),
                      const SizedBox(height: 4),
                      Text(
                        saldo == null ? '—' : formatarReais(saldo),
                        style: Theme.of(context).textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.bold),
                      ),
                      Text(
                        'Agência ${agenciaDaConta(app.idConta!)}',
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                      const SizedBox(height: 8),
                      OutlinedButton.icon(
                        onPressed: _ocupados.contains('saldo') ? null : () => _executar('saldo', () async {
                          await app.atualizarSaldo();
                          return null;
                        }),
                        icon: const Icon(Icons.refresh),
                        label: const Text('Atualizar saldo'),
                      ),
                      MensagemBox(_mensagens['saldo']),
                    ],
                  ),
                ),
              ),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Text('Depósito / Saque', style: Theme.of(context).textTheme.titleMedium),
                      const SizedBox(height: 12),
                      TextField(
                        controller: _valorOperacao,
                        keyboardType: const TextInputType.numberWithOptions(decimal: true),
                        decoration: const InputDecoration(labelText: 'Valor (R\$)', border: OutlineInputBorder()),
                      ),
                      const SizedBox(height: 12),
                      Row(
                        children: [
                          Expanded(
                            child: FilledButton(
                              onPressed: _ocupados.contains('operacao') ? null : () => _operar(deposito: true),
                              child: const Text('Depositar'),
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: OutlinedButton(
                              onPressed: _ocupados.contains('operacao') ? null : () => _operar(deposito: false),
                              child: const Text('Sacar'),
                            ),
                          ),
                        ],
                      ),
                      MensagemBox(_mensagens['operacao']),
                    ],
                  ),
                ),
              ),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Text('Transferência', style: Theme.of(context).textTheme.titleMedium),
                      const SizedBox(height: 12),
                      TextField(
                        controller: _destino,
                        keyboardType: TextInputType.number,
                        decoration: const InputDecoration(
                          labelText: 'Conta de destino',
                          helperText: 'Local ou de outra agência',
                          border: OutlineInputBorder(),
                        ),
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: _valorTransferencia,
                        keyboardType: const TextInputType.numberWithOptions(decimal: true),
                        decoration: const InputDecoration(labelText: 'Valor (R\$)', border: OutlineInputBorder()),
                      ),
                      const SizedBox(height: 12),
                      FilledButton(
                        onPressed: _ocupados.contains('transferencia') ? null : _transferir,
                        child: const Text('Transferir'),
                      ),
                      MensagemBox(_mensagens['transferencia']),
                    ],
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}
