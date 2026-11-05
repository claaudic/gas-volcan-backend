"""Prueba HTTP real de documentacion/stock; MySQL solo lectura y esquema validate."""
import json
import urllib.error
import urllib.request
from verificar_etapa5 import ROOT, Configuracion, Verificador, comprobar


def main():
    verificador = Verificador(Configuracion.desde_entorno())
    obtener = verificador.obtener

    def verificar():
        productos = verificador.verificar_catalogo()
        for producto in productos:
            ruta = "/api/v1/productos/" + str(producto["id"]) + "/disponibilidad"
            estado, disponible = obtener(ruta)
            comprobar(estado == 200 and disponible["stock"] == producto["stock"], "Stock incorrecto.")
            comprobar(disponible["disponible"] == (producto["activo"] and producto["stock"] >= 1), "Disponibilidad incorrecta.")
            comprobar(not obtener(ruta + "?cantidad=" + str(producto["stock"] + 1))[1]["disponible"], "Se esperaba stock insuficiente.")
        for ruta, esperado in [("/api/v1/productos/999999999/disponibilidad",404),
                ("/api/v1/productos/" + str(productos[0]["id"]) + "/disponibilidad?cantidad=0",400),
                ("/api/v1/productos/" + str(productos[0]["id"]) + "/disponibilidad?cantidad=abc",400)]:
            try:
                obtener(ruta)
                raise RuntimeError("Se esperaba error HTTP.")
            except urllib.error.HTTPError as error:
                comprobar(error.code == esperado, "Estado HTTP incorrecto.")
        estado, docs = obtener("/v3/api-docs")
        comprobar(estado == 200, "OpenAPI no disponible.")
        for recurso in ("categorias", "productos"):
            base = "/api/v1/" + recurso
            for ruta, metodos in ((base, ("get","post")), (base + "/{id}", ("get","put","delete"))):
                for metodo in metodos:
                    operacion = docs["paths"][ruta][metodo]
                    codigo = "201" if metodo == "post" else "204" if metodo == "delete" else "200"
                    comprobar(codigo in operacion["responses"], "Respuesta exitosa no documentada.")
                    if metodo in ("post","put","delete"):
                        comprobar("400" in operacion["responses"], "Falta documentar 400.")
        comprobar("get" in docs["paths"]["/api/v1/productos/{id}/disponibilidad"], "Stock no documentado.")
        schemas = docs["components"]["schemas"]
        for nombre in ("CategoriaSolicitud","CategoriaRespuesta","ProductoSolicitud","ProductoRespuesta","DisponibilidadRespuesta","ErrorRespuesta"):
            comprobar(nombre in schemas, "DTO sin documentar: " + nombre)
        solicitud = schemas["ProductoSolicitud"]
        comprobar("codigo" in solicitud["required"] and "stock" in solicitud["required"], "Validaciones obligatorias no documentadas.")
        comprobar(solicitud["properties"]["stock"]["minimum"] == 0, "Stock minimo no documentado.")
        comprobar(solicitud["properties"]["codigo"]["maxLength"] == 255, "Longitud no documentada.")
        for ruta in ("/swagger-ui.html", "/swagger-ui/index.html", "/swagger-ui/swagger-ui-bundle.js", "/swagger-ui/swagger-ui.css", "/swagger-ui/swagger-initializer.js"):
            with urllib.request.urlopen(verificador.config.url + ruta, timeout=10) as respuesta:
                comprobar(respuesta.status == 200 and len(respuesta.read()) > 0, "Recurso Swagger no disponible: " + ruta)
        config = obtener("/v3/api-docs/swagger-config")[1]
        comprobar(config["url"] == "/v3/api-docs", "Swagger apunta a otra especificacion.")
        (ROOT / "target/openapi-etapa6.json").write_text(json.dumps(docs,ensure_ascii=False,indent=2),encoding="utf-8")
        print("PASS: stock real de 14 productos; stock insuficiente; errores 400/404.",flush=True)
        print("PASS: OpenAPI, 11 operaciones documentadas, DTO y restricciones.",flush=True)
        print("PASS: Swagger UI, configuracion, HTML, JavaScript y CSS responden 200.",flush=True)

    verificador.ejecutar(verificar, "verificacion-openapi-stock.log")



if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("Verificacion interrumpida (" + type(error).__name__ + "). Revisar configuracion y logs locales.")
        raise SystemExit(1)
