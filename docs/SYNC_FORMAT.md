# Formato de sincronización

Mis 2FA guarda en Nextcloud **un único fichero**: `Mis2FA/vault.json` (ruta WebDAV `remote.php/dav/files/<usuario>/Mis2FA/vault.json`).

Las copias de seguridad cifradas que se exportan desde Ajustes usan **el mismo formato** con `"format": "mis2fa-backup"`.

## Sobre

```json
{
  "format": "mis2fa-vault",
  "version": 1,
  "kdf": {
    "name": "PBKDF2WithHmacSHA256",
    "iterations": 600000,
    "salt": "<Base64, 16 bytes>"
  },
  "check": "<Base64>",
  "data": "<Base64>"
}
```

| Campo | Contenido |
|-------|-----------|
| `format` | `mis2fa-vault` (Nextcloud) o `mis2fa-backup` (copia exportada). |
| `version` | Versión del formato. Una app que lea un número mayor que el suyo no toca el fichero. |
| `kdf` | Parámetros públicos para derivar la clave de la contraseña maestra. Iteraciones aceptadas: 100.000–10.000.000; salt de al menos 16 bytes. |
| `check` | `AES-GCM(clave, "mis2fa-key-check-v1")` con AAD `<format>:check`. |
| `data` | `AES-GCM(clave, JSON de la bóveda)` con AAD `<format>:data`. |

Cada bloque cifrado es `IV (12 bytes) ‖ texto cifrado ‖ etiqueta (16 bytes)` codificado en Base64.

## Bóveda (descifrada)

```json
{
  "version": 1,
  "tokens": [
    {
      "id": "2f9c…",
      "issuer": "GitHub",
      "account": "octocat",
      "secret": "JBSWY3DPEHPK3PXP",
      "type": "TOTP",
      "algorithm": "SHA1",
      "digits": 6,
      "period": 30,
      "counter": 0,
      "pinned": false,
      "icon": "iVBORw0KGgo…",
      "createdAt": 1763540000000,
      "updatedAt": 1763540000000,
      "trashedAt": 1763600000000
    }
  ],
  "tombstones": { "<id>": 1763700000000 }
}
```

- `type`: `TOTP`, `HOTP` o `STEAM`. `algorithm`: `SHA1`, `SHA256` o `SHA512`.
- `secret`: Base32 (RFC 4648) en mayúsculas, sin espacios ni relleno.
- `icon`: opcional. Icono del servicio como PNG de 96 px en Base64 (máx. 64 KB); se ignora si es mayor.
- `trashedAt`: solo si la cuenta está en la papelera.
- `tombstones`: cuentas eliminadas definitivamente (id → momento del borrado).
- Fechas en milisegundos desde 1970 (UTC). Cada modificación local garantiza un `updatedAt` mayor que el anterior aunque el reloj retroceda.

## Algoritmo de sincronización

1. `PROPFIND` de la carpeta (y `MKCOL` si no existe).
2. `GET vault.json` → contenido + ETag.
   - Si no existe: `PUT` con la bóveda local e `If-None-Match: *`.
3. Comprobar que `kdf` coincide con el guardado en el dispositivo y que `check` se descifra con la clave local. Si no: la contraseña se cambió en otro dispositivo → se pide la nueva y no se sube nada.
4. Descifrar `data` y **fusionar** con la bóveda local (abajo).
5. Si el resultado difiere de lo que hay en el servidor: `PUT` con `If-Match: <ETag>`. Si responde **412**, volver al paso 2 (hasta 4 intentos).

### Reglas de fusión

La fusión es conmutativa: dos dispositivos con los mismos datos llegan al mismo resultado.

- `tombstones` = unión; si un id aparece en los dos, el borrado más reciente.
- Para cada id presente en algún lado:
  - Gana la versión con `updatedAt` mayor (empate: la de JSON mayor en orden lexicográfico).
  - `counter` = máximo de los dos (HOTP nunca repite un código).
  - Si hay lápida y `deletedAt ≥ updatedAt`, la cuenta se elimina.
- Una cuenta que falta en un lado **no** se borra del otro.
- Tras fusionar, se descartan las lápidas de hace más de **180 días**. Si un dispositivo pasa más tiempo sin sincronizar, una cuenta que se borró en otro puede reaparecer (nunca se pierde una que no se borró).

## Cambio de contraseña maestra

Se sincroniza primero; luego se genera un salt nuevo, se deriva la clave nueva y se sube la bóveda recifrada con `If-Match`. Los demás dispositivos detectan en el paso 3 que `kdf` ha cambiado y piden la contraseña nueva.
