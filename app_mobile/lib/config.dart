/// Configuracao das agencias conhecidas pelo app.
///
/// No emulador Android, o computador que roda as agencias e acessado por
/// 10.0.2.2 (localhost do emulador seria o proprio celular virtual).
/// Em um celular fisico, rode com o IP do computador:
///   flutter run --dart-define=API_HOST=192.168.0.10
const String hostApi = String.fromEnvironment('API_HOST', defaultValue: '10.0.2.2');

const int portaBase = 4000;
const int numeroAgencias = 3;

String urlDaAgencia(int idAgencia) => 'http://$hostApi:${portaBase + idAgencia}';

/// As contas sao particionadas por id % 3 (igual ao backend): cada conta
/// so existe na agencia correspondente.
int agenciaDaConta(int idConta) => idConta % numeroAgencias;
