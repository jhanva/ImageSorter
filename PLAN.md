# Plan de trabajo: ImageSorter

> Estado (v0.3.0): Fases 0, 1, 2 y 4 implementadas; de la Fase 3 se implemento la calibracion para carpetas pequenas, la deteccion de duplicados (badge en revision) y el punto 3.4: se investigo e integro CCIP (deepghs/ccip_onnx, caformer 24) como modelo "Anime (personajes)", especializado en identidad de personajes anime. Pendientes: ruteo por texto zero-shot (3.1) y migracion del indexado a WorkManager (1.2).

Objetivo: que la app sea realmente util para organizar carpetas de imagenes de anime y videojuegos, con una interfaz minimalista, 100% offline, y con el menor numero de pasos posible entre "abrir la app" y "imagenes movidas a su carpeta correcta".

Estado de partida (v0.2.6): flujo funcional de indexado, analisis por similitud (MobileNet TFLite y MobileCLIP ONNX) y movimiento via SAF. Puntos debiles detectados: demasiados pasos manuales (agregar, indexar, analizar, revisar), UI recargada de tarjetas y texto, strings hardcodeados en ingles, modelo por defecto poco adecuado para anime, README desactualizado.

---

## Fase 0 - Quick wins (base y limpieza)

Bajo esfuerzo, alto impacto inmediato.

1. **Modelo Semantic (MobileCLIP) por defecto.** Hoy el default es FAST (MobileNet Small, `SettingsDataStore.kt`), el peor para ilustraciones. Renombrar las opciones por caso de uso: "Semantico (recomendado para anime/arte)", "Rapido", "Preciso".
2. **Actualizar README.** Eliminar el modo manual (ya no existe en codigo), documentar multi-carpeta destino/origen, la formula de score actual y el backend ONNX.
3. **Extraer strings a `strings.xml` y localizar a espanol.** Hoy todo esta hardcodeado en los composables en ingles (`HomeScreen.kt`, `ResultsScreen.kt`). Prerrequisito para cualquier rediseno.
4. **Corregir textos obsoletos.** La EngineCard dice "bundled TFLite models" cuando ya hay ONNX.
5. **Confirmacion al quitar carpeta.** Hoy "Remove from library" es un boton grande sin confirmacion.

Entregable: release v0.3.0 con defaults correctos y app en espanol.

## Fase 1 - Flujo UX: menos pasos, mas resultado

El valor real de la app es mover imagenes con el minimo esfuerzo. Hoy el usuario debe: agregar carpetas, indexar destinos, indexar origenes, analizar, revisar, mover. Meta: dos toques (Analizar -> Mover).

1. **Pipeline de un toque.** El boton "Analizar" detecta carpetas sin indexar o desactualizadas, las indexa automaticamente y encadena el analisis. Eliminar los botones separados "Index destinations" / "Index sources" (dejar reindexado manual solo en Settings).
2. **Indexado/analisis resistente.** Mover el trabajo largo a WorkManager con notificacion de progreso, para que sobreviva al salir de la app (hoy corre en el scope del ViewModel).
3. **Revision rapida en resultados:**
   - Vista de cuadricula por carpeta destino con secciones colapsables y "seleccionar todo" por seccion.
   - Accion "Mover todo lo de alta confianza" (score y margen altos) en un toque.
   - Toque en miniatura abre preview a pantalla completa con zoom (hoy la miniatura es de 96 dp y no se puede ampliar; critico para decidir sobre arte).
   - Deshacer el ultimo movimiento (registrar origen de cada archivo movido y ofrecer revertir).
4. **Grupo "sin asignar" util.** Para imagenes bajo el umbral, mostrar chips con las 2-3 carpetas candidatas y su score para asignarlas con un toque, en lugar del dialogo generico actual.
5. **Recordar sesion.** Si el usuario cierra la app a mitad de revision, volver a la lista de sugerencias pendientes (ya se persisten en Room; falta la navegacion directa).

Entregable: release v0.4.0. Metrica de exito: organizar una carpeta de 500 imagenes en menos de 5 minutos de atencion activa.

## Fase 2 - UI minimalista (rediseno visual)

Principio: una accion primaria por pantalla, menos contenedores anidados, mas espacio en blanco.

