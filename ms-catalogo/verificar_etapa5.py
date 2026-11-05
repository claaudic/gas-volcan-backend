"""Verifica un catalogo existente: solo SELECT/GET, sin carga ni cambios de esquema."""
import argparse
from contextlib import contextmanager
from dataclasses import dataclass, field
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parent


def comprobar(condicion, mensaje):
    if not condicion:
        raise RuntimeError(mensaje)


def docker(*argumentos, entrada=None):
    resultado = subprocess.run(["docker", *argumentos],
        input=entrada.encode("utf-8") if entrada is not None else None, capture_output=True)
    comprobar(resultado.returncode == 0, "Fallo de Docker/MySQL; revisar el entorno sin repetir escrituras.")
    return resultado.stdout.decode("utf-8")


@dataclass(frozen=True)
class Configuracion:
    contenedor: str
    puerto_mysql: int
    usuario: str
    clave: str = field(repr=False)
    base: str = "volcan_catalogo"
    puerto_api: int = 8082

    @classmethod
    def desde_entorno(cls):
        def requerido(nombre):
            valor = os.environ.get(nombre, "")
            comprobar(bool(valor), "Falta configurar " + nombre)
            return valor
        config = cls(requerido("CATALOGO_VERIFY_CONTAINER"),
                     int(requerido("CATALOGO_VERIFY_MYSQL_PORT")),
                     requerido("CATALOGO_VERIFY_DB_USER"), requerido("CATALOGO_DB_PASSWORD"),
                     os.environ.get("CATALOGO_VERIFY_DATABASE", "volcan_catalogo"),
                     int(os.environ.get("CATALOGO_VERIFY_API_PORT", "8082")))
        comprobar(bool(re.fullmatch(r"[A-Za-z0-9_]+", config.base)), "Nombre de base invalido.")
        comprobar(0 < config.puerto_mysql < 65536 and 0 < config.puerto_api < 65536, "Puerto invalido.")
        comprobar("\n" not in config.clave and "\r" not in config.clave,
                  "La credencial debe proporcionarse en una sola linea.")
        return config

    @property
    def url(self):
        return "http://127.0.0.1:" + str(self.puerto_api)


