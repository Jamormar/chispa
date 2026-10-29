package es.jamormar.chispa;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * Puente entre Android y la web de Chispa.
 * Envía a la web un evento «entrada» cuando la app se abre desde el widget,
 * desde un aviso o desde «Compartir con Chispa».
 */
@CapacitorPlugin(
    name = "ChispaNativo",
    permissions = {
        @Permission(alias = "llamadas", strings = {Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG}),
        @Permission(alias = "bluetooth", strings = {Manifest.permission.BLUETOOTH_CONNECT})
    }
)
public class ChispaNativoPlugin extends Plugin {

    static final String EXTRA_ACCION = "chispa_accion";
    private static final int MAX_IMAGENES = 6;
    private static final long MAX_BYTES = 12L * 1024 * 1024;

    private static ChispaNativoPlugin instancia;
    private static JSObject pendiente;

    @Override
    public void load() {
        instancia = this;
        Correo.programar(getContext());
        Suenos.programar(getContext());
        if (pendiente != null) {
            notifyListeners("entrada", pendiente, true);
            pendiente = null;
        }
    }

    /** Lee el intent con el que se abrió la app y se lo pasa a la web. */
    static void recibir(Context ctx, Intent intent) {
        JSObject datos = leer(ctx, intent);
        if (datos == null) return;
        if (instancia != null) instancia.notifyListeners("entrada", datos, true);
        else pendiente = datos;
    }

    @SuppressWarnings("deprecation")
    private static JSObject leer(Context ctx, Intent intent) {
        if (intent == null) return null;
        String accion = intent.getAction();

        // «Compartir con Chispa» desde otra app
        if (Intent.ACTION_SEND.equals(accion) || Intent.ACTION_SEND_MULTIPLE.equals(accion)) {
            JSObject o = new JSObject();
            o.put("accion", "compartir");
            CharSequence texto = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (texto != null) o.put("texto", texto.toString());
            String asunto = intent.getStringExtra(Intent.EXTRA_SUBJECT);
            if (asunto != null) o.put("asunto", asunto);

            ArrayList<Uri> uris = new ArrayList<>();
            if (Intent.ACTION_SEND.equals(accion)) {
                Uri u = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (u != null) uris.add(u);
            } else {
                ArrayList<Uri> varias = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (varias != null) uris.addAll(varias);
            }
            JSArray imagenes = new JSArray();
            long total = 0;
            for (Uri u : uris) {
                if (imagenes.length() >= MAX_IMAGENES) break;
                String tipo = ctx.getContentResolver().getType(u);
                if (tipo == null || !tipo.startsWith("image/")) continue;
                byte[] bytes = leerBytes(ctx, u);
                if (bytes == null || total + bytes.length > MAX_BYTES) continue;
                total += bytes.length;
                imagenes.put("data:" + tipo + ";base64," + Base64.encodeToString(bytes, Base64.NO_WRAP));
            }
            if (imagenes.length() > 0) o.put("imagenes", imagenes);
            intent.setAction(Intent.ACTION_MAIN);   // para no procesarlo dos veces
            return o;
        }

        // Widget, avisos y accesos directos
        String a = intent.getStringExtra(EXTRA_ACCION);
        if (a != null) {
            JSObject o = new JSObject();
            o.put("accion", a);
            for (String clave : new String[]{"quien", "nota", "duracion"}) {
                String v = intent.getStringExtra("chispa_" + clave);
                if (v != null) o.put(clave, v);
            }
            intent.removeExtra(EXTRA_ACCION);
            return o;
        }
        return null;
    }

