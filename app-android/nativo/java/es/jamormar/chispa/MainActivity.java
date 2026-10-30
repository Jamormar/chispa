package es.jamormar.chispa;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

/**
 * Pantalla principal de Visitario: carga la web y le pasa lo que llega
 * desde el widget, desde «Compartir» o desde los avisos.
 */
public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(ChispaNativoPlugin.class);
        super.onCreate(savedInstanceState);
        // Al volver desde «Recientes» o al recrearse la pantalla, Android repite la última orden
        // (por ejemplo, el botón Ancla del widget). Solo se atiende cuando es nueva de verdad.
        boolean repetida = savedInstanceState != null
                || (getIntent().getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0;
        if (!repetida && !vueltaLogin(getIntent())) ChispaNativoPlugin.recibir(this, getIntent());
        Correo.programar(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!vueltaLogin(intent)) ChispaNativoPlugin.recibir(this, intent);
    }

    /** Vuelta de Microsoft tras conectar el correo (es.jamormar.chispa://auth?code=…). */
    private boolean vueltaLogin(Intent intent) {
        Uri d = intent != null ? intent.getData() : null;
        if (d == null || !"es.jamormar.chispa".equals(d.getScheme())) return false;
        intent.setData(null);
        new Thread(() -> {
            String r = Correo.terminarLogin(getApplicationContext(), d);
            runOnUiThread(() -> {
                try { getBridge().getWebView().loadUrl(Correo.WEB + "?correo=" + Uri.encode(r)); } catch (Exception ignorado) { }
            });
        }).start();
        return true;
    }
}
