# Admin Control (Fabric 1.21.1)

ESTO ES CÓDIGO FUENTE: hay que COMPILARLO para obtener el .jar (el .jar correcto pesa ~100 KB, no 1 KB).

## Opción A: sin instalar nada (GitHub Actions)
1. Crea cuenta en github.com y un repositorio nuevo (privado está bien).
2. Sube TODO el contenido de esta carpeta (incluida la carpeta oculta .github).
3. Pestaña "Actions" -> espera el check verde (2-4 min) -> abre la ejecución -> abajo "Artifacts" -> descarga admincontrol-jar.
4. Descomprime el zip: dentro está admincontrol-1.0.0.jar. Ponlo en la carpeta mods.
Si sale rojo, abre el paso "gradle build" y copia el error.

## Opción B: en tu PC
1. Instala JDK 21 (adoptium.net) y Gradle 8.10 (gradle.org/install).
2. En esta carpeta: gradle build
3. El jar queda en build/libs/admincontrol-1.0.0.jar (NO el -sources).

## Usar
Escribe /ac en el juego. Config en config/admincontrol.json

## Novedades
- /ac > Items y bloqueos: catálogo por categorías (como el panel del video), buscador, detalle del ítem
  (bloquear ítem / receta / proteger bloque / permitir a jugador / obtener).
- /ac > Evento: reparte jugadores al azar y de forma equilibrada entre los anfitriones (ej. 75 jugadores / 5 admins = 15 c/u),
  los teletransporta dispersos alrededor de cada anfitrión, con cuenta atrás, vista previa, "barajar de nuevo" y "devolver a todos".
  Comandos: /ac event | run | shuffle | return | cancel

## Panel gráfico (cliente)
Con el mod instalado en tu cliente, `/ac` (o la tecla K) abre un panel propio con barra lateral:
Items (categorías, buscador, detalle), Spawn, Daño, Reglas, Jugadores, Evento y Vanish/Admin.
Si un jugador NO tiene el mod, /ac le abre el menú de cofre (/ac chest lo fuerza).
Los admins pueden usar /vanish directamente.

## Reglas extremas: por qué "no pasa nada"
Se necesitan las dos cosas: interruptor general ENCENDIDO y no estar en creativo (o activar "Afecta a creativo").
Al añadir una regla desde el panel el interruptor general se enciende solo, y si una regla se ignora el juego te avisa en la barra de acción.

## Permisos
El panel, /ac, /vanish, el bastón y la varita son SOLO para OP (o el dueño de un mundo de un jugador).
A los demás jugadores no se les envía nada y los comandos ni les aparecen.

## Transición del evento
Al teletransportar: cortinas negras que se cierran, título "Teletransportando", TP, y las cortinas se abren con "¡Equipo de X!".
Con el mod instalado se ve la transición completa; sin el mod se usa título + ceguera como alternativa.

## Contadores
Panel > Evento > Contadores. Formato 5:00 / 90s / 10 (=minutos). Barra superior para todos, avisos a 1:00, 0:30 y 0:10,
"¡Tiempo!" al llegar a 0 y aviso a los admins para que hagan el TP (o TP automático opcional).
Comandos: /ac timer 5:00 Nombre · /ac timer stop

## Cambios recientes
- "Sin aire en puertas": ahora detecta burbujas de aire bajo el agua (cabeza fuera del agua + agua encima + puerta/trampilla junto a la cabeza).
- Jugadores > Items: botones "Copiar a...", "A todos" y "Copiar de..." para pasar las excepciones de items entre jugadores
  (incluye jugadores que ya no están conectados). "Todos" en el selector de jugadores para dar un item a varios a la vez.
- Textos recortados/ajustados, tooltips con salto de línea, caché de bloqueos en el servidor y de iconos en el cliente.
