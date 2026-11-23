# Fase 6B · Hardware microSD

La arquitectura final mantiene microSD → selector persistente → instalación controlada en Flash → reinicio → firmware musical. La fase 6B prepara únicamente el diagnóstico RAM. No cambia el bootloader.

| Ruta | Estado |
|---|---|
| USB‑C / MSC | **NO-GO**. El bootloader `2.4-395E6CC` sólo sondea SPI1 para **SD CARD**. Su DOWNLOAD MODE expone el FM‑1 como periférico USB. Sigue sin demostrarse el camino eléctrico CC/VBUS y la ausencia de alimentación inversa en esta placa. |
| SPI1 / m-card | **Preparado en software, pendiente de hardware.** El bootloader y el STG2 se probaron con FAT32 simulado. El diseño público m-card declara PCB sin fabricar o probar y ajuste comprobado con papel. La API de releases de upstream no ofrecía placas o paquetes publicados al revisar. [Matriz 5Z](goose/FASE-5Z-MCARD-COMPATIBILITY.md) |

## Material pendiente para SPI1

Correspondencia con `fab/bom.csv` y el layout de `ec63784fe94006296ad25bdeb5d32bf7afa834d1`. El diseño ofrece Gerbers, BOM, CPL, layout y generador de redes; no publica un fichero esquemático KiCad independiente. Revisar conectividad contra el layout generado y la tabla de señales de upstream.

| Cantidad | Pieza |
|---|---|
| 1 | PCB m-card de dos capas, 30 × 25,2 mm, 1,0 mm recomendado por el diseño |
| 1 | J1 microSD bisagra Hirose DM3CS-SF, C202111 |
| 1 | J2 XUNPU FPC-05F-10PH20, 10 pines, pitch 0,5 mm, contacto inferior, C2856798 |
| 1 | J3 XUNPU FPC-0.5AS-10PWBH20, mismo pitch, contacto superior, C3446068 |
| 1 | U1 inversor 74LVC1G04 SOT-23-5, C7827 |
| 1 + 2 + 5 | R1 220 Ω C17557; R5/R6 470 Ω C17710; R2/R3/R4/R7/R8 47 kΩ C17713, 0805 |
| 2 | C1 10 µF C15850 y C2 100 nF C49678, 0805 |
| 1 cada uno | FFC tipo A de 10 pines a 0,5 mm, aproximadamente 80 mm; fijación fina aislante; microSD FAT32, sectores de 512 B |

El cable existente del LCD se intercala en J3 y J12 se conecta a J2. La intervención requiere abrir la carcasa y desconectar la batería, con autorización independiente. El diseño no exige soldar la placa principal. Necesita una m-card ensamblada y verificada. La accesibilidad y holgura, la retención de ambos cables y el radio de curvatura deben comprobarse con la plantilla 1:1 para la revisión concreta, incluida la separación de los jacks. La tarjeta del operador muestra `vfat`, dato insuficiente para certificar FAT32. El primer medio validado debe llevar el selector STG2 en `/MBOOT/MENU.BIN` y la biblioteca del proyecto `/FM1/INDEX.CSV`, con archivos de pruebas auditados.

### Comprobaciones para una futura autorización de hardware

1. Verificar fuera del instrumento orientación J2/J3/FFC y pin 1, continuidad de los diez pines y separación de 3V3 J12‑7 respecto a masa J12‑1/10. Probar el inversor: con LCD seleccionado, la SD debe permanecer deseleccionada, también durante reset.
2. Comprobar en banco 3,3 V bajo transitorios con la microSD elegida, consumo y margen del regulador, señales PC7–PC10, integridad de SPI a frecuencia baja y ausencia de contención de DAT0/MISO con D/C de LCD (pin 2). Las resistencias serie limitan corriente. Validar la función con LCD y tarjeta alternados y sin fuente USB externa.
3. Sólo tras documentar esos resultados y autorizar intercalación, comprobar LCD sin tarjeta, detección SPI1, FAT32, opción **SD CARD**, carga STG2 y retorno. Confirmar que la ausencia de tarjeta y fallos de lectura conservan el acceso al menú original.

No se compraron ni instalaron componentes. [PCB y archivos de fabricación fijados](https://github.com/jvitkauskas/m-card/tree/ec63784fe94006296ad25bdeb5d32bf7afa834d1) · [BOM](https://github.com/jvitkauskas/m-card/blob/ec63784fe94006296ad25bdeb5d32bf7afa834d1/fab/bom.csv) · [Conclusión 6A](FASE-6A-MICROSD-DECISION.md)
