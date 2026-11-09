package cl.gaselvolcan.ms_catalogo.config;

import java.security.Principal;

public record ServicioAutenticado(String nombre) implements Principal {
    @Override public String getName() { return nombre; }
}
