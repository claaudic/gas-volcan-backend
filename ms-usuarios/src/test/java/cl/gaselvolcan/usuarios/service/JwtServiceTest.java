package cl.gaselvolcan.usuarios.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import cl.gaselvolcan.usuarios.model.Rol;
import cl.gaselvolcan.usuarios.model.Usuario;
import io.jsonwebtoken.Claims;

// Pruebas del token: generar, verificar y rechazar tokens malos.
// No necesitan MySQL ni levantar Spring.
class JwtServiceTest {

    private static final String SECRETO = "clave-de-prueba-con-mas-de-32-caracteres";

    private final JwtService jwt = new JwtService(SECRETO, 30);
    private final Usuario camila = new Usuario("Camila", "Rojas", "camila@gmail.com", "x", Rol.CLIENTE);

    @Test
    void elTokenLlevaCorreoYRol() {
        Claims datos = jwt.verificar(jwt.generar(camila));

        assertEquals("camila@gmail.com", datos.getSubject());
        assertEquals("CLIENTE", datos.get("rol"));
    }

    @Test
    void elTokenVenceEn30Minutos() {
        Claims datos = jwt.verificar(jwt.generar(camila));
        long segundos = (datos.getExpiration().getTime() - datos.getIssuedAt().getTime()) / 1000;

        assertEquals(30 * 60, segundos);
    }

    @Test
    void rechazaUnTokenModificado() {
        String token = jwt.generar(camila);
        String modificado = token.substring(0, token.length() - 2) + "xx";

        assertFalse(jwt.esValido(modificado));
    }

    @Test
    void rechazaUnTokenFirmadoConOtraClave() {
        JwtService otro = new JwtService("otra-clave-distinta-tambien-de-32-caracteres", 30);

        assertFalse(jwt.esValido(otro.generar(camila)));
    }

    @Test
    void rechazaUnTokenVencido() {
        // Dura 0 minutos: nace vencido
        JwtService vencido = new JwtService(SECRETO, 0);

        assertFalse(jwt.esValido(vencido.generar(camila)));
    }

    @Test
    void aceptaUnTokenCorrecto() {
        assertTrue(jwt.esValido(jwt.generar(camila)));
    }
}
