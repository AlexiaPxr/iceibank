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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('ICEIBank')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
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
                    decoration: const InputDecoration(labelText: 'Número da conta', border: OutlineInputBorder()),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _senha,
                    obscureText: true,
                    decoration: const InputDecoration(labelText: 'Senha', border: OutlineInputBorder()),
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
            child: ExpansionTile(
              title: const Text('Criar conta'),
              childrenPadding: const EdgeInsets.all(16),
              children: [
                TextField(
                  controller: _novoId,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'Número da conta', border: OutlineInputBorder()),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _novoNome,
                  decoration: const InputDecoration(labelText: 'Nome', border: OutlineInputBorder()),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _novaSenha,
                  obscureText: true,
                  decoration: const InputDecoration(labelText: 'Senha', border: OutlineInputBorder()),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _novoSaldo,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(labelText: 'Saldo inicial', border: OutlineInputBorder()),
                ),
                const SizedBox(height: 12),
                OutlinedButton(onPressed: _carregando ? null : _criarConta, child: const Text('Criar conta')),
                MensagemBox(_msgCriar),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
