String formatarReais(double valor) => 'R\$ ${valor.toStringAsFixed(2).replaceAll('.', ',')}';

String horaCurta(DateTime data) {
  String dois(int n) => n.toString().padLeft(2, '0');
  return '${dois(data.hour)}:${dois(data.minute)}:${dois(data.second)}';
}
