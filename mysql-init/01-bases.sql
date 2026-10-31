CREATE DATABASE IF NOT EXISTS volcan_usuarios;
CREATE DATABASE IF NOT EXISTS volcan_tienda;
GRANT ALL PRIVILEGES ON volcan_usuarios.* TO 'volcan'@'%';
GRANT ALL PRIVILEGES ON volcan_tienda.* TO 'volcan'@'%';