    private static byte[] leerBytes(Context ctx, Uri u) {
        try (InputStream in = ctx.getContentResolver().openInputStream(u)) {
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            int n;
            long leidos = 0;
            while ((n = in.read(buf)) > 0) {
                leidos += n;
                if (leidos > MAX_BYTES) return null;
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /** Versión de la app nativa, para mostrarla en Ajustes y saber si hay una nueva. */
    @PluginMethod
    @SuppressWarnings("deprecation")
    public void info(PluginCall call) {
        JSObject r = new JSObject();
        try {
            PackageInfo pi = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0);
            r.put("version", pi.versionName);
            r.put("codigo", Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode);
        } catch (Exception e) {
            r.put("version", "?");
            r.put("codigo", 0);
        }
        r.put("android", Build.VERSION.SDK_INT);
        call.resolve(r);
    }

    private boolean permiso(String alias) {
        if ("bluetooth".equals(alias) && Build.VERSION.SDK_INT < 31) return true;
        return getPermissionState(alias) == PermissionState.GRANTED;
    }

    /** Ajustes de llamadas y coche guardados en el móvil. */
    @PluginMethod
    public void ajustes(PluginCall call) {
        SharedPreferences p = Ajustes.prefs(getContext());
        JSObject r = new JSObject();
        r.put("llamadas", p.getBoolean(Ajustes.LLAMADAS, false) && permiso("llamadas"));
        r.put("coche", p.getBoolean(Ajustes.COCHE, false));
        r.put("cocheDir", p.getString(Ajustes.COCHE_DIR, ""));
        r.put("cocheNombre", p.getString(Ajustes.COCHE_NOMBRE, ""));
        r.put("enCoche", Ajustes.enCoche(getContext()));
        r.put("permisoLlamadas", permiso("llamadas"));
        r.put("permisoBluetooth", permiso("bluetooth"));
        call.resolve(r);
    }

    @PluginMethod
    public void guardarAjustes(PluginCall call) {
        SharedPreferences.Editor e = Ajustes.prefs(getContext()).edit();
        if (call.hasOption("llamadas")) e.putBoolean(Ajustes.LLAMADAS, Boolean.TRUE.equals(call.getBoolean("llamadas")));
        if (call.hasOption("cocheDir")) {
            String dir = call.getString("cocheDir", "");
            e.putString(Ajustes.COCHE_DIR, dir);
            e.putString(Ajustes.COCHE_NOMBRE, call.getString("cocheNombre", ""));
            e.putBoolean(Ajustes.COCHE, dir != null && !dir.isEmpty());
        }
        e.apply();
        if (!Ajustes.prefs(getContext()).getBoolean(Ajustes.COCHE, false) && Ajustes.enCoche(getContext()))
            Ajustes.ponerEnCoche(getContext(), false);
        ajustes(call);
    }

    /** Pide el permiso de llamadas («llamadas») o de Bluetooth («bluetooth»). */
    @PluginMethod
    public void pedirPermiso(PluginCall call) {
        String tipo = call.getString("tipo", "llamadas");
        if (permiso(tipo)) { JSObject r = new JSObject(); r.put("concedido", true); call.resolve(r); return; }
        requestPermissionForAlias(tipo, call, "trasPermiso");
    }

    @PermissionCallback
    private void trasPermiso(PluginCall call) {
        JSObject r = new JSObject();
        r.put("concedido", permiso(call.getString("tipo", "llamadas")));
        call.resolve(r);
    }

    /** Dispositivos Bluetooth emparejados, para elegir el del coche. */
    @PluginMethod
    public void dispositivos(PluginCall call) {
        JSArray lista = new JSArray();
        if (!permiso("bluetooth")) { call.reject("Sin permiso de Bluetooth"); return; }
        try {
            BluetoothManager bm = (BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter ad = bm != null ? bm.getAdapter() : null;
            if (ad != null && ad.getBondedDevices() != null) {
                for (BluetoothDevice d : ad.getBondedDevices()) {
                    JSObject o = new JSObject();
                    String nombre = d.getName();
                    o.put("nombre", nombre != null ? nombre : d.getAddress());
                    o.put("dir", d.getAddress());
                    lista.put(o);
                }
            }
        } catch (SecurityException e) {
            call.reject("Sin permiso de Bluetooth");
            return;
        }
        JSObject r = new JSObject();
        r.put("dispositivos", lista);
        call.resolve(r);
    }

    /** Muestra el aviso de «después de una llamada» para probarlo. */
    @PluginMethod
    public void probarLlamada(PluginCall call) {
        Ajustes.avisoLlamada(getContext(), "Prueba de Chispa", "2 min");
        call.resolve();
    }

    /** Activa o quita el modo coche a mano (para probarlo sin el coche). */
    @PluginMethod
    public void probarCoche(PluginCall call) {
        Ajustes.ponerEnCoche(getContext(), Boolean.TRUE.equals(call.getBoolean("dentro", true)));
        ajustes(call);
    }

    /** Abre un enlace fuera de la app (por ejemplo, para descargar la versión nueva). */
    @PluginMethod
    public void abrirUrl(PluginCall call) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(call.getString("url", "")));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("No se pudo abrir el enlace");
        }
    }

    // ---------------------------------------------------------------- resumen por correo

