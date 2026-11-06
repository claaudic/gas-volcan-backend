package cl.gaselvolcan.usuarios.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import cl.gaselvolcan.usuarios.model.Rol;
import cl.gaselvolcan.usuarios.model.Usuario;
import cl.gaselvolcan.usuarios.repository.UsuarioRepository;

// Al arrancar, carga las 5 cuentas de demostración de la EP2.
// Solo si la tabla está vacía, para no duplicarlas en cada arranque.
@Component
public class DatosIniciales implements CommandLineRunner {

    private final UsuarioRepository repositorio;
    private final PasswordEncoder cifrador;

    public DatosIniciales(UsuarioRepository repositorio, PasswordEncoder cifrador) {
        this.repositorio = repositorio;
        this.cifrador = cifrador;
    }

    @Override
    public void run(String... args) {
        if (repositorio.count() > 0) {
            return;
        }

        crear("Sofía", "Pérez", "admin@gaselvolcan.cl", "Admin1234", Rol.ADMINISTRADOR);
        crear("Daniela", "Fuentes", "despachadora@gaselvolcan.cl", "Desp123", Rol.DESPACHADORA);
        crear("Matías", "Vera", "repartidor@gaselvolcan.cl", "Repart123", Rol.REPARTIDOR);
        crear("Rodrigo", "Peña", "repartidor2@gaselvolcan.cl", "Repart123", Rol.REPARTIDOR);
        crear("Camila", "Rojas", "camila@gmail.com", "Clien1234", Rol.CLIENTE);
    }

    private void crear(String nombre, String apellidos, String correo, String contrasena, Rol rol) {
        // La contraseña se guarda cifrada con BCrypt
        repositorio.save(new Usuario(nombre, apellidos, correo, cifrador.encode(contrasena), rol));
    }
}
