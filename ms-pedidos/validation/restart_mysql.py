#!/usr/bin/env python3
"""Servicios reales + MySQL nuevo sin volumenes. No utiliza Compose ni .env."""
import argparse
import base64
import hashlib
import hmac
import http.client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import threading
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[2]


def port():
    with socket.socket() as s:
        s.bind(('127.0.0.1', 0))
        return s.getsockname()[1]


def token(key, claims):
    now = int(time.time())
    claims = {'iat': now, 'exp': now + 300, **claims}
    def encode(obj):
        return base64.urlsafe_b64encode(json.dumps(obj, separators=(',', ':')).encode()).rstrip(b'=')
    data = encode({'alg': 'HS256', 'typ': 'JWT'}) + b'.' + encode(claims)
    sig = base64.urlsafe_b64encode(hmac.new(key.encode(), data, hashlib.sha256).digest()).rstrip(b'=')
    return (data + b'.' + sig).decode()


def request(base, method, path, body=None, jwt=None, key=None):
    headers = {'Content-Type': 'application/json'}
    if jwt: headers['Authorization'] = 'Bearer ' + jwt
    if key: headers['Idempotency-Key'] = key
    req = urllib.request.Request(base + path, data=None if body is None else json.dumps(body).encode(),
                                 method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status, json.loads(r.read() or b'null')
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b'null')


