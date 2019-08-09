package d.d.meshenger;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;


class Database extends SQLiteOpenHelper {
    private Context context;
    private SQLiteDatabase database = null;

    private final String contactTableName = "contacts";
    private final String columnID = "id";
    private final String columnName = "name";
    private final String columnPubKey = "pubKey";
    private final String columnInfo = "info";

    private final String AppDataTableName = "appData";
    private final String columnId = "id";
    private final String columnDbVer = "dbVer";
    private final String columnSecretKey = "secretKey";
    private final String columnPublicKey = "publicKey";
    private final String columnUsername = "username";
    private final String columnMode = "mode";
    private final String columnBlockUC = "blockUC";
    private final String columnLanguage = "language";
    private final String columnListData = "listData";

    public Database(Context context) {
        super(context, "Contacts.db", null, 2);
        this.context = context;
        createDatabase();
    }

    public List<Contact> getContacts() {
        Cursor cursor = this.database.query(contactTableName, new String[]{"*"}, "", null, "", "", "");
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
                    parseConnectionData(cursor.getString(posListData))
                ));
            } while (cursor.moveToNext());
        }
        cursor.close();

        return contacts;
    }

    public AppData getAppData() {
        Cursor cursor = this.database.query(AppDataTableName, new String[]{"*"}, "", null, "", "", "");
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
                        (cursor.getInt(posBlockUC) != 0),
                    parseConnectionData(cursor.getString(posListData))
                );
            } while (cursor.moveToNext());
        }
        cursor.close();

        return appData;
    }

    private List<ConnectionData> parseConnectionData(String data) {
        if (data.isEmpty()) {
            return new ArrayList<>();
        }

        return new Gson().fromJson(data, List.class);
    }

    public void insertContact(Contact c) throws ContactAlreadyAddedException {
        ContentValues values = new ContentValues(3);
        values.put(columnName, c.getName());
        values.put(columnPubKey, c.getPubKey());
        values.put(columnInfo, c.getInfo());

        Cursor cur = database.query(contactTableName, new String[]{columnID}, columnPubKey + "=" + DatabaseUtils.sqlEscapeString(c.getPubKey()), null, "", "", "");
        int length = cur.getCount();
        cur.close();
        if (length > 0) {
            throw new ContactAlreadyAddedException();
        }

        c.setId(database.insert(contactTableName, null, values));
    }

    public void insertAppData(AppData a) {
        ContentValues values = new ContentValues(8);
        values.put(columnDbVer, a.getDbVer());
        values.put(columnSecretKey, a.getSecretKey());
        values.put(columnPublicKey, a.getPublicKey());
        values.put(columnUsername, a.getUsername());
        values.put(columnListData, new Gson().toJson(a.getConnectionData()));
        values.put(columnMode, a.getMode());
        values.put(columnBlockUC, (a.getBlockUC() ? 1 : 0));
        values.put(columnLanguage, a.getLanguage());

        Cursor cur = database.query(AppDataTableName, new String[]{columnID}, columnLanguage + "=" + DatabaseUtils.sqlEscapeString(a.getLanguage()), null, "", "", "");
        //int length = cur.getCount();
        cur.close();

        a.setId(database.insert(AppDataTableName,null, values));
    }

    public boolean contactSaved(String publicKey){
        Log.d("SQL", "searching " + publicKey);
        Cursor c = database.query(this.contactTableName, new String[]{columnID}, columnPubKey + "=?", new String[]{publicKey}, null, null, null);
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
        values.put(columnListData, new Gson().toJson(c.getConnectionData()));

        database.update(contactTableName, values, columnID + "=" + DatabaseUtils.sqlEscapeString(String.valueOf(c.getId())), null);
    }

    public void deleteContact(Contact c) {
        database.delete(contactTableName, columnID + "=" + DatabaseUtils.sqlEscapeString(String.valueOf(c.getId())), null);
    }

    public void updateAppData(AppData a) {
        ContentValues values = new ContentValues(8);
        values.put(columnDbVer, a.getDbVer());
        values.put(columnPublicKey, a.getPublicKey());
        values.put(columnSecretKey, a.getSecretKey());
        values.put(columnUsername, a.getUsername());
        values.put(columnListData, new Gson().toJson(a.getConnectionData()));
        values.put(columnMode, a.getMode());
        values.put(columnBlockUC, a.getBlockUC());
        values.put(columnLanguage, a.getLanguage());

        database.update(AppDataTableName, values, columnId + "=" + DatabaseUtils.sqlEscapeString(String.valueOf(a.getId())), null);
    }

    private void createDatabase() {
        if (this.database != null) {
            return;
        }

        this.database = getWritableDatabase();

        this.database.execSQL("CREATE TABLE IF NOT EXISTS " + contactTableName + "(" +
            columnID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            columnName + " TEXT," +
            columnListData + " TEXT," +
            columnPubKey + " TEXT," +
            columnInfo + " TEXT" +
            ");"
        );

        this.database.execSQL("CREATE TABLE IF NOT EXISTS " + AppDataTableName + "(" +
            columnId + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            columnDbVer + " INTEGER," +
            columnSecretKey + " TEXT, " +
            columnPublicKey + " TEXT," +
            columnUsername + " TEXT," +
            columnMode + " INTEGER," +
            columnBlockUC + " INTEGER," +
            columnLanguage + " TEXT," +
            columnListData + " TEXT" +
            ");"
        );
    }

    public String getPublicKeyFromContacts(String listData){
        String pubKey = "";
        String query = " SELECT * FROM " + contactTableName + " WHERE " + columnListData + " = " + "'" + listData + "'";
        Cursor cursor = database.rawQuery(query,null);
        if (cursor.moveToFirst()) {
            pubKey = cursor.getString(cursor.getColumnIndex(columnPubKey));
        }
        return pubKey;
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        // nothing to do
    }

    @Override
    protected void finalize() throws Throwable {
        this.close();
        super.finalize();
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        // nothing to do
    }

    class ContactAlreadyAddedException extends Exception {
        @Override
        public String getMessage() {
            return "Contact already added";
        }
    }
}
