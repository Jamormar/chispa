package es.jamormar.chispa;

import android.content.Intent;
import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

/**
 * Pantalla principal de Chispa: carga la web y le pasa lo que llega
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
        if (!repetida) ChispaNativoPlugin.recibir(this, getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        ChispaNativoPlugin.recibir(this, intent);
    }
}
