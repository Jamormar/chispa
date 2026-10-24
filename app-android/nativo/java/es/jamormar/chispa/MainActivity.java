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
        ChispaNativoPlugin.recibir(this, getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        ChispaNativoPlugin.recibir(this, intent);
    }
}
