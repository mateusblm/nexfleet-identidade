package br.com.nexfleet.identidade.gerenciarusuarios.application;

import static br.com.nexfleet.identidade.gerenciarusuarios.application.FalhaUsuario.Tipo.*;

import br.com.nexfleet.identidade.usuario.domain.Perfil;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class GerenciarUsuarios {
  private final Usuarios usuarios;
  private final ProtegerSenha senhas;

  public GerenciarUsuarios(Usuarios usuarios, ProtegerSenha senhas) {
    this.usuarios = usuarios;
    this.senhas = senhas;
  }

  public List<Usuarios.Dados> listar(UUID autor, int pagina, int tamanho) {
    autorizar(autor);
    if (pagina < 0 || pagina > 100000 || tamanho < 1 || tamanho > 100)
      throw new FalhaUsuario(INVALIDO, "Página deve ser de 0 a 100000 e tamanho de 1 a 100.");
    return usuarios.listar(pagina * tamanho, tamanho);
  }

  public Usuarios.Dados consultar(UUID autor, UUID id) {
    autorizar(autor);
    return usuarios.buscar(id).orElseThrow(GerenciarUsuarios::naoEncontrado);
  }

  public Usuarios.Dados criar(UUID autor, String email, String senha, Perfil perfil) {
    autorizar(autor);
    var normalizado = email(email);
    perfil(perfil);
    if (senha == null || senha.length() < 12 || senha.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new FalhaUsuario(
          INVALIDO, "Senha deve ter pelo menos 12 caracteres e no máximo 72 bytes UTF-8.");
    return usuarios.criar(UUID.randomUUID(), normalizado, senhas.hash(senha), perfil);
  }

  public Usuarios.Dados atualizar(UUID autor, UUID id, String email, Perfil perfil, Boolean ativo) {
    autorizar(autor);
    var normalizado = email(email);
    perfil(perfil);
    if (ativo == null) throw new FalhaUsuario(INVALIDO, "Informe se o usuário está ativo.");
    return usuarios
        .atualizar(id, normalizado, perfil, ativo)
        .orElseThrow(GerenciarUsuarios::naoEncontrado);
  }

  public void desativar(UUID autor, UUID id) {
    autorizar(autor);
    if (!usuarios.desativar(id)) throw naoEncontrado();
  }

  private void autorizar(UUID autor) {
    if (autor == null || !usuarios.administradorAtivo(autor))
      throw new FalhaUsuario(PROIBIDO, "Acesso exclusivo de Administrador ativo.");
  }

  private static String email(String email) {
    if (email == null) throw new FalhaUsuario(INVALIDO, "Informe um e-mail válido.");
    var valor = email.strip().toLowerCase(Locale.ROOT);
    if (valor.length() > 254 || !valor.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
      throw new FalhaUsuario(INVALIDO, "Informe um e-mail válido.");
    return valor;
  }

  private static void perfil(Perfil perfil) {
    if (perfil == null)
      throw new FalhaUsuario(INVALIDO, "Informe Administrador ou Gestor de Frota.");
  }

  private static FalhaUsuario naoEncontrado() {
    return new FalhaUsuario(NAO_ENCONTRADO, "Usuário não encontrado.");
  }
}
