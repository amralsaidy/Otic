package com.asb.otic.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.asb.otic.model.Score;

import java.util.ArrayList;

/**
 * Created by Amr Alsaidy on 2017/11/1.
 */

public class DatabaseHandler extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "otic.db",
            TABLE_WINS = "wins",
            KEY_ID = "id",
            KEY_SIZE = "size",
            KEY_NAME = "name";

    public DatabaseHandler(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);

    }


    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_WINS +
                "(" + KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," + KEY_SIZE + " INTEGER," + KEY_NAME + " TEXT " + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_WINS);
        onCreate(db);
    }

    public void addWin(Score score) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(KEY_SIZE, score.getSize());
        values.put(KEY_NAME, score.getName());
        db.insert(TABLE_WINS, null, values);
        db.close();
    }

    public ArrayList<Score> getWins(int size) {
        ArrayList<Score> scores = new ArrayList<>();

        String selectQuery = "SELECT " + KEY_NAME + " , COUNT(*) AS no_wins FROM " + TABLE_WINS +
                " WHERE " + KEY_SIZE + " = " + size +
                " GROUP BY " + KEY_NAME +
                " ORDER BY no_wins DESC";

        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                Score score = new Score();
                score.setName(cursor.getString(0));
                score.setNoWins(Integer.parseInt(cursor.getString(1)));
                scores.add(score);
            } while (cursor.moveToNext());
        }

        return scores;
    }

    public void deleteAll() {
        SQLiteDatabase db = this.getWritableDatabase();
        String deleteQuery = "DELETE FROM " + TABLE_WINS;
        db.execSQL(deleteQuery);
        db.close();
    }

}
