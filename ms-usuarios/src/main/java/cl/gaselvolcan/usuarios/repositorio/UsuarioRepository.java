package cl.gaselvolcan.usuarios.repositorio;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.gaselvolcan.usuarios.modelo.Usuario;

// JpaRepository ya trae save, findAll, findById, deleteById y count.
// Spring escribe el SQL a partir del nombre de cada método.
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCorreo(String correo);

    boolean existsByCorreo(String correo);
}
