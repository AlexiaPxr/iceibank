package com.iceibank.agencia.service;

import com.iceibank.agencia.config.AgenciaProperties;
import com.iceibank.agencia.exception.ContaForaDaParticaoException;
import com.iceibank.agencia.exception.ContaJaExisteException;
import com.iceibank.agencia.exception.ContaNaoEncontradaException;
import com.iceibank.agencia.exception.SaldoInsuficienteException;
import com.iceibank.agencia.model.Conta;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ContaService {

    private final Map<Integer, Conta> contas = new ConcurrentHashMap<>();
    private final AgenciaProperties agenciaProperties;
    private final RelogioVetorial relogio;
    private final RegistroEventos registro;

    public ContaService(AgenciaProperties agenciaProperties, RelogioVetorial relogio, RegistroEventos registro) {
        this.agenciaProperties = agenciaProperties;
        this.relogio = relogio;
        this.registro = registro;
    }

    public boolean pertenceAEstaAgencia(int idConta) {
        return agenciaProperties.agenciaResponsavel(idConta) == agenciaProperties.getIdAgencia();
    }

    public Conta buscar(int id) {
        return contas.get(id);
    }

    public Conta consultar(int id) {
        Conta conta = contas.get(id);
        if (conta == null) {
            throw new ContaNaoEncontradaException("Conta não encontrada nesta agência.");
        }
        return conta;
    }

    public synchronized Conta criar(int id, String nomeAluno, String senha, double saldoInicial) {
        if (!pertenceAEstaAgencia(id)) {
            throw new ContaForaDaParticaoException("Conta " + id + " não pertence a esta agência.");
        }
        if (contas.containsKey(id)) {
            throw new ContaJaExisteException("Conta já existe.");
        }

        Conta conta = new Conta(id, nomeAluno, senha, saldoInicial);
        contas.put(id, conta);

        int[] vetor = relogio.eventoLocal();
        registro.registrar("CRIAR_CONTA", vetor, Map.of(
                "id", id, "nomeAluno", nomeAluno, "saldoInicial", saldoInicial
        ));

        return conta;
    }

    public synchronized Conta depositar(int id, double valor) {
        Conta conta = consultar(id);
        conta.setSaldo(conta.getSaldo() + valor);

        int[] vetor = relogio.eventoLocal();
        registro.registrar("DEPOSITO", vetor, Map.of("id", id, "valor", valor, "novoSaldo", conta.getSaldo()));

        return conta;
    }

    public synchronized Conta sacar(int id, double valor) {
        Conta conta = consultar(id);
        if (conta.getSaldo() < valor) {
            throw new SaldoInsuficienteException("Saldo insuficiente.");
        }
        conta.setSaldo(conta.getSaldo() - valor);

        int[] vetor = relogio.eventoLocal();
        registro.registrar("SAQUE", vetor, Map.of("id", id, "valor", valor, "novoSaldo", conta.getSaldo()));

        return conta;
    }
}