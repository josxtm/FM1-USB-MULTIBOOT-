# FM1 Manager To USB v1.1 alpha03

## Actualizaciones

**Buscar actualizaciones** consulta el catálogo agregado. También se comprueba al abrir la aplicación si han pasado seis horas desde la última comprobación correcta. La descarga de paquetes se inicia desde la selección del usuario. La tablet obtiene dos archivos HTTPS públicos de la rama alpha03 del repositorio oficial, `discovery.json` y `discovery.sha256`. Comprueba el hash, el esquema, el tamaño y la fecha de generación UTC. Rechaza instantáneas de más de 14 días, fechas adelantadas más de 10 minutos y revisiones anteriores al catálogo activo. El SHA‑256 detecta un traslado incompleto o una pareja desincronizada. La procedencia depende de que ambos archivos se obtengan del repositorio oficial o del mismo artefacto de CI.

En **Actualizaciones** aparecen las versiones de la tarjeta y la candidata, con fecha y notas de la publicación cuando están disponibles. Puedes seleccionar una, varias o todas las candidatas habilitadas. **Omitir** conserva la decisión para ese proyecto y paquete. Una revisión posterior vuelve a aparecer. **Recuperar omitidas** limpia esas preferencias.

Se coteja el identificador estable del proyecto con autor y repositorio de publicación. Se ordenan versiones numéricas semánticas, incluidas las preversiones, y etiquetas de fecha válidas. Una compilación comunitaria necesita su revisión exacta y un antecedente del mismo origen con fecha de publicación comprobada. La app trata una versión idéntica con hash nuevo o un cambio de origen como **Versión no comparable**. Excluye prepublicaciones, descargas retiradas y paquetes cuya procedencia o licencia no pasa el filtro. No reinstala paquetes presentes por SHA‑256. Conserva las versiones antiguas de la tarjeta y los metadatos históricos del manifiesto.

El generador sigue usando las fuentes ya aprobadas. Para seguir nuevos Releases del autor toma como referencia el activo identificado en el catálogo previo o en The Firmware Hub. Admite activos únicos con el mismo nombre o la misma familia cuando el nombre incluye la etiqueta de versión. Conserva el historial comprobado y marca una publicación actual por ficha, salvo empates de fecha que requieren ordenación adicional. La instantánea inicial tiene 913 fichas, 410 paquetes actuales y 1.463 activos comprobados contando el historial.

### Publicar una actualización del catálogo

El workflow *Refresh FM1 discovery manifest* genera un artefacto con `discovery.json` y `discovery.sha256`. La programación de GitHub sólo se activa con el workflow en la rama predeterminada. Mientras el cambio siga en borrador, una persona con acceso al repositorio puede generar manualmente el par en la rama de trabajo y publicarlo mediante un único commit revisado:

```bash
python3 scripts/update_manager_catalog.py \
  --snapshot-dir /tmp/fm1-discovery-refresh \
  --previous android/app/src/manager/assets/discovery.json \
  --output /tmp/fm1-discovery-output/discovery.json
python3 scripts/audit_manager_alpha03.py /tmp/fm1-discovery-output/discovery.json
```

El generador lee metadatos públicos y comprueba los activos con HEAD. Necesita Python 3 y GitHub CLI con acceso a las API públicas. El comando crea la instantánea fuera del árbol para facilitar la revisión. En Android, **Más → Cargar manifiesto** permite seleccionar los dos archivos del mismo artefacto sin esperar a la publicación automática. Los fallos de red o validación dejan disponible el catálogo anterior.

## Copia y expulsión

La transferencia comprueba el espacio de cada archivo cuando el proveedor SAF expone el espacio libre del volumen. Cuando falta ese dato sólo la escritura permite confirmar que hay espacio suficiente. Durante la copia se contabilizan los bloques que el flujo de Android aceptó. Luego se cierra y se relee cada original, se verifica su SHA‑256 y se publica el índice en el slot alternativo. La relectura del índice termina la operación. Los duplicados verificados cuentan como reutilizados, con cero bytes escritos. La barra puede alcanzar el 100 % de bytes mientras sigue la verificación. Los índices FM1CAT v2 y las rutas 8.3 de `FM1/FW` conservan el formato previo.

**Cancelar** se ofrece hasta iniciar la publicación. Si la aplicación se cierra o se retira el lector, el índice anterior permite recuperar la biblioteca. Las copias completas sin publicar se vuelven a comprobar y se reutilizan al reintentar. Un archivo parcial restante se trata como conflicto y se debe revisar desde Android. Los archivos y el índice de la tarjeta se preservan durante el cambio de APK.

Cuando la app muestra **Copia y verificación finalizadas**, abre **Almacenamiento** o la notificación de la unidad USB, pulsa **Expulsar** y espera la confirmación del sistema antes de retirar el lector. El enlace de la app utiliza el Intent público de ajustes de almacenamiento con fallback a los ajustes generales. SAF carece de una API pública para que una aplicación ordinaria desmonte ese volumen por su cuenta.

## Firma Android de prueba

La versión es `1.1-alpha03`, código 3 e identificador `com.josxtm.fm1managerusb`. El workflow admite reutilizar un almacén privado con los secretos `MANAGER_KEYSTORE_BASE64`, `MANAGER_KEYSTORE_PASSWORD`, `MANAGER_KEY_ALIAS` y `MANAGER_KEY_PASSWORD`. Configúralos juntos para conservar la misma firma en futuras compilaciones. Guarda una copia privada del almacén. Mientras no estén configurados, GitHub Actions usa la firma de depuración del ejecutor. El certificado publicado de alpha02 permite comparar firmas, pero no contiene su clave privada.

Si Android rechaza la actualización por firma distinta, comprueba primero que la microSD contiene tu carpeta `FM1` y confirma que puedes abrirla desde Archivos. Después puedes desinstalar únicamente la app anterior e instalar alpha03. Android elimina la caché, preferencias y permisos internos al desinstalar. En alpha03, vuelve a conceder acceso a la misma carpeta `FM1` y recarga su índice. Los archivos registrados en la microSD siguen siendo utilizables.

## Validación y límites

CI genera `copy-progress-matrix.json` con cantidades de 1 a 4.096 originales sintéticos, tiempos del anfitrión, bytes escritos e integridad verificada. Los tiempos corresponden a un almacén simulado en memoria. La prueba de relectura FAT32 para FM1CAT v1 sigue ejecutándose junto con el verificador FM1CAT v2 independiente. Las pruebas Compose instrumentadas cubren la navegación, el marcador cuando falta una imagen y los estados de la barra de copia en dos tamaños de emulador. La velocidad y los avisos del proveedor SAF con el lector real, así como la ergonomía y la confirmación de expulsión en la tablet de 13,4 pulgadas, requieren prueba física.
