package cl.gaselvolcan.usuarios;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "jwt.secret=clave-sintetica-usuarios-openapi-1234567890",
        "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:usuarios-contexto;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never", "spring.jpa.show-sql=false"
})
class MsUsuariosApplicationTests {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private cl.gaselvolcan.usuarios.config.DatosIniciales cargaInicial;

	@Test
	void contextLoads() {
	}

}
