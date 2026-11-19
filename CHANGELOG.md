# Cambios

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones según [SemVer](https://semver.org/lang/es/).

## [1.0.0] - Sin publicar

Primera versión.

### Añadido
- Códigos TOTP, HOTP y Steam Guard (SHA-1, SHA-256 y SHA-512; de 4 a 10 cifras; cualquier periodo).
- Añadir cuentas escaneando un QR (CameraX + ZXing, sin servicios de Google), desde una imagen, escribiendo la clave o abriendo un enlace `otpauth://` (con confirmación).
- Importar desde Google Authenticator (QR de transferencia, también en varios lotes), Aegis y 2FAS (sin cifrar), Ente Auth (texto) y listas de enlaces `otpauth://`.
- Exportar una copia cifrada con contraseña o en texto `otpauth://`.
- Sincronización cifrada de extremo a extremo con Nextcloud: un único fichero, subidas con `If-Match`, fusión que nunca borra por ausencia y cambio de contraseña maestra.
- Bóveda local cifrada con el Android Keystore (StrongBox si está disponible), escritura atómica e instantáneas automáticas con recuperación.
- Papelera sin vaciado automático y "Deshacer".
- Bloqueo con biometría fuerte o PIN (inmediato, 1 o 5 minutos), nueva autenticación antes de exportar o mostrar un QR, capturas bloqueadas, códigos ocultables y portapapeles sensible con borrado automático.
- Tutorial de bienvenida en la primera instalación (4 pantallas que se pueden saltar), que se puede volver a ver desde Ajustes; la app empieza sin cuentas.
- Siguiente código, anillo de cuenta atrás, búsqueda, cuentas fijadas y orden configurable.
- Exclusión total del autocompletado (las claves nunca se ofrecen a un gestor de contraseñas) y protección contra *tapjacking*.
- Tema claro, oscuro y negro puro; icono temático; español, inglés, francés, alemán e italiano.
