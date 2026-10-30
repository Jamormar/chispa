package es.jamormar.chispa;

import androidx.core.content.FileProvider;

/** Da acceso temporal a los PDF que se comparten desde Visitario (carpeta de caché «compartir»). */
public class ArchivosCompartidos extends FileProvider {
    public ArchivosCompartidos() {
        super(R.xml.chispa_compartir);
    }
}
