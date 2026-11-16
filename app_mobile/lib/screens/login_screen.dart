import 'package:flutter/material.dart';

import '../api_client.dart';
import '../app_state.dart';
import '../config.dart';
import '../widgets/mensagem_box.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key, required this.app});

  final AppState app;

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _id = TextEditingController();
  final _senha = TextEditingController();

  final _novoId = TextEditingController();
  final _novoNome = TextEditingController();
  final _novaSenha = TextEditingController();
  final _novoSaldo = TextEditingController(text: '100');

  bool _carregando = false;
  Mensagem? _msgLogin;
  Mensagem? _msgCriar;
  Mensagem? _msgTeste;

  @override
  void initState() {
    super.initState();
    final aviso = widget.app.avisoLogin;
    if (aviso != null) {
      _msgLogin = Mensagem(aviso, true);
    }
  }

  @override
  void dispose() {
    for (final c in [_id, _senha, _novoId, _novoNome, _novaSenha, _novoSaldo]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _entrar() async {
    final id = int.tryParse(_id.text.trim());
    if (id == null || _senha.text.isEmpty) {
      setState(() => _msgLogin = const Mensagem('Informe o número da conta e a senha.', true));
      return;
    }
    setState(() {
      _carregando = true;
      _msgLogin = null;
    });
    try {
      await widget.app.login(id, _senha.text);
    } on ApiException catch (e) {
      if (mounted) setState(() => _msgLogin = Mensagem(e.mensagem, true));
    } finally {
      if (mounted) setState(() => _carregando = false);
    }
  }

  Future<void> _criarConta() async {
    final id = int.tryParse(_novoId.text.trim());
    final saldo = double.tryParse(_novoSaldo.text.trim().replaceAll(',', '.')) ?? 0;
    if (id == null || _novoNome.text.trim().isEmpty || _novaSenha.text.isEmpty) {
      setState(() => _msgCriar = const Mensagem('Preencha número, nome e senha.', true));
      return;
    }
    setState(() {
      _carregando = true;
      _msgCriar = null;
    });
    try {
      await widget.app.criarConta(id, _novoNome.text.trim(), _novaSenha.text, saldo);
      if (mounted) {
        setState(() {
          _msgCriar = Mensagem('Conta $id criada na agência ${agenciaDaConta(id)}. Você já pode entrar.', false);
          _id.text = '$id';
        });
      }
    } on ApiException catch (e) {
      if (mounted) setState(() => _msgCriar = Mensagem(e.mensagem, true));
    } finally {
      if (mounted) setState(() => _carregando = false);
    }
  }

  Future<void> _criarContasDeTeste() async {
    setState(() {
      _carregando = true;
      _msgTeste = null;
    });
    try {
      final texto = await widget.app.criarContasDeTeste();
      if (mounted) setState(() => _msgTeste = Mensagem(texto, false));
    } on ApiException catch (e) {
      if (mounted) setState(() => _msgTeste = Mensagem(e.mensagem, true));
    } finally {
      if (mounted) setState(() => _carregando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cores = Theme.of(context).colorScheme;
    return Scaffold(
      body: Align(
        alignment: Alignment.topCenter,
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 520),
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Container(
                margin: const EdgeInsets.only(top: 24, bottom: 20),
                padding: const EdgeInsets.symmetric(vertical: 28, horizontal: 20),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(24),
                  gradient: LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [cores.primary, const Color(0xFF1E88E5)],
                  ),
                ),
                child: const Column(
                  children: [
                    Icon(Icons.account_balance, color: Colors.white, size: 48),
                    SizedBox(height: 10),
                    Text('ICEIBank',
                        style: TextStyle(color: Colors.white, fontSize: 30, fontWeight: FontWeight.w800)),
                    SizedBox(height: 4),
                    Text('Banco distribuído · 3 agências',
                        style: TextStyle(color: Colors.white70)),
                  ],
                ),
              ),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text('Entrar', style: Theme.of(context).textTheme.titleLarge),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _id,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'Número da conta'),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _senha,
                    obscureText: true,
                    decoration: const InputDecoration(labelText: 'Senha'),
                  ),
                  const SizedBox(height: 12),
                  FilledButton(
                    onPressed: _carregando ? null : _entrar,
                    child: _carregando
                        ? const SizedBox(height: 18, width: 18, child: CircularProgressIndicator(strokeWidth: 2))
                        : const Text('Entrar'),
                  ),
                  MensagemBox(_msgLogin),
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
                  Text('Modo de teste', style: Theme.of(context).textTheme.titleMedium),
                  const SizedBox(height: 4),
                  const Text('Cria as contas 0, 1 e 2 (uma por agência), senha 123 e saldo R\$ 100.'),
                  const SizedBox(height: 8),
                  OutlinedButton.icon(
                    onPressed: _carregando ? null : _criarContasDeTeste,
                    icon: const Icon(Icons.science),
                    label: const Text('Criar contas de teste (0, 1 e 2)'),
                  ),
                  MensagemBox(_msgTeste),
                ],
              ),
            ),
          ),
          Card(
            child: ExpansionTile(
              title: const Text('Criar conta'),
              childrenPadding: const EdgeInsets.all(16),
              children: [
                TextField(
                  controller: _novoId,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Número da conta'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _novoNome,
                  decoration: const InputDecoration(labelText: 'Nome'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _novaSenha,
                  obscureText: true,
                  decoration: const InputDecoration(labelText: 'Senha'),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _novoSaldo,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(labelText: 'Saldo inicial'),
                ),
                const SizedBox(height: 12),
                OutlinedButton(onPressed: _carregando ? null : _criarConta, child: const Text('Criar conta')),
                MensagemBox(_msgCriar),
              ],
            ),
          ),
            ],
          ),
        ),
      ),
    );
  }
}
