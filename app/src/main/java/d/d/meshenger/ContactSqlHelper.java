package d.d.meshenger;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.google.gson.Gson;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


class ContactSqlHelper extends SQLiteOpenHelper {
    private Context context;
    private SQLiteDatabase database = null;

    private final String tableName = "contacts";
    private final String columnID = "id";
    private final String columnName = "name";
    private final String columnPubKey = "pubKey";
    private final String columnInfo = "info";

    private final String tableName2 = "appData";
    private final String columnId = "id";
    private final String columnDbVer = "dbVer";
    private final String columnSecretKey = "secretKey";
    private final String columnPublicKey = "publicKey";
    private final String columnUsername = "username";
    private final String columnMode = "mode";
    private final String columnBlockUC = "blockUC";
    private final String columnLanguage = "language";
    private final String columnListData = "listData";


    public ContactSqlHelper(Context context) {
        super(context, "Contacts.db", null, 2);
        this.context = context;
        createDatabase();
    }

    public List<Contact> getContacts() {
        Cursor cursor = this.database.query(tableName, new String[]{"*"}, "", null, "", "", "");
        ArrayList<Contact> contacts = new ArrayList<>(cursor.getCount());

        if (cursor.moveToFirst()) {
            final int posID = cursor.getColumnIndex(columnID);
            final int posName = cursor.getColumnIndex(columnName);
            final int posPubKey = cursor.getColumnIndex(columnPubKey);
            final int posInfo = cursor.getColumnIndex(columnInfo);
            final int posListData = cursor.getColumnIndex(columnListData);

            do {
                contacts.add(new Contact(
                        cursor.getInt(posID),
                        cursor.getString(posName),
                        cursor.getString(posInfo),
                        cursor.getString(posPubKey),
                        (cursor.getString(posListData).equalsIgnoreCase(""))?new ArrayList<>(): (new Gson().fromJson(cursor.getString(posListData), List.class))
                ));
            } while (cursor.moveToNext());
        }
        cursor.close();

        return contacts;
    }

    public AppData getAppData() {
        Cursor cursor = this.database.query(tableName2, new String[]{"*"}, "", null, "", "", "");
        AppData appData = null;

        if (cursor.moveToFirst()) {
            final int posId = cursor.getColumnIndex(columnId);
            final int posDbVer = cursor.getColumnIndex(columnDbVer);
            final int posSecretKey = cursor.getColumnIndex(columnSecretKey);
            final int posPublicKey = cursor.getColumnIndex(columnPublicKey);
            final int posUsername  = cursor.getColumnIndex(columnUsername);
            final int posMode = cursor.getColumnIndex(columnMode);
            final int posBlockUC = cursor.getColumnIndex(columnBlockUC);
            final int posLanguage = cursor.getColumnIndex(columnLanguage);
            final int posListData = cursor.getColumnIndex(columnListData);

            do {
                appData = new AppData(
                        cursor.getInt(posDbVer),
                        cursor.getString(posSecretKey),
                        cursor.getString(posPublicKey),
                        cursor.getString(posUsername),
                        cursor.getString(posLanguage),
                        cursor.getInt(posMode),
                        cursor.getInt(posBlockUC),
                        (cursor.getString(posListData).equalsIgnoreCase(""))? new ArrayList<>(): (new Gson().fromJson(cursor.getString(posListData), List.class))

                );
            } while (cursor.moveToNext());
        }
        cursor.close();

        return appData;
    }

    public void insertContact(Contact c) throws ContactAlreadyAddedException {
        ContentValues values = new ContentValues(3);
        values.put(columnName, c.getName());
        values.put(columnPubKey, c.getPubKey());
        values.put(columnInfo, c.getInfo());

        Cursor cur = database.query(tableName, new String[]{columnID}, columnPubKey + "=" + DatabaseUtils.sqlEscapeString(c.getPubKey()), null, "", "", "");
        int length = cur.getCount();
        cur.close();
        if (length > 0) {
            throw new ContactAlreadyAddedException();
        }

        c.setId(database.insert(tableName, null, values));
    }

