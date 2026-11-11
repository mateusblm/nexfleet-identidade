package br.com.nexfleet.identidade.config;

import br.com.nexfleet.identidade.consultarservico.application.ConsultarServico;
import br.com.nexfleet.identidade.consultarservico.application.VerificarBanco;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {
  @Bean
  ConsultarServico consultarServico(VerificarBanco banco) {
    return new ConsultarServico(banco);
  }
}