class Verificador:
    def __init__(self, config):
        self.config = config
        self.original = json.loads((ROOT / "src/main/resources/catalogo-original.json").read_text(encoding="utf-8"))

    def consultar(self, sql):
        # La clave viaja por stdin, nunca por argumentos ni por salida de consola.
        return docker("exec", "-i", self.config.contenedor, "sh", "-c",
            'IFS= read -r MYSQL_PWD; export MYSQL_PWD; exec mysql --user="$1" --database="$2" --default-character-set=utf8mb4 --batch --raw --skip-column-names',
            "sh", self.config.usuario, self.config.base,
            entrada=self.config.clave + "\n" + sql).strip()

    def obtener(self, ruta):
        with urllib.request.urlopen(self.config.url + ruta, timeout=10) as respuesta:
            return respuesta.status, json.load(respuesta)

    def snapshot(self):
        return self.consultar("SELECT * FROM categorias ORDER BY id; SELECT * FROM productos ORDER BY id;")

    def comprobar_entorno(self):
        config = self.config
        info = json.loads(docker("inspect", config.contenedor))[0]
        comprobar(info["Name"] == "/" + config.contenedor and info["State"]["Running"], "Contenedor no disponible.")
        puertos = info["NetworkSettings"]["Ports"].get("3306/tcp") or []
        comprobar(any(p["HostPort"] == str(config.puerto_mysql) for p in puertos), "Puerto MySQL inesperado.")
        comprobar(self.consultar("SELECT DATABASE();") == config.base, "Base inesperada.")
        with socket.socket() as conexion:
            comprobar(conexion.connect_ex(("127.0.0.1", config.puerto_api)) != 0, "Puerto API ocupado.")
        comprobar((ROOT / "target/ms-catalogo-0.0.1-SNAPSHOT.jar").is_file(), "Ejecutar antes Maven package.")

    def argumentos(self):
        config = self.config
        return ["java", "-jar", str(ROOT / "target/ms-catalogo-0.0.1-SNAPSHOT.jar"),
            "--server.address=127.0.0.1", "--server.port=" + str(config.puerto_api),
            "--spring.datasource.url=jdbc:mysql://127.0.0.1:" + str(config.puerto_mysql) + "/" + config.base,
            "--spring.datasource.username=" + config.usuario,
            "--spring.jpa.hibernate.ddl-auto=validate", "--catalogo.carga-inicial.enabled=false",
            "--spring.sql.init.mode=never", "--spring.flyway.enabled=false", "--spring.liquibase.enabled=false"]

    @contextmanager
    def servicio(self, nombre_log):
        entorno = os.environ.copy()
        entorno["CATALOGO_DB_PASSWORD"] = self.config.clave
        (ROOT / "target").mkdir(exist_ok=True)
        with (ROOT / "target" / nombre_log).open("w", encoding="utf-8") as log:
            proceso = subprocess.Popen(self.argumentos(), cwd=ROOT, env=entorno,
                                       stdout=log, stderr=subprocess.STDOUT)
            try:
                for _ in range(120):
                    comprobar(proceso.poll() is None, "Spring Boot termino; revisar el log local.")
                    try:
                        if self.obtener("/api/v1/productos")[0] == 200:
                            break
                    except (OSError, urllib.error.URLError):
                        pass
                    time.sleep(0.5)
                else:
                    raise RuntimeError("No se confirmo el arranque.")
                yield
            finally:
                if proceso.poll() is None:
                    proceso.terminate()
                    try:
                        proceso.wait(timeout=15)
                    except subprocess.TimeoutExpired:
                        proceso.kill()
                        proceso.wait(timeout=5)
                entorno.pop("CATALOGO_DB_PASSWORD", None)

    def verificar_catalogo(self):
        estado, categorias = self.obtener("/api/v1/categorias")
        comprobar(estado == 200 and len(categorias) == 4, "Categorias incorrectas.")
        estado, productos = self.obtener("/api/v1/productos")
        comprobar(estado == 200 and len(productos) == 14, "Productos incorrectos.")
        comprobar(sum(p["precioOferta"] is not None for p in productos) == 4, "Ofertas incorrectas.")
        comprobar({c["nombre"] for c in categorias} == {p["categoria"] for p in self.original}, "Categorias diferentes del origen.")
        por_codigo = {p["codigo"]: p for p in productos}
        comprobar(len(por_codigo) == len(productos), "Codigos duplicados.")
        for fuente in self.original:
            actual = por_codigo.get(fuente["codigo"])
            comprobar(actual is not None, "Codigo original ausente.")
            for campo in fuente.keys() - {"id", "categoria"}:
                comprobar(actual[campo] == fuente[campo], "Diferencia: " + fuente["codigo"] + " " + campo)
            comprobar(actual["categoria"]["nombre"] == fuente["categoria"], "Relacion incorrecta.")
            comprobar(self.obtener("/api/v1/productos/" + str(actual["id"])) == (200, actual), "Consulta por ID incorrecta.")
        for categoria in categorias:
            comprobar(self.obtener("/api/v1/categorias/" + str(categoria["id"])) == (200, categoria), "Consulta por ID incorrecta.")
        return productos

    def ejecutar(self, prueba, nombre_log):
        self.comprobar_entorno()
        inicial = self.snapshot()
        try:
            with self.servicio(nombre_log):
                prueba()
        finally:
            comprobar(self.snapshot() == inicial, "Datos o IDs cambiaron durante la prueba; revisar sin reintentar.")
        print("PASS: esquema validate; todas las filas e IDs sin cambios. Java de prueba detenido.")


def ejecutar_cli():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--solo-reinicio", action="store_true", required=True,
                        help="Verificar la base existente sin ejecutar la carga inicial")
    parser.parse_args()
    verificador = Verificador(Configuracion.desde_entorno())
    verificador.ejecutar(verificador.verificar_catalogo, "verificacion-reinicio.log")
    print("PASS: 4 categorias, 14 productos, 4 ofertas; precios y stock originales.")


if __name__ == "__main__":
    try:
        ejecutar_cli()
    except Exception as error:
        print("Verificacion interrumpida (" + type(error).__name__ + "). Revisar configuracion y logs locales. No se ejecuta limpieza de MySQL.")
        raise SystemExit(1)
