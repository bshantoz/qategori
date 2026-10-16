package com.qategori.kartu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

/** Layar pengaturan sederhana: pilih level kartu kotoba, ulangi hapalan, buka situs. */
public class MainActivity extends Activity {
    private float density = 1f;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        density = getResources().getDisplayMetrics().density;
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private int dp(int v) {
        return (int) (v * density);
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(null, Typeface.BOLD);
        }
        t.setPadding(0, dp(8), 0, dp(4));
        return t;
    }

    private void build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFFFDFBF7);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(40), dp(20), dp(24));
        sv.addView(root);

        root.addView(text("Qategori Kartu", 26, true, 0xFF1B2440));
        root.addView(text("Widget kartu hapalan kotoba dan kanji hari ini untuk layar utama HP.", 14, false, 0xFF6B7186));

        root.addView(text("Cara memasang widget", 16, true, 0xFF1B2440));
        root.addView(text("1. Tahan bagian kosong layar utama, lalu pilih Widget.\n2. Cari \"Qategori Kartu\".\n3. Seret \"Kartu kotoba\" atau \"Kanji hari ini\" ke layar utama.", 14, false, 0xFF1B2440));

        root.addView(text("Level kartu kotoba", 16, true, 0xFF1B2440));
        RadioGroup g = new RadioGroup(this);
        String cur = Data.level(this);
        for (final String lv : Data.LV) {
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(lv.toUpperCase() + " · " + Data.done(this, lv).size() + "/" + Data.words(this, lv).length() + " hapal");
            rb.setTextSize(15);
            rb.setChecked(lv.equals(cur));
            rb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Data.setLevel(MainActivity.this, lv);
                    KotobaWidget.refresh(MainActivity.this);
                }
            });
            g.addView(rb);
        }
        root.addView(g);

        Button reset = new Button(this);
        reset.setText("Ulangi hapalan level ini dari awal");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("Hapus tanda hapal untuk level ini?")
                        .setPositiveButton("Ya", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int w) {
                                Data.reset(MainActivity.this, Data.level(MainActivity.this));
                                KotobaWidget.refresh(MainActivity.this);
                                build();
                            }
                        })
                        .setNegativeButton("Batal", null)
                        .show();
            }
        });
        root.addView(reset);

        Button open = new Button(this);
        open.setText("Buka Qategori");
        open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://qategori.com")));
            }
        });
        root.addView(open);

        root.addView(text("Catatan: hapalan di aplikasi ini tersimpan di HP dan belum tersambung dengan hapalan di situs.", 12, false, 0xFF6B7186));
        setContentView(sv);
    }
}
