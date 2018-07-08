package d.d.meshenger;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;


class ContactSqlHelper extends SQLiteOpenHelper {
    private Context context;
    private SQLiteDatabase database = null;

    private final String tableName = "contacts";
    private final String columnID = "id";
    private final String columnIP = "address";
    private final String columnName = "name";
    private final String columnPhoto = "photo";
    private final String columnIdentifier = "identifier";

    public ContactSqlHelper(Context context) {
        super(context, "Contacts.db", null, 1);
        this.context = context;
        createDatabase();
    }

    public List<Contact> getContacts() {
        Cursor cursor = this.database.query(tableName, new String[]{columnID, columnIP, columnPhoto, columnName, columnIdentifier}, "", null, "", "", "");
        ArrayList<Contact> contacts = new ArrayList<>(cursor.getCount());

        if(cursor.moveToFirst()){
            final int posID = cursor.getColumnIndex(columnID);
            final int posIP = cursor.getColumnIndex(columnIP);
            final int posName = cursor.getColumnIndex(columnName);
            final int posPhoto = cursor.getColumnIndex(columnPhoto);
            final int posIdentifier = cursor.getColumnIndex(columnIdentifier);
            do{
                contacts.add(new Contact(
                        cursor.getInt(posID),
                        cursor.getString(posIP),
                        cursor.getString(posName),
                        cursor.getString(posPhoto),
                        cursor.getString(posIdentifier)
                ));
            }while(cursor.moveToNext());
        }
        cursor.close();

        return contacts;
    }

    public void close(){
        super.close();
        database.close();
    }

    public void insertContact(Contact c) throws ContactAlreadyAddedException{
        ContentValues values = new ContentValues(3);
        //values.put(columnID, c.getId());
        values.put(columnIdentifier, c.getIdentifier());
        values.put(columnIP, c.getAddress());
        values.put(columnName, c.getName());
        values.put(columnPhoto, c.getPhoto());

        Cursor cur = database.query(tableName, new String[]{columnID}, columnIdentifier + "=\"" + c.getIdentifier() + "\"", null, "", "", "");
        int length = cur.getCount();
        cur.close();
        if(length > 0){
            throw new ContactAlreadyAddedException();
        }


        c.setId(database.insert(tableName, null, values));
    }

    public void updateContact(Contact c) {
        ContentValues values = new ContentValues(2);
        values.put(columnIP, c.getAddress());
        values.put(columnPhoto, c.getPhoto());
        values.put(columnName, c.getName());
        values.put(columnIdentifier, c.getIdentifier());

        database.update(tableName, values, columnID + "=" + c.getId(), null);
    }

    public void deleteContact(Contact c){
        database.delete(tableName, columnID + "=" + c.getId(), null);
    }

    private void createDatabase() {
        if (this.database != null) {
            return;
        }
        this.database = getWritableDatabase();
        this.database.execSQL("CREATE TABLE IF NOT EXISTS " + tableName + "(" +
                columnID + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                columnIP + " TEXT, " +
                columnName + " TEXT," +
                columnIdentifier + " TEXT," +
                columnPhoto + " TEXT" +
                ");");
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {

    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {

    }

    class ContactAlreadyAddedException extends Exception{
        @Override
        public String getMessage() {
            return "Contact already added";
        }
    }
}
