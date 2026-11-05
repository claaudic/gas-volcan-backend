"""Pruebas de los verificadores auxiliares: sin Docker, Java ni MySQL."""
import contextlib
import io
import os
import subprocess
import unittest
from unittest.mock import MagicMock, patch
import verificar_etapa5 as script


class VerificacionTest(unittest.TestCase):
    def config(self):
        return script.Configuracion("mysql-prueba", 13306, "usuario-prueba", "valor-simulado")

    def test_docker_decodifica_utf8_sin_depender_de_windows(self):
        texto = "v\u00e1lvula y bater\u00eda"
        respuesta = subprocess.CompletedProcess([], 0, texto.encode("utf-8"), b"")
        with patch.object(script.subprocess, "run", return_value=respuesta) as run:
            self.assertEqual(script.docker("exec", "mysql-prueba", entrada="SELECT '\u00e1';"), texto)
            self.assertEqual(run.call_args.kwargs["input"], "SELECT '\u00e1';".encode("utf-8"))
            self.assertNotIn("text", run.call_args.kwargs)

    def test_mysql_utf8_y_clave_fuera_de_argumentos(self):
        verificador = script.Verificador(self.config())
        with patch.object(script, "docker", return_value="4\n") as docker:
            self.assertEqual(verificador.consultar("SELECT COUNT(*);"), "4")
            argumentos = docker.call_args.args
            self.assertTrue(any("--default-character-set=utf8mb4" in a for a in argumentos))
            self.assertNotIn(verificador.config.clave, str(argumentos))
            self.assertEqual(docker.call_args.kwargs["entrada"], verificador.config.clave + "\nSELECT COUNT(*);")

    def test_configuracion_externa_y_sin_clave_en_repr(self):
        entorno = {"CATALOGO_VERIFY_CONTAINER":"mysql-prueba", "CATALOGO_VERIFY_MYSQL_PORT":"13306",
                   "CATALOGO_VERIFY_DB_USER":"usuario-prueba", "CATALOGO_DB_PASSWORD":"valor-simulado",
                   "CATALOGO_VERIFY_DATABASE":"catalogo_prueba", "CATALOGO_VERIFY_API_PORT":"18082"}
        with patch.dict(os.environ, entorno, clear=True):
            config = script.Configuracion.desde_entorno()
            self.assertEqual(config.puerto_mysql, 13306)
            self.assertEqual(config.url, "http://127.0.0.1:18082")
            self.assertEqual(config.base, "catalogo_prueba")
            self.assertNotIn(config.clave, repr(config))

    def test_entorno_incompleto_no_arranca_procesos(self):
        with patch.dict(os.environ, {}, clear=True), patch.object(script.subprocess, "Popen") as proceso:
            with self.assertRaisesRegex(RuntimeError, "CATALOGO_VERIFY_CONTAINER"):
                script.Configuracion.desde_entorno()
            proceso.assert_not_called()

    def test_cli_exige_modo_lectura(self):
        with patch("sys.argv", ["verificar_etapa5.py"]), contextlib.redirect_stderr(io.StringIO()), patch.object(script, "Verificador") as verificador:
            with self.assertRaises(SystemExit):
                script.ejecutar_cli()
            verificador.assert_not_called()

    def test_argumentos_solo_validate_y_sin_inicializadores(self):
        argumentos = script.Verificador(self.config()).argumentos()
        self.assertIn("--spring.jpa.hibernate.ddl-auto=validate", argumentos)
        self.assertIn("--catalogo.carga-inicial.enabled=false", argumentos)
        self.assertIn("--spring.sql.init.mode=never", argumentos)
        self.assertNotIn(self.config().clave, str(argumentos))

    def test_fallo_prueba_solo_detiene_java(self):
        verificador = script.Verificador(self.config())
        proceso = MagicMock()
        proceso.poll.return_value = None
        with patch.object(script.subprocess, "Popen", return_value=proceso), patch.object(verificador, "obtener", return_value=(200, [])), patch("pathlib.Path.open", return_value=MagicMock()), patch("pathlib.Path.mkdir"), patch.object(script, "docker") as docker:
            with self.assertRaisesRegex(RuntimeError, "prueba fallida"):
                with verificador.servicio("prueba.log"):
                    raise RuntimeError("prueba fallida")
            proceso.terminate.assert_called_once()
            docker.assert_not_called()

    def test_compara_datos_incluso_si_falla_prueba(self):
        verificador = script.Verificador(self.config())
        with patch.object(verificador, "comprobar_entorno"), patch.object(verificador, "snapshot", return_value="datos e IDs") as snapshot, patch.object(verificador, "servicio", return_value=contextlib.nullcontext()):
            with self.assertRaisesRegex(RuntimeError, "fallo"):
                verificador.ejecutar(lambda: (_ for _ in ()).throw(RuntimeError("fallo")), "prueba.log")
            self.assertEqual(snapshot.call_count, 2)


if __name__ == "__main__":
    unittest.main()
