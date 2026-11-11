package br.com.nexfleet.identidade.autenticar.infrastructure;

import br.com.nexfleet.identidade.autenticar.application.EmitirToken;
import br.com.nexfleet.identidade.usuario.domain.Perfil;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

public class JwtEmitirToken implements EmitirToken {
  public static final String ISSUER = "https://nexfleet.local/identidade";
  public static final String AUDIENCE = "nexfleet-api";
  private final JwtEncoder encoder;
  private final Clock clock;
  private final JwsHeader header;

  public JwtEmitirToken(JwtEncoder encoder, Clock clock) {
    this(encoder, clock, JwsHeader.with(MacAlgorithm.HS256).build());
  }

  public JwtEmitirToken(JwtEncoder encoder, Clock clock, JwsHeader header) {
    this.encoder = encoder;
    this.clock = clock;
    this.header = header;
  }

  @Override
  public Token emitir(UUID usuarioId, Perfil perfil) {
    var agora = clock.instant();
    var claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(usuarioId.toString())
            .claim("perfil", java.util.Objects.requireNonNull(perfil).name())
            .audience(List.of(AUDIENCE))
            .issuedAt(agora)
            .expiresAt(agora.plusSeconds(900))
            .id(UUID.randomUUID().toString())
            .build();
    var token = encoder.encode(JwtEncoderParameters.from(header, claims));
    return new Token(token.getTokenValue(), "Bearer", 900);
  }
}
