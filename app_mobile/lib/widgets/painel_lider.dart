import 'package:flutter/material.dart';

import '../config.dart';
import '../services/lider_service.dart';
import '../utils.dart';

/// Painel de lideranca: mostra qual agencia e a lider agora e se atualiza
/// sozinho a cada consulta do LiderService.
class PainelLider extends StatelessWidget {
  const PainelLider({super.key, required this.servico});

  final LiderService servico;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: servico,
      builder: (context, _) {
        late final Color fundo;
        late final IconData icone;
        late final String titulo;
        late final String detalhe;

        if (!servico.jaConsultou) {
          fundo = Colors.blueGrey.shade50;
          icone = Icons.hourglass_top;
          titulo = 'Consultando líder...';
          detalhe = 'Procurando uma agência no ar.';
        } else if (servico.semResposta) {
          fundo = Colors.red.shade50;
          icone = Icons.cloud_off;
          titulo = 'Nenhuma agência respondeu';
          detalhe = 'Tentei as $numeroAgencias agências. Tentando de novo...';
        } else if (servico.lider == null) {
          fundo = Colors.orange.shade50;
          icone = Icons.how_to_vote;
          titulo = 'Eleição em andamento';
          detalhe = 'A agência ${servico.agenciaConsultada} ainda não sabe quem é a líder.';
        } else {
          fundo = Colors.green.shade50;
          icone = Icons.stars;
          titulo = 'Líder atual: Agência ${servico.lider}';
          detalhe = 'Consultado na agência ${servico.agenciaConsultada}';
        }

        final hora = servico.atualizadoEm == null ? '' : ' · atualizado às ${horaCurta(servico.atualizadoEm!)}';

        return Card(
          color: fundo,
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(icone, size: 28),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        titulo,
                        style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                Text('$detalhe$hora', style: Theme.of(context).textTheme.bodySmall),
                const SizedBox(height: 10),
                Wrap(
                  spacing: 8,
                  children: [
                    for (var id = 0; id < numeroAgencias; id++)
                      Chip(
                        avatar: servico.lider == id && !servico.semResposta ? const Icon(Icons.star, size: 18) : null,
                        label: Text('Agência $id'),
                        backgroundColor:
                            servico.lider == id && !servico.semResposta ? Colors.amber.shade200 : null,
                      ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}
