package cl.gaselvolcan.ms_catalogo.modelo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;

class ModeloCatalogoTest {

    @Test
    void aceptaProductoCompletoYRechazaDatosInvalidos() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var producto = new Producto("PRUEBA", "Producto de prueba", null,
                    new BigDecimal("100.00"), new BigDecimal("90.00"), 0,
                    new Categoria("Categoria de prueba", null));
            assertTrue(validator.validate(producto).isEmpty());

            producto.setStock(-1);
            producto.setPrecioResidencial(new BigDecimal("-1"));
            producto.setPrecioComercial(new BigDecimal("1.001"));
            producto.setCodigo(" ");
            producto.setCategoria(null);
            producto.setActivo(null);
            var campos = validator.validate(producto).stream()
                    .map(error -> error.getPropertyPath().toString()).toList();
            assertTrue(campos.containsAll(java.util.List.of("stock", "precioResidencial",
                    "precioComercial", "codigo", "categoria", "activo")));

            producto.setStock(null);
            assertFalse(validator.validateProperty(producto, "stock").isEmpty());
        }
    }

    @Test
    void rechazaCategoriaSinNombreOConNombreDemasiadoLargo() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new Categoria(" ", null)).isEmpty());
            assertFalse(validator.validate(new Categoria("a".repeat(256), null)).isEmpty());
            assertTrue(validator.validate(new Categoria("Prueba", null)).isEmpty());
        }
    }
}
