#!/usr/bin/env python3
"""Prueba destructiva SOLO dentro de un contenedor nuevo, sin volumenes existentes."""
import os
from pathlib import Path
import subprocess
import tempfile
import time
import uuid
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[3]
SQL = ROOT / 'migrations/mysql'
PASSWORD = 'migration-test-only'  # Exclusivamente para el contenedor efimero.

def run(args, **kwargs):
    return subprocess.run([str(x) for x in args], text=True, capture_output=True, check=True, **kwargs).stdout


def main():
    with tempfile.TemporaryDirectory(prefix='gas-volcan-v09-') as work:
        work = Path(work)
        # Los artefactos se construyen previamente con los POM reales; no se edita ningun modulo.
        for module in ('ms-pedidos', 'ms-catalogo'):
            jar = ROOT / module / 'target' / f'{module}-0.0.1-SNAPSHOT.jar'
            with ZipFile(jar) as z:
                for name in z.namelist():
                    if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
                        (work / Path(name).name).write_bytes(z.read(name))
        cp = os.pathsep.join([str(work / '*'), str(work),
            str(ROOT / 'ms-pedidos/target/classes'), str(ROOT / 'ms-catalogo/target/classes')])
        run(['javac', '-cp', cp, '-d', work, SQL / 'validation/SchemaProbe.java'])
        name = 'gas-volcan-v09-test-' + uuid.uuid4().hex[:12]
        container = run(['docker', 'run', '-d', '--name', name,
            '-e', 'MYSQL_ROOT_PASSWORD=' + PASSWORD, '-p', '127.0.0.1::3306', 'mysql:8.4']).strip()
        try:
            def mysql(statement, db=None, check=True):
                cmd = ['docker', 'exec', '-i', '-e', 'MYSQL_PWD=' + PASSWORD, container,
                    'mysql', '-uroot', '--batch', '--skip-column-names']
                if db: cmd += ['--database', db]
                result = subprocess.run(cmd, input=statement, text=True, capture_output=True)
                if check and result.returncode:
                    raise RuntimeError(result.stderr)
                return result

            for _ in range(90):
                if mysql('SELECT 1;', check=False).returncode == 0: break
                time.sleep(1)
            else: raise RuntimeError('MySQL no arranco en 90 segundos')
            port = run(['docker', 'port', container, '3306']).strip().split(':')[-1]

            def probe(db, module, mode='validate'):
                run(['java', '-cp', cp, 'SchemaProbe',
                    f'jdbc:mysql://127.0.0.1:{port}/{db}', mode, module])

            def apply(db, module):
                mysql((SQL / f'09_{module}.sql').read_text(), db)

            def index_count(db):
                return mysql('SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE();', db).stdout

            tables = {'pedidos': ['boletas', 'detalles_boleta', 'operaciones_compra'],
                      'catalogo': ['categorias', 'productos', 'operaciones_stock', 'operacion_stock_productos']}
            ddl = {}
            for module in tables:
                # Una base creada por Hibernate debe aceptar la migracion sin duplicar indices.
                current = 'current_' + module
                mysql(f'CREATE DATABASE {current};')
                probe(current, module, 'create-only')
                ddl[module] = {}
                for table in tables[module]:
                    row = mysql(f'SHOW CREATE TABLE `{table}`;', current).stdout.strip()
                    # --batch escapa saltos de linea y tabulaciones del DDL.
                    ddl[module][table] = row.split('\t', 1)[1].replace('\\n', '\n').replace('\\t', '\t')
                before = index_count(current)
                apply(current, module); apply(current, module)
                assert before == index_count(current), 'Se duplicaron indices Hibernate'
                probe(current, module)
                print(module + ': esquema Hibernate actual, repeticion y validacion JPA OK', flush=True)

                # Inicializacion vacia exclusivamente mediante SQL versionado.
                fresh = 'fresh_' + module
                mysql(f'CREATE DATABASE {fresh};')
                apply(fresh, module)
                before = index_count(fresh)
                apply(fresh, module)
                assert before == index_count(fresh)
                probe(fresh, module)
                print(module + ': base vacia y repeticion OK', flush=True)

                # Esquema previo a tarjeta 09: sin columnas nuevas ni tablas durables.
                legacy = 'legacy_' + module
                mysql(f'CREATE DATABASE {legacy};')
                excluded = {'version', 'operacion_stock_id', 'nombre_invitado', 'correo_invitado', 'telefono_invitado'}
                base_tables = tables[module][:2]
                for table in base_tables:
                    lines = ddl[module][table].splitlines()
                    lines = [line for line in lines if not any('`' + col + '`' in line for col in excluded)]
                    statement = '\n'.join(lines).replace(',\n)', '\n)').replace('`cliente_id` bigint DEFAULT NULL', '`cliente_id` bigint NOT NULL')
                    mysql(statement + ';', legacy)
                if module == 'pedidos':
                    mysql("INSERT INTO boletas(id,cliente_id,direccion,comuna,estado,fecha,total) VALUES (41,10,'Direccion sintetica','Comuna','PENDIENTE','2026-01-01 12:00:00',25.50); INSERT INTO detalles_boleta(id,boleta_id,producto_id,cantidad,precio) VALUES(81,41,99,3,8.50);", legacy)
                    snapshots = ["SELECT id,cliente_id,direccion,comuna,estado,fecha,total FROM boletas", "SELECT * FROM detalles_boleta"]
                else:
                    mysql("INSERT INTO categorias(id,nombre) VALUES(7,'Categoria sintetica'); INSERT INTO productos(id,categoria_id,codigo,nombre,precio_residencial,precio_comercial,stock,activo,unidad) VALUES(99,7,'TEST-99','Producto sintetico',8.50,9.00,30,1,'Unidad');", legacy)
                    snapshots = ['SELECT * FROM categorias', 'SELECT id,codigo,stock,precio_residencial,categoria_id FROM productos']
                before_data = [mysql(q, legacy).stdout for q in snapshots]
                apply(legacy, module); before = index_count(legacy); apply(legacy, module)
                assert before == index_count(legacy)
                assert before_data == [mysql(q, legacy).stdout for q in snapshots]
                assert mysql(f'SELECT version FROM {base_tables[0] if module == "pedidos" else "productos"};', legacy).stdout.strip() == '0'
                probe(legacy, module)
                if module == 'pedidos':
                    mysql("INSERT INTO boletas(cliente_id,direccion,comuna,estado,fecha,total,nombre_invitado,correo_invitado,telefono_invitado) VALUES(NULL,'Direccion sintetica','Comuna','PENDIENTE',NOW(),8.50,'Invitado sintetico','invitado@example.test','123456789');", legacy)
                    mysql("INSERT INTO operaciones_compra(id,solicitud,estado,creada,ultima_revision) VALUES('test-long',REPEAT('x',4096),'REGISTRADA',NOW(),NOW());", legacy)
                    assert mysql("SELECT LENGTH(solicitud) FROM operaciones_compra WHERE id='test-long';", legacy).stdout.strip() == '4096'
                print(module + ': datos historicos intactos, version 0 y validacion JPA OK', flush=True)

            # Ensanchamiento del antiguo TINYTEXT sin truncar solicitudes pendientes.
            mysql('CREATE DATABASE old_lob;')
            old = ddl['pedidos']['operaciones_compra'].replace('`solicitud` longtext', '`solicitud` tinytext CHARACTER SET latin1 COLLATE latin1_bin')
            mysql(old + "; INSERT INTO operaciones_compra(id,solicitud,estado,creada,ultima_revision) VALUES('pendiente','contenido sintetico','REGISTRADA',NOW(),NOW());", 'old_lob')
            apply('old_lob', 'pedidos'); apply('old_lob', 'pedidos'); probe('old_lob', 'pedidos')
            assert mysql("SELECT solicitud FROM operaciones_compra WHERE id='pendiente';", 'old_lob').stdout.strip() == 'contenido sintetico'
            assert mysql("SELECT collation_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='solicitud';", 'old_lob').stdout.strip() == 'latin1_bin'
            mysql("UPDATE operaciones_compra SET solicitud=REPEAT('x',4096) WHERE id='pendiente';", 'old_lob')
            assert mysql("SELECT LENGTH(solicitud) FROM operaciones_compra;", 'old_lob').stdout.strip() == '4096'
            print('TINYTEXT a LONGTEXT: contenido conservado, 4096 caracteres OK', flush=True)

            # Drift incompatible: SIGNAL antes de modificar tablas o datos existentes.
            mysql('CREATE DATABASE incompatible;')
            mysql(ddl['pedidos']['boletas'].replace('`cliente_id` bigint', '`cliente_id` int') + ';', 'incompatible')
            result = mysql((SQL / '09_pedidos.sql').read_text(), 'incompatible', check=False)
            assert result.returncode != 0 and 'tipo o nulabilidad incompatible' in result.stderr
            assert mysql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE();", 'incompatible').stdout.strip() == '1'
            print('Drift incompatible: rechazo previo a DDL de tablas OK', flush=True)
            # Duplicados: no eliminarlos para forzar la creacion de un indice unico.
            mysql('CREATE DATABASE duplicates;')
            lines = [line for line in ddl['pedidos']['boletas'].splitlines() if 'UNIQUE KEY' not in line]
            mysql('\n'.join(lines).replace(',\n)', '\n)') + ';', 'duplicates')
            mysql("INSERT INTO boletas(cliente_id,direccion,comuna,estado,fecha,total,operacion_stock_id) VALUES (10,'Prueba','Comuna','PENDIENTE',NOW(),1,'duplicada'),(11,'Prueba','Comuna','PENDIENTE',NOW(),1,'duplicada');", 'duplicates')
            result = mysql((SQL / '09_pedidos.sql').read_text(), 'duplicates', check=False)
            assert result.returncode != 0 and 'duplicados' in result.stderr
            assert mysql('SELECT COUNT(*) FROM boletas;', 'duplicates').stdout.strip() == '2'
            print('Duplicados: rechazo sin eliminar registros OK', flush=True)

            # Huerfanos: no borrarlos ni introducir una relacion diferente.
            mysql('CREATE DATABASE orphans;')
            mysql(ddl['pedidos']['boletas'] + ';', 'orphans')
            lines = [line for line in ddl['pedidos']['detalles_boleta'].splitlines() if 'CONSTRAINT' not in line]
            mysql('\n'.join(lines).replace(',\n)', '\n)') + ';', 'orphans')
            mysql('INSERT INTO detalles_boleta(id,boleta_id,producto_id,cantidad,precio) VALUES(81,99,1,1,1);', 'orphans')
            result = mysql((SQL / '09_pedidos.sql').read_text(), 'orphans', check=False)
            assert result.returncode != 0 and 'huerfanas' in result.stderr
            assert mysql('SELECT boleta_id FROM detalles_boleta;', 'orphans').stdout.strip() == '99'
            print('Huerfanos: rechazo sin eliminar registros OK', flush=True)
            print('VALIDACION MYSQL 8.4 COMPLETA OK', flush=True)
        finally:
            # Se elimina exclusivamente el ID creado por esta ejecucion; nunca volumenes del proyecto.
            run(['docker', 'rm', '-f', container])


if __name__ == '__main__':
    main()
