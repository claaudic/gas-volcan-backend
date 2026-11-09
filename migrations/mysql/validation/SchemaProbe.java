import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

// Compilado contra los mismos JAR y entidades que las aplicaciones, sin arrancarlas.
public class SchemaProbe {
    public static void main(String[] args) throws Exception {
        var registry = new StandardServiceRegistryBuilder()
            .applySetting("hibernate.connection.driver_class", "com.mysql.cj.jdbc.Driver")
            .applySetting("hibernate.connection.url", args[0])
            .applySetting("hibernate.connection.username", "root")
            .applySetting("hibernate.connection.password", "migration-test-only")
            .applySetting("hibernate.hbm2ddl.auto", args[1])
            .applySetting("hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
            .build();
        try {
            var metadata = new MetadataSources(registry);
            String pkg = args[2].equals("pedidos") ? "cl.gaselvolcan.ms_pedidos.model." : "cl.gaselvolcan.ms_catalogo.model.";
            String[] names = args[2].equals("pedidos")
                ? new String[]{"Boleta", "DetalleBoleta", "OperacionCompra"}
                : new String[]{"Categoria", "Producto", "OperacionStock"};
            for (String name : names) metadata.addAnnotatedClass(Class.forName(pkg + name));
            try (var factory = metadata.buildMetadata().buildSessionFactory()) { }
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
