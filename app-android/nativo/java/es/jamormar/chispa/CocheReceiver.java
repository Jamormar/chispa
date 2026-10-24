package es.jamormar.chispa;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Entra en modo coche al conectarse al Bluetooth del coche elegido y sale al desconectarse. */
public class CocheReceiver extends BroadcastReceiver {

    @Override
    @SuppressWarnings("deprecation")
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null) return;
        String accion = intent.getAction();
        boolean conecta = BluetoothDevice.ACTION_ACL_CONNECTED.equals(accion);
        if (!conecta && !BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(accion)) return;
        SharedPreferences p = Ajustes.prefs(ctx);
        if (!p.getBoolean(Ajustes.COCHE, false)) return;
        String elegido = p.getString(Ajustes.COCHE_DIR, "");
        BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (d == null || elegido.isEmpty()) return;
        String dir;
        try { dir = d.getAddress(); } catch (SecurityException e) { return; }
        if (!elegido.equalsIgnoreCase(dir)) return;
        if (conecta != Ajustes.enCoche(ctx)) Ajustes.ponerEnCoche(ctx, conecta);
    }
}
