package com.qategori.kartu;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Pasang ulang jadwal cek artikel setelah HP menyala. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        NewsJob.schedule(c);
    }
}
