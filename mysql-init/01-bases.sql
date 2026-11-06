-- Una base por microservicio: cada uno es dueño de sus datos.
CREATE DATABASE IF NOT EXISTS volcan_usuarios;
CREATE DATABASE IF NOT EXISTS volcan_catalogo;
CREATE DATABASE IF NOT EXISTS volcan_pedidos;
GRANT ALL PRIVILEGES ON volcan_usuarios.* TO 'volcan'@'%';
GRANT ALL PRIVILEGES ON volcan_catalogo.* TO 'volcan'@'%';
GRANT ALL PRIVILEGES ON volcan_pedidos.* TO 'volcan'@'%';
