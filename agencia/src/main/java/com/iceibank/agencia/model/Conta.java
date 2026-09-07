package com.iceibank.agencia.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Conta {

    private int id;
    private String nomeAluno;

    // Nunca deve ser serializada de volta nas respostas da API.
    @JsonIgnore
    private String senha;

    private double saldo;

    public Conta() {
    }

    public Conta(int id, String nomeAluno, String senha, double saldo) {
        this.id = id;
        this.nomeAluno = nomeAluno;
        this.senha = senha;
        this.saldo = saldo;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNomeAluno() {
        return nomeAluno;
    }

    public void setNomeAluno(String nomeAluno) {
        this.nomeAluno = nomeAluno;
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }

    public double getSaldo() {
        return saldo;
    }

    public void setSaldo(double saldo) {
        this.saldo = saldo;
    }
}