    public void insertAppData(AppData a){
        ContentValues values = new ContentValues(8);
        values.put(columnDbVer, a.getDbVer());
        values.put(columnSecretKey, a.getSecretKey());
        values.put(columnPublicKey, a.getPublicKey());
        values.put(columnUsername, a.getUsername());
        values.put(columnListData, new Gson().toJson(a.getConnectData()));
        values.put(columnMode, a.getMode());
        values.put(columnBlockUC, a.getBlockUC());
        values.put(columnLanguage, a.getLanguage());

        Cursor cur = database.query(tableName2, new String[]{columnID}, columnLanguage + "=" + DatabaseUtils.sqlEscapeString(a.getLanguage()), null, "", "", "");
        int length = cur.getCount();
        cur.close();

        a.setId(database.insert(tableName2, null, values));
    }

    public boolean contactSaved(String publicKey){
        Log.d("SQL", "searching " + publicKey);
        Cursor c = database.query(this.tableName, new String[]{columnID}, columnPubKey + "=?", new String[]{publicKey}, null, null, null);
        boolean has = c.getCount() > 0;
        c.close();
        return has;
    }

    public void updateContact(Contact c) {
        ContentValues values = new ContentValues(5);
        values.put(columnId, c.getId());
        values.put(columnPubKey, c.getPubKey());
        values.put(columnName, c.getName());
        values.put(columnInfo, c.getInfo());
        values.put(columnListData,new Gson().toJson(c.getConnectionData()));

        database.update(tableName, values, columnID + "=" + DatabaseUtils.sqlEscapeString(String.valueOf(c.getId())), null);
    }

    public void deleteContact(Contact c) {
        database.delete(tableName, columnID + "=" + DatabaseUtils.sqlEscapeString(String.valueOf(c.getId()))
                , null);
    }

    public void updateAppData(AppData a) {
        ContentValues values = new ContentValues(8);
        values.put(columnDbVer, a.getDbVer());
        values.put(columnPublicKey, a.getPublicKey());
        values.put(columnSecretKey, a.getSecretKey());
        values.put(columnUsername, a.getUsername());
        values.put(columnListData, new Gson().toJson(a.getConnectData()));
        values.put(columnMode, a.getMode());
        values.put(columnBlockUC, a.getBlockUC());
        values.put(columnLanguage, a.getLanguage());

        database.update(tableName2, values, columnId + "=" + DatabaseUtils.sqlEscapeString(String.valueOf(a.getId())), null);
    }

    public void deleteAppData(AppData a) {
        database.delete(tableName2, columnId + "=" + DatabaseUtils.sqlEscapeString(String.valueOf(a.getId()))
                , null);
    }

    private void createDatabase() {
        if (this.database != null) {
            return;
        }
        this.database = getWritableDatabase();

        this.database.execSQL("CREATE TABLE IF NOT EXISTS " + tableName + "(" +
                columnID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                columnName + " TEXT," +
                columnPubKey + " TEXT," +
                columnInfo + " TEXT," +
                columnListData + " TEXT" +
                ");");

        this.database.execSQL("CREATE TABLE IF NOT EXISTS " + tableName2 + "(" +
                columnId + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                columnDbVer + " INTEGER," +
                columnSecretKey + " TEXT, " +
                columnPublicKey + " TEXT," +
                columnUsername + " TEXT," +
                columnMode + " INTEGER," +
                columnBlockUC + " INTEGER," +
                columnLanguage + " TEXT," +
                columnListData + " TEXT" +
                ");");
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {

    }
    @Override
    protected void finalize() throws Throwable {
        this.close();
        super.finalize();
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {

    }

    class ContactAlreadyAddedException extends Exception {
        @Override
        public String getMessage() {
            return "Contact already added";
        }
    }
}
