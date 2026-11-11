package br.com.nexfleet.identidade.gerenciarusuarios.application;

public class FalhaUsuario extends RuntimeException {
  public enum Tipo {
    PROIBIDO,
    INVALIDO,
    NAO_ENCONTRADO,
    CONFLITO
  }

  private final Tipo tipo;

  public FalhaUsuario(Tipo tipo, String mensagem) {
    super(mensagem);
    this.tipo = tipo;
  }

  public Tipo tipo() {
    return tipo;
  }
}
