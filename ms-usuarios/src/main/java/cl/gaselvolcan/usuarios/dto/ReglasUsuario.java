package cl.gaselvolcan.usuarios.dto;

import java.util.List;

// Las reglas de los datos de un usuario, en un solo lugar.
// Las usan UsuarioSolicitud, RegistroSolicitud y UsuarioService:
// si una regla cambia, se cambia solo aquí. Son las mismas de la EP2.
public final class ReglasUsuario {

    public static final int MAX_NOMBRE = 50;
    public static final int MAX_APELLIDOS = 100;
    public static final int MAX_CORREO = 100;
    public static final int MIN_CONTRASENA = 4;
    public static final int MAX_CONTRASENA = 10;
    public static final int MAX_COMUNA = 60;
    public static final int MAX_DIRECCION = 300;
    public static final int MAX_REGION = 60;

    // Vacío, o entre 8 y 15 números, espacios o +
    public static final String TELEFONO = "^$|^[+0-9\\s]{8,15}$";

    public static final String MSJ_NOMBRE_VACIO = "El nombre es obligatorio.";
    public static final String MSJ_NOMBRE_LARGO = "El nombre no puede superar los 50 caracteres.";
    public static final String MSJ_APELLIDOS_VACIO = "Los apellidos son obligatorios.";
    public static final String MSJ_APELLIDOS_LARGO = "Los apellidos no pueden superar los 100 caracteres.";
    public static final String MSJ_CORREO_VACIO = "El correo es obligatorio.";
    public static final String MSJ_CORREO_FORMATO = "El correo no tiene un formato válido.";
    public static final String MSJ_CORREO_LARGO = "El correo no puede superar los 100 caracteres.";
    public static final String MSJ_CONTRASENA_VACIA = "La contraseña es obligatoria.";
    public static final String MSJ_CONTRASENA_LARGO = "La contraseña debe tener entre 4 y 10 caracteres.";
    public static final String MSJ_TELEFONO = "El teléfono solo admite números, espacios y el signo +.";
    public static final String MSJ_COMUNA_LARGO = "La comuna no puede superar los 60 caracteres.";
    public static final String MSJ_DIRECCION_LARGO = "La dirección no puede superar los 300 caracteres.";
    public static final String MSJ_NACIMIENTO_FUTURO = "La fecha de nacimiento no puede ser una fecha futura.";
    public static final String MSJ_REGION_LARGO = "La región no puede superar los 60 caracteres.";

    // Los mismos dominios permitidos en la EP2 (Anexo 1)
    public static final List<String> DOMINIOS_PERMITIDOS = List.of(
            "@gaselvolcan.cl", "@duoc.cl", "@profesor.duoc.cl", "@gmail.com");

    private ReglasUsuario() {
    }
}