def wait_until(predicate, seconds=90):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            result = predicate()
            if result: return result
        except (ConnectionError, OSError): pass
        time.sleep(.25)
    raise AssertionError('Tiempo agotado esperando condicion de prueba')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path, help='Directorio nuevo fuera del repositorio')
    args = parser.parse_args()
    out = args.output.resolve()
    if out.is_relative_to(ROOT): raise ValueError('Guardar evidencias fuera del repositorio')
    out.mkdir(parents=True, exist_ok=False)
    evidence = {'git_head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
                'jwt': {}, 'snapshots': [], 'pids': {}}
    container = None
    processes, files = [], []
    server = None
    db_password = secrets.token_urlsafe(24)
    internal, users = secrets.token_urlsafe(32), secrets.token_urlsafe(32)
    assert internal != users
    env = {k: os.environ[k] for k in ('PATH', 'JAVA_HOME', 'TMPDIR', 'LANG') if k in os.environ}
    env.update(JWT_SECRET=users, INTERNAL_STOCK_JWT_SECRET=internal, MYSQL_PASSWORD=db_password)
    catalog_port, pedidos_port = port(), port()
    catalog, pedidos = f'http://127.0.0.1:{catalog_port}', f'http://127.0.0.1:{pedidos_port}'
    fault = {'blocked': False, 'injected': False, 'discount_posts': 0, 'compensation_puts': 0}

    class Proxy(BaseHTTPRequestHandler):
        def log_message(self, *args): pass
        def relay(self):
            internal_path = self.path.startswith('/internal/v1/stock/')
            if internal_path and fault['blocked']:
                self.send_response(503); self.end_headers(); self.wfile.write(b'{}'); return
            payload = self.rfile.read(int(self.headers.get('Content-Length', '0')))
            headers = {k: v for k, v in self.headers.items() if k.lower() not in ('host', 'connection', 'content-length')}
            conn = http.client.HTTPConnection('127.0.0.1', catalog_port, timeout=10)
            try:
                conn.request(self.command, self.path, body=payload or None, headers=headers)
                response = conn.getresponse(); data = response.read(); status = response.status
                if internal_path and self.command == 'POST':
                    fault['discount_posts'] += 1
                    if status == 200 and not fault['injected']:
                        # El servidor ya confirmo la transaccion; se pierde solo la respuesta.
                        fault['injected'] = True; fault['blocked'] = True
                        status, data = 503, b'{}'
                if internal_path and self.command == 'PUT': fault['compensation_puts'] += 1
                self.send_response(status); self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(data))); self.end_headers(); self.wfile.write(data)
            finally: conn.close()
        do_GET = do_POST = do_PUT = relay

    try:
        name = 'gas-volcan-restart-test-' + uuid.uuid4().hex[:12]
        container = subprocess.check_output(['docker', 'run', '-d', '--name', name,
            '-e', 'MYSQL_ROOT_PASSWORD=' + db_password, '-p', '127.0.0.1::3306', 'mysql:8.4'], text=True).strip()
        evidence['container_name'] = name
        def sql(statement, db=None, check=True):
            cmd = ['docker', 'exec', '-i', '-e', 'MYSQL_PWD=' + db_password, container,
                   'mysql', '-uroot', '--batch', '--skip-column-names']
            if db: cmd += ['--database', db]
            r = subprocess.run(cmd, input=statement, text=True, capture_output=True)
            if check and r.returncode: raise RuntimeError(r.stderr)
            return r.stdout.strip() if check else r.returncode == 0
        wait_until(lambda: sql('SELECT 1;', check=False))
        mysql_port = subprocess.check_output(['docker', 'port', container, '3306'], text=True).strip().split(':')[-1]
        sql('CREATE DATABASE prueba_catalogo; CREATE DATABASE prueba_pedidos;')
        for module in ('catalogo', 'pedidos'):
            sql((ROOT / f'migrations/mysql/09_{module}.sql').read_text(), 'prueba_' + module)
        sql("INSERT INTO categorias(id,nombre) VALUES(1,'Categoria sintetica'); INSERT INTO productos(id,categoria_id,codigo,nombre,precio_residencial,precio_comercial,stock,activo,unidad) VALUES(1,1,'REINICIO-TEST','Producto sintetico',1000,1000,10,1,'Unidad');", 'prueba_catalogo')
        server = ThreadingHTTPServer(('127.0.0.1', 0), Proxy)
        thread = threading.Thread(target=server.serve_forever, daemon=True); thread.start()
        proxy = f'http://127.0.0.1:{server.server_port}'

        def start(module, recovery=False):
            log = open(out / f'{module}-{len(processes)}.log', 'w'); files.append(log)
            db = 'prueba_' + module
            child_env = dict(env, PEDIDOS_RECUPERACION_ENABLED=str(recovery).lower(), PEDIDOS_RECUPERACION_INTERVALO_MS='1000')
            options = ['--server.address=127.0.0.1', f'--server.port={catalog_port if module == "catalogo" else pedidos_port}',
                '--spring.config.location=classpath:/application.properties', '--spring.config.import=',
                f'--spring.datasource.url=jdbc:mysql://127.0.0.1:{mysql_port}/{db}',
                '--spring.datasource.username=root', '--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver',
                '--spring.jpa.hibernate.ddl-auto=validate', '--spring.jpa.show-sql=false', '--spring.sql.init.mode=never',
                '--catalogo.carga-inicial.enabled=false', f'--pedidos.catalogo.url={proxy}']
            proc = subprocess.Popen(['java', '-Xmx256m', '-jar', str(ROOT / f'ms-{module}/target/ms-{module}-0.0.1-SNAPSHOT.jar'), *options],
                                    cwd=out, env=child_env, stdout=log, stderr=subprocess.STDOUT)
            processes.append(proc)
            def healthy():
                if proc.poll() is not None: raise RuntimeError(f'ms-{module} termino: revisar {log.name}')
                return request(catalog if module == 'catalogo' else pedidos, 'GET', '/actuator/health')[0] == 200
            wait_until(healthy)
            return proc

        catalog_proc = start('catalogo'); pedidos_proc = start('pedidos', recovery=False)
        evidence['pids']['before'] = {'catalogo': catalog_proc.pid, 'pedidos': pedidos_proc.pid}
        claims = {'iss': 'ms-pedidos', 'sub': 'ms-pedidos', 'aud': ['ms-catalogo'],
                  'scope': 'stock:descontar stock:consultar stock:compensar', 'jti': str(uuid.uuid4())}
        service_jwt = token(internal, claims)
        customer = token(users, {'sub': 'cliente@example.test', 'id': 10, 'rol': 'CLIENTE'})
        admin = token(users, {'sub': 'admin@example.test', 'id': 1, 'rol': 'ADMINISTRADOR'})
        missing_id = str(uuid.uuid4()); path = '/internal/v1/stock/descuentos/' + missing_id
        for label, jwt, expected in [('valido', service_jwt, 404), ('sin_token', None, 401),
                ('firma_invalida', token('otra-clave-sintetica-de-prueba-123456789', claims), 401), ('jwt_cliente', customer, 401),
                ('vencido', token(internal, {**claims, 'iat': int(time.time())-120, 'exp': int(time.time())-60}), 401)]:
            code, _ = request(catalog, 'GET', path, jwt=jwt)
            assert code == expected, (label, code)
            evidence['jwt'][label] = code
        body_stock = {'productos': [{'productoId': 1, 'cantidad': 3}]}
        for label, jwt in [('sin_token', None), ('firma_invalida', token('otra-clave-sintetica-de-prueba-123456789', claims)), ('jwt_cliente', customer)]:
            code, _ = request(catalog, 'POST', '/internal/v1/stock/descuentos', body_stock, jwt, str(uuid.uuid4()))
            assert code == 401, (label, code)
            evidence['jwt']['post_' + label] = code
        limited = token(internal, {**claims, 'scope': 'stock:consultar'})
        code, _ = request(catalog, 'POST', '/internal/v1/stock/descuentos', body_stock, limited, str(uuid.uuid4()))
        assert code == 403; evidence['jwt']['scope_insuficiente'] = code
        assert sql('SELECT stock FROM productos WHERE id=1;', 'prueba_catalogo') == '10'
        assert sql('SELECT COUNT(*) FROM operaciones_stock;', 'prueba_catalogo') == '0'
        assert request(pedidos, 'GET', '/api/v1/boletas', jwt=customer)[0] == 200
        assert request(pedidos, 'GET', '/api/v1/boletas')[0] == 401
        evidence['jwt']['usuario_valido_pedidos'] = 200
        evidence['jwt']['sin_usuario_pedidos'] = 401
        print('JWT interno y JWT usuario: OK', flush=True)

        key = str(uuid.uuid4()); evidence['idempotency_key'] = key
        purchase = {'clienteId': 999, 'direccion': 'Direccion sintetica', 'comuna': 'Comuna prueba',
                    'fecha': '2026-01-01T12:00:00', 'detalles': [{'productoId': 1, 'cantidad': 3}]}
        def snapshot(label):
            state = sql(f"SELECT estado FROM operaciones_compra WHERE id='{key}';", 'prueba_pedidos')
            snap = {'fase': label, 'compra': state,
                'stock': int(sql('SELECT stock FROM productos WHERE id=1;', 'prueba_catalogo')),
                'boletas': int(sql('SELECT COUNT(*) FROM boletas;', 'prueba_pedidos')),
                'operaciones_compra': int(sql('SELECT COUNT(*) FROM operaciones_compra;', 'prueba_pedidos')),
                'operaciones_stock': int(sql('SELECT COUNT(*) FROM operaciones_stock;', 'prueba_catalogo')),
                'stock_estado': sql(f"SELECT estado FROM operaciones_stock WHERE id='{key}';", 'prueba_catalogo'),
                'lineas_stock': sql(f"SELECT producto_id,cantidad FROM operacion_stock_productos WHERE operacion_id='{key}';", 'prueba_catalogo')}
            evidence['snapshots'].append(snap); print(json.dumps(snap), flush=True); return snap
        code, _ = request(pedidos, 'POST', '/api/v1/boletas', purchase, customer, key)
        assert code == 503 and fault['injected'], (code, fault)
        pending = snapshot('pendiente_antes_reinicio')
        assert pending['compra'] == 'COTIZADA' and pending['stock'] == 7 and pending['boletas'] == 0
        created = sql(f"SELECT creada,solicitud,cotizacion FROM operaciones_compra WHERE id='{key}';", 'prueba_pedidos')
        # Corte real de la JVM. La base MySQL no se reinicia ni se elimina.
        pedidos_proc.kill(); pedidos_proc.wait(timeout=15)
        catalog_proc.terminate(); catalog_proc.wait(timeout=20)
        catalog_proc = start('catalogo'); pedidos_proc = start('pedidos', recovery=True)
        evidence['pids']['after'] = {'catalogo': catalog_proc.pid, 'pedidos': pedidos_proc.pid}
        assert evidence['pids']['before'] != evidence['pids']['after']
        assert created == sql(f"SELECT creada,solicitud,cotizacion FROM operaciones_compra WHERE id='{key}';", 'prueba_pedidos')
        pending = snapshot('pendiente_tras_reinicio_transporte_bloqueado')
        assert pending['compra'] == 'COTIZADA' and pending['stock'] == 7 and pending['boletas'] == 0
        fault['blocked'] = False
        wait_until(lambda: sql(f"SELECT estado FROM operaciones_compra WHERE id='{key}';", 'prueba_pedidos') == 'CONFIRMADA', 30)
        confirmed = snapshot('recuperada_automaticamente')
        assert confirmed['stock'] == 7 and confirmed['boletas'] == 1 and confirmed['stock_estado'] == 'APLICADO'
        code, receipt = request(pedidos, 'POST', '/api/v1/boletas', purchase, customer, key)
        assert code == 201 and receipt['clienteId'] == 10 and receipt['total'] == 3000
        repeated = snapshot('reintento_misma_clave')
        assert repeated['boletas'] == 1 and repeated['stock'] == 7
        assert fault['discount_posts'] == 1, fault
        for _ in range(2):
            code, _ = request(pedidos, 'PUT', f'/api/v1/boletas/{receipt["id"]}/estado', {'estado': 'CANCELADO'}, admin)
            assert code == 200
        wait_until(lambda: sql(f"SELECT estado FROM operaciones_compra WHERE id='{key}';", 'prueba_pedidos') == 'COMPENSADA', 30)
        code, _ = request(pedidos, 'PUT', f'/api/v1/boletas/{receipt["id"]}/estado', {'estado': 'CANCELADO'}, admin)
        assert code == 200
        time.sleep(2)
        cancelled = snapshot('cancelacion_repetida')
        assert cancelled['stock'] == 10 and cancelled['boletas'] == 1 and cancelled['stock_estado'] == 'COMPENSADO'
        assert cancelled['operaciones_compra'] == cancelled['operaciones_stock'] == 1
        assert cancelled['lineas_stock'] == '1\t3' and fault['compensation_puts'] == 1, fault
        assert created == sql(f"SELECT creada,solicitud,cotizacion FROM operaciones_compra WHERE id='{key}';", 'prueba_pedidos')
        code, _ = request(pedidos, 'POST', '/api/v1/boletas', purchase, customer, key)
        assert code == 409
        evidence['reintento_cancelada'] = code
        evidence['transporte'] = dict(fault)
        evidence['result'] = 'PASS'
        print('REINICIO REAL MYSQL 8.4: PASS', flush=True)
    finally:
        for proc in reversed(processes):
            if proc.poll() is None:
                proc.terminate()
                try: proc.wait(timeout=15)
                except subprocess.TimeoutExpired: proc.kill(); proc.wait(timeout=10)
        if server: server.shutdown(); server.server_close()
        for f in files: f.close()
        if container:
            subprocess.run(['docker', 'rm', '-f', container], check=True, capture_output=True)
        evidence['cleanup_completed'] = True
        (out / 'evidence.json').write_text(json.dumps(evidence, indent=2) + '\n')


if __name__ == '__main__':
    main()
