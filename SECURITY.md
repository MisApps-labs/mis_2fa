# Seguridad

## Informar de una vulnerabilidad

**No abras una incidencia pública** para fallos de seguridad. Usa el aviso privado de GitHub:
pestaña **Security → Report a vulnerability** del repositorio
([enlace directo](https://github.com/ferlagod/mis_2fa/security/advisories/new)).

Indica la versión de la app, los pasos para reproducirlo y el impacto. Intentaré responder en un plazo de 7 días.

Este es un proyecto personal mantenido en el tiempo libre y **no ha pasado una auditoría de seguridad independiente**.

## Modelo de amenazas

Mis 2FA protege tus claves secretas frente a:

| Amenaza | Protección |
|---------|------------|
| Quien controla o accede al servidor Nextcloud (administrador, atacante, intercepción del tráfico) | La bóveda viaja y se guarda cifrada de extremo a extremo con tu contraseña maestra. El servidor no ve ni el número de cuentas ni sus nombres. |
| Quien copia los ficheros de la app (copia de seguridad, otro dispositivo, análisis forense sin desbloquear el Keystore) | Todo lo guardado en el dispositivo está cifrado con una clave del Android Keystore que no se puede extraer. |
| Quien coge tu teléfono desbloqueado | Bloqueo de la app con biometría fuerte o PIN; nueva autenticación antes de exportar o mostrar un QR. |
| Capturas de pantalla, grabación y vista de "Recientes" | `FLAG_SECURE` activado por defecto. |
| Otras apps que leen el portapapeles | Códigos marcados como sensibles y borrado automático. |
| Una web que abre un enlace `otpauth://` para colarte una cuenta suya | Los enlaces externos se confirman antes de añadirse. |
| Gestores de contraseñas que ofrecen guardar lo que escribes (p. ej. el de Google) | Toda la app, incluidos los diálogos, está excluida del autocompletado: ni las claves secretas ni las contraseñas salen hacia ellos. |
| Apps que dibujan encima para engañarte a pulsar algo (*tapjacking*) | Se ignoran los toques cuando la ventana está tapada por otra app. |
| Pérdida de datos por fallos, borrados por error o conflictos | Ver "Integridad y disponibilidad". |

**No protege** frente a un teléfono comprometido (root, malware con permisos de accesibilidad) mientras la app está desbloqueada, ni frente a quien conozca tu contraseña maestra y tenga acceso a tu Nextcloud.

## Diseño criptográfico

### Almacenamiento en el dispositivo

- Las cuentas se guardan en un único fichero (`files/vault/vault.bin`) cifrado con **AES-256-GCM** con una clave del **Android Keystore** (alias `mis2fa_local_key`), generada en **StrongBox** cuando el dispositivo lo tiene y, si no, en el TEE. La clave no es exportable.
- AAD: `mis2fa:local-vault` (la bóveda) y `mis2fa:sync-config` (credenciales de Nextcloud y clave de sincronización, en `files/secure/sync.bin`).
- No se usa `EncryptedSharedPreferences` (obsoleto): el cifrado lo hace la app directamente con el Keystore.
- La clave no está ligada a la autenticación del usuario para que la sincronización en segundo plano pueda funcionar. La protección frente a quien tiene el teléfono desbloqueado la da el bloqueo de la app.
- `allowBackup=false` y reglas de extracción que excluyen todo: los datos nunca van a la copia de Google ni a la transferencia entre dispositivos (además serían ilegibles fuera de este Keystore).

### Cifrado con contraseña (sincronización y copias exportadas)

- Derivación: `PBKDF2WithHmacSHA256`, **600.000 iteraciones**, salt aleatorio de 16 bytes, clave de 256 bits.
- Cifrado: `AES/GCM/NoPadding`, IV aleatorio de 12 bytes por operación, etiqueta de 128 bits.
- El fichero incluye un **verificador** (`mis2fa-key-check-v1` cifrado) para distinguir una contraseña incorrecta de un fichero dañado, y para que un dispositivo con una contraseña equivocada no pueda subir nada.
- AAD distinto para el verificador y los datos, y distinto para la bóveda de Nextcloud (`mis2fa-vault:*`) y las copias exportadas (`mis2fa-backup:*`): un bloque no se puede trasplantar de un fichero a otro.
- Al desbloquear se prueban las formas Unicode equivalentes de la contraseña (NFC/NFD, espacio final), para que una contraseña correcta escrita con otro teclado no deje fuera al usuario.
- Formato completo en [docs/SYNC_FORMAT.md](docs/SYNC_FORMAT.md).

### Transporte

- Solo **HTTPS** (`cleartextTrafficPermitted=false` y rechazo de URLs `http://`).
- Se confía en las autoridades del sistema y en las **instaladas por el usuario**, para permitir un Nextcloud propio con CA privada. Aunque alguien interceptara el TLS, la bóveda va cifrada de extremo a extremo; lo único expuesto sería la contraseña de aplicación de Nextcloud. Ojo: esa contraseña da acceso a **todos** tus archivos de Nextcloud, no solo a `Mis2FA/`. Una CA instalada por una app maliciosa, por tu empresa (perfil de trabajo, MDM) o por un proxy de inspección podría capturarla. Si no usas una CA privada, no instales certificados de usuario; y si sospechas que la contraseña de aplicación se ha expuesto, revócala en Nextcloud (Ajustes → Seguridad → Dispositivos y sesiones).
- Las redirecciones HTTP no se siguen (las credenciales nunca se envían a otro sitio).
- Autenticación con *contraseña de aplicación* de Nextcloud (HTTP Basic sobre TLS).

## Integridad y disponibilidad

- **Escritura atómica** de la bóveda local e **instantáneas cifradas** automáticas (máx. 15, una por hora). Si la bóveda no se puede leer, se restaura la instantánea legible más reciente; si ninguna lo es, la app no escribe nada hasta que el problema se resuelva.
- **Concurrencia**: la subida usa `If-Match` con el ETag descargado. Si otro dispositivo subió antes, el servidor responde 412 y se repite descarga + fusión.
- **Fusión que solo suma**: la ausencia de una cuenta nunca la borra. Solo una lápida (borrado definitivo desde la papelera) elimina una cuenta, y solo si no se ha modificado después. Si una cuenta cambia en dos sitios gana la modificación más reciente; en HOTP se conserva siempre el contador más alto.
- Esto también limita el efecto de un **rollback** del servidor (devolver una versión antigua del fichero): lo antiguo pierde frente a lo local, y los borrados locales se mantienen.
- Un fichero del servidor dañado o de otra versión **no se sobrescribe**: la sincronización se detiene y se avisa.

## Limitaciones conocidas

- **Versiones antiguas en Nextcloud.** Nextcloud guarda versiones anteriores de `Mis2FA/vault.json` (y una copia en la papelera si se borra). Al cambiar la contraseña maestra, esas versiones siguen cifradas con la contraseña **antigua**: si la cambias porque alguien podría conocerla, borra también las versiones (pestaña *Versiones* del fichero) y vacía *Archivos eliminados*. La app lo recuerda en el diálogo de cambio de contraseña.
- PBKDF2 es más débil frente a ataques con GPU que Argon2. Se eligió por estar disponible en Android sin código nativo; usa una contraseña maestra larga.
- Las lápidas de borrado van dentro del bloque cifrado, pero el tamaño del fichero y las fechas de modificación de WebDAV son visibles para el servidor.
- No se importan las exportaciones **cifradas** de Aegis, 2FAS ni Ente Auth (habría que añadir scrypt/Argon2): hay que exportarlas sin cifrar y borrar el fichero después.
