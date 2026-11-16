import 'package:flutter/material.dart';

/// Mensagem de sucesso ou de erro exibida dentro de um cartao (como as
/// mensagens coloridas do frontend web).
class Mensagem {
  const Mensagem(this.texto, this.erro);
  final String texto;
  final bool erro;
}

class MensagemBox extends StatelessWidget {
  const MensagemBox(this.mensagem, {super.key});

  final Mensagem? mensagem;

  @override
  Widget build(BuildContext context) {
    final m = mensagem;
    if (m == null) {
      return const SizedBox.shrink();
    }
    final cor = m.erro ? Colors.red : Colors.green;
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.only(top: 10),
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: cor.shade50,
        border: Border.all(color: cor.shade300),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(m.erro ? Icons.error_outline : Icons.check_circle_outline, color: cor.shade700, size: 20),
          const SizedBox(width: 8),
          Expanded(child: Text(m.texto, style: TextStyle(color: cor.shade900))),
        ],
      ),
    );
  }
}
