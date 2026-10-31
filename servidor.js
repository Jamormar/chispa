// Dirección del servidor de Cuaderna (Azure Functions). Vacía = sin servidor: la app funciona igual, sin IA/voz compartidas ni estadísticas.
window.CUADERNA_API = "";
// Estadísticas anónimas de difusión: solo se suma 1 al contador del día de cada evento; no se envía nada de la persona.
window.cuadernaEvento = function(e){
  try { const b = String(window.CUADERNA_API || "").replace(/\/+$/, ""); if (!b) return;
    fetch(b + "/evento", {method: "POST", headers: {"Content-Type": "text/plain"}, body: JSON.stringify({e}), keepalive: true, mode: "cors"}).catch(() => {}); } catch(err){}
};