    @PluginMethod
    public void correoEstado(PluginCall call) {
        JSObject r = new JSObject();
        r.put("conectado", Correo.conectado(getContext()));
        r.put("cuenta", Correo.cuenta(getContext()));
        r.put("ultimo", Correo.ultimo(getContext()));
        call.resolve(r);
    }

    /** Devuelve la dirección de Microsoft para dar permiso de envío de correo. */
    @PluginMethod
    public void conectarCorreo(PluginCall call) {
        try {
            JSObject r = new JSObject();
            r.put("url", Correo.urlLogin(getContext(), call.getString("pista", "")));
            call.resolve(r);
        } catch (Exception e) {
            call.reject("No se pudo preparar la conexión");
        }
    }

    @PluginMethod
    public void desconectarCorreo(PluginCall call) {
        Correo.desconectar(getContext());
        correoEstado(call);
    }

    /** Envía el resumen ahora. Con prueba=true se envía aunque no sea la hora ni haya nada. */
    @PluginMethod
    public void enviarResumen(PluginCall call) {
        boolean prueba = Boolean.TRUE.equals(call.getBoolean("prueba", true));
        new Thread(() -> {
            JSObject r = new JSObject();
            r.put("resultado", Correo.enviarSiToca(getContext(), prueba));
            call.resolve(r);
        }).start();
    }

    // ---------------------------------------------------------------- compartir

    /** Guarda el archivo (en base64) en la caché y abre el menú «Compartir» de Android. */
    @PluginMethod
    public void compartirArchivo(PluginCall call) {
        String nombre = call.getString("nombre", "Chispa.pdf").replaceAll("[\\\\/:*?\"<>|]", " ");
        String datos = call.getString("datos", "");
        String tipo = call.getString("tipo", "application/pdf");
        String asunto = call.getString("asunto", "");
        try {
            java.io.File carpeta = new java.io.File(getContext().getCacheDir(), "compartir");
            if (carpeta.exists()) { java.io.File[] viejos = carpeta.listFiles(); if (viejos != null) for (java.io.File f : viejos) f.delete(); }
            else carpeta.mkdirs();
            java.io.File archivo = new java.io.File(carpeta, nombre);
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(archivo)) { out.write(Base64.decode(datos, Base64.DEFAULT)); }
            Uri uri = androidx.core.content.FileProvider.getUriForFile(getContext(), "es.jamormar.chispa.archivos", archivo);
            Intent enviar = new Intent(Intent.ACTION_SEND);
            enviar.setType(tipo);
            enviar.putExtra(Intent.EXTRA_STREAM, uri);
            if (!asunto.isEmpty()) { enviar.putExtra(Intent.EXTRA_SUBJECT, asunto); enviar.putExtra(Intent.EXTRA_TITLE, asunto); }
            enviar.setClipData(android.content.ClipData.newRawUri(nombre, uri));
            enviar.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent menu = Intent.createChooser(enviar, "Compartir nota");
            menu.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getActivity().startActivity(menu);
            call.resolve();
        } catch (Exception e) {
            call.reject("No se pudo compartir: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- pregunta de la mañana (Sueños)

    @PluginMethod
    public void suenos(PluginCall call) {
        SharedPreferences p = Ajustes.prefs(getContext());
        SharedPreferences.Editor e = p.edit();
        if (call.hasOption("activo")) e.putBoolean(Suenos.P_ACTIVO, Boolean.TRUE.equals(call.getBoolean("activo")));
        if (call.hasOption("minutos")) e.putInt(Suenos.P_MINUTOS, Math.max(0, Math.min(120, call.getInt("minutos", 15))));
        if (call.hasOption("respaldo")) e.putString(Suenos.P_RESPALDO, call.getString("respaldo", ""));
        e.commit();
        Suenos.programar(getContext());
        JSObject r = new JSObject();
        r.put("activo", Suenos.activo(getContext()));
        r.put("minutos", p.getInt(Suenos.P_MINUTOS, 15));
        r.put("respaldo", p.getString(Suenos.P_RESPALDO, "07:30"));
        r.put("proximo", p.getLong(Suenos.P_PROXIMO, 0));
        r.put("despertador", p.getLong(Suenos.P_DESPERTADOR, 0));
        call.resolve(r);
    }

    @PluginMethod
    public void probarSueno(PluginCall call) {
        Suenos.avisar(getContext(), true);
        call.resolve();
    }
}
