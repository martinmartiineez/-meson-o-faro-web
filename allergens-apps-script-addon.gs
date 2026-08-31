/*
  Mesón O Faro · complemento de alérgenos para el Apps Script en producción.

  En la función que construye los objetos de la carta, la pestaña "Carta" ahora
  tiene una novena columna (I) llamada "Alérgenos".

  Añade esta propiedad al objeto de cada plato:

      alergenos: r[8] || ''

  Ejemplo:

  const carta = cartaRows.slice(1).filter(r => r[2]).map(r => ({
    id: r[0],
    categoria: r[1],
    producto: r[2],
    descripcion: r[3] || '',
    precioMedia: numOrNull_(r[4]),
    precioRacion: numOrNull_(r[5]),
    disponible: yes_(r[6]),
    orden: Number(r[7]) || 0,
    alergenos: r[8] || ''
  }));

  Después guarda y actualiza la implementación existente con una nueva versión.
  La web ya está preparada para mostrar los alérgenos como etiquetas cuando
  esta propiedad llegue en la API.
*/
