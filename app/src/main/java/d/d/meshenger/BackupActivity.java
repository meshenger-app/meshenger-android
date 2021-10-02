package d.d.meshenger;

import com.github.isabsent.filepicker.SimpleFilePickerDialog;
import static com.github.isabsent.filepicker.SimpleFilePickerDialog.CompositeMode.FILE_OR_FOLDER_SINGLE_CHOICE;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;


public class BackupActivity extends MeshengerActivity implements ServiceConnection,
        SimpleFilePickerDialog.InteractionListenerString {
    private static final String SELECT_PATH_REQUEST = "SELECT_PATH_REQUEST";
    private static final int REQUEST_PERMISSION = 0x01;
    private AlertDialog.Builder builder;
    private Button exportButton;
    private Button importButton;
    private ImageButton selectButton;
    private TextView pathEditText;
    private TextView passwordEditText;
    private MainService.MainBinder binder;

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

        bindService();
        initViews();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (this.binder != null) {
            unbindService(this);
        }
    }

    private void bindService() {
        // ask MainService to get us the binder object
        Intent serviceIntent = new Intent(this, MainService.class);
        bindService(serviceIntent, this, Service.BIND_AUTO_CREATE);
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        this.binder = (MainService.MainBinder) iBinder;
        initViews();
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        this.binder = null;
    }

    private void initViews() {
        if (this.binder == null) {
            return;
        }

        builder = new AlertDialog.Builder(this);
        importButton = findViewById(R.id.ImportButton);
        exportButton = findViewById(R.id.ExportButton);
        selectButton = findViewById(R.id.SelectButton);
        pathEditText = findViewById(R.id.PathEditText);
        passwordEditText = findViewById(R.id.PasswordEditText);

        importButton.setOnClickListener((View v) -> {
            if (Utils.hasReadPermission(BackupActivity.this)) {
                importDatabase();
            } else {
                Utils.requestReadPermission(BackupActivity.this, REQUEST_PERMISSION);
            }
        });

        exportButton.setOnClickListener((View v) -> {
            if (Utils.hasReadPermission(BackupActivity.this) && Utils.hasWritePermission(BackupActivity.this)) {
                exportDatabase();
            } else {
                Utils.requestWritePermission(BackupActivity.this, REQUEST_PERMISSION);
                Utils.requestReadPermission(BackupActivity.this, REQUEST_PERMISSION);
            }
        });

        selectButton.setOnClickListener((View v) -> {
            if (Utils.hasReadPermission(BackupActivity.this)) {
                final String rootPath = Environment.getExternalStorageDirectory().getAbsolutePath();
                showListItemDialog(getResources().getString(R.string.button_select), rootPath, FILE_OR_FOLDER_SINGLE_CHOICE, SELECT_PATH_REQUEST);
            } else {
                Utils.requestReadPermission(BackupActivity.this, REQUEST_PERMISSION);
            }
        });
    }

    private void exportDatabase() {
        String password = passwordEditText.getText().toString();
        String path = pathEditText.getText().toString();

        if (path == null || path.isEmpty()) {
            showErrorMessage(getResources().getString(R.string.error), getResources().getString(R.string.no_path_selected));
            return;
        }

        if ((new File(path)).isDirectory() || path.endsWith("/")) {
            showErrorMessage(getResources().getString(R.string.error), getResources().getString(R.string.no_file_name));
            return;
        }

        if ((new File(path)).exists()) {
            showErrorMessage(getResources().getString(R.string.error), getResources().getString(R.string.file_already_exists));
            return;
        }

        try {
            Database db = this.binder.getDatabase();
            Database.store(path, db, password);
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            showErrorMessage(getResources().getString(R.string.error), e.getMessage());
        }
    }

    private void importDatabase() {
        String password = passwordEditText.getText().toString();
        String path = pathEditText.getText().toString();

        if (path == null || path.isEmpty()) {
            showErrorMessage(getResources().getString(R.string.error), getResources().getString(R.string.no_path_selected));
            return;
        }

        if ((new File(path)).isDirectory() || path.endsWith("/")) {
            showErrorMessage(getResources().getString(R.string.error), getResources().getString(R.string.no_file_name));
            return;
        }

        if (!(new File(path)).exists()) {
            showErrorMessage(getResources().getString(R.string.error), getResources().getString(R.string.file_does_not_exist));
            return;
        }

        try {
            Database db = Database.load(path, password);
            this.binder.replaceDatabase(db);
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            showErrorMessage(getResources().getString(R.string.error), e.toString());
        }
    }

    // path picker
    @Override
    public void showListItemDialog(String title, String folderPath, SimpleFilePickerDialog.CompositeMode mode, String dialogTag) {
        SimpleFilePickerDialog.build(folderPath, mode)
                .title(title)
                .show(this, dialogTag);
    }

    @Override
    public boolean onResult(@NonNull String dialogTag, int which, @NonNull Bundle extras) {
        switch (dialogTag) {
            case SELECT_PATH_REQUEST:
                if (extras.containsKey(SimpleFilePickerDialog.SELECTED_SINGLE_PATH)) {
                    String path = extras.getString(SimpleFilePickerDialog.SELECTED_SINGLE_PATH);
                    if ((new File(path)).isDirectory()) {
                        // append slash
                        if (!path.endsWith("/")) {
                            path += "/";
                        }
                        path += "meshenger_backup.json";
                    }
                    pathEditText.setText(path);
                }
                break;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION:
                if (Utils.allGranted(grantResults)) {
                    // permissions granted
                    Toast.makeText(getApplicationContext(), "Permissions granted - please try again.", Toast.LENGTH_SHORT).show();
                } else {
                    showErrorMessage("Permissions Required", "Action cannot be performed.");
                }
                break;
        }
    }

    private void log(String s) {
        Log.d(this, s);
    }
}
