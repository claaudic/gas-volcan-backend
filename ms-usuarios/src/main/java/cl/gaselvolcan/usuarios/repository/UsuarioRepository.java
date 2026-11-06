package cl.gaselvolcan.usuarios.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.gaselvolcan.usuarios.model.Rol;
import cl.gaselvolcan.usuarios.model.Usuario;

// JpaRepository ya trae save, findAll, findById, deleteById y count.
// Spring escribe el SQL a partir del nombre de cada método.
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCorreo(String correo);

    boolean existsByCorreo(String correo);

    // SELECT * FROM usuarios WHERE rol = ? AND activo = true
    List<Usuario> findByRolAndActivoTrue(Rol rol);
}
