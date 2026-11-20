# Cambios

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones según [SemVer](https://semver.org/lang/es/).

## [1.0.1] - Sin publicar

### Corregido
- Importar una copia de seguridad después de editar una cuenta la duplicaba con el mismo id: la lista se cerraba y al sincronizar se perdía una de las dos. Ahora cada cuenta importada recibe un id nuevo.
- Una cuenta guardada con valores fuera de rango (periodo 0, más de 10 cifras) cerraba la app en cada arranque. Se corrigen al leerla; un secreto no válido se muestra como error en lugar de un código falso.
- Con el bloqueo activado y las capturas permitidas, los códigos se veían en la miniatura de "Recientes".
- Con la app bloqueada, TalkBack y otros servicios de accesibilidad podían leer los códigos de debajo del bloqueo, y un campo con foco seguía recibiendo lo tecleado.
- El portapapeles no se borraba si el sistema cerraba la app antes de tiempo (ahora hay un borrado de respaldo con WorkManager, que solo guarda la huella del código).
- Dos primeras escrituras simultáneas podían generar dos veces la clave del Keystore.
- La sincronización no terminaba nunca con servidores que no envían ETag.
- Se limita a 5 MB lo que se descarga de Nextcloud.
- El cifrado de la configuración de sincronización ya no se hace en el hilo principal.
- Los errores de sincronización se clasificaban mal ("dirección incorrecta" para errores de cifrado, entre otros).

### Seguridad
- Las GitHub Actions se fijan por SHA de commit.
- El diálogo de cambio de contraseña y SECURITY.md avisan de que las versiones antiguas de `vault.json` en Nextcloud siguen cifradas con la contraseña anterior, y SECURITY.md explica el riesgo de las CA de usuario para la contraseña de aplicación.

### Mejorado
- Conexión con Nextcloud para quien inicia sesión con su email o LDAP (se consulta el ID de usuario real).
- Mensaje claro cuando la copia del servidor o un fichero importado es de una versión más nueva de la app.
- Los QR de dos exportaciones distintas de Google Authenticator ya no se mezclan.
- Las lápidas de borrado de más de 180 días se descartan.
- El reloj de la pantalla de inicio se para en segundo plano y la sincronización al volver a la app se hace como mucho cada 30 s.
- Más tests: importación con ids repetidos, datos fuera de rango, rutas WebDAV, caducidad de lápidas y casos límite de la fusión.

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
