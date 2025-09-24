# 3. PROCEDIMIENTO ✨

> En el curso de **Interfaces** se desarrollará una serie de aplicativos para un **laboratorio virtual**.  
> Uno de ellos será una app en **Java** para **supervisar y gestionar** un hardware conectado por **puerto serie**.

---

## 🎛️ Objetivo de esta práctica
Implementar una **GUI** para la **supervisión** y **gestión** del hardware.

<details>
  <summary>📦 Alcance (haz clic para ver)</summary>

- Conectar con el dispositivo por puerto serie.
- Seleccionar señales **analógicas** y **digitales** de **entrada**.
- Visualizar señales **en función del tiempo**.
- **Almacenar** datos en archivos.
- **Controlar** salidas digitales.
- **Configurar** y **mostrar** el tiempo de muestreo.
</details>

---

## ✅ Requerimientos (checklist)

### 🔌 Entradas analógicas
- [ ] Selección entre **8 señales analógicas** de entrada.
- [ ] **Visualización** de la señal analógica seleccionada *(vs. tiempo)*.
- [ ] **Almacenamiento** en archivo de datos  
      Formato: `valor_de_la_señal, tiempo`.

### 🧠 Entradas digitales
- [ ] Selección entre **4 señales digitales** de entrada.
- [ ] **Visualización** de la señal digital seleccionada *(vs. tiempo)*.
- [ ] **Almacenamiento** en archivo de datos  
      Formato: `valor_de_la_señal, tiempo`.

### 🚦 Salidas digitales
- [ ] **Activación / desactivación** de **4 señales digitales** de salida.

### ⏱️ Muestreo
- [ ] **Visualización** del **tiempo de muestreo** actual.
- [ ] **Cambio** del **tiempo de muestreo**.
