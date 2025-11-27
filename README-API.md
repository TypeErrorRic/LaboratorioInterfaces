# Laboratorio Interfaces - API JAR

## Descripción
Este proyecto genera dos archivos JAR:
1. **Laboratorio1-1.0-SNAPSHOT.jar** - Aplicación completa con GUI
2. **Laboratorio1-1.0-SNAPSHOT-api.jar** - Solo las clases de la API (paquete `com.myproject.laboratorio1.api`)

## Generación del JAR de API

### Opción 1: Usando Maven (si está instalado)
```bash
mvn clean package
```

### Opción 2: Manualmente con jar.exe
```powershell
# 1. Compilar el proyecto (si usas VS Code, Ctrl+Shift+B)
# 2. Ejecutar desde la raíz del proyecto:
cd target\classes
& "C:\Program Files\Java\jdk-21\bin\jar.exe" cvf ..\Laboratorio1-1.0-SNAPSHOT-api.jar com\myproject\laboratorio1\api\
cd ..\..
```

Los JARs se generarán en la carpeta `target/`.

## Uso del JAR de API en otros proyectos

### 1. Agregar como dependencia en otro proyecto Maven

Copia el JAR `Laboratorio1-1.0-SNAPSHOT-api.jar` a tu proyecto y agrégalo al `pom.xml`:

```xml
<dependency>
    <groupId>com.myproject</groupId>
    <artifactId>Laboratorio1</artifactId>
    <version>1.0-SNAPSHOT</version>
    <classifier>api</classifier>
    <scope>system</scope>
    <systemPath>${project.basedir}/lib/Laboratorio1-1.0-SNAPSHOT-api.jar</systemPath>
</dependency>
```

### 2. Agregar al classpath manualmente

```bash
javac -cp "Laboratorio1-1.0-SNAPSHOT-api.jar;mysql-connector-java-8.0.33.jar" MiPrograma.java
java -cp ".;Laboratorio1-1.0-SNAPSHOT-api.jar;mysql-connector-java-8.0.33.jar" MiPrograma
```

### 3. Ejemplo de uso

```java
import com.myproject.laboratorio1.api.*;

public class MiPrograma {
    public static void main(String[] args) {
        // Usar la API
        DBConnection db = DBConnection.getInstance();
        IntUsuariosDAO usuariosDAO = new IntUsuariosDAO(db);
        IntProcesoDAO procesoDAO = new IntProcesoDAO(db);
        
        // Tus operaciones...
    }
}
```

## Clases incluidas en el API JAR

- `DBConnection` - Gestión de conexión a base de datos (Singleton)
- `IntUsuariosDAO` - Gestión de usuarios y programación de procesos
- `IntProcesoDAO` - Gestión de procesos (experimentos)
- `IntProcesoDataDAO` - Gestión de datos de variables y referencias
- `IntProcesoDefinicionesDAO` - Gestión de definiciones de procesos

## Dependencias necesarias

El JAR de API requiere:
- MySQL Connector/J 8.0.33 o superior
- JDK 21 o superior

## Estructura de la base de datos

El API espera la base de datos `laboratorio_virtual` con las tablas:
- `int_usuarios`
- `int_usuarios_proceso`
- `int_proceso`
- `int_proceso_vars`
- `int_proceso_vars_data`
- `int_proceso_refs`
- `int_proceso_refs_data`

Ver `src/main/java/com/db/laboratorio_virtual.sql` para el esquema completo.
