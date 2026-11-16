import 'package:flutter_test/flutter_test.dart';

import 'package:app_mobile/config.dart';
import 'package:app_mobile/utils.dart';

void main() {
  test('a conta pertence a agencia id % 3 (mesma particao do backend)', () {
    expect(agenciaDaConta(0), 0);
    expect(agenciaDaConta(4), 1);
    expect(agenciaDaConta(11), 2);
  });

  test('cada agencia tem uma porta propria a partir de 4000', () {
    expect(urlDaAgencia(0).endsWith(':4000'), isTrue);
    expect(urlDaAgencia(2).endsWith(':4002'), isTrue);
  });

  test('formata reais com virgula', () {
    expect(formatarReais(1234.5), 'R\$ 1234,50');
  });
}
