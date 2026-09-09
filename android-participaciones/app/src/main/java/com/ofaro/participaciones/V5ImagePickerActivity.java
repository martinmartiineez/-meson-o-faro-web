package com.ofaro.participaciones;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/** Activity mínima para seleccionar una imagen con SAF y devolverla al editor v5. */
public class V5ImagePickerActivity extends Activity {
    private static final int PICK_IMAGE = 5109;
    private boolean launched = false;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) launched = state.getBoolean("launched", false);
        if (!launched) launchPicker();
    }

    private void launchPicker() {
        launched = true;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, PICK_IMAGE);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("launched", launched);
        super.onSaveInstanceState(out);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_IMAGE) return;
        String encoded = null;
        if (resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {}
                try {
                    encoded = ImageUtil.toDataUri(this, uri.toString());
                } catch (Exception ignored) {}
            }
        }
        V5PrintPreview.deliverPickedImage(encoded);
        finish();
        overridePendingTransition(0, 0);
    }

    @Override public void onBackPressed() {
        V5PrintPreview.deliverPickedImage(null);
        super.onBackPressed();
    }
}