1. **Home como dashboard simple.** Hoy hay 4 tarjetas grandes compitiendo (hero, engine, destinos, origenes). Propuesta:
   - Un encabezado con estado ("N carpetas, M imagenes indexadas") y un solo boton primario contextual (el patron ya existe en `HomeVisuals`, pero queda enterrado entre tarjetas).
   - Carpetas destino y origen como listas compactas (fila con icono, nombre, contador y menu overflow), no tarjetas con botones full-width.
   - Mover la seleccion de modelo y el perfil de ejecucion a Settings: son decisiones de una sola vez, no de cada sesion.
2. **Color y tema.** Adoptar dynamic color (Material You) con la paleta teal actual como fallback. Revisar contraste del modo oscuro existente.
3. **Iconografia consistente (assets gratis):**
   - Opcion A: Material Symbols (Apache 2.0), integracion directa con Compose.
   - Opcion B: Phosphor (MIT) o Lucide (ISC) para un look mas distintivo con trazo uniforme.
   - Todos permiten uso comercial sin atribucion; elegir uno solo y unificar.
4. **Ilustraciones para estados vacios:** unDraw (gratis, sin atribucion, SVG recoloreable al color primario del tema). Ya existen `illus_no_photos` y `illus_all_clean`; unificar estilo en todos los estados vacios y de error.
5. **Tipografia:** una fuente variable de Google Fonts con licencia OFL (Inter, Manrope o Space Grotesk) para dar identidad sin costo.
6. **Animaciones:** mantener Lottie solo para la marca (BrandLottie); para el resto usar animaciones de Compose (transiciones de pantalla, progreso animado). Si se descargan Lottie de LottieFiles, verificar licencia por asset (solo los marcados como Lottie Simple License son libres).
7. **Accesibilidad:** contentDescription en todas las imagenes accionables, tamanos de toque minimos de 48 dp.

Entregable: release v0.5.0 con el rediseno completo y capturas actualizadas en el README.

## Fase 3 - Utilidad real del motor ML

1. **Ruteo por texto (zero-shot).** Agregar el text encoder de MobileCLIP (ONNX + tokenizador, offline) para que una carpeta nueva funcione sin imagenes de referencia: el usuario escribe una descripcion ("fanart de Zelda", "capturas de Genshin") y se compara contra ella. Es la feature que elimina el problema de arranque en frio de carpetas nuevas. Requiere investigacion previa (tamano del modelo, tokenizador en Kotlin).
2. **Deteccion de duplicados.** Con los embeddings ya calculados, detectar near-duplicates (similitud > 0.98) dentro del origen y entre origen y destino; ofrecer omitirlos o listarlos para limpieza.
3. **Calibracion para carpetas pequenas.** Con pocas imagenes de referencia el topK y el centroide se degradan; ajustar pesos del score segun el tamano de la carpeta destino.
4. **Investigacion (timebox):** evaluar modelos alternativos especializados en anime (p. ej. encoders tipo WD-tagger exportados a ONNX) contra MobileCLIP con un set de prueba propio de carpetas reales del usuario.

Entregable: release v0.6.0. Metrica: porcentaje de sugerencias aceptadas sin cambio de destino (medible localmente).

## Fase 4 - Rendimiento y robustez

1. Ajustar lote y workers de indexado midiendo throughput real en el S23 Ultra (GPU/NNAPI vs CPU).
2. Cache de miniaturas de Coil dimensionada para carpetas de miles de imagenes.
3. Limpieza automatica de embeddings huerfanos (imagenes borradas fuera de la app).
4. Tests para los flujos nuevos (pipeline de un toque, undo, ruteo por texto); el CI de GitHub Actions ya existe.

## Orden y dependencias

```
Fase 0 (1-2 dias)  ->  Fase 1 (1-2 semanas)  ->  Fase 2 (1-2 semanas)
                                             \->  Fase 3 (2-3 semanas, en paralelo con Fase 2)
Fase 4: continua, intercalada
```

La Fase 0 va primero porque los strings localizados y el default de modelo condicionan todo lo demas. Fase 1 antes que Fase 2: primero que el flujo sea corto, despues hacerlo bonito, para no redisenar pantallas que van a cambiar de estructura.

## Fuentes de assets gratuitos (licencias verificadas)

| Recurso | Tipo | Licencia | Atribucion |
|---------|------|----------|------------|
| Material Symbols | Iconos | Apache 2.0 | No requerida |
| Phosphor Icons | Iconos | MIT | No requerida |
| Lucide | Iconos | ISC | No requerida |
| unDraw | Ilustraciones SVG | Propia, libre comercial | No requerida |
| Google Fonts (Inter, Manrope) | Tipografia | OFL | No requerida |
| LottieFiles | Animaciones | Varia por asset | Verificar cada una |
