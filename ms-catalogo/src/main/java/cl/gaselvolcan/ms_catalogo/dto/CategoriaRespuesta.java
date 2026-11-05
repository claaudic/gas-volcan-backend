package cl.gaselvolcan.ms_catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import cl.gaselvolcan.ms_catalogo.modelo.Categoria;

@Schema(description = "Datos devueltos por la API")
public record CategoriaRespuesta(@Schema(description = "ID persistente generado por MySQL") Long id, @Schema(description = "Nombre del recurso") String nombre, @Schema(description = "Descripcion opcional") String descripcion) {
    public static CategoriaRespuesta desde(Categoria categoria) {
        return new CategoriaRespuesta(categoria.getId(), categoria.getNombre(), categoria.getDescripcion());
    }
}
