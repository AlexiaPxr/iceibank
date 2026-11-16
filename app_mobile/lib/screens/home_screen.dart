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
    widget.app.addListener(_verificarConflito);
  }

  bool _dialogoAberto = false;

  void _verificarConflito() {
    if (_dialogoAberto || widget.app.conflito == null || !mounted) return;
    _dialogoAberto = true;
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) {
        _dialogoAberto = false;
        return;
      }
      final c = widget.app.conflito;
      if (c == null) {
        _dialogoAberto = false;
        return;
      }
      final op = c['op'] as Map<String, dynamic>;
      final saldoAtual = c['saldoAtual'] as double?;
      final aplicar = await showDialog<bool>(
        context: context,
        barrierDismissible: false,
        builder: (ctx) => AlertDialog(
          icon: const Icon(Icons.warning_amber, color: Colors.orange),
          title: const Text('Conflito de sincronização (409)'),
          content: Text(
            'O estado da conta mudou enquanto você estava offline.\n\n'
            'Operação pendente: ${op['tipo']} de ${formatarReais((op['valor'] as num).toDouble())}\n'
            'Saldo real atual na agência: ${saldoAtual == null ? '—' : formatarReais(saldoAtual)}\n\n'
            'O que deseja fazer?',
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Descartar operação')),
            FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Aplicar mesmo assim')),
          ],
        ),
      );
      _dialogoAberto = false;
      await widget.app.resolverConflito(aplicar: aplicar ?? false);
    });
  }

  @override
  void dispose() {
    widget.app.removeListener(_verificarConflito);
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
      final pendente = deposito ? await widget.app.depositar(valor) : await widget.app.sacar(valor);
      if (pendente) {
        return 'Sem conexão: operação salva como PENDENTE. Será sincronizada automaticamente.';
      }
      return deposito ? 'Depósito realizado com sucesso.' : 'Saque realizado com sucesso.';
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

  int _aba = 0;

  Widget _titulo(BuildContext context, IconData icone, String texto) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        children: [
          Icon(icone, color: Theme.of(context).colorScheme.primary),
          const SizedBox(width: 8),
          Text(texto, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
        ],
      ),
    );
  }

  /// Corpo centralizado com largura maxima (fica bonito no Chrome e no celular).
  Widget _pagina(List<Widget> filhos) {
    return Align(
      alignment: Alignment.topCenter,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 640),
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
          children: filhos,
        ),
      ),
    );
  }

  Widget _cartaoSaldo(BuildContext context, AppState app) {
    final saldo = app.saldo;
    final cores = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.all(22),
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(22),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [cores.primary, const Color(0xFF1E88E5)],
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.account_balance_wallet, color: Colors.white70, size: 20),
              const SizedBox(width: 8),
              const Text('Saldo disponível', style: TextStyle(color: Colors.white70)),
              const Spacer(),
              IconButton(
                tooltip: 'Atualizar saldo',
                color: Colors.white,
                onPressed: _ocupados.contains('saldo')
                    ? null
                    : () => _executar('saldo', () async {
                          await app.atualizarSaldo();
                          return null;
                        }),
                icon: _ocupados.contains('saldo')
                    ? const SizedBox(
                        height: 18,
                        width: 18,
                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                      )
                    : const Icon(Icons.refresh),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            saldo == null ? '—' : formatarReais(saldo),
            style: const TextStyle(color: Colors.white, fontSize: 38, fontWeight: FontWeight.w800),
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 6,
            children: [
              _selo('Conta #${app.idConta}', Icons.person),
              _selo('Agência ${agenciaDaConta(app.idConta!)}', Icons.apartment),
              if (app.pendentes.isNotEmpty) _selo('saldo inclui pendentes', Icons.hourglass_top, destaque: true),
            ],
          ),
          MensagemBox(_mensagens['saldo']),
        ],
      ),
    );
  }

  Widget _selo(String texto, IconData icone, {bool destaque = false}) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: destaque ? Colors.orange.shade300 : Colors.white.withAlpha(40),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icone, size: 14, color: destaque ? Colors.black87 : Colors.white),
          const SizedBox(width: 4),
          Text(texto, style: TextStyle(fontSize: 12, color: destaque ? Colors.black87 : Colors.white)),
        ],
      ),
    );
  }

  Widget _bannerStatus(AppState app) {
    if (!(app.offline || app.pendentes.isNotEmpty || app.avisoSync != null)) {
      return const SizedBox.shrink();
    }
    final alerta = app.offline || app.pendentes.isNotEmpty;
    final cor = alerta ? Colors.orange : Colors.green;
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: cor.shade50,
        border: Border.all(color: cor.shade300),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (app.offline)
            Row(
              children: [
                Icon(Icons.cloud_off, color: cor.shade800, size: 20),
                const SizedBox(width: 8),
                Expanded(
                  child: Text('OFFLINE — usando dados salvos no aparelho',
                      style: TextStyle(fontWeight: FontWeight.bold, color: cor.shade900)),
                ),
              ],
            ),
          if (app.pendentes.isNotEmpty) ...[
            const SizedBox(height: 6),
            Text('⏳ ${app.pendentes.length} operação(ões) PENDENTE(S) de sincronização',
                style: const TextStyle(fontWeight: FontWeight.bold)),
            for (final p in app.pendentes)
              Text('• ${p['tipo']} ${formatarReais((p['valor'] as num).toDouble())} (aguardando)'),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              onPressed: app.sincronizando ? null : () => app.sincronizar(),
              icon: const Icon(Icons.sync),
              label: const Text('Sincronizar agora'),
            ),
          ],
          if (app.avisoSync != null) ...[
            const SizedBox(height: 6),
            Text('✔ ${app.avisoSync}'),
          ],
        ],
      ),
    );
  }

  Widget _abaInicio(BuildContext context, AppState app) {
    return _pagina([
      _cartaoSaldo(context, app),
      _bannerStatus(app),
      PainelLider(servico: widget.lider),
    ]);
  }

  Widget _abaOperacoes(BuildContext context, AppState app) {
    return _pagina([
      Card(
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _titulo(context, Icons.payments, 'Depósito / Saque'),
              TextField(
                controller: _valorOperacao,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                decoration: const InputDecoration(labelText: 'Valor (R\$)', prefixIcon: Icon(Icons.attach_money)),
              ),
              const SizedBox(height: 10),
              Wrap(
                spacing: 8,
                children: [
                  for (final v in const [10, 50, 100])
                    ActionChip(
                      label: Text('R\$ $v'),
                      onPressed: () => setState(() => _valorOperacao.text = '$v'),
                    ),
                ],
              ),
              const SizedBox(height: 14),
              Row(
                children: [
                  Expanded(
                    child: FilledButton.icon(
                      onPressed: _ocupados.contains('operacao') ? null : () => _operar(deposito: true),
                      icon: const Icon(Icons.arrow_downward),
                      label: const Text('Depositar'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _ocupados.contains('operacao') ? null : () => _operar(deposito: false),
                      icon: const Icon(Icons.arrow_upward),
                      label: const Text('Sacar'),
                    ),
                  ),
                ],
              ),
              MensagemBox(_mensagens['operacao']),
            ],
          ),
        ),
      ),
      const SizedBox(height: 4),
      Card(
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _titulo(context, Icons.swap_horiz, 'Transferência'),
              TextField(
                controller: _destino,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(
                  labelText: 'Conta de destino',
                  helperText: 'Local ou de outra agência',
                  prefixIcon: Icon(Icons.person_outline),
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _valorTransferencia,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                decoration: const InputDecoration(labelText: 'Valor (R\$)', prefixIcon: Icon(Icons.attach_money)),
              ),
              const SizedBox(height: 14),
              FilledButton.icon(
                onPressed: _ocupados.contains('transferencia') ? null : _transferir,
                icon: const Icon(Icons.send),
                label: const Text('Transferir'),
              ),
              MensagemBox(_mensagens['transferencia']),
            ],
          ),
        ),
      ),
    ]);
  }

  Widget _abaTeste(BuildContext context, AppState app) {
    return _pagina([
      Card(
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _titulo(context, Icons.science, 'Modo de teste'),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Simular offline'),
                subtitle: const Text('Corta a conexão com as agências dentro do app (sem F12)'),
                value: app.simulandoOffline,
                onChanged: (v) => app.definirSimularOffline(v),
              ),
              const Divider(),
              const Text('Para provocar um conflito (409):', style: TextStyle(fontWeight: FontWeight.w600)),
              const SizedBox(height: 6),
              const Text('1) Ligue "Simular offline" e faça um depósito (fica PENDENTE).\n'
                  '2) Toque em "Simular mudança externa": um depósito direto na agência faz o vetor avançar.\n'
                  '3) Desligue "Simular offline": a sincronização recebe 409 e abre o diálogo de conflito.'),
              const SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: _ocupados.contains('teste')
                    ? null
                    : () => _executar('teste', () async {
                          await app.simularMudancaExterna(50);
                          return 'Depósito externo de R\$ 50 feito direto na agência.';
                        }),
                icon: const Icon(Icons.bolt),
                label: const Text('Simular mudança externa (+R\$ 50)'),
              ),
              MensagemBox(_mensagens['teste']),
            ],
          ),
        ),
      ),
    ]);
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.app,
      builder: (context, _) {
        final app = widget.app;
        return Scaffold(
          appBar: AppBar(
            title: const Text('ICEIBank', style: TextStyle(fontWeight: FontWeight.w800)),
            actions: [
              Tooltip(
                message: 'Simular queda de rede',
                child: Row(
                  children: [
                    Icon(app.simulandoOffline ? Icons.wifi_off : Icons.wifi, size: 18),
                    Switch(
                      value: app.simulandoOffline,
                      onChanged: (v) => app.definirSimularOffline(v),
                    ),
                  ],
                ),
              ),
              IconButton(
                tooltip: 'Sair',
                icon: const Icon(Icons.logout),
                onPressed: () => app.logout(),
              ),
            ],
          ),
          body: IndexedStack(
            index: _aba,
            children: [
              _abaInicio(context, app),
              _abaOperacoes(context, app),
              _abaTeste(context, app),
            ],
          ),
          bottomNavigationBar: NavigationBar(
            selectedIndex: _aba,
            onDestinationSelected: (i) => setState(() => _aba = i),
            destinations: [
              NavigationDestination(
                icon: Badge(
                  isLabelVisible: app.pendentes.isNotEmpty,
                  label: Text('${app.pendentes.length}'),
                  child: const Icon(Icons.home_outlined),
                ),
                selectedIcon: const Icon(Icons.home),
                label: 'Início',
              ),
              const NavigationDestination(
                icon: Icon(Icons.swap_horiz_outlined),
                selectedIcon: Icon(Icons.swap_horiz),
                label: 'Operações',
              ),
              const NavigationDestination(
                icon: Icon(Icons.science_outlined),
                selectedIcon: Icon(Icons.science),
                label: 'Teste',
              ),
            ],
          ),
        );
      },
    );
  }
}
