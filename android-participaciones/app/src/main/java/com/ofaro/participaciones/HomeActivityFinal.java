package com.ofaro.participaciones;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Launcher estable de O Faro Gestión 5.
 * Conserva el shell operativo v5 de una sola Activity y añade acceso directo a las
 * herramientas avanzadas terminadas sin duplicar la lógica diaria de reservas/promociones.
 */
public class HomeActivityFinal extends HomeActivityV2 {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        addToolboxButton();
    }

    private void addToolboxButton() {
        ImageView tools = new ImageView(this);
        tools.setImageResource(R.drawable.ic_grid_v5);
        tools.setColorFilter(Color.WHITE);
        tools.setPadding(V5Ui.dp(this,12),V5Ui.dp(this,12),V5Ui.dp(this,12),V5Ui.dp(this,12));
        tools.setBackground(V5Ui.bg(this,V5Ui.GREEN,23));
        tools.setElevation(V5Ui.dp(this,3));
        tools.setContentDescription("Herramientas avanzadas");
        tools.setOnClickListener(v -> showToolbox());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(V5Ui.dp(this,46),V5Ui.dp(this,46),Gravity.LEFT|Gravity.BOTTOM);
        lp.leftMargin=V5Ui.dp(this,20);
        lp.bottomMargin=V5Ui.dp(this,67);
        addContentView(tools,lp);
    }

    private void showToolbox() {
        String[] options={
                "Estadísticas completas",
                "Plantillas de impresión",
                "Cola de impresión",
                "Crear ticket",
                "Promociones avanzadas",
                "Gestión web",
                "Copia de seguridad",
                "Diagnóstico"
        };
        new AlertDialog.Builder(this)
                .setTitle("O Faro · Herramientas")
                .setItems(options,(d,which)->{
                    switch(which){
                        case 0: open(StatsDashboardActivity.class); break;
                        case 1: open(V5TemplateManagerActivity.class); break;
                        case 2: open(PrintQueueActivity.class); break;
                        case 3: open(FreePrintActivity.class); break;
                        case 4: open(PromotionsV5Activity.class); break;
                        case 5: open(WebManagementActivity.class); break;
                        case 6: open(BackupRestoreActivity.class); break;
                        default: open(DiagnosticsActivity.class); break;
                    }
                })
                .setNegativeButton("Cerrar",null)
                .show();
    }

    private void open(Class<?> activityClass){
        startActivity(new Intent(this,activityClass));
        overridePendingTransition(0,0);
    }
}
