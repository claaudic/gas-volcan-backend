package cl.gaselvolcan.usuarios.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

// Cada objeto Usuario es una fila de la tabla "usuarios".
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Opcional: los clientes se registran sin RUN; el administrador lo agrega al crear cuentas del equipo.
    @Size(max = 12)
    @Column(length = 12)
    private String run;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String nombre;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String apellidos;

    // Único: no puede haber dos cuentas con el mismo correo.
    @NotBlank
    @Email
    @Size(max = 100)
    @Column(nullable = false, unique = true, length = 100)
    private String correo;

    // Se guarda cifrada con BCrypt (unos 60 caracteres), nunca en texto plano.
    @NotBlank
    @Column(nullable = false, length = 100)
    private String contrasena;

    // Se guarda como texto ("CLIENTE"), no como número.
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @Size(max = 15)
    @Column(length = 15)
    private String telefono;

    @Size(max = 60)
    @Column(length = 60)
    private String comuna;

    @Size(max = 300)
    @Column(length = 300)
    private String direccion;

    // Opcional, como en el formulario de la EP2 (Admin Usuarios). No puede ser futura
    @PastOrPresent
    private LocalDate nacimiento;

    // En la EP2 siempre es "Ñuble": todas las comunas con reparto están ahí
    @Size(max = 60)
    @Column(length = 60)
    private String region;

    // En vez de borrar una cuenta se puede desactivar.
    @Column(nullable = false)
    private boolean activo = true;

    // JPA necesita un constructor vacío.
    public Usuario() {
    }

    public Usuario(String nombre, String apellidos, String correo, String contrasena, Rol rol) {
        this.nombre = nombre;
        this.apellidos = apellidos;
        this.correo = correo;
        this.contrasena = contrasena;
        this.rol = rol;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRun() { return run; }
    public void setRun(String run) { this.run = run; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getContrasena() { return contrasena; }
    public void setContrasena(String contrasena) { this.contrasena = contrasena; }

    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getComuna() { return comuna; }
    public void setComuna(String comuna) { this.comuna = comuna; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public LocalDate getNacimiento() { return nacimiento; }
    public void setNacimiento(LocalDate nacimiento) { this.nacimiento = nacimiento; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
