package es.jamormar.chispa;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * Puente entre Android y la web de Chispa.
 * Envía a la web un evento «entrada» cuando la app se abre desde el widget,
 * desde un aviso o desde «Compartir con Chispa».
 */
@CapacitorPlugin(name = "ChispaNativo")
public class ChispaNativoPlugin extends Plugin {

    static final String EXTRA_ACCION = "chispa_accion";
    private static final int MAX_IMAGENES = 6;
    private static final long MAX_BYTES = 12L * 1024 * 1024;

    private static ChispaNativoPlugin instancia;
    private static JSObject pendiente;

    @Override
    public void load() {
        instancia = this;
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

    /** Versión de la app nativa, para mostrarla en Ajustes. */
    @PluginMethod
    public void info(PluginCall call) {
        JSObject r = new JSObject();
        try {
            r.put("version", getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0).versionName);
        } catch (Exception e) {
            r.put("version", "?");
        }
        r.put("android", Build.VERSION.SDK_INT);
        call.resolve(r);
    }
}
