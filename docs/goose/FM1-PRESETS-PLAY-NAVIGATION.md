# Navegación PRESETS/PLAY

PRESETS produce eventos lógicos NEXT/PREVIOUS mediante el decoder de cuadratura. PLAY continúa usando el adaptador de pulsación nueva con debounce y repetición controlada; una pulsación mantenida durante el arranque no se transforma en evento porque el adaptador exige transición estable desde reposo.

La secuencia de PLAY es: entrada seleccionada → verificación/planificación → resultado visible o bloqueo. No se añade autorización implícita de Flash. Cambios de selección, desconexión, hash incorrecto y formato desconocido terminan en bloqueo, nunca en salto.

La HAL host puede entregar encoder A/B y botones por separado. El decoder descarta transiciones inválidas y no bloquea el loop. Los límites circular/no circular permanecen responsabilidad de `mb_event` y del core.
