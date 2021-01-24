package d.d.meshenger;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;


public class BackupActivity extends MeshengerActivity {
    private static final String TAG = "BackupActivity";
    private static final int READ_REQUEST_CODE = 0x01;
    private static final int WRITE_REQUEST_CODE = 0x02;
    private AlertDialog.Builder builder;
    private Button exportButton;
    private Button importButton;
    private ImageButton selectButton;
    private TextView passwordEditText;

    private void showErrorMessage(String title, String message) {
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup);

        initViews();
    }

    private void initViews() {
        builder = new AlertDialog.Builder(this);
        importButton = findViewById(R.id.ImportButton);
        exportButton = findViewById(R.id.ExportButton);
        selectButton = findViewById(R.id.SelectButton);
        passwordEditText = findViewById(R.id.PasswordEditText);

        importButton.setOnClickListener((View v) -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            startActivityForResult(intent, READ_REQUEST_CODE);
        });

        exportButton.setOnClickListener((View v) -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_TITLE, "meshenger-backup.json");
            intent.setType("application/json");
            startActivityForResult(intent, WRITE_REQUEST_CODE);
        });
    }

    private void exportDatabase(Uri uri) {
        String password = passwordEditText.getText().toString();

        try {
            Database db = MainService.instance.getDatabase();
            byte[] data = Database.toData(db, password);
            Utils.writeExternalFile(this, uri, data);
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            showErrorMessage(getResources().getString(R.string.error), e.getMessage());
        }
    }

    private void importDatabase(Uri uri) {
        String password = passwordEditText.getText().toString();

        try {
            byte[] data = Utils.readExternalFile(this, uri);
            Database db = Database.fromData(data, password);
            MainService.instance.replaceDatabase(db);
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            showErrorMessage(getResources().getString(R.string.error), e.toString());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK) {
            return;
        }

        if (data == null || data.getData() == null) {
            return;
        }

        switch (requestCode) {
        case READ_REQUEST_CODE:
            importDatabase(data.getData());
            break;
        case WRITE_REQUEST_CODE:
            exportDatabase(data.getData());
            break;
        }
    }
}
